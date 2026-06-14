package com.happymouse.cryd.service.agent;

import com.happymouse.cryd.agent.core.AgentCapability;
import com.happymouse.cryd.agent.core.AgentContext;
import com.happymouse.cryd.agent.core.BaseAgent;
import com.happymouse.cryd.model.entity.Student;
import com.happymouse.cryd.repository.StudentRepository;
import com.happymouse.cryd.service.OnboardingService;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 导学智能体 — 流程指挥，招呼 + 引导问答 + 结果总结
 *
 * 职责：不亲自生成内容，而是协调其他智能体完成任务。
 * 每个回复带 agent 标记，让学生看到背后有多智能体在协作。
 */
@Component("onboardingAgent")
public class OnboardingAgent extends BaseAgent {

    private static final String SYSTEM_PROMPT = """
        你是「导学智能体」，负责欢迎新同学、引导画像采集、安排出题测评、总结个性化结果。

        你的风格：
        - 热情但简洁，每句话不超过2行
        - 每次只问一个问题
        - 介绍其他智能体时自然融入对话，如"让出题专家来考考你"
        - 选择题提供括号选项方便学生快选
        """;

    private final StudentRepository studentRepo;
    private final OnboardingService onboardingService;

    public OnboardingAgent(StudentRepository studentRepo, OnboardingService onboardingService) {
        this.studentRepo = studentRepo;
        this.onboardingService = onboardingService;
    }

    @Override public String getName() { return "导学智能体"; }

    @Override
    public List<AgentCapability> getCapabilities() {
        return List.of(
            AgentCapability.of("onboarding:guide", "引导学生完成画像采集和测评", 0.95),
            AgentCapability.of("onboarding:orchestrate", "协调多智能体协作生成资源", 0.90)
        );
    }

    @Override protected String getSystemPrompt() { return SYSTEM_PROMPT; }

    @Override
    protected String doExecute(AgentContext context) {
        Long studentId = context.getStudentId();
        Student student = studentRepo.findByUsername("student_" + studentId).orElse(null);
        return onboardingService.buildSystemPrompt(studentId);
    }

    /** 生成阶段总结（画像采集完成后的个性化总结） */
    public String generateSummary(Long studentId) {
        Student s = studentRepo.findByUsername("student_" + studentId).orElse(null);
        if (s == null) return "画像采集完成！";

        var sb = new StringBuilder();
        sb.append("根据这几位智能体的分析：\n\n");

        if (s.getInterestDirection() != null)
            sb.append("- 【画像分析师】你的目标是**").append(s.getInterestDirection()).append("**\n");
        if (s.getKnowledgeLevel() != null)
            sb.append("- 当前水平约**").append(s.getKnowledgeLevel()).append("/100**\n");
        if (s.getLearningPreference() != null)
            sb.append("- 偏好**").append(switch (s.getLearningPreference()) {
                case "video" -> "看视频"; case "doc" -> "读文档"; case "exercise" -> "敲代码做题";
                default -> "多种方式";
            }).append("**学习\n");
        if (s.getWeakAreas() != null)
            sb.append("- 【出题专家】发现薄弱点：**").append(s.getWeakAreas()).append("**\n");

        sb.append("\n以下是为你量身定制的学习方案 👇\n\n");
        sb.append("📄 **知识讲解师**已生成适合你水平的讲解文档\n");
        sb.append("📝 **出题专家**针对薄弱点准备了专项练习\n");
        sb.append("🧠 **思维导图师**画了知识结构图\n");
        sb.append("💻 **代码示例师**准备了实战代码\n");
        sb.append("🗺️ **路径规划师**规划了学习路线\n");
        sb.append("\n首页右边可以查看，加油！🎓");

        return sb.toString();
    }
}
