package com.happymouse.cryd.agent.core;

import com.happymouse.cryd.model.dto.ChatRequest;
import com.happymouse.cryd.model.dto.ChatResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 管道编排器 — 新多智能体协作管道
 * 替换旧的 AgentOrchestrator（关键词路由）
 *
 * 管道流程:
 *   用户请求 -> TaskDecomposer(拆解) -> AgentScheduler(调度执行) -> AgentReviewer(审核) -> AggregatorAgent(整合)
 */
public class PipelineOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(PipelineOrchestrator.class);

    private final TaskDecomposer decomposer;
    private final AgentScheduler scheduler;
    private final AgentReviewer reviewer;
    private final AggregatorAgent aggregator;

    // 异步任务（画像更新等）
    private Runnable asyncProfileUpdater;

    public PipelineOrchestrator(
            TaskDecomposer decomposer,
            AgentScheduler scheduler,
            AgentReviewer reviewer,
            AggregatorAgent aggregator) {
        this.decomposer = decomposer;
        this.scheduler = scheduler;
        this.reviewer = reviewer;
        this.aggregator = aggregator;
    }

    public void setAsyncProfileUpdater(Runnable updater) {
        this.asyncProfileUpdater = updater;
    }

    /**
     * 主入口：处理用户请求
     */
    public ChatResponse process(ChatRequest request) {
        log.info("[管道] 收到请求: studentId={}, msg={}", request.getStudentId(), request.getMessage());

        // 构建上下文
        AgentContext context = new AgentContext(
            request.getStudentId(),
            request.getMessage(),
            null // history由各Agent从memory加载
        );
        context.setAttribute("mode", request.getMode() != null ? request.getMode() : "fast");

        // 步骤1: 需求拆解
        log.info("[管道] 步骤1: 需求拆解");
        decomposer.execute(context);

        // 步骤2: 调度执行所有子任务
        log.info("[管道] 步骤2: 调度执行 ({}个子任务)", context.getTasks().size());
        List<AgentContext.AgentResult> results = scheduler.executeTaskGraph(context);

        // 步骤3: 审核
        log.info("[管道] 步骤3: 质量审核");
        reviewer.execute(context);

        // 步骤4: 判断是否需要重试（审核不通过且重试次数<3）
        Boolean reviewPassed = context.getAttribute("reviewPassed", Boolean.class);
        if (reviewPassed == null || !reviewPassed) {
            // 对失败的任务重试
            for (AgentContext.SubTask task : context.getTasks()) {
                if ("failed".equals(task.getStatus())) {
                    log.info("[管道] 重试失败任务: {}", task.getId());
                    // 重新调度执行
                    List<AgentContext.AgentResult> retryResults = scheduler.executeTaskGraph(context);
                    results.addAll(retryResults);
                }
            }
            // 再次审核
            reviewer.execute(context);
        }

        // 步骤5: 整合输出
        log.info("[管道] 步骤5: 内容整合");
        String finalOutput = aggregator.doExecute(context);

        // 步骤6: 异步更新画像
        if (asyncProfileUpdater != null) {
            CompletableFuture.runAsync(asyncProfileUpdater);
        }

        // 构建响应
        ChatResponse response = new ChatResponse();
        response.setMessage(finalOutput);

        // 找到实际执行的Agent名称
        for (AgentContext.AgentResult r : results) {
            if (r.isSuccess()) {
                response.setAgentName(r.getAgentName());
                break;
            }
        }
        if (response.getAgentName() == null) {
            response.setAgentName("多智能体协作组");
        }

        // 检查是否有资源生成
        boolean hasResource = results.stream().anyMatch(r ->
            r.getOutput() != null && r.getOutput().length() > 200);
        response.setResourceGenerated(hasResource);

        log.info("[管道] 完成: agent={}, outputLen={}", response.getAgentName(),
            finalOutput != null ? finalOutput.length() : 0);

        return response;
    }
}
