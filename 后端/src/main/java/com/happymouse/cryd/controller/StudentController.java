package com.happymouse.cryd.controller;

import com.happymouse.cryd.common.Result;
import com.happymouse.cryd.model.entity.*;
import com.happymouse.cryd.repository.*;
import com.happymouse.cryd.service.spark.SparkClient;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/student")
public class StudentController {

    private static final Logger log = LoggerFactory.getLogger(StudentController.class);
    private static final String PROFILE_CHAT_PROMPT = """
        你是「学习画像助手」，负责通过自然对话构建学生的学习画像。
        
        请根据学生的回答，识别以下维度并更新：
        1. 知识基础(knowledgeLevel)：0-100的数值
        2. 认知风格(cognitiveStyle)：visual(视觉型)/auditory(听觉型)/kinesthetic(动觉型)/reading(阅读型)
        3. 学习偏好(learningPreference)：video(视频)/doc(文档)/exercise(练习)/mixed(混合)
        4. 学习节奏(learningPace)：fast(快速)/steady(稳健)/slow(慢速)
        5. 兴趣方向(interestDirection)：如算法、数据库、前端等，逗号分隔
        6. 薄弱环节(weakAreas)：学生觉得困难的知识点，逗号分隔
        7. 学习动机(studyMotivation)：intrinsic(内在兴趣)/extrinsic(外在目标)/hybrid(混合)
        8. 专注力(focusLevel)：high/medium/low
        
        回复格式要求：
        - 先用自然语言友好地回应学生
        - 然后换行，用JSON输出更新后的画像：```json{...}```
        - 如果信息不足，保持原值，继续引导学生回答
        
        开场引导语示例：
        "你好！我是你的学习画像助手。为了更好地帮助你学习，我想了解一些你的学习情况。你是什么专业的？对哪些课程比较感兴趣？"
        """;

    private final StudentRepository studentRepository;
    private final ChapterRepository chapterRepository;
    private final ChapterProgressRepository progressRepository;
    private final SysUserRepository sysUserRepository;
    private final CourseRepository courseRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final SparkClient sparkClient;

    public StudentController(StudentRepository studentRepository,
                             ChapterRepository chapterRepository,
                             ChapterProgressRepository progressRepository,
                             SysUserRepository sysUserRepository,
                             CourseRepository courseRepository,
                             ChatMessageRepository chatMessageRepository,
                             SparkClient sparkClient) {
        this.studentRepository = studentRepository;
        this.chapterRepository = chapterRepository;
        this.progressRepository = progressRepository;
        this.sysUserRepository = sysUserRepository;
        this.courseRepository = courseRepository;
        this.chatMessageRepository = chatMessageRepository;
        this.sparkClient = sparkClient;
    }

    @GetMapping("/{id}")
    public Result<Student> getProfile(@PathVariable Long id) {
        return studentRepository.findById(id)
                .map(Result::success)
                .orElse(Result.error(404, "学生不存在"));
    }

    /**
     * 根据 SysUser.id 获取学生画像
     */
    @GetMapping("/by-sysuser/{sysUserId}")
    public Result<Map<String, Object>> getProfileBySysUser(@PathVariable Long sysUserId) {
        String studentUsername = "student_" + sysUserId;
        Map<String, Object> profile = new LinkedHashMap<>();
        
        Optional<Student> opt = studentRepository.findByUsername(studentUsername);
        if (opt.isPresent()) {
            Student s = opt.get();
            profile.put("id", s.getId());
            profile.put("knowledgeLevel", s.getKnowledgeLevel());
            profile.put("cognitiveStyle", s.getCognitiveStyle());
            profile.put("learningPreference", s.getLearningPreference());
            profile.put("learningPace", s.getLearningPace());
            profile.put("interestDirection", s.getInterestDirection());
            profile.put("weakAreas", s.getWeakAreas());
            profile.put("studyMotivation", s.getStudyMotivation());
            profile.put("focusLevel", s.getFocusLevel());
        }
        return Result.success(profile);
    }

    // ===== 关卡相关 =====

    /**
     * 获取已发布关卡列表（含学生进度和布置状态）
     * 用于刷题房 - 显示所有关卡，但只有布置了的可以点开
     */
    @GetMapping("/chapters/{studentId}")
    public Result<List<Map<String, Object>>> getChapters(@PathVariable Long studentId) {
        // 获取该学生所有的进度记录（说明已布置）
        List<ChapterProgress> myProgress = progressRepository.findByStudentId(studentId);
        Set<Long> assignedChapterIds = new LinkedHashSet<>();
        Map<Long, ChapterProgress> progressMap = new LinkedHashMap<>();
        for (ChapterProgress cp : myProgress) {
            assignedChapterIds.add(cp.getChapterId());
            progressMap.put(cp.getChapterId(), cp);
        }

        List<Chapter> chapters = chapterRepository.findByStatusOrderByOrderNum("published");
        List<Map<String, Object>> result = new ArrayList<>();
        for (Chapter ch : chapters) {
            boolean assigned = assignedChapterIds.contains(ch.getId());
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", ch.getId());
            item.put("courseId", ch.getCourseId());
            item.put("name", ch.getName());
            item.put("description", ch.getDescription());
            item.put("orderNum", ch.getOrderNum());
            item.put("assigned", assigned);
            item.put("status", assigned ? "pending" : "locked");
            item.put("score", null);
            item.put("questionCount", ch.getQuestions() != null ? getQuestionCount(ch.getQuestions()) : 0);

            if (assigned) {
                ChapterProgress cp = progressMap.get(ch.getId());
                item.put("status", cp.getStatus());
                item.put("score", cp.getScore());
                item.put("progress", "completed".equals(cp.getStatus()) ? 100
                        : cp.getScore() != null ? cp.getScore() : 0);
            }

            // 获取课程名称
            courseRepository.findById(ch.getCourseId()).ifPresent(c -> {
                item.put("courseName", c.getName());
            });

            result.add(item);
        }
        return Result.success(result);
    }

    /**
     * 获取关卡题目（给学生答题用）
     */
    @GetMapping("/chapter/{chapterId}/questions")
    public Result<Map<String, Object>> getChapterQuestions(@PathVariable Long chapterId,
                                                            @RequestParam Long studentId) {
        return chapterRepository.findById(chapterId).map(ch -> {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("chapterId", ch.getId());
            result.put("chapterName", ch.getName());
            result.put("questions", ch.getQuestions()); // JSON数组字符串
            // 如果已提交过，返回之前答案
            progressRepository.findByChapterIdAndStudentId(chapterId, studentId).ifPresent(cp -> {
                result.put("previousAnswers", cp.getAnswers());
                result.put("previousScore", cp.getScore());
                result.put("previousStatus", cp.getStatus());
            });
            return Result.success(result);
        }).orElse(Result.error(404, "关卡不存在"));
    }

    /**
     * 提交关卡答案
     */
    @PostMapping("/chapter/{chapterId}/submit")
    public Result<Map<String, Object>> submitChapter(@PathVariable Long chapterId,
                                                      @RequestBody Map<String, Object> body) {
        Long studentId = Long.valueOf(body.get("studentId").toString());
        String answers = body.get("answers").toString();

        Chapter chapter = chapterRepository.findById(chapterId).orElse(null);
        if (chapter == null) return Result.error(404, "关卡不存在");

        // 更新或创建进度记录
        ChapterProgress cp = progressRepository.findByChapterIdAndStudentId(chapterId, studentId)
                .orElse(new ChapterProgress());
        cp.setChapterId(chapterId);
        cp.setStudentId(studentId);
        cp.setAnswers(answers);
        cp.setStatus("submitted");
        cp.setSubmittedAt(LocalDateTime.now());
        progressRepository.save(cp);

        // 自动评分
        try {
            JSONArray questions = JSON.parseArray(chapter.getQuestions());
            JSONObject studentAnswers = JSON.parseObject(answers);
            int total = questions.size();
            int correct = 0;
            int codeGraded = 0;
            StringBuilder fb = new StringBuilder();
            for (int i = 0; i < total; i++) {
                JSONObject q = questions.getJSONObject(i);
                String type = q.getString("type");
                String correctAnswer = q.getString("answer");
                String studentAnswer = studentAnswers.getString(String.valueOf(i + 1));

                if ("code".equals(type)) {
                    // 代码题使用AI评阅
                    if (studentAnswer != null && studentAnswer.length() >= 10) {
                        String aiFeedback = gradeCodeQuestion(q.getString("content"),
                                correctAnswer, studentAnswer);
                        boolean passed = aiFeedback.contains("✓") || aiFeedback.contains("通过");
                        if (passed) { correct++; codeGraded++; }
                        fb.append(aiFeedback);
                    } else {
                        fb.append("✗第").append(i + 1).append("题(未作答) ");
                    }
                } else {
                    // 选择题/填空题：直接比对
                    if (studentAnswer != null && correctAnswer != null
                            && correctAnswer.equalsIgnoreCase(studentAnswer.trim())) {
                        correct++;
                        fb.append("✓第").append(i + 1).append("题 ");
                    } else {
                        fb.append("✗第").append(i + 1).append("题 ");
                    }
                }
            }
            int score = (int) Math.round((double) correct / total * 100);
            cp.setScore(score);
            cp.setFeedback(fb.toString());
            cp.setStatus("completed");
            cp.setGradedBy("system");
            cp.setGradedAt(LocalDateTime.now());
            progressRepository.save(cp);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("score", score);
            result.put("correctCount", correct);
            result.put("totalCount", total);
            result.put("feedback", fb.toString());
            return Result.success(result);
        } catch (Exception e) {
            log.error("自动评分失败", e);
            return Result.error(500, "评分失败：" + e.getMessage());
        }
    }

    private int getQuestionCount(String questionsJson) {
        try { return JSON.parseArray(questionsJson).size(); } catch (Exception e) { return 0; }
    }

    /**
     * 学生个人信息更新
     */
    @PostMapping("/{id}/info")
    public Result<SysUser> updateInfo(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return sysUserRepository.findById(id).map(u -> {
            if (body.containsKey("phone")) u.setPhone(body.get("phone"));
            if (body.containsKey("studentId")) u.setStudentId(body.get("studentId"));
            if (body.containsKey("className")) u.setClassName(body.get("className"));
            return Result.success(sysUserRepository.save(u));
        }).orElse(Result.error(404, "用户不存在"));
    }

    @PostMapping("/{id}/password")
    public Result<?> changePassword(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return sysUserRepository.findById(id).map(u -> {
            String oldPwd = body.get("oldPassword");
            String newPwd = body.get("newPassword");
            if (!u.getPassword().equals(oldPwd)) return Result.error(400, "原密码错误");
            u.setPassword(newPwd);
            sysUserRepository.save(u);
            return Result.success("修改成功");
        }).orElse(Result.error(404, "用户不存在"));
    }

    /**
     * 最新作业（刷题房用）
     */
    @GetMapping("/latest-assignment/{studentId}")
    public Result<Map<String, Object>> getLatestAssignment(@PathVariable Long studentId) {
        List<Chapter> publishedChapters = chapterRepository.findByStatusOrderByOrderNum("published");
        if (publishedChapters.isEmpty()) return Result.error(404, "暂无作业");

        // 找第一个未完成的关卡
        Chapter target = null;
        for (Chapter ch : publishedChapters) {
            Optional<ChapterProgress> cp = progressRepository.findByChapterIdAndStudentId(ch.getId(), studentId);
            if (cp.isEmpty() || !"completed".equals(cp.get().getStatus())) {
                target = ch;
                break;
            }
        }
        if (target == null) target = publishedChapters.get(publishedChapters.size() - 1);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("chapterId", target.getId());
        result.put("chapterName", target.getName());
        result.put("description", target.getDescription());
        progressRepository.findByChapterIdAndStudentId(target.getId(), studentId).ifPresent(cp -> {
            result.put("status", cp.getStatus());
            result.put("previousScore", cp.getScore());
        });
        if (!result.containsKey("status")) result.put("status", "pending");
        return Result.success(result);
    }

    @PostMapping("/register")
    public Result<Student> register(@RequestBody Map<String, String> body) {
        Student student = new Student();
        student.setUsername(body.get("username"));
        student.setNickname(body.getOrDefault("nickname", body.get("username")));
        student.setKnowledgeLevel(0);
        student.setCognitiveStyle("visual");
        student.setLearningPreference("mixed");
        student.setLearningPace("steady");
        student.setProgress(0);
        return Result.success(studentRepository.save(student));
    }

    /**
     * 画像对话接口 - 通过对话构建学习画像
     * 请求体: { "sysUserId": 2, "message": "我是计算机专业的..." }
     * sysUserId 是前端 store.id (SysUser.id)
     */
    @PostMapping("/profile/chat")
    public Result<Map<String, Object>> profileChat(@RequestBody Map<String, Object> body) {
        Long sysUserId = Long.valueOf(body.get("sysUserId").toString());
        String message = (String) body.get("message");

        // 根据 SysUser.id 查找或创建 Student 记录
        // 约定：Student.username = "student_" + SysUser.id
        String studentUsername = "student_" + sysUserId;
        Student student = studentRepository.findByUsername(studentUsername)
                .orElseGet(() -> {
                    Student s = new Student();
                    s.setUsername(studentUsername);
                    s.setKnowledgeLevel(30);
                    s.setCognitiveStyle("visual");
                    s.setLearningPreference("mixed");
                    s.setLearningPace("steady");
                    s.setProgress(0);
                    return studentRepository.save(s);
                });

        // 构造当前画像状态作为上下文
        String profileContext = buildProfileContext(student);
        String aiPrompt = PROFILE_CHAT_PROMPT + "\n\n【当前画像状态】\n" + profileContext + "\n【学生说】\n" + message + "\n\n请回应并更新画像。";

        try {
            String aiReply = sparkClient.chat(aiPrompt, message, 0.5f, 2048);
            log.info("画像对话 AI 回复: {}", aiReply);

            // 尝试从回复中提取 JSON 更新画像
            Map<String, Object> updatedProfile = extractProfileFromReply(aiReply, student);

            // 保存更新后的学生信息
            studentRepository.save(student);

            Map<String, Object> result = new HashMap<>();
            // 只返回 AI 的对话回复内容（去掉JSON部分）
            String displayReply = stripJsonFromReply(aiReply);
            result.put("reply", displayReply);
            result.put("profile", updatedProfile);
            return Result.success(result);
        } catch (Exception e) {
            log.error("画像对话失败", e);
            Map<String, Object> result = new HashMap<>();
            result.put("reply", "抱歉，暂时无法处理，请稍后再试。");
            return Result.success(result);
        }
    }

    private String buildProfileContext(Student s) {
        return String.format("知识基础=%d, 认知风格=%s, 学习偏好=%s, 学习节奏=%s, 兴趣方向=%s, 薄弱环节=%s, 学习动机=%s, 专注力=%s",
            s.getKnowledgeLevel() != null ? s.getKnowledgeLevel() : 30,
            s.getCognitiveStyle() != null ? s.getCognitiveStyle() : "unknown",
            s.getLearningPreference() != null ? s.getLearningPreference() : "unknown",
            s.getLearningPace() != null ? s.getLearningPace() : "unknown",
            s.getInterestDirection() != null ? s.getInterestDirection() : "未知",
            s.getWeakAreas() != null ? s.getWeakAreas() : "未知",
            s.getStudyMotivation() != null ? s.getStudyMotivation() : "unknown",
            s.getFocusLevel() != null ? s.getFocusLevel() : "unknown");
    }

    private Map<String, Object> extractProfileFromReply(String aiReply, Student student) {
        Map<String, Object> profile = new LinkedHashMap<>();
        // 提取 ```json ... ``` 中的 JSON
        Pattern p = Pattern.compile("```json\\s*\\n?([\\s\\S]*?)```");
        Matcher m = p.matcher(aiReply);
        if (m.find()) {
            try {
                com.alibaba.fastjson2.JSONObject json = com.alibaba.fastjson2.JSON.parseObject(m.group(1).trim());
                if (json.containsKey("knowledgeLevel")) student.setKnowledgeLevel(json.getInteger("knowledgeLevel"));
                if (json.containsKey("cognitiveStyle")) student.setCognitiveStyle(json.getString("cognitiveStyle"));
                if (json.containsKey("learningPreference")) student.setLearningPreference(json.getString("learningPreference"));
                if (json.containsKey("learningPace")) student.setLearningPace(json.getString("learningPace"));
                if (json.containsKey("interestDirection")) student.setInterestDirection(json.getString("interestDirection"));
                if (json.containsKey("weakAreas")) student.setWeakAreas(json.getString("weakAreas"));
                if (json.containsKey("studyMotivation")) student.setStudyMotivation(json.getString("studyMotivation"));
                if (json.containsKey("focusLevel")) student.setFocusLevel(json.getString("focusLevel"));
            } catch (Exception e) {
                log.warn("JSON解析失败，使用规则提取", e);
            }
        }
        // 构建返回给前端的profile
        profile.put("knowledgeLevel", student.getKnowledgeLevel());
        profile.put("cognitiveStyle", student.getCognitiveStyle());
        profile.put("learningPreference", student.getLearningPreference());
        profile.put("learningPace", student.getLearningPace());
        profile.put("interestDirection", student.getInterestDirection());
        profile.put("weakAreas", student.getWeakAreas());
        profile.put("studyMotivation", student.getStudyMotivation());
        profile.put("focusLevel", student.getFocusLevel());
        return profile;
    }

    private String stripJsonFromReply(String aiReply) {
        if (aiReply == null) return "";
        return aiReply.replaceAll("```json\\s*\\n?[\\s\\S]*?```", "").trim();
    }

    /**
     * 获取学生的错题列表
     */
    @GetMapping("/wrong-questions/{sysUserId}")
    public Result<List<Map<String, Object>>> getWrongQuestions(@PathVariable Long sysUserId) {
        String studentUsername = "student_" + sysUserId;
        Optional<Student> opt = studentRepository.findByUsername(studentUsername);
        if (opt.isEmpty()) return Result.success(new ArrayList<>());

        List<Map<String, Object>> wrongList = new ArrayList<>();
        // 只查当前学生的答题记录
        List<ChapterProgress> progressList = progressRepository.findByStudentId(sysUserId);

        for (ChapterProgress cp : progressList) {
            Optional<Chapter> chapterOpt = chapterRepository.findById(cp.getChapterId());
            if (chapterOpt.isEmpty()) continue;
            Chapter chapter = chapterOpt.get();
            if (chapter.getQuestions() == null) continue;

            try {
                JSONArray questions = JSON.parseArray(chapter.getQuestions());
                JSONObject studentAnswers = JSON.parseObject(cp.getAnswers());
                for (int i = 0; i < questions.size(); i++) {
                    JSONObject q = questions.getJSONObject(i);
                    String correctAnswer = q.getString("answer");
                    String studentAnswer = studentAnswers.getString(String.valueOf(i + 1));
                    if (correctAnswer != null && studentAnswer != null && !correctAnswer.equalsIgnoreCase(studentAnswer)) {
                        Map<String, Object> wrong = new LinkedHashMap<>();
                        wrong.put("id", cp.getId() * 1000 + i);
                        wrong.put("courseName", "课程");
                        wrong.put("knowledgePoint", q.getString("knowledgePoint"));
                        wrong.put("content", q.getString("content"));
                        wrong.put("studentAnswer", studentAnswer);
                        wrong.put("correctAnswer", correctAnswer);
                        wrong.put("analysis", q.getString("analysis"));
                        wrongList.add(wrong);
                    }
                }
            } catch (Exception e) {
                log.warn("解析错题失败: {}", e.getMessage());
            }
        }
        return Result.success(wrongList);
    }

    /**
     * 使用AI评阅代码题
     */
    private String gradeCodeQuestion(String questionContent, String referenceAnswer, String studentAnswer) {
        String prompt = "你是C语言代码评阅老师。\n"
            + "题目：" + questionContent + "\n"
            + "参考答案：\n" + referenceAnswer + "\n"
            + "学生代码：\n" + studentAnswer + "\n"
            + "请判断学生代码是否正确实现了题目要求。\n"
            + "如果正确，回复：代码正确，通过！\n"
            + "如果有小问题，回复：基本正确，但有小问题\n"
            + "如果错误，回复：代码未通过\n"
            + "只回复评语，不要重复代码。";
        try {
            String feedback = sparkClient.chat(prompt, studentAnswer, 0.3f, 200);
            return feedback.trim() + " ";
        } catch (Exception e) {
            log.warn("AI代码评阅失败：{}", e.getMessage());
            return "? 代码题待老师评阅 ";
        }
    }

    private static final String PROFILE_ANALYZE_PROMPT = """
        你是「学习画像分析师」。
        请根据学生与AI辅导的聊天记录，分析学生的学习画像。

        输出格式要求：直接输出一个JSON对象，包含以下字段（不要加```json标记）：
        {
          "knowledgeLevel": 0-100的数值,
          "cognitiveStyle": "visual"或"auditory"或"kinesthetic"或"reading",
          "learningPreference": "video"或"doc"或"exercise"或"mixed",
          "learningPace": "fast"或"steady"或"slow",
          "interestDirection": "具体的专业或方向",
          "weakAreas": "薄弱知识点，逗号分隔",
          "studyMotivation": "intrinsic"或"extrinsic"或"hybrid",
          "focusLevel": "high"或"medium"或"low"
        }

        只输出JSON，不要其他文字。
        """;

    /**
     * 从AI辅导聊天记录分析学生画像
     */
    @PostMapping("/profile/analyze")
    public Result<Map<String, Object>> analyzeProfileFromChat(@RequestBody Map<String, Object> body) {
        Long sysUserId = Long.valueOf(body.get("sysUserId").toString());
        String studentUsername = "student_" + sysUserId;

        Student student = studentRepository.findByUsername(studentUsername)
                .orElseGet(() -> {
                    Student s = new Student();
                    s.setUsername(studentUsername);
                    s.setKnowledgeLevel(30);
                    s.setCognitiveStyle("visual");
                    s.setLearningPreference("mixed");
                    s.setLearningPace("steady");
                    s.setProgress(0);
                    return studentRepository.save(s);
                });

        // 获取最近的聊天记录
        List<ChatMessage> messages = chatMessageRepository.findTop20ByStudentIdOrderByCreatedAtDesc(sysUserId);
        if (messages.isEmpty()) {
            return Result.error(400, "暂无AI辅导聊天记录，请先进行AI辅导对话");
        }

        StringBuilder chatContext = new StringBuilder();
        chatContext.append("【学生聊天记录】\n");
        for (int i = messages.size() - 1; i >= 0; i--) {
            ChatMessage m = messages.get(i);
            chatContext.append(m.getRole()).append(": ").append(m.getContent()).append("\n");
        }

        String prompt = PROFILE_ANALYZE_PROMPT + "\n" + chatContext;
        try {
            String aiReply = sparkClient.chat(prompt, chatContext.toString(), 0.3f, 1500);
            log.info("画像分析AI回复: {}", aiReply);

            // 解析JSON
            String jsonStr = aiReply.trim();
            if (jsonStr.startsWith("```json")) {
                jsonStr = jsonStr.replaceAll("```json\\s*", "").replaceAll("```", "").trim();
            }
            JSONObject json = JSON.parseObject(jsonStr);

            if (json.containsKey("knowledgeLevel")) student.setKnowledgeLevel(json.getInteger("knowledgeLevel"));
            if (json.containsKey("cognitiveStyle")) student.setCognitiveStyle(json.getString("cognitiveStyle"));
            if (json.containsKey("learningPreference")) student.setLearningPreference(json.getString("learningPreference"));
            if (json.containsKey("learningPace")) student.setLearningPace(json.getString("learningPace"));
            if (json.containsKey("interestDirection")) student.setInterestDirection(json.getString("interestDirection"));
            if (json.containsKey("weakAreas")) student.setWeakAreas(json.getString("weakAreas"));
            if (json.containsKey("studyMotivation")) student.setStudyMotivation(json.getString("studyMotivation"));
            if (json.containsKey("focusLevel")) student.setFocusLevel(json.getString("focusLevel"));

            studentRepository.save(student);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("message", "画像分析完成");
            Map<String, Object> profile = new LinkedHashMap<>();
            profile.put("knowledgeLevel", student.getKnowledgeLevel());
            profile.put("cognitiveStyle", student.getCognitiveStyle());
            profile.put("learningPreference", student.getLearningPreference());
            profile.put("learningPace", student.getLearningPace());
            profile.put("interestDirection", student.getInterestDirection());
            profile.put("weakAreas", student.getWeakAreas());
            profile.put("studyMotivation", student.getStudyMotivation());
            profile.put("focusLevel", student.getFocusLevel());
            result.put("profile", profile);
            return Result.success(result);
        } catch (Exception e) {
            log.error("画像分析失败", e);
            return Result.error(500, "画像分析失败：" + e.getMessage());
        }
    }

    // ===== 疑难突破资源生成提示词 =====
    private static final Map<String, String> BREAKTHROUGH_PROMPTS = Map.of(
        "explanation", """
            你是「课程导师」，擅长用清晰的语言讲解知识。
            请针对学生做错的题目，生成一份结构化的课程讲解文档。
            要求：
            1. 先指出题目的知识点
            2. 讲解核心概念和原理
            3. 给出典型例题和解析
            4. 总结易错点和解题技巧
            输出格式：Markdown
            """,
        "mindmap", """
            你是「知识架构师」，擅长用思维导图组织知识。
            请针对学生做错的题目，生成一份知识点思维导图。
            要求：
            1. 使用文本缩进表示层级
            2. 中心主题是该知识点
            3. 分支包括：概念、原理、应用、易错点
            输出格式：文本形式的思维导图
            """,
        "exercise", """
            你是「出题专家」，擅长设计练习题。
            请针对学生做错的题目，生成3-5道同类练习题。
            要求：
            1. 题型与原题相似
            2. 每题附答案和解析
            3. 难度递进
            输出格式：Markdown
            """,
        "reading", """
            你是「学习顾问」，擅长推荐学习资源。
            请针对学生做错的题目，推荐相关的拓展阅读材料。
            要求：
            1. 推荐该知识点的经典教材章节
            2. 推荐相关的在线学习资源
            3. 给出阅读建议
            输出格式：Markdown
            """,
        "code", """
            你是「实践指导」，擅长设计代码实操案例。
            请针对学生做错的题目，设计与知识点相关的代码实操案例。
            要求：
            1. 提供可运行的代码示例
            2. 代码需有详细注释
            3. 说明运行结果和原理
            输出格式：Markdown（代码块用```语言标记）
            """
    );

    /**
     * 多智能体生成疑难突破资源
     */
    @PostMapping("/breakthrough/generate/{resourceType}")
    public Result<Map<String, Object>> generateBreakthroughResource(
            @PathVariable String resourceType,
            @RequestBody Map<String, Object> body) {

        String promptTemplate = BREAKTHROUGH_PROMPTS.get(resourceType);
        if (promptTemplate == null) {
            return Result.error(400, "不支持的资源类型：" + resourceType);
        }

        String knowledgePoint = (String) body.getOrDefault("knowledgePoint", "");
        String questionContent = (String) body.getOrDefault("questionContent", "");
        String wrongAnswer = (String) body.getOrDefault("wrongAnswer", "");
        String correctAnswer = (String) body.getOrDefault("correctAnswer", "");

        String context = String.format("""
            【题目】%s
            【知识点】%s
            【学生错误答案】%s
            【正确答案】%s
            """, questionContent, knowledgePoint, wrongAnswer, correctAnswer);

        String prompt = promptTemplate + "\n\n" + context;
        try {
            String content = sparkClient.chat(prompt, context, 0.5f, 2048);
            log.info("疑难突破资源生成 [{}] 成功", resourceType);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("resourceType", resourceType);
            result.put("content", content);
            return Result.success(result);
        } catch (Exception e) {
            log.error("资源生成失败", e);
            return Result.error(500, "资源生成失败：" + e.getMessage());
        }
    }
}