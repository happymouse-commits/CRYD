package com.happymouse.cryd.service.agent;

import com.happymouse.cryd.agent.core.AgentScheduler;
import com.happymouse.cryd.agent.core.AggregatorAgent;
import com.happymouse.cryd.agent.core.AgentReviewer;
import com.happymouse.cryd.agent.core.PipelineOrchestrator;
import com.happymouse.cryd.agent.core.TaskDecomposer;
import com.happymouse.cryd.model.dto.ChatRequest;
import com.happymouse.cryd.model.dto.ChatResponse;
import com.happymouse.cryd.service.spark.SparkClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 智能体编排器 - 核心调度中心
 * @deprecated 使用 PipelineOrchestrator 替代，保留此类以兼容旧代码
 */
@Service
public class AgentOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(AgentOrchestrator.class);

    private final PipelineOrchestrator pipeline;
    private final ProfileAnalystAgent profileAnalyst;
    private final KnowledgeManagerAgent knowledgeManager;
    private final CourseDesignerAgent courseDesigner;
    private final QuestionExpertAgent questionExpert;
    private final PathPlannerAgent pathPlanner;
    private final TutorAgent tutorAgent;

    public AgentOrchestrator(
            SparkClient sparkClient,
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

        // 构建新管道
        TaskDecomposer decomposer = new TaskDecomposer(sparkClient);
        AgentScheduler scheduler = new AgentScheduler(3, 120_000);

        // 注入SparkClient到所有Agent
        profileAnalyst.setSparkClient(sparkClient);
        knowledgeManager.setSparkClient(sparkClient);
        courseDesigner.setSparkClient(sparkClient);
        questionExpert.setSparkClient(sparkClient);
        pathPlanner.setSparkClient(sparkClient);
        tutorAgent.setSparkClient(sparkClient);

        // 注册所有Agent到调度器
        scheduler.register(profileAnalyst);
        scheduler.register(knowledgeManager);
        scheduler.register(courseDesigner);
        scheduler.register(questionExpert);
        scheduler.register(pathPlanner);
        scheduler.register(tutorAgent);

        AgentReviewer reviewer = new AgentReviewer(sparkClient);
        AggregatorAgent aggregator = new AggregatorAgent(sparkClient);

        this.pipeline = new PipelineOrchestrator(decomposer, scheduler, reviewer, aggregator);
        this.pipeline.setAsyncProfileUpdater(() -> {}); // 画像更新由主流程触发

        log.info("AgentOrchestrator已升级为PipelineOrchestrator管道模式");
    }

    /**
     * @deprecated 使用 PipelineOrchestrator.process() 替代
     */
    public ChatResponse process(ChatRequest request) {
        log.info("收到消息(管道模式): studentId={}, message={}", request.getStudentId(), request.getMessage());

        // 先调用画像分析（异步不阻塞）
        try {
            profileAnalyst.analyze(request);
        } catch (Exception e) {
            log.warn("画像分析失败: {}", e.getMessage());
        }

        // 如果PipelineOrchestrator有任务拆解结果，走新管道
        // 否则降级到旧的关键词路由
        ChatResponse response = pipeline.process(request);

        // 如果是学习/练习类请求，更新进度
        if (response.isResourceGenerated()) {
            pathPlanner.updateProgress(request);
        }

        return response;
    }
}
