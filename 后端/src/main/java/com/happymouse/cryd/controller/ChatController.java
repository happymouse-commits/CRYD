package com.happymouse.cryd.controller;

import com.happymouse.cryd.common.Result;
import com.happymouse.cryd.model.dto.ChatRequest;
import com.happymouse.cryd.model.dto.ChatResponse;
import com.happymouse.cryd.model.entity.ChatMessage;
import com.happymouse.cryd.repository.ChatMessageRepository;
import com.happymouse.cryd.service.agent.AgentOrchestrator;
import com.happymouse.cryd.service.rag.RagService;
import com.happymouse.cryd.service.rag.VectorStore;
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
    private final RagService ragService;
    private final VectorStore vectorStore;
    
    @Value("${server.port:8080}")
    private String serverPort;

    public ChatController(AgentOrchestrator orchestrator, ChatMessageRepository chatMessageRepository, SparkClient sparkClient, RagService ragService, VectorStore vectorStore) {
        this.orchestrator = orchestrator;
        this.chatMessageRepository = chatMessageRepository;
        this.sparkClient = sparkClient;
        this.ragService = ragService;
        this.vectorStore = vectorStore;
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
            String msg = request.getMessage();
            // 短消息且非复杂问题 → 快速通道，跳过 PipelineOrchestrator
            boolean isShort = msg != null && msg.length() < 30;
            boolean isComplex = msg != null && (msg.contains("代码") || msg.contains("编程")
                    || msg.contains("算法") || msg.contains("指针") || msg.contains("题目"));
            ChatResponse response;
            if (isShort && !isComplex) {
                String aiReply = sparkClient.chat(
                    "你是C语言辅导老师，回答简洁友好。学生水平：大一。",
                    msg, 0.5f, 512);
                response = new ChatResponse();
                response.setAgentName("辅导老师");
                response.setMessage(aiReply);
            } else {
                response = orchestrator.process(request);
            }

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

    /**
     * RAG四道门测试接口
     */
    @GetMapping("/rag/test")
    public Result<Map<String, Object>> testRag(@RequestParam String query, @RequestParam(defaultValue = "summary") String taskType) {
        Map<String, Object> result = new HashMap<>();
        result.put("query", query);
        result.put("taskType", taskType);
        result.put("vectorStoreSize", vectorStore.size());
        
        // 第一道门：检索门控
        RagService.RetrievalGateResult gate = ragService.gateByRetrieval(query);
        result.put("gate1_passed", gate.passed);
        result.put("gate1_rejectReason", gate.rejectReason);
        result.put("gate1_hitCount", gate.fragments.size());
        
        if (!gate.passed) {
            result.put("result", "被第一道门拦截：无相关知识");
            return Result.success(result);
        }
        
        // 记录检索到的内容摘要
        for (int i = 0; i < Math.min(gate.fragments.size(), 3); i++) {
            RagService.KnowledgeFragment f = gate.fragments.get(i);
            result.put("fragment_" + i + "_similarity", String.format("%.3f", f.similarity));
            result.put("fragment_" + i + "_preview", f.content != null && f.content.length() > 80 ? f.content.substring(0, 80) + "..." : f.content);
        }
        
        // 完整流水线
        RagService.RagPipelineResult pipelineResult = ragService.generateWithGuard(taskType, query);
        result.put("pipeline_success", pipelineResult.success);
        result.put("pipeline_confidence", pipelineResult.confidence);
        result.put("pipeline_error", pipelineResult.errorMessage);
        if (pipelineResult.content != null) {
            result.put("generated_content", pipelineResult.content.length() > 500 ? pipelineResult.content.substring(0, 500) + "..." : pipelineResult.content);
        }
        
        return Result.success(result);
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