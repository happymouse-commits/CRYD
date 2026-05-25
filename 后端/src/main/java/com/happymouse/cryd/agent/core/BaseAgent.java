package com.happymouse.cryd.agent.core;

import com.happymouse.cryd.agent.memory.AgentMemory;
import com.happymouse.cryd.service.spark.SparkClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 智能体抽象基类 —— 模板方法模式。
 *
 * <p>职责：封装 LLM 调用、记忆管理、重试机制的通用逻辑，
 * 子类只需实现 {@link #doExecute} 核心业务。</p>
 *
 * <p>注入机制：子类为 Spring {@code @Component}，Spring 自动通过
 * {@link #setSparkClient}（带 @Autowired）注入 LLM 客户端。</p>
 *
 * <p>生命周期：{@link #execute(AgentContext)} 模板方法保证
 * 预处理→执行→后处理的标准流程和异常兜底。</p>
 */
public abstract class BaseAgent {

    protected final Logger log = LoggerFactory.getLogger(getClass());

    protected SparkClient sparkClient;
    protected AgentMemory memory;
    private final Map<String, Object> config = new ConcurrentHashMap<>();

    /**
     * 返回此Agent的唯一名称
     */
    public abstract String getName();

    /**
     * 返回此Agent的能力列表，供调度器匹配
     */
    public abstract List<AgentCapability> getCapabilities();

    /**
     * 返回此Agent的系统提示词
     */
    protected abstract String getSystemPrompt();

    /**
     * 核心执行逻辑 — 子类实现
     */
    protected abstract String doExecute(AgentContext context);

    /**
     * 模板方法：预处理 -> 执行 -> 后处理
     */
    public final AgentContext.AgentResult execute(AgentContext context) {
        long start = System.currentTimeMillis();
        String agentName = getName();
        log.info("[{}] 开始执行", agentName);

        try {
            preProcess(context);
            String output = doExecute(context);
            postProcess(context, output);

            AgentContext.AgentResult result = new AgentContext.AgentResult(agentName, output, true);
            result.setDurationMs(System.currentTimeMillis() - start);
            log.info("[{}] 执行完成，耗时{}ms", agentName, result.getDurationMs());
            return result;

        } catch (Exception e) {
            log.error("[{}] 执行失败: {}", agentName, e.getMessage());
            AgentContext.AgentResult result = new AgentContext.AgentResult(agentName, null, false);
            result.setDurationMs(System.currentTimeMillis() - start);
            result.setErrorMessage(e.getMessage());
            return result;
        }
    }

    /**
     * 带重试的执行
     */
    public AgentContext.AgentResult executeWithRetry(AgentContext context, int maxRetries) {
        AgentContext.AgentResult result = null;
        for (int i = 0; i <= maxRetries; i++) {
            result = execute(context);
            result.setRetryCount(i);
            if (result.isSuccess()) break;
            log.warn("[{}] 第{}次重试", getName(), i + 1);
        }
        return result;
    }

    /**
     * 预处理 — 子类可覆写
     */
    protected void preProcess(AgentContext context) {
        // 默认从记忆加载对话历史
        if (memory != null) {
            List<Map<String, String>> history = memory.load(context.getStudentId());
            if (history != null && !history.isEmpty()) {
                context.setAttribute("memoryHistory", history);
            }
        }
    }

    /**
     * 后处理 — 子类可覆写
     */
    protected void postProcess(AgentContext context, String output) {
        // 默认保存到记忆
        if (memory != null && output != null) {
            memory.save(context.getStudentId(), "assistant", output);
        }
    }

    /**
     * 调用 LLM 的便捷方法
     */
    protected String callLLM(AgentContext context, String userMessage, float temperature, int maxTokens) {
        String systemPrompt = buildAugmentedPrompt(context);
        List<Map<String, String>> history = context.getConversationHistory();
        if (history != null && !history.isEmpty()) {
            return sparkClient.chatWithHistory(systemPrompt, history, userMessage, temperature, maxTokens);
        }
        return sparkClient.chat(systemPrompt, userMessage, temperature, maxTokens);
    }

    /**
     * 构建增强提示词 — 子类可覆写以注入 RAG 内容
     */
    protected String buildAugmentedPrompt(AgentContext context) {
        return getSystemPrompt();
    }

    // --- Setter 注入 ---
    @Autowired
    public void setSparkClient(SparkClient sparkClient) { this.sparkClient = sparkClient; }
    public void setMemory(AgentMemory memory) { this.memory = memory; }
    public void setConfig(String key, Object value) { config.put(key, value); }
    public Object getConfig(String key) { return config.get(key); }
    public Map<String, Object> getConfig() { return config; }
}
