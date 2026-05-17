package com.happymouse.cryd.agent.core;

import com.happymouse.cryd.service.spark.SparkClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * 整合Agent — 合并多Agent产出为统一响应
 */
public class AggregatorAgent extends BaseAgent {

    private static final Logger log = LoggerFactory.getLogger(AggregatorAgent.class);

    private static final String AGGREGATE_PROMPT =
        """
        你是一个内容整合专家。请将多个AI智能体生成的内容整合为一份完整、连贯的学习材料。

        要求:
        1. 按逻辑顺序组织: 知识检索 -> 概念讲解 -> 练习题 -> 拓展
        2. 去除重复内容
        3. 保持Markdown格式，层次分明
        4. 如有代码示例，确保完整可运行
        5. 为不同部分添加分隔线和标题
        6. 最后添加"📝 学习建议"小节
        """;

    public AggregatorAgent(SparkClient sparkClient) {
        this.sparkClient = sparkClient;
    }

    @Override
    public String getName() { return "整合Agent"; }

    @Override
    public List<AgentCapability> getCapabilities() {
        return List.of(AgentCapability.of("aggregate:content", "内容整合、去重、格式化输出", 0.85));
    }

    @Override
    protected String getSystemPrompt() { return AGGREGATE_PROMPT; }

    @Override
    protected String doExecute(AgentContext context) {
        StringBuilder combined = new StringBuilder();
        combined.append("# 学习内容\n\n");

        int idx = 1;
        for (AgentContext.AgentResult result : context.getAllResults().values()) {
            if (result.isSuccess() && result.getOutput() != null) {
                // 跳过管道Agent的元数据输出
                if (result.getOutput().startsWith("decomposed:") || result.getOutput().startsWith("审核")) {
                    continue;
                }
                combined.append("---\n\n");
                combined.append(result.getOutput()).append("\n\n");
                idx++;
            }
        }

        // 如果只有1个Agent的输出，直接返回
        if (idx <= 2) {
            // 找到第一个非管道输出
            for (AgentContext.AgentResult result : context.getAllResults().values()) {
                if (result.isSuccess() && result.getOutput() != null
                    && !result.getOutput().startsWith("decomposed:")
                    && !result.getOutput().startsWith("审核")) {
                    return result.getOutput();
                }
            }
        }

        // 多Agent输出，调用LLM整合
        if (combined.length() > 100) {
            // 直接拼接，让AI整合
            try {
                String aggregated = sparkClient.chat(AGGREGATE_PROMPT,
                    "请整合以下内容：\n\n" + combined.toString(), 0.5f, 2048);
                return aggregated;
            } catch (Exception e) {
                log.warn("[整合] AI整合失败，直接拼接: {}", e.getMessage());
                return combined.toString();
            }
        }

        return combined.toString();
    }
}
