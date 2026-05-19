package com.happymouse.cryd.service.agent;

import com.happymouse.cryd.agent.core.AgentCapability;
import com.happymouse.cryd.agent.core.AgentContext;
import com.happymouse.cryd.agent.core.BaseAgent;
import com.happymouse.cryd.model.dto.ChatRequest;
import com.happymouse.cryd.model.dto.ChatResponse;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 知识库管理员 - 管理课程知识库，提供知识点检索服务
 */
@Component("knowledgeManagerAgent")
public class KnowledgeManagerAgent extends BaseAgent {

    private static final String SYSTEM_PROMPT = """
            你是「知识库管理员」，负责管理C语言程序设计课程的知识体系。

            C语言知识体系：
            第1章：C语言概述与开发环境
            第2章：数据类型、运算符与表达式
            第3章：顺序结构程序设计
            第4章：选择结构程序设计
            第5章：循环结构程序设计
            第6章：函数
            第7章：数组
            第8章：指针
            第9章：结构体与共用体
            第10章：文件操作
            第11章：动态内存管理
            第12章：预处理器与多文件编译

            你的任务：
            1. 根据学生查询，返回相关知识点及所在章节
            2. 列出知识点的前置知识和后续知识
            3. 标注重点难点
            4. 给出学习建议

            用Markdown格式输出，用思维导图文字版展示知识关联。
            """;

    public KnowledgeManagerAgent() {}

    @Override
    public String getName() { return "知识库管理员"; }

    @Override
    public List<AgentCapability> getCapabilities() {
        return List.of(
            AgentCapability.of("knowledge:search", "知识检索、知识点查询", 0.92),
            AgentCapability.of("knowledge:structure", "知识体系结构化管理", 0.88)
        );
    }

    @Override
    protected String getSystemPrompt() { return SYSTEM_PROMPT; }

    @Override
    protected String doExecute(AgentContext context) {
        String message = context.getOriginalMessage();

        if (sparkClient == null) {
            return "AI服务暂不可用，请稍后再试";
        }

        return sparkClient.chat(SYSTEM_PROMPT, message, 0.3f, 1500);
    }

    /**
     * 保持向后兼容的旧接口
     */
    public ChatResponse search(ChatRequest request) {
        AgentContext ctx = new AgentContext(request.getStudentId(), request.getMessage(), null);
        String content = execute(ctx).getOutput();

        ChatResponse response = new ChatResponse();
        response.setAgentName("知识库管理员");
        response.setAgentDescription("知识体系检索与关联");
        response.setMessage(content);
        return response;
    }
}
