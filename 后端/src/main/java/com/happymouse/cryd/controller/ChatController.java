package com.happymouse.cryd.controller;

import com.happymouse.cryd.common.Result;
import com.happymouse.cryd.model.dto.ChatRequest;
import com.happymouse.cryd.model.dto.ChatResponse;
import com.happymouse.cryd.model.entity.ChatMessage;
import com.happymouse.cryd.repository.ChatMessageRepository;
import com.happymouse.cryd.service.agent.AgentOrchestrator;
import com.happymouse.cryd.service.spark.SparkClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);
    private final AgentOrchestrator orchestrator;
    private final ChatMessageRepository chatMessageRepository;

    private final SparkClient sparkClient;
    
    @Value("${server.port:8080}")
    private String serverPort;

    public ChatController(AgentOrchestrator orchestrator, ChatMessageRepository chatMessageRepository, SparkClient sparkClient) {
        this.orchestrator = orchestrator;
        this.chatMessageRepository = chatMessageRepository;
        this.sparkClient = sparkClient;
    }

    @PostMapping("/send")
    public Result<ChatResponse> send(@RequestBody ChatRequest request) {
        log.info("收到聊天请求: studentId={}", request.getStudentId());

        ChatMessage userMsg = new ChatMessage();
        userMsg.setStudentId(request.getStudentId());
        userMsg.setRole("user");
        userMsg.setContent(request.getMessage());
        chatMessageRepository.save(userMsg);

        try {
            ChatResponse response = orchestrator.process(request);

            ChatMessage aiMsg = new ChatMessage();
            aiMsg.setStudentId(request.getStudentId());
            aiMsg.setRole("assistant");
            aiMsg.setContent(response.getMessage());
            aiMsg.setAgentName(response.getAgentName());
            chatMessageRepository.save(aiMsg);

            return Result.success(response);
        } catch (Exception e) {
            log.error("AI处理失败", e);
            ChatResponse fallback = new ChatResponse();
            fallback.setAgentName("系统");
            fallback.setMessage("抱歉，AI辅导暂时不可用，请稍后再试。错误：" + e.getMessage());
            return Result.success(fallback);
        }
    }

    @GetMapping("/history/{studentId}")
    public Result<List<ChatMessage>> history(@PathVariable Long studentId) {
        return Result.success(chatMessageRepository.findByStudentIdOrderByCreatedAtAsc(studentId));
    }

    /**
     * 语音转文字接口 — 调用讯飞语音听写 API
     */
    @PostMapping("/voice-to-text")
    public Result<Map<String, String>> voiceToText(@RequestParam("audio") MultipartFile audioFile) {
        log.info("收到语音转文字请求, 文件大小: {}", audioFile.getSize());
        Map<String, String> result = new HashMap<>();

        try {
            byte[] audioBytes = audioFile.getBytes();
            String text = sparkClient.voiceToText(audioBytes);
            if (text == null || text.isEmpty()) {
                log.warn("语音听写返回空结果");
                result.put("text", "语音识别未得到结果，请重试或检查音频质量");
            } else {
                log.info("语音听写成功: {}", text);
                result.put("text", text);
            }
            return Result.success(result);
        } catch (Exception e) {
            log.error("语音转文字失败", e);
            result.put("text", "语音识别失败: " + e.getMessage());
            return Result.success(result);
        }
    }

    /**
     * 图片上传接口
     */
    @PostMapping("/upload-image")
    public Result<Map<String, String>> uploadImage(@RequestParam("image") MultipartFile imageFile) {
        log.info("收到图片上传请求, 文件大小: {}, 文件名: {}", imageFile.getSize(), imageFile.getOriginalFilename());
        Map<String, String> result = new HashMap<>();
        
        try {
            // 保存图片到本地
            String uploadDir = "uploads/images/";
            File dir = new File(uploadDir);
            if (!dir.exists()) {
                dir.mkdirs();
            }
            
            String fileName = System.currentTimeMillis() + "_" + imageFile.getOriginalFilename();
            File destFile = new File(uploadDir + fileName);
            imageFile.transferTo(destFile);
            
            // 生成访问URL
            String url = "http://localhost:" + serverPort + "/api/chat/image/" + fileName;
            result.put("url", url);
            
            // 转换为Base64
            byte[] imageBytes = Files.readAllBytes(destFile.toPath());
            String base64 = Base64.getEncoder().encodeToString(imageBytes);
            String fileType = imageFile.getContentType();
            if (fileType == null) {
                fileType = "image/jpeg";
            }
            result.put("base64", "data:" + fileType + ";base64," + base64);
            
            return Result.success(result);
        } catch (IOException e) {
            log.error("图片上传失败", e);
            return Result.error("图片上传失败");
        }
    }

    /**
     * 图片访问接口
     */
    @GetMapping("/image/{fileName}")
    public byte[] getImage(@PathVariable String fileName) throws IOException {
        File file = new File("uploads/images/" + fileName);
        if (!file.exists()) {
            throw new RuntimeException("图片不存在");
        }
        return Files.readAllBytes(file.toPath());
    }

    /**
     * 发送图片给AI分析
     */
    @PostMapping("/send-image")
    public Result<ChatResponse> sendImage(@RequestBody Map<String, Object> request) {
        Long studentId = ((Number) request.get("studentId")).longValue();
        String imageBase64 = (String) request.get("imageBase64");
        
        log.info("收到图片分析请求, studentId: {}", studentId);
        
        try {
            // 使用星火大模型分析图片
            String prompt = "请分析这张图片的内容，描述图片中包含的信息。";
            String response = sparkClient.chatWithImage(prompt, imageBase64);
            
            ChatResponse chatResponse = new ChatResponse();
            chatResponse.setAgentName("图像分析师");
            chatResponse.setMessage(response);
            
            // 保存消息
            ChatMessage aiMsg = new ChatMessage();
            aiMsg.setStudentId(studentId);
            aiMsg.setRole("assistant");
            aiMsg.setContent(response);
            aiMsg.setAgentName("图像分析师");
            chatMessageRepository.save(aiMsg);
            
            return Result.success(chatResponse);
        } catch (Exception e) {
            log.error("图片分析失败", e);
            ChatResponse fallback = new ChatResponse();
            fallback.setAgentName("系统");
            fallback.setMessage("图片分析失败，请重试：" + e.getMessage());
            return Result.success(fallback);
        }
    }
}