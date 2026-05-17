package com.happymouse.cryd.service.agent;

import com.happymouse.cryd.agent.core.AgentCapability;
import com.happymouse.cryd.agent.core.AgentContext;
import com.happymouse.cryd.agent.core.BaseAgent;
import com.happymouse.cryd.model.entity.Student;
import com.happymouse.cryd.repository.StudentRepository;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 画像分析师 - 通过对话分析学生学习画像
 * 六维度：认知风格、学习偏好、学习节奏、知识水平、兴趣方向、薄弱环节
 */
@Component("profileAnalystAgent")
public class ProfileAnalystAgent extends BaseAgent {

    private static final String SYSTEM_PROMPT = """
            你是「画像分析师」，负责分析学生的对话来提取学习画像特征。

            你需要从学生消息中识别以下6个维度：
            1. 认知风格：visual(视觉型)/auditory(听觉型)/kinesthetic(动觉型)
            2. 学习偏好：video(视频)/doc(文档)/exercise(练习)/mixed(混合)
            3. 学习节奏：fast(快速)/steady(稳扎稳打)/slow(慢慢来)
            4. 知识水平：0-100的数值估算
            5. 兴趣方向：如算法、数据库、前端、系统等
            6. 薄弱环节：学生可能需要加强的领域

            请用JSON格式输出分析结果，例如：
            {"cognitiveStyle":"visual","preference":"video","pace":"steady","level":35,"interest":"算法","weakness":"指针"}

            只输出JSON，不要多余文字。如果信息不足以判断某个维度，使用默认值。
            """;

    private static final int AI_PROFILE_INTERVAL = 5;

    private final StudentRepository studentRepository;

    public ProfileAnalystAgent(StudentRepository studentRepository) {
        this.studentRepository = studentRepository;
    }

    @Override
    public String getName() { return "画像分析师"; }

    @Override
    public List<AgentCapability> getCapabilities() {
        return List.of(
            AgentCapability.of("profile:analyze", "分析学习画像、提取画像维度", 0.95),
            AgentCapability.of("profile:extract", "从对话提取学生特征", 0.90)
        );
    }

    @Override
    protected String getSystemPrompt() { return SYSTEM_PROMPT; }

    @Override
    protected String doExecute(AgentContext context) {
        Long studentId = context.getStudentId();
        String message = context.getOriginalMessage();

        Student student = studentRepository.findByUsername("student_" + studentId)
                .orElseGet(() -> {
                    Student s = createDefaultStudent(studentId);
                    return studentRepository.save(s);
                });

        updateProfileByRules(student, message);

        boolean shouldTriggerAi = student.getProgress() == null || student.getProgress() % AI_PROFILE_INTERVAL == 0;
        if (shouldTriggerAi && sparkClient != null) {
            final Long studentDbId = student.getId();
            final String msgCopy = message;
            Thread.ofVirtual().start(() -> {
                try {
                    String aiResult = sparkClient.chat(SYSTEM_PROMPT, msgCopy, 0.3f, 256);
                    Student s = studentRepository.findById(studentDbId).orElse(null);
                    if (s != null) {
                        applyAiProfile(s, aiResult);
                        studentRepository.save(s);
                    }
                } catch (Exception e) {
                    log.warn("异步AI画像分析失败: {}", e.getMessage());
                }
            });
        }

        int progress = student.getProgress() != null ? student.getProgress() : 0;
        student.setProgress(progress + 1);
        studentRepository.save(student);

        return "profile_updated";
    }

    /**
     * 保持向后兼容的旧接口（供AgentOrchestrator调用）
     */
    public void analyze(com.happymouse.cryd.model.dto.ChatRequest request) {
        AgentContext ctx = new AgentContext(request.getStudentId(), request.getMessage(), null);
        execute(ctx);
    }

    private void updateProfileByRules(Student student, String message) {
        String msg = message != null ? message.toLowerCase() : "";
        if (msg.contains("看图") || msg.contains("图解") || msg.contains("思维导图")) {
            student.setCognitiveStyle("visual");
        } else if (msg.contains("听") || msg.contains("讲") || msg.contains("说")) {
            student.setCognitiveStyle("auditory");
        } else if (msg.contains("做") || msg.contains("练") || msg.contains("代码")) {
            student.setCognitiveStyle("kinesthetic");
        }
        if (msg.contains("视频") || msg.contains("看")) {
            student.setLearningPreference("video");
        } else if (msg.contains("文档") || msg.contains("看文字") || msg.contains("读")) {
            student.setLearningPreference("doc");
        } else if (msg.contains("练习") || msg.contains("做题")) {
            student.setLearningPreference("exercise");
        }
        if (msg.contains("快") || msg.contains("赶")) {
            student.setLearningPace("fast");
        } else if (msg.contains("慢慢") || msg.contains("稳")) {
            student.setLearningPace("steady");
        }
    }

    private void applyAiProfile(Student student, String aiResult) {
        try {
            String json = aiResult.trim();
            if (json.startsWith("```")) {
                json = json.replaceAll("```json\\s*", "").replaceAll("```\\s*", "");
            }
            com.alibaba.fastjson2.JSONObject obj = com.alibaba.fastjson2.JSON.parseObject(json);
            if (obj.containsKey("cognitiveStyle")) student.setCognitiveStyle(obj.getString("cognitiveStyle"));
            if (obj.containsKey("preference")) student.setLearningPreference(obj.getString("preference"));
            if (obj.containsKey("pace")) student.setLearningPace(obj.getString("pace"));
            if (obj.containsKey("level")) student.setKnowledgeLevel(obj.getIntValue("level"));
        } catch (Exception e) {
            log.debug("AI画像JSON解析失败，保留规则引擎结果");
        }
    }

    private Student createDefaultStudent(Long id) {
        Student s = new Student();
        s.setUsername("student_" + id);
        s.setKnowledgeLevel(30);
        s.setCognitiveStyle("visual");
        s.setLearningPreference("mixed");
        s.setLearningPace("steady");
        return s;
    }
}
