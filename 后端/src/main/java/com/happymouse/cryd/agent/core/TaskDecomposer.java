package com.happymouse.cryd.agent.core;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.happymouse.cryd.service.spark.SparkClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.UUID;

/**
 * 需求拆解Agent — 将用户请求拆解为子任务DAG
 * 示例: "生成指针的讲解和练习题" ->
 *   [搜索指针知识点] -> [生成讲解文档] + [生成练习题] -> [审核] -> [整合]
 */
public class TaskDecomposer extends BaseAgent {

    private static final Logger log = LoggerFactory.getLogger(TaskDecomposer.class);

    private static final String DECOMPOSE_PROMPT =
        """
        你是一个任务拆解专家。请将用户的学习需求拆解为子任务列表。
        可用Agent类型: knowledge_search(知识检索), course_design(课程讲解), question_gen(出题),
                      path_plan(路径规划), tutor_answer(辅导答疑), review(质量审核)

        以JSON格式输出:
        {
          "tasks": [
            {"id": "t1", "type": "knowledge_search", "description": "检索指针相关知识点", "priority": 1, "dependsOn": []},
            {"id": "t2", "type": "course_design", "description": "生成指针讲解文档", "priority": 2, "dependsOn": ["t1"]}
          ]
        }
        规则:
        - 简单答疑(tutor_answer)只需1个任务
        - 生成资源需要 knowledge_search -> course_design/question_gen -> review -> aggregate
        - 路径规划(path_plan)只需1个任务
        - priority: 1最高, 数字越大优先级越低
        """;

    public TaskDecomposer(SparkClient sparkClient) {
        this.sparkClient = sparkClient;
    }

    @Override
    public String getName() { return "需求拆解Agent"; }

    @Override
    public List<AgentCapability> getCapabilities() {
        return List.of(AgentCapability.of("task:decompose", "任务拆解、需求分析、子任务划分", 0.95));
    }

    @Override
    protected String getSystemPrompt() { return DECOMPOSE_PROMPT; }

    @Override
    protected String doExecute(AgentContext context) {
        String message = context.getOriginalMessage();
        log.info("[需求拆解] 分析请求: {}", message);

        try {
            String jsonStr = sparkClient.chat(DECOMPOSE_PROMPT, message, 0.3f, 1024);
            jsonStr = extractJson(jsonStr);
            JSONObject json = JSON.parseObject(jsonStr);
            JSONArray taskArray = json.getJSONArray("tasks");

            for (int i = 0; i < taskArray.size(); i++) {
                JSONObject t = taskArray.getJSONObject(i);
                AgentContext.SubTask task = new AgentContext.SubTask();
                task.setId(t.getString("id") != null ? t.getString("id") : UUID.randomUUID().toString().substring(0, 8));
                task.setType(t.getString("type"));
                task.setDescription(t.getString("description"));
                task.setPriority(t.getIntValue("priority", 1));
                JSONArray deps = t.getJSONArray("dependsOn");
                if (deps != null) {
                    for (int j = 0; j < deps.size(); j++) {
                        task.getDependsOn().add(deps.getString(j));
                    }
                }
                context.addTask(task);
            }
            log.info("[需求拆解] 拆解出{}个子任务", context.getTasks().size());

        } catch (Exception e) {
            log.warn("[需求拆解] AI拆解失败，使用默认拆解: {}", e.getMessage());
            fallbackDecompose(context, message);
        }

        return "decomposed:" + context.getTasks().size();
    }

    /**
     * 降级拆解 — 基于关键词的简单规则
     */
    private void fallbackDecompose(AgentContext context, String message) {
        String msg = message.toLowerCase();
        if (msg.contains("练习") || msg.contains("做题") || msg.contains("题目") || msg.contains("出题")) {
            context.addTask(new AgentContext.SubTask("t1", "question_gen", "生成练习题", 1));
        } else if (msg.contains("路径") || msg.contains("计划") || msg.contains("规划") || msg.contains("进度")) {
            context.addTask(new AgentContext.SubTask("t1", "path_plan", "规划学习路径", 1));
        } else if (msg.contains("知识点") || msg.contains("知识库") || msg.contains("大纲")) {
            context.addTask(new AgentContext.SubTask("t1", "knowledge_search", "检索知识点", 1));
        } else if (msg.contains("讲解") || msg.contains("学习") || msg.contains("教我") || msg.contains("介绍")) {
            context.addTask(new AgentContext.SubTask("t1", "course_design", "生成课程讲解", 1));
        } else {
            context.addTask(new AgentContext.SubTask("t1", "tutor_answer", "辅导答疑", 1));
        }
    }

    private String extractJson(String text) {
        if (text == null) return "{}";
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return text.substring(start, end + 1);
        }
        return text;
    }
}
