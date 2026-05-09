package com.happymouse.cryd.service.agent;

import com.happymouse.cryd.model.dto.ChatRequest;
import com.happymouse.cryd.model.entity.Student;
import com.happymouse.cryd.repository.StudentRepository;
import com.happymouse.cryd.service.spark.SparkClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 画像分析师 - 通过对话分析学生学习画像
 * 六维度：认知风格、学习偏好、学习节奏、知识水平、兴趣方向、薄弱环节
 */
@Service
public class ProfileAnalystAgent {

    private static final Logger log = LoggerFactory.getLogger(ProfileAnalystAgent.class);
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

    private final StudentRepository studentRepository;
    private final SparkClient sparkClient;

    public ProfileAnalystAgent(StudentRepository studentRepository, SparkClient sparkClient) {
        this.studentRepository = studentRepository;
        this.sparkClient = sparkClient;
    }

    public void analyze(ChatRequest request) {
        Student student = studentRepository.findById(request.getStudentId())
                .orElseGet(() -> createDefaultStudent(request.getStudentId()));

        String message = request.getMessage();
        updateProfileByRules(student, message);

        // 调用星火API做深度画像分析
        try {
            String aiResult = sparkClient.chat(SYSTEM_PROMPT, message, 0.3f, 256);
            log.info("AI画像分析结果: {}", aiResult);
            applyAiProfile(student, aiResult);
        } catch (Exception e) {
            log.warn("AI画像分析失败，使用规则引擎结果: {}", e.getMessage());
        }

        studentRepository.save(student);
        log.info("学生画像已更新: studentId={}, level={}, style={}",
                student.getId(), student.getKnowledgeLevel(), student.getCognitiveStyle());
    }

    private void updateProfileByRules(Student student, String message) {
        String msg = message.toLowerCase();
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
            // 尝试解析AI返回的JSON
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
        s.setId(id);
        s.setUsername("student_" + id);
        s.setKnowledgeLevel(30);
        s.setCognitiveStyle("visual");
        s.setLearningPreference("mixed");
        s.setLearningPace("steady");
        return s;
    }
}
