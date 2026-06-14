package com.happymouse.cryd.service.agent;

import com.happymouse.cryd.agent.core.AgentCapability;
import com.happymouse.cryd.agent.core.AgentContext;
import com.happymouse.cryd.agent.core.BaseAgent;
import com.happymouse.cryd.model.entity.LearningResource;
import com.happymouse.cryd.model.entity.Student;
import com.happymouse.cryd.repository.LearningResourceRepository;
import com.happymouse.cryd.repository.StudentRepository;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 代码示例师 — 根据学生画像生成实战代码示例
 */
@Component("codeExampleAgent")
public class CodeExampleAgent extends BaseAgent {

    private static final String SYSTEM_PROMPT = """
        你是「代码示例师」，擅长生成匹配学生水平的C语言代码示例。

        要求：
        - 代码可运行，注释中文
        - 零基础: 每行代码都加注释，5-15行
        - 有基础: 关键逻辑加注释，20-50行
        - 每个示例包含"运行结果"说明
        - 输出Markdown格式，代码块用 ```c
        """;

    private final StudentRepository studentRepo;
    private final LearningResourceRepository resourceRepo;

    public CodeExampleAgent(StudentRepository studentRepo, LearningResourceRepository resourceRepo) {
        this.studentRepo = studentRepo;
        this.resourceRepo = resourceRepo;
    }

    @Override public String getName() { return "代码示例师"; }
    @Override
    public List<AgentCapability> getCapabilities() {
        return List.of(AgentCapability.of("resource:code", "生成个性化代码示例", 0.90));
    }
    @Override protected String getSystemPrompt() { return SYSTEM_PROMPT; }

    @Override
    protected String doExecute(AgentContext context) {
        Long studentId = context.getStudentId();
        Student s = studentRepo.findByUsername("student_" + studentId).orElse(null);
        if (s == null) return "";

        String topic = s.getWeakAreas() != null ? s.getWeakAreas() : "C语言基础";
        int level = s.getKnowledgeLevel() != null ? s.getKnowledgeLevel() : 30;

        String prompt = "请为C语言学生生成代码示例。知识点：" + topic + "，水平：" + level + "/100。包含代码块和运行结果说明。";
        String result = callLLM(context, prompt, 0.5f, 1024);

        LearningResource res = new LearningResource();
        res.setStudentId(studentId);
        res.setTitle(topic + " 代码实例");
        res.setType("code");
        res.setKnowledgePoint(topic);
        res.setContent(result);
        res.setDifficulty(level < 30 ? "easy" : level < 60 ? "medium" : "hard");
        res.setGeneratedBy("代码示例师");
        res.setCreatedAt(LocalDateTime.now());
        resourceRepo.save(res);

        return result;
    }
}
