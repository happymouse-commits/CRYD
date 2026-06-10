package com.happymouse.cryd.service;

import com.happymouse.cryd.model.entity.Student;
import com.happymouse.cryd.repository.StudentRepository;
import com.happymouse.cryd.service.spark.SparkClient;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 引导式对话服务 — 登录后 AI 主动提问收集画像
 *
 * 核心职责：
 * 1. 计算画像完整度，判断是新用户还是老用户
 * 2. 生成引导式 System Prompt（主动提问而非被动回答）
 * 3. 从学生回答中实时提取画像维度并更新数据库
 */
@Service
public class OnboardingService {
    private static final Logger log = LoggerFactory.getLogger(OnboardingService.class);

    private final StudentRepository studentRepo;
    private final SparkClient spark;

    // 引导问题定义（按优先级排序）
    private static final List<QuestionDef> QUESTIONS = List.of(
        new QuestionDef("interestDirection", 20, "学C语言你想往哪个方向发展？嵌入式开发、游戏开发、后端服务、还是先打好基础考个好成绩？"),
        new QuestionDef("weakAreas", 20, "之前学的编程内容里，感觉哪块最难？指针、内存管理、还是递归？"),
        new QuestionDef("knowledgeLevel", 15, "现在C语言大概什么水平？完全零基础、学过一点、还是已经能写小程序了？"),
        new QuestionDef("learningPreference", 15, "你喜欢哪种学习方式？看视频教程、读文档、还是直接敲代码做练习？"),
        new QuestionDef("learningPace", 10, "你大概多久能学一次？每天都能学、一周两三次、还是偶尔有空？"),
        new QuestionDef("cognitiveStyle", 10, "学新东西的时候，你更喜欢看图理解、听人讲解、还是亲自上手试试？"),
        new QuestionDef("studyMotivation", 5, "你学C语言主要为了什么？应付考试、还是想真正掌握一门编程技能？"),
        new QuestionDef("focusLevel", 5, "学习的时候容易走神吗？还是能比较专注？")
    );

    // 构建画像的系统 Prompt
    private static final String PROFILE_SYSTEM_PROMPT = """
        你是学生画像分析师。学生正在回答关于学习情况的提问。

        请从学生的回答中提取以下维度的信息，输出JSON格式：
        {
          "interestDirection": "选择的领域或方向，如：嵌入式/后端/游戏/基础",
          "knowledgeLevel": "0-100的数字，0=零基础，30=知道一点，60=能写简单程序，80=比较熟练",
          "learningPreference": "video(视频)/doc(文档)/exercise(做题)/mixed(混合)",
          "learningPace": "fast(快节奏)/steady(稳扎稳打)/slow(慢慢来)",
          "cognitiveStyle": "visual(视觉型)/auditory(听觉型)/kinesthetic(动手型)",
          "weakAreas": "薄弱的知识点，如：指针/数组/递归/内存",
          "studyMotivation": "考试/兴趣/就业/技能提升",
          "focusLevel": "high(专注)/medium(一般)/low(容易分心)"
        }

        规则：
        1. 学生明确表达的就用他的说法，不要编造
        2. 学生回答模糊的，用你合理的推断
        3. 学生没有提到的维度，用null表示
        4. 只输出JSON，一句别的话不说
        """;

    public OnboardingService(StudentRepository studentRepo, SparkClient spark) {
        this.studentRepo = studentRepo;
        this.spark = spark;
    }

    /**
     * 生成引导系统的初始 System Prompt（放 system prompt 位置）
     */
    public String buildSystemPrompt(Student student) {
        String name = student.getNickname() != null ? student.getNickname() : "同学";
        int completeness = calcCompleteness(student);

        StringBuilder sb = new StringBuilder();
        sb.append("你是「小智老师」，一个友好的 AI 学习助手。\n\n");

        if (completeness < 50) {
            // 新手模式
            sb.append("## 当前任务：了解学生情况\n\n");
            sb.append("这位同学叫").append(name).append("，刚来还不了解。你需要通过轻松的聊天来了解TA。\n\n");
            sb.append("## 对话规则（必须遵守）：\n");
            sb.append("1. **每次只问一个问题**，等学生回答了再继续\n");
            sb.append("2. 先打个招呼，简短介绍自己，然后自然地开始提问\n");
            sb.append("3. 语气轻松友好，不要像做调查问卷\n");
            sb.append("4. 学生回答后，先肯定一下，再自然地问下一个问题\n");
            sb.append("5. 问完 4-5 个关键问题后，告诉学生「差不多了，已经帮你生成了学习路径和资料，去首页看看吧！」\n");
            sb.append("6. 不要问已经知道答案的问题（比如学生已经说过自己零基础了就别再问水平）\n\n");
            sb.append("## 需要了解的信息（按优先级）：\n");
            sb.append("- 学习目标和兴趣方向\n");
            sb.append("- 当前水平和薄弱环节\n");
            sb.append("- 喜欢的学习方式\n");
            sb.append("- 学习节奏和可投入时间\n\n");
            sb.append("## 学生当前已知信息（不需要问的）：\n");
            sb.append(buildKnownInfo(student));
        } else {
            // 老用户模式
            sb.append("## 当前任务：欢迎回来 + 快速了解变化\n\n");
            sb.append(name).append("回来了，之前的信息如下：\n");
            sb.append(buildKnownInfo(student)).append("\n\n");
            sb.append("## 对话规则：\n");
            sb.append("1. 欢迎TA回来，简单说说上次学到哪了\n");
            sb.append("2. 问1-2个关键变化（比如：最近有啥新的学习目标？感觉之前的内容掌握得怎么样？）\n");
            sb.append("3. 根据TA的回答适当更新学习路径\n");
            sb.append("4. 如果没什么变化就直接进入辅导模式\n");
        }
        return sb.toString();
    }

    /**
     * 计算画像完整度（0-100）
     */
    public int calcCompleteness(Student s) {
        int total = 0;
        if (s.getInterestDirection() != null) total += 20;
        if (s.getWeakAreas() != null) total += 20;
        if (s.getKnowledgeLevel() != null && s.getKnowledgeLevel() > 0) total += 15;
        if (s.getLearningPreference() != null && !"mixed".equals(s.getLearningPreference())) total += 15;
        if (s.getLearningPace() != null && !"steady".equals(s.getLearningPace())) total += 10;
        if (s.getCognitiveStyle() != null && !"visual".equals(s.getCognitiveStyle())) total += 10;
        if (s.getStudyMotivation() != null) total += 5;
        if (s.getFocusLevel() != null) total += 5;
        return total;
    }

    /**
     * 从未填维度中找出下一个该问的问题
     */
    public String getNextUnfilledQuestion(Student s) {
        for (QuestionDef q : QUESTIONS) {
            boolean filled = switch (q.field) {
                case "interestDirection" -> s.getInterestDirection() != null;
                case "weakAreas" -> s.getWeakAreas() != null;
                case "knowledgeLevel" -> s.getKnowledgeLevel() != null && s.getKnowledgeLevel() > 0;
                case "learningPreference" -> s.getLearningPreference() != null && !"mixed".equals(s.getLearningPreference());
                case "learningPace" -> s.getLearningPace() != null && !"steady".equals(s.getLearningPace());
                case "cognitiveStyle" -> s.getCognitiveStyle() != null && !"visual".equals(s.getCognitiveStyle());
                case "studyMotivation" -> s.getStudyMotivation() != null;
                case "focusLevel" -> s.getFocusLevel() != null;
                default -> true;
            };
            if (!filled) return q.question;
        }
        return null; // 所有维度已填写
    }

    /**
     * 从学生回答中提取画像维度并更新数据库
     * 两层策略：
     * 1. 关键词规则快速提取（同步，低延迟）
     * 2. AI 深度解析（补充规则漏掉的维度）
     */
    public void extractAndUpdateProfile(Student student, String studentMessage) {
        if (studentMessage == null || studentMessage.isBlank()) return;

        try {
            // 1. 先用关键词规则快速提取
            applyKeywordRules(student, studentMessage);

            // 2. AI 深度解析
            String aiResult = spark.chat(PROFILE_SYSTEM_PROMPT, studentMessage, 0.2f, 512);
            String json = aiResult
                .replaceAll("```json\\s*", "")
                .replaceAll("```\\s*", "")
                .trim();

            JSONObject obj = JSON.parseObject(json);

            if (obj.containsKey("interestDirection") && !obj.get("interestDirection").equals(obj.get(null)))
                student.setInterestDirection(obj.getString("interestDirection"));
            if (obj.getIntValue("knowledgeLevel") > 0)
                student.setKnowledgeLevel(obj.getIntValue("knowledgeLevel"));
            if (obj.getString("learningPreference") != null)
                student.setLearningPreference(obj.getString("learningPreference"));
            if (obj.getString("learningPace") != null)
                student.setLearningPace(obj.getString("learningPace"));
            if (obj.getString("cognitiveStyle") != null)
                student.setCognitiveStyle(obj.getString("cognitiveStyle"));
            if (obj.getString("weakAreas") != null)
                student.setWeakAreas(obj.getString("weakAreas"));
            if (obj.getString("studyMotivation") != null)
                student.setStudyMotivation(obj.getString("studyMotivation"));
            if (obj.getString("focusLevel") != null)
                student.setFocusLevel(obj.getString("focusLevel"));

            studentRepo.save(student);
        } catch (Exception e) {
            log.warn("画像提取失败: {}", e.getMessage());
        }
    }

    /**
     * 关键词规则快速提取（不依赖 AI，低延迟）
     */
    private void applyKeywordRules(Student s, String msg) {
        String m = msg != null ? msg.toLowerCase() : "";
        if (m.contains("嵌入")) s.setInterestDirection("嵌入式");
        else if (m.contains("游戏")) s.setInterestDirection("游戏开发");
        else if (m.contains("后端") || m.contains("服务器")) s.setInterestDirection("后端开发");
        else if (m.contains("基础") || m.contains("考") || m.contains("成绩")) s.setInterestDirection("打好基础");
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

    private String buildKnownInfo(Student s) {
        StringBuilder sb = new StringBuilder();
        if (s.getInterestDirection() != null) sb.append("- 目标方向: ").append(s.getInterestDirection()).append("\n");
        if (s.getKnowledgeLevel() != null) sb.append("- 知识水平: ").append(s.getKnowledgeLevel()).append("/100\n");
        if (s.getLearningPreference() != null) sb.append("- 学习偏好: ").append(s.getLearningPreference()).append("\n");
        if (s.getLearningPace() != null) sb.append("- 学习节奏: ").append(s.getLearningPace()).append("\n");
        if (s.getWeakAreas() != null) sb.append("- 薄弱环节: ").append(s.getWeakAreas()).append("\n");
        if (s.getStudyMotivation() != null) sb.append("- 学习动机: ").append(s.getStudyMotivation()).append("\n");
        if (sb.length() == 0) sb.append("- 暂无信息\n");
        return sb.toString();
    }

    /** 引导问题定义 */
    record QuestionDef(String field, int weight, String question) {}
}
