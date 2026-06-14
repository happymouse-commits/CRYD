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
 * 知识讲解师 — 根据学生画像生成个性化讲解文档/文章
 */
@Component("knowledgeExplainerAgent")
public class KnowledgeExplainerAgent extends BaseAgent {

    private static final String SYSTEM_PROMPT = """
        你是「知识讲解师」，擅长根据学生特点定制讲解内容。

        要求：
        - 标题格式：面向[学习偏好]学习者的[知识点]讲解
        - 内容匹配学生水平（零基础多用比喻和例子，有基础可深入原理）
        - 偏好视频的学生→用"想象一下…"开头
        - 偏好文档的学生→结构化分点说明
        - 偏好做题的学生→先抛问题再讲解
        - 200-400字，Markdown格式
        """;

    private final StudentRepository studentRepo;
    private final LearningResourceRepository resourceRepo;

    public KnowledgeExplainerAgent(StudentRepository studentRepo, LearningResourceRepository resourceRepo) {
        this.studentRepo = studentRepo;
        this.resourceRepo = resourceRepo;
    }

    @Override public String getName() { return "知识讲解师"; }

    @Override
    public List<AgentCapability> getCapabilities() {
        return List.of(AgentCapability.of("resource:explain", "生成个性化知识讲解", 0.92));
    }

    @Override protected String getSystemPrompt() { return SYSTEM_PROMPT; }

    @Override
    protected String doExecute(AgentContext context) {
        Long studentId = context.getStudentId();
        Student s = studentRepo.findByUsername("student_" + studentId).orElse(null);
        if (s == null) return "";

        String topic = s.getWeakAreas() != null ? s.getWeakAreas() : "C语言基础";
        int level = s.getKnowledgeLevel() != null ? s.getKnowledgeLevel() : 30;
        String pref = s.getLearningPreference() != null ? s.getLearningPreference() : "mixed";

        String prompt = buildPrompt(topic, level, pref);
        String result = callLLM(context, prompt, 0.5f, 1024);

        LearningResource res = new LearningResource();
        res.setStudentId(studentId);
        res.setTitle("面向" + switch (pref) {
            case "video" -> "视频偏好"; case "doc" -> "阅读偏好"; case "exercise" -> "实践偏好";
            default -> "混合";
        } + "学习者：" + topic);
        res.setType("article");
        res.setKnowledgePoint(topic);
        res.setContent(result);
        res.setDifficulty(level < 30 ? "easy" : level < 60 ? "medium" : "hard");
        res.setGeneratedBy("知识讲解师");
        res.setCreatedAt(LocalDateTime.now());
        resourceRepo.save(res);

        return result;
    }

    private String buildPrompt(String topic, int level, String pref) {
        return "请为一位C语言学生生成知识讲解。\n知识点：" + topic + "\n水平：" + level + "/100\n偏好：" + pref + "\n请直接输出Markdown，包含标题。";
    }
}
