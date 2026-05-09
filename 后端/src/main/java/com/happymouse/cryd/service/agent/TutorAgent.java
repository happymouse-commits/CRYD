package com.happymouse.cryd.service.agent;

import com.happymouse.cryd.model.dto.ChatRequest;
import com.happymouse.cryd.model.dto.ChatResponse;
import com.happymouse.cryd.model.entity.Student;
import com.happymouse.cryd.repository.StudentRepository;
import com.happymouse.cryd.service.spark.SparkClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 辅导老师 - 即时答疑解惑，提供多模态辅导
 */
@Service
public class TutorAgent {

    private static final Logger log = LoggerFactory.getLogger(TutorAgent.class);
    private static final String SYSTEM_PROMPT = """
            你是「辅导老师」，一位耐心友好的C语言程序设计辅导老师。
            
            你的职责：
            1. 回答学生关于C语言的任何问题
            2. 用通俗易懂的语言解释概念
            3. 提供代码示例帮助理解
            4. 指出常见错误和陷阱
            5. 根据学生画像调整讲解深度
            
            辅导风格：
            - 如果学生是初学者，用最简单的语言，避免过多术语
            - 如果学生有一定基础，可以更深入，提及相关原理
            - 每个回答都尽量包含一个可运行的C语言代码示例
            - 鼓励学生，多用"你可以试试""很好的问题"等正面反馈
            
            用Markdown格式输出，代码用```c代码块。
            """;

    private final StudentRepository studentRepository;
    private final SparkClient sparkClient;

    public TutorAgent(StudentRepository studentRepository, SparkClient sparkClient) {
        this.studentRepository = studentRepository;
        this.sparkClient = sparkClient;
    }

    public ChatResponse answer(ChatRequest request) {
        Student student = studentRepository.findById(request.getStudentId()).orElse(null);
        String message = request.getMessage();

        String userPrompt = buildUserPrompt(message, student);
        String answer = sparkClient.chat(SYSTEM_PROMPT, userPrompt, 0.7f, 4096);

        ChatResponse response = new ChatResponse();
        response.setAgentName("辅导老师");
        response.setAgentDescription("即时答疑，帮你搞懂知识点");
        response.setMessage(answer);
        response.setProfileUpdated(true);
        return response;
    }

    /**
     * 多轮对话答疑
     */
    public ChatResponse answerWithHistory(ChatRequest request, List<Map<String, String>> history) {
        Student student = studentRepository.findById(request.getStudentId()).orElse(null);
        String message = request.getMessage();

        String userPrompt = buildUserPrompt(message, student);
        String answer = sparkClient.chatWithHistory(SYSTEM_PROMPT, history, userPrompt, 0.7f, 4096);

        ChatResponse response = new ChatResponse();
        response.setAgentName("辅导老师");
        response.setAgentDescription("即时答疑，帮你搞懂知识点");
        response.setMessage(answer);
        return response;
    }

    private String buildUserPrompt(String message, Student student) {
        if (student == null) return message;
        return String.format("[学生水平：%d/100，风格：%s]\n\n%s",
                student.getKnowledgeLevel(),
                student.getCognitiveStyle(),
                message);
    }
}
