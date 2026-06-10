package com.happymouse.cryd.service;

import com.happymouse.cryd.model.entity.*;
import com.happymouse.cryd.repository.*;
import com.happymouse.cryd.service.rag.RagService;
import com.happymouse.cryd.service.spark.SparkClient;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 引导式对话服务 — 数字人主动提问收集画像 + 知识库增强资源生成
 *
 * 核心职责：
 * 1. 计算画像完整度，判断新用户/老用户
 * 2. 生成引导式 System Prompt（融入专业、错题历史等多维信息）
 * 3. 从学生回答中实时提取画像维度并更新数据库
 * 4. 画像达标时，先检索知识库再用 RAG 增强生成个性化资源
 *
 * 维度可扩展：新增维度只需
 *   ① Student 表加字段 ② DIMENSIONS 列表加一条 ③ PROFILE_SYSTEM_PROMPT JSON 加 key
 */
@Service
public class OnboardingService {
    private static final Logger log = LoggerFactory.getLogger(OnboardingService.class);

    private final StudentRepository studentRepo;
    private final SysUserRepository sysUserRepo;
    private final ErrorNotebookRepository errorRepo;
    private final ChatMessageRepository chatRepo;
    private final LearningResourceRepository resourceRepo;
    private final SparkClient spark;
    private final RagService ragService;

    // ============================================================
    // 维度定义（可自主拓展）
    // 新增维度：在这里加一条即可
    // ============================================================
    public static final List<DimensionDef> DIMENSIONS = List.of(
        new DimensionDef("interestDirection", "目标方向", 20,
            "学C语言你想往哪个方向发展？嵌入式开发、游戏开发、后端服务、还是先打好基础？",
            "选择的领域或方向，如：嵌入式/后端/游戏/基础"),
        new DimensionDef("weakAreas", "薄弱环节", 20,
            "之前学的编程内容里，感觉哪块最难？指针、内存管理、还是递归？",
            "薄弱的知识点，如：指针/数组/递归/内存管理"),
        new DimensionDef("className", "专业方向", 15,
            "你是哪个专业/班级的？",
            "专业或班级，如：计算机科学/软件工程/自动化"),
        new DimensionDef("knowledgeLevel", "知识水平", 15,
            "现在C语言大概什么水平？零基础、学过一点、还是已经能写小程序了？",
            "0-100的数字"),
        new DimensionDef("learningPreference", "学习偏好", 10,
            "你喜欢哪种学习方式？看视频教程、读文档、还是直接敲代码？",
            "video(视频)/doc(文档)/exercise(做题)/mixed(混合)"),
        new DimensionDef("learningPace", "学习节奏", 10,
            "你大概多久能学一次？每天都能学、一周两三次、还是偶尔有空？",
            "fast(快)/steady(稳)/slow(慢)"),
        new DimensionDef("studyMotivation", "学习动机", 5,
            "学C语言主要为了什么？考试、兴趣、还是就业？",
            "考试/兴趣/就业/技能提升"),
        new DimensionDef("focusLevel", "专注程度", 5,
            "学习的时候容易走神吗？还是能比较专注？",
            "high(专注)/medium(一般)/low(容易分心)")
    );

    // ============================================================
    // 构造函数
    // ============================================================
    public OnboardingService(StudentRepository studentRepo, SysUserRepository sysUserRepo,
                             ErrorNotebookRepository errorRepo, ChatMessageRepository chatRepo,
                             LearningResourceRepository resourceRepo,
                             SparkClient spark, RagService ragService) {
        this.studentRepo = studentRepo;
        this.sysUserRepo = sysUserRepo;
        this.errorRepo = errorRepo;
        this.chatRepo = chatRepo;
        this.resourceRepo = resourceRepo;
        this.spark = spark;
        this.ragService = ragService;
    }

    // ============================================================
    // System Prompt 生成
    // ============================================================

    /**
     * 生成引导式 System Prompt（融入了专业、错题历史等完整上下文）
     */
    public String buildSystemPrompt(Long sysUserId) {
        Student student = studentRepo.findByUsername("student_" + sysUserId).orElse(null);
        if (student == null) return buildNewUserPrompt("同学");

        String name = student.getNickname() != null ? student.getNickname() : "同学";
        int completeness = calcCompleteness(student);

        // 获取 SysUser 的专业/班级信息
        SysUser sysUser = sysUserRepo.findById(sysUserId).orElse(null);
        String className = sysUser != null ? sysUser.getClassName() : null;
        String department = sysUser != null ? sysUser.getDepartment() : null;

        StringBuilder sb = new StringBuilder();
        sb.append("你是「小智老师」，一个友好的 AI 学习助手。\n\n");

        if (completeness < 50) {
            sb.append("## 当前任务：了解学生情况\n\n");
            sb.append(name).append("同学，需要了解TA的学习情况。\n\n");
            sb.append("## 对话规则（必须遵守）：\n");
            sb.append("1. **每次只问一个问题**，等学生回答了再继续\n");
            sb.append("2. 先打个招呼，简短介绍自己，然后自然地开始提问\n");
            sb.append("3. 语气轻松友好，不要像做调查问卷\n");
            sb.append("4. 学生回答后，先肯定一下，再自然地转入下一个问题\n");
            sb.append("5. 问完 4-5 个关键问题后，告诉学生「差不多了，已帮你生成了学习路径和资料，去首页看看吧！」\n");
            sb.append("6. 不要重复问已经知道答案的问题\n\n");
            sb.append("## 需要了解（按优先级）：\n");
            for (DimensionDef d : DIMENSIONS) {
                String status = isDimensionFilled(student, d.field) ? " ✓已知" : " ★待了解";
                sb.append("- ").append(d.label).append(status).append("\n");
            }
            sb.append("\n## 学生已知信息：\n");
            sb.append(buildKnownInfo(student, className, department));
        } else {
            sb.append("## 当前任务：欢迎回来 + 快速了解变化\n\n");
            sb.append(name).append("回来了。\n");
            sb.append("## 学生信息：\n");
            sb.append(buildKnownInfo(student, className, department));
            sb.append("\n## 对话规则：\n");
            sb.append("1. 欢迎TA回来，提一下上次学到哪了或之前的知识薄弱点\n");
            sb.append("2. 问1-2个关键变化（最近有新目标？哪块还需要加强？）\n");
            sb.append("3. 如果没什么变化就直接进入辅导模式\n");
        }
        return sb.toString();
    }

    private String buildNewUserPrompt(String name) {
        return "你是「小智老师」，一个友好的 AI 学习助手。\n\n" +
            "## 当前任务：了解初次见面的同学\n\n" +
            "这位同学叫" + name + "。你需要通过轻松聊天了解TA。\n\n" +
            "## 对话规则：\n" +
            "1. **每次只问一个问题**，等学生回答了再继续\n" +
            "2. 打招呼 + 简短自我介绍，然后自然开始提问\n" +
            "3. 语气轻松友好，不要像做调查问卷\n" +
            "4. 学生回答后，先肯定，再问下一个问题\n" +
            "5. 问完4-5个关键问题后，告诉学生「差不多了，已帮你生成学习路径和资料！」\n" +
            "6. 不要重复问已知答案的问题\n\n" +
            "## 需要了解（按优先级）：\n" +
            "- 专业方向和学习目标\n" +
            "- 当前编程水平和薄弱环节\n" +
            "- 喜欢的学习方式和节奏\n" +
            "- 学习动机";
    }

    // ============================================================
    // 完整度计算
    // ============================================================

    /** 计算画像完整度（0-100），基于 DIMENSIONS 权重 */
    public int calcCompleteness(Student s) {
        int total = 0;
        if (isFilled(s, "interestDirection")) total += getWeight("interestDirection");
        if (isFilled(s, "weakAreas")) total += getWeight("weakAreas");
        if (isFilled(s, "className")) total += getWeight("className");
        if (s.getKnowledgeLevel() != null && s.getKnowledgeLevel() > 0) total += getWeight("knowledgeLevel");
        if (s.getLearningPreference() != null && !"mixed".equals(s.getLearningPreference())) total += getWeight("learningPreference");
        if (s.getLearningPace() != null && !"steady".equals(s.getLearningPace())) total += getWeight("learningPace");
        if (s.getStudyMotivation() != null) total += getWeight("studyMotivation");
        if (s.getFocusLevel() != null) total += getWeight("focusLevel");
        return total;
    }

    /** 获取下一道该问的问题（未填维度中权重最高的） */
    public String getNextUnfilledQuestion(Student s) {
        for (DimensionDef d : DIMENSIONS) {
            if (!isDimensionFilled(s, d.field)) return d.question;
        }
        return null;
    }

    /** 列出已填/未填的维度状态 */
    public List<Map<String, Object>> getDimensionStatus(Student s) {
        return DIMENSIONS.stream().map(d -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("field", d.field);
            m.put("label", d.label);
            m.put("weight", d.weight);
            m.put("filled", isDimensionFilled(s, d.field));
            m.put("question", d.question);
            return m;
        }).collect(Collectors.toList());
    }

    // ============================================================
    // 画像提取（关键词规则 + AI 双重）
    // ============================================================

    public void extractAndUpdateProfile(Student student, String studentMessage) {
        if (studentMessage == null || studentMessage.isBlank()) return;
        try {
            applyKeywordRules(student, studentMessage);
            applyAIRules(student, studentMessage);
            studentRepo.save(student);
        } catch (Exception e) {
            log.warn("画像提取失败: {}", e.getMessage());
        }
    }

    private void applyAIRules(Student student, String msg) {
        try {
            String jsonSpec = buildExtractionJsonSpec(student);
            String aiResult = spark.chat(jsonSpec, msg, 0.2f, 512);
            String json = aiResult.replaceAll("```json\\s*", "").replaceAll("```\\s*", "").trim();
            JSONObject obj = JSON.parseObject(json);
            if (obj.getString("interestDirection") != null) student.setInterestDirection(obj.getString("interestDirection"));
            if (obj.getIntValue("knowledgeLevel") > 0) student.setKnowledgeLevel(obj.getIntValue("knowledgeLevel"));
            if (obj.getString("learningPreference") != null) student.setLearningPreference(obj.getString("learningPreference"));
            if (obj.getString("learningPace") != null) student.setLearningPace(obj.getString("learningPace"));
            if (obj.getString("weakAreas") != null) student.setWeakAreas(obj.getString("weakAreas"));
            if (obj.getString("studyMotivation") != null) student.setStudyMotivation(obj.getString("studyMotivation"));
            if (obj.getString("focusLevel") != null) student.setFocusLevel(obj.getString("focusLevel"));
        } catch (Exception e) {
            log.debug("AI画像提取跳过: {}", e.getMessage());
        }
    }

    private String buildExtractionJsonSpec(Student s) {
        return "你是学生画像分析师。从学生的回答中提取学习相关维度，输出JSON：\n" +
            "{\n" +
            "  \"interestDirection\": \"方向如：嵌入式/后端/游戏/基础\",\n" +
            "  \"knowledgeLevel\": 0-100的数字,\n" +
            "  \"learningPreference\": \"video/doc/exercise/mixed\",\n" +
            "  \"learningPace\": \"fast/steady/slow\",\n" +
            "  \"weakAreas\": \"薄弱知识点\",\n" +
            "  \"studyMotivation\": \"考试/兴趣/就业/技能提升\",\n" +
            "  \"focusLevel\": \"high/medium/low\"\n" +
            "}\n" +
            "规则：明确表达的用原话；模糊的合理推断；没提到的用null；只输出JSON。";
    }

    private void applyKeywordRules(Student s, String msg) {
        String m = msg != null ? msg.toLowerCase() : "";
        if (m.contains("嵌入")) s.setInterestDirection("嵌入式");
        else if (m.contains("游戏")) s.setInterestDirection("游戏开发");
        else if (m.contains("后端") || m.contains("服务器")) s.setInterestDirection("后端开发");
        else if (m.contains("基础") || m.contains("考") || m.contains("成绩")) s.setInterestDirection("打好基础");
        else if (m.contains("就业") || m.contains("工作")) s.setInterestDirection("就业准备");
        if (m.contains("零基础") || m.contains("没学过") || m.contains("不会")) s.setKnowledgeLevel(10);
        else if (m.contains("学过一点") || m.contains("有点基础")) s.setKnowledgeLevel(35);
        else if (m.contains("能写") || m.contains("会写") || m.contains("熟练")) s.setKnowledgeLevel(65);
        if (m.contains("视频") || m.contains("看教程")) s.setLearningPreference("video");
        else if (m.contains("文档") || m.contains("看书") || m.contains("读")) s.setLearningPreference("doc");
        else if (m.contains("敲") || m.contains("做") || m.contains("练") || m.contains("代码")) s.setLearningPreference("exercise");
        if (m.contains("每天") || m.contains("天天")) s.setLearningPace("fast");
        else if (m.contains("偶尔") || m.contains("没时间")) s.setLearningPace("slow");
        if (m.contains("考试") || m.contains("期末") || m.contains("及格")) s.setStudyMotivation("考试");
        else if (m.contains("兴趣") || m.contains("喜欢")) s.setStudyMotivation("兴趣");
        else if (m.contains("就业") || m.contains("工作") || m.contains("找实习")) s.setStudyMotivation("就业");
    }

    // ============================================================
    // 知识库增强资源生成
    // ============================================================

    /**
     * 从知识库检索与画像相关的知识点，然后生成个性化资源
     * 流程：画像关键词 → RAG检索知识库 → 用检索结果增强 LLM 生成 → 保存 LearningResource
     *
     * @return 生成的资源数量
     */
    public int generateResourcesFromKnowledgeBase(Long sysUserId) {
        Student student = studentRepo.findByUsername("student_" + sysUserId).orElse(null);
        if (student == null) return 0;

        // 1. 从画像中提取搜索关键词
        List<String> searchQueries = buildSearchQueries(student);
        if (searchQueries.isEmpty()) return 0;

        // 2. 对每个查询词检索知识库
        Set<String> seenContent = new HashSet<>();
        List<String> allReferences = new ArrayList<>();
        for (String query : searchQueries) {
            try {
                List<RagService.KnowledgeFragment> fragments = ragService.retrieveRelevant(query, 3);
                for (RagService.KnowledgeFragment f : fragments) {
                    if (f.content != null && seenContent.add(f.content.substring(0, Math.min(80, f.content.length())))) {
                        allReferences.add("- [" + f.tags + "] " + f.content);
                    }
                }
            } catch (Exception e) {
                log.warn("知识库检索失败: query={}, err={}", query, e.getMessage());
            }
        }

        // 3. 构建资源生成 Prompt（知识库文献 + 学生画像）
        String referencesText = allReferences.isEmpty()
            ? "（知识库暂无相关文献，请基于C语言基础知识生成）"
            : String.join("\n", allReferences);

        String resourcePrompt = buildResourcePrompt(student, referencesText);

        // 4. LLM 生成资源（高 temperature 保证多样性）
        String aiResult;
        try {
            aiResult = spark.chat(resourcePrompt, "请生成个性化学习资源", 0.4f, 4096);
        } catch (Exception e) {
            log.warn("资源生成LLM失败: {}", e.getMessage());
            return 0;
        }

        // 5. 解析 JSON 并保存
        return saveResources(sysUserId, student, aiResult);
    }

    /** 从画像构建检索查询词 */
    private List<String> buildSearchQueries(Student s) {
        List<String> queries = new ArrayList<>();
        if (s.getInterestDirection() != null && !s.getInterestDirection().isBlank())
            queries.add(s.getInterestDirection() + " C语言");
        if (s.getWeakAreas() != null && !s.getWeakAreas().isBlank()) {
            for (String area : s.getWeakAreas().split("[,，、\\s]+")) {
                if (!area.isBlank()) queries.add(area.trim() + " C语言");
            }
        }
        // 也加错题知识点
        try {
            List<ErrorNotebook> errors = errorRepo.findByStudentIdAndStatus(
                Long.valueOf(s.getUsername().replace("student_", "")), "active");
            errors.stream()
                .map(ErrorNotebook::getKnowledgePoint)
                .filter(Objects::nonNull)
                .distinct()
                .limit(3)
                .forEach(kp -> queries.add(kp + " C语言"));
        } catch (Exception ignored) {}
        if (queries.isEmpty()) queries.add("C语言基础入门");
        return queries.stream().distinct().limit(5).collect(Collectors.toList());
    }

    private String buildResourcePrompt(Student s, String references) {
        String style = s.getCognitiveStyle() != null ? s.getCognitiveStyle() : "visual";
        String pace = s.getLearningPace() != null ? s.getLearningPace() : "steady";
        return """
            你是课程设计师。为一位C语言学生生成个性化学习资源。

            ## 学生画像
            - 知识水平: %d/100
            - 学习偏好: %s
            - 学习节奏: %s
            - 目标方向: %s
            - 薄弱环节: %s

            ## 知识库参考材料（必须基于这些材料生成，不要编造）
            %s

            ## 要求
            生成3个学习资源，输出JSON数组：
            [
              {
                "title": "资源标题",
                "type": "article|exercise|explanation",
                "difficulty": "easy|medium|hard",
                "content": "资源正文（Markdown格式，200-500字）",
                "knowledgePoint": "关联的知识点"
              }
            ]

            ## 规则
            1. 难度匹配学生水平（当前%d/100）
            2. 学习偏好%s，用这种风格写
            3. 只输出JSON数组，别的什么都不说
            4. 基于知识库材料，不要凭空捏造
            """.formatted(
                s.getKnowledgeLevel() != null ? s.getKnowledgeLevel() : 30,
                s.getLearningPreference() != null ? s.getLearningPreference() : "mixed",
                pace,
                s.getInterestDirection() != null ? s.getInterestDirection() : "未指定",
                s.getWeakAreas() != null ? s.getWeakAreas() : "未指定",
                references,
                s.getKnowledgeLevel() != null ? s.getKnowledgeLevel() : 30,
                s.getLearningPreference() != null ? s.getLearningPreference() : "mixed"
            );
    }

    /** 解析 LLM 生成的 JSON 并保存为 LearningResource */
    public int saveResources(Long sysUserId, Student student, String aiResult) {
        try {
            String json = aiResult.replaceAll("```json\\s*", "").replaceAll("```\\s*", "").trim();
            List<LearningResource> resources = new ArrayList<>();

            if (json.startsWith("[")) {
                var arr = JSON.parseArray(json);
                for (int i = 0; i < arr.size(); i++) {
                    JSONObject obj = arr.getJSONObject(i);
                    resources.add(buildResource(sysUserId, obj));
                }
            } else if (json.startsWith("{")) {
                JSONObject obj = JSON.parseObject(json);
                resources.add(buildResource(sysUserId, obj));
            }

            if (!resources.isEmpty()) {
                for (LearningResource res : resources) {
                    resourceRepo.save(res);
                }
            }
            return resources.size();
        } catch (Exception e) {
            log.warn("资源解析失败: {}", e.getMessage());
            return 0;
        }
    }

    private LearningResource buildResource(Long sysUserId, JSONObject obj) {
        LearningResource res = new LearningResource();
        res.setStudentId(sysUserId);
        res.setTitle(obj.getString("title") != null ? obj.getString("title") : "个性化学习资料");
        res.setType(obj.getString("type") != null ? obj.getString("type") : "article");
        res.setDifficulty(obj.getString("difficulty") != null ? obj.getString("difficulty") : "medium");
        res.setContent(obj.getString("content") != null ? obj.getString("content") : "生成内容为空");
        res.setKnowledgePoint(obj.getString("knowledgePoint"));
        res.setGeneratedBy("数字人+知识库");
        res.setCreatedAt(java.time.LocalDateTime.now());
        return res;
    }

    // ============================================================
    // 工具方法
    // ============================================================

    /** 判断某个维度是否已填写 */
    private boolean isDimensionFilled(Student s, String field) {
        return switch (field) {
            case "interestDirection" -> s.getInterestDirection() != null;
            case "weakAreas" -> s.getWeakAreas() != null;
            case "className" -> false; // 从 SysUser 取，这里返回false表示需要从对话中确认
            case "knowledgeLevel" -> s.getKnowledgeLevel() != null && s.getKnowledgeLevel() > 0;
            case "learningPreference" -> s.getLearningPreference() != null && !"mixed".equals(s.getLearningPreference());
            case "learningPace" -> s.getLearningPace() != null && !"steady".equals(s.getLearningPace());
            case "studyMotivation" -> s.getStudyMotivation() != null;
            case "focusLevel" -> s.getFocusLevel() != null;
            default -> true;
        };
    }

    private boolean isFilled(Student s, String field) {
        return isDimensionFilled(s, field);
    }

    private int getWeight(String field) {
        return DIMENSIONS.stream()
            .filter(d -> d.field.equals(field))
            .findFirst().map(d -> d.weight).orElse(0);
    }

    /** 构建已知信息文本（含专业、错题历史、最近对话） */
    private String buildKnownInfo(Student student, String className, String department) {
        StringBuilder sb = new StringBuilder();
        if (className != null && !className.isBlank())
            sb.append("- 专业/班级: ").append(className).append("\n");
        if (department != null && !department.isBlank())
            sb.append("- 学院: ").append(department).append("\n");
        if (student.getInterestDirection() != null)
            sb.append("- 目标方向: ").append(student.getInterestDirection()).append("\n");
        if (student.getKnowledgeLevel() != null)
            sb.append("- 知识水平: ").append(student.getKnowledgeLevel()).append("/100\n");
        if (student.getLearningPreference() != null)
            sb.append("- 学习偏好: ").append(student.getLearningPreference()).append("\n");
        if (student.getLearningPace() != null)
            sb.append("- 学习节奏: ").append(student.getLearningPace()).append("\n");
        if (student.getWeakAreas() != null)
            sb.append("- 薄弱环节: ").append(student.getWeakAreas()).append("\n");
        if (student.getStudyMotivation() != null)
            sb.append("- 学习动机: ").append(student.getStudyMotivation()).append("\n");

        // 错题历史（最近5条）
        try {
            List<ErrorNotebook> errors = errorRepo.findByStudentIdOrderByCreatedAtDesc(
                Long.valueOf(student.getUsername().replace("student_", "")));
            if (!errors.isEmpty()) {
                sb.append("- 错题知识点: ");
                errors.stream().limit(5)
                    .map(ErrorNotebook::getKnowledgePoint)
                    .filter(Objects::nonNull)
                    .distinct()
                    .forEach(kp -> sb.append(kp).append(" "));
                sb.append("\n");
            }
        } catch (Exception ignored) {}

        if (sb.length() == 0) sb.append("- 暂无信息\n");
        return sb.toString();
    }

    // ============================================================
    // 维度定义 record
    // ============================================================
    public record DimensionDef(String field, String label, int weight, String question, String description) {}
}
