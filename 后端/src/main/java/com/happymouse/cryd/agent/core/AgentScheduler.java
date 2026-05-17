package com.happymouse.cryd.agent.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.*;

/**
 * 调度Agent — 能力匹配、优先级调度、并行执行、失败重试
 */
public class AgentScheduler {

    private static final Logger log = LoggerFactory.getLogger(AgentScheduler.class);

    private final Map<String, BaseAgent> agentRegistry = new ConcurrentHashMap<>();
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final int maxRetries;
    private final long taskTimeoutMs;

    public AgentScheduler(int maxRetries, long taskTimeoutMs) {
        this.maxRetries = maxRetries;
        this.taskTimeoutMs = taskTimeoutMs;
    }

    public AgentScheduler() {
        this(3, 120_000);
    }

    /**
     * 注册Agent
     */
    public void register(BaseAgent agent) {
        agentRegistry.put(agent.getName(), agent);
        log.info("[调度器] 注册Agent: {}", agent.getName());
    }

    /**
     * 执行任务图
     */
    public List<AgentContext.AgentResult> executeTaskGraph(AgentContext context) {
        List<AgentContext.SubTask> tasks = context.getTasks();
        if (tasks.isEmpty()) {
            log.warn("[调度器] 无任务可执行");
            return Collections.emptyList();
        }

        List<AgentContext.AgentResult> results = new CopyOnWriteArrayList<>();
        Map<String, CompletableFuture<Void>> taskFutures = new HashMap<>();
        Set<String> completedTasks = ConcurrentHashMap.newKeySet();

        // 按优先级排序
        List<AgentContext.SubTask> sorted = new ArrayList<>(tasks);
        sorted.sort(Comparator.comparingInt(AgentContext.SubTask::getPriority));

        for (AgentContext.SubTask task : sorted) {
            CompletableFuture<Void> future = CompletableFuture.supplyAsync(() -> {
                // 等待依赖任务完成
                for (String depId : task.getDependsOn()) {
                    CompletableFuture<Void> depFuture = taskFutures.get(depId);
                    if (depFuture != null) {
                        try { depFuture.get(taskTimeoutMs, TimeUnit.MILLISECONDS); }
                        catch (Exception e) { log.warn("[调度器] 等待依赖{}超时", depId); }
                    }
                }

                // 匹配Agent
                BaseAgent agent = matchAgent(task);
                if (agent == null) {
                    log.warn("[调度器] 未找到匹配Agent: type={}", task.getType());
                    return null;
                }

                // 执行
                task.setStatus("running");
                log.info("[调度器] 执行任务: id={}, agent={}", task.getId(), agent.getName());
                AgentContext.AgentResult result = agent.executeWithRetry(context, maxRetries);

                if (result.isSuccess()) {
                    task.setStatus("completed");
                    context.putResult(agent.getName(), result);
                } else {
                    task.setStatus("failed");
                }

                results.add(result);
                completedTasks.add(task.getId());
                return null;
            }, executor);

            taskFutures.put(task.getId(), future);
        }

        // 等待所有任务完成
        CompletableFuture.allOf(taskFutures.values().toArray(new CompletableFuture[0]))
                .orTimeout(taskTimeoutMs * tasks.size(), TimeUnit.MILLISECONDS)
                .exceptionally(e -> { log.warn("[调度器] 部分任务超时"); return null; })
                .join();

        log.info("[调度器] 执行完成: {}/{} 成功", completedTasks.size(), tasks.size());
        return results;
    }

    /**
     * 能力匹配 — 根据任务类型匹配最合适的Agent
     */
    private BaseAgent matchAgent(AgentContext.SubTask task) {
        String type = task.getType();
        String description = task.getDescription();
        BaseAgent best = null;
        double bestScore = 0;

        for (BaseAgent agent : agentRegistry.values()) {
            // 排除管道Agent自身
            if (agent instanceof TaskDecomposer || agent instanceof AgentReviewer
                || agent instanceof AggregatorAgent) {
                continue;
            }
            for (AgentCapability cap : agent.getCapabilities()) {
                double score = cap.matchScore(type + " " + description);
                if (score > bestScore) {
                    bestScore = score;
                    best = agent;
                }
            }
        }

        if (best == null) {
            // 兜底：类型名匹配
            best = matchByTypeName(type);
        }

        return best;
    }

    /**
     * 按类型名兜底匹配
     */
    private BaseAgent matchByTypeName(String type) {
        for (BaseAgent agent : agentRegistry.values()) {
            for (AgentCapability cap : agent.getCapabilities()) {
                if (cap.getId().contains(type) || cap.getDescription().contains(type)) {
                    return agent;
                }
            }
        }
        // 最终兜底：返回TutorAgent
        for (BaseAgent agent : agentRegistry.values()) {
            if (agent.getName().contains("辅导")) {
                return agent;
            }
        }
        return agentRegistry.values().stream().findFirst().orElse(null);
    }

    public Map<String, BaseAgent> getAgentRegistry() { return Collections.unmodifiableMap(agentRegistry); }
}
