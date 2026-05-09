package com.happymouse.cryd.service.agent;

import com.happymouse.cryd.model.dto.ChatRequest;
import com.happymouse.cryd.model.dto.ChatResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 智能体编排器 - 核心调度中心
 */
@Service
public class AgentOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(AgentOrchestrator.class);

    private final ProfileAnalystAgent profileAnalyst;
    private final KnowledgeManagerAgent knowledgeManager;
    private final CourseDesignerAgent courseDesigner;
    private final QuestionExpertAgent questionExpert;
    private final PathPlannerAgent pathPlanner;
    private final TutorAgent tutorAgent;

    public AgentOrchestrator(
            ProfileAnalystAgent profileAnalyst,
            KnowledgeManagerAgent knowledgeManager,
            CourseDesignerAgent courseDesigner,
            QuestionExpertAgent questionExpert,
            PathPlannerAgent pathPlanner,
            TutorAgent tutorAgent) {
        this.profileAnalyst = profileAnalyst;
        this.knowledgeManager = knowledgeManager;
        this.courseDesigner = courseDesigner;
        this.questionExpert = questionExpert;
        this.pathPlanner = pathPlanner;
        this.tutorAgent = tutorAgent;
    }

    public ChatResponse process(ChatRequest request) {
        log.info("收到消息: studentId={}, message={}", request.getStudentId(), request.getMessage());

        profileAnalyst.analyze(request);
        log.info("画像分析完成");

        String intent = detectIntent(request.getMessage());
        log.info("意图识别: {}", intent);

        ChatResponse response = switch (intent) {
            case "LEARN" -> courseDesigner.generate(request);
            case "PRACTICE" -> questionExpert.generate(request);
            case "PATH" -> pathPlanner.plan(request);
            case "QUESTION" -> tutorAgent.answer(request);
            case "KNOWLEDGE" -> knowledgeManager.search(request);
            default -> tutorAgent.answer(request);
        };

        if ("LEARN".equals(intent) || "PRACTICE".equals(intent)) {
            pathPlanner.updateProgress(request);
        }

        return response;
    }

    private String detectIntent(String message) {
        if (message == null) return "QUESTION";
        String msg = message.toLowerCase();

        if (msg.contains("学习") || msg.contains("讲解") || msg.contains("教我") 
            || msg.contains("什么是") || msg.contains("怎么理解")) {
            return "LEARN";
        }
        if (msg.contains("练习") || msg.contains("做题") || msg.contains("考我")
            || msg.contains("测试") || msg.contains("题目")) {
            return "PRACTICE";
        }
        if (msg.contains("路径") || msg.contains("计划") || msg.contains("规划")
            || msg.contains("接下来学什么") || msg.contains("进度")) {
            return "PATH";
        }
        if (msg.contains("知识点") || msg.contains("知识库") || msg.contains("大纲")) {
            return "KNOWLEDGE";
        }
        return "QUESTION";
    }
}
