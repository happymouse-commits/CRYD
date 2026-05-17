package com.happymouse.cryd.agent.core;

import com.happymouse.cryd.service.spark.SparkClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.regex.Pattern;

/**
 * 审核Agent — 校验各Agent输出质量
 * 质量不达标则返回具体反馈供重试
 */
public class AgentReviewer extends BaseAgent {

    private static final Logger log = LoggerFactory.getLogger(AgentReviewer.class);

    private static final String REVIEW_PROMPT =
        """
        你是一个内容质量审核专家。请审核以下AI生成的学习内容质量。
        评估标准:
        1. 内容准确性: C语言知识点是否正确
        2. 代码质量: 代码示例是否可运行、有无明显错误
        3. 难度适配: 是否适合目标学生水平
        4. 完整性: 是否覆盖了用户需求的核心内容

        以JSON格式输出:
        {"passed": true/false, "score": 0.0-1.0, "issues": ["问题1", "问题2"], "suggestion": "改进建议"}
        """;

    // 基础代码检查模式
    private static final Pattern C_CODE_BLOCK = Pattern.compile("```c\\s*\\n([\\s\\S]*?)```");
    private static final Pattern HAS_MAIN = Pattern.compile("int\\s+main\\s*\\(");
    private static final Pattern HAS_INCLUDE = Pattern.compile("#include\\s*<");

    public AgentReviewer(SparkClient sparkClient) {
        this.sparkClient = sparkClient;
    }

    @Override
    public String getName() { return "审核Agent"; }

    @Override
    public List<AgentCapability> getCapabilities() {
        return List.of(AgentCapability.of("review:quality", "内容质量审核、代码检查、难度评估", 0.90));
    }

    @Override
    protected String getSystemPrompt() { return REVIEW_PROMPT; }

    @Override
    protected String doExecute(AgentContext context) {
        // 审核所有已完成Agent的输出
        double lowestScore = 1.0;
        boolean allPassed = true;

        for (AgentContext.AgentResult result : context.getAllResults().values()) {
            ReviewResult review = reviewOutput(result.getOutput(), context.getStudentId());
            if (!review.passed) {
                allPassed = false;
                log.warn("[审核] {} 不通过: score={}, issues={}", result.getAgentName(), review.score, review.issues);
            }
            if (review.score < lowestScore) {
                lowestScore = review.score;
            }
            context.setAttribute("review_" + result.getAgentName(), review);
        }

        context.setAttribute("reviewPassed", allPassed);
        context.setAttribute("reviewScore", lowestScore);

        return allPassed ? "审核通过" : "审核不通过";
    }

    /**
     * 静态检查 + AI语义质量审核
     */
    public ReviewResult reviewOutput(String output, Long studentId) {
        ReviewResult review = new ReviewResult();
        review.passed = true;
        review.score = 1.0;

        if (output == null || output.isEmpty()) {
            review.passed = false;
            review.score = 0;
            review.issues.add("输出为空");
            review.suggestion = "请重新生成内容";
            return review;
        }

        // 静态检查：有代码块则检查质量
        var matcher = C_CODE_BLOCK.matcher(output);
        boolean hasCode = false;
        while (matcher.find()) {
            hasCode = true;
            String code = matcher.group(1);
            if (!HAS_INCLUDE.matcher(code).find() && !HAS_MAIN.matcher(code).find()) {
                review.issues.add("代码块缺少必要的#include或main函数");
                review.score -= 0.1;
            }
        }

        // 内容长度检查
        if (output.length() < 50) {
            review.issues.add("输出内容过短，可能不完整");
            review.score -= 0.2;
        }

        // AI语义质量审核 — 调用星火大模型
        if (sparkClient != null) {
            try {
                String aiReview = sparkClient.chat(
                    REVIEW_PROMPT,
                    "请审核以下内容：\n" + (output.length() > 2000 ? output.substring(0, 2000) : output),
                    0.3f, 300
                );
                if (aiReview != null && !aiReview.isEmpty()) {
                    review.aiFeedback = aiReview;
                    // 尝试提取评分
                    try {
                        String jsonStr = aiReview.replaceAll("```json|```", "").trim();
                        if (jsonStr.startsWith("{")) {
                            var json = com.alibaba.fastjson2.JSON.parseObject(jsonStr);
                            if (json.containsKey("score")) {
                                double aiScore = json.getDoubleValue("score");
                                review.score = (review.score + aiScore) / 2.0; // 综合静态检查和AI评分
                            }
                            if (json.containsKey("passed")) {
                                review.passed = review.passed && json.getBooleanValue("passed");
                            }
                            if (json.containsKey("issues")) {
                                var aiIssues = json.getJSONArray("issues");
                                for (int i = 0; i < aiIssues.size(); i++) {
                                    review.issues.add(aiIssues.getString(i));
                                }
                            }
                            if (json.containsKey("suggestion")) {
                                review.suggestion = json.getString("suggestion");
                            }
                        }
                    } catch (Exception parseErr) {
                        log.debug("AI审核结果JSON解析失败，使用静态检查结果");
                    }
                }
            } catch (Exception e) {
                log.warn("AI审核调用失败，使用静态检查结果: {}", e.getMessage());
            }
        }

        review.score = Math.max(0, review.score);
        if (review.score < 0.6) review.passed = false;

        return review;
    }

    /**
     * 审核结果
     */
    public static class ReviewResult {
        public boolean passed = true;
        public double score = 1.0;
        public List<String> issues = new java.util.ArrayList<>();
        public String suggestion = "";
        public String aiFeedback = "";

        @Override
        public String toString() {
            return String.format("Review{passed=%s, score=%.2f, issues=%s}", passed, score, issues);
        }
    }
}
