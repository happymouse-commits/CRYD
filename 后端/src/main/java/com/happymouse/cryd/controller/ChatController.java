package com.happymouse.cryd.controller;

import com.happymouse.cryd.common.Result;
import com.happymouse.cryd.model.dto.ChatRequest;
import com.happymouse.cryd.model.dto.ChatResponse;
import com.happymouse.cryd.model.entity.ChatMessage;
import com.happymouse.cryd.repository.ChatMessageRepository;
import com.happymouse.cryd.service.agent.AgentOrchestrator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/chat")
@CrossOrigin(origins = "*")
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);
    private final AgentOrchestrator orchestrator;
    private final ChatMessageRepository chatMessageRepository;

    public ChatController(AgentOrchestrator orchestrator, ChatMessageRepository chatMessageRepository) {
        this.orchestrator = orchestrator;
        this.chatMessageRepository = chatMessageRepository;
    }

    @PostMapping("/send")
    public Result<ChatResponse> send(@RequestBody ChatRequest request) {
        log.info("收到聊天请求: studentId={}", request.getStudentId());

        ChatMessage userMsg = new ChatMessage();
        userMsg.setStudentId(request.getStudentId());
        userMsg.setRole("user");
        userMsg.setContent(request.getMessage());
        chatMessageRepository.save(userMsg);

        ChatResponse response = orchestrator.process(request);

        ChatMessage aiMsg = new ChatMessage();
        aiMsg.setStudentId(request.getStudentId());
        aiMsg.setRole("assistant");
        aiMsg.setContent(response.getMessage());
        aiMsg.setAgentName(response.getAgentName());
        chatMessageRepository.save(aiMsg);

        return Result.success(response);
    }

    @GetMapping("/history/{studentId}")
    public Result<List<ChatMessage>> history(@PathVariable Long studentId) {
        return Result.success(chatMessageRepository.findByStudentIdOrderByCreatedAtAsc(studentId));
    }
}
