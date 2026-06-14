package com.happymouse.cryd.service.xunfei;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.concurrent.TimeUnit;

/**
 * 小米 MiMo Token Plan — TTS 语音合成 + 声音克隆
 * OpenAI 兼容接口，使用 Token Plan 预付费 key (tp-开头)
 * 文档: https://platform.xiaomimimo.com/docs
 */
@Service
public class XiaomiMiMoTtsService {

    private static final Logger log = LoggerFactory.getLogger(XiaomiMiMoTtsService.class);

    @Value("${mimo.api.url:https://token-plan-cn.xiaomimimo.com/v1}")
    private String baseUrl;

    @Value("${mimo.api.key:}")
    private String apiKey;

    @Value("${mimo.api.voice-id:}")
    private String configVoiceId;

    // Available voices: mimo_default, 冰糖, 茉莉, 苏打, 白桦, Mia, Chloe, Milo, Dean
    private static final String DEFAULT_VOICE = "茉莉";

    // 克隆声音持久化文件（部署时为 /opt/sghr/app/voice-id.txt，开发时为 ./voice-id.txt）
    private static final Path VOICE_FILE = Paths.get(
            System.getProperty("os.name", "").toLowerCase().contains("win")
                    ? "voice-id.txt"
                    : "/opt/sghr/app/voice-id.txt");

    // 当前使用的声音ID
    private volatile String clonedVoiceId = null;

    @PostConstruct
    public void init() {
        // 优先级: 配置文件 > 持久化文件
        if (configVoiceId != null && !configVoiceId.isEmpty()) {
            clonedVoiceId = configVoiceId;
            log.info("从配置加载声音ID: {}", configVoiceId);
        } else {
            try {
                if (Files.exists(VOICE_FILE)) {
                    clonedVoiceId = Files.readString(VOICE_FILE).trim();
                    log.info("从文件加载声音ID: {}", clonedVoiceId);
                }
            } catch (Exception e) {
                log.warn("读取声音文件失败", e);
            }
        }
    }

    private final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build();

    /**
     * 文字转语音
     * @param text 要朗读的文字
     * @param voice 音色（默认 default_zh，或克隆的声音ID）
     * @return MP3 音频字节，失败返回 null
     */
    public byte[] textToSpeech(String text, String voice) {
        if (apiKey == null || apiKey.isEmpty()) {
            log.error("MiMo TTS未配置: mimo.api.key 为空");
            return null;
        }
        if (text == null || text.trim().isEmpty()) {
            log.warn("TTS文字为空");
            return null;
        }

        // 最多读512字，避免请求过大
        String ttsText = text.length() > 512 ? text.substring(0, 512) : text;
        String voiceId = (voice != null && !voice.isEmpty()) ? voice
                : (clonedVoiceId != null ? clonedVoiceId : DEFAULT_VOICE);

        try {
            JSONObject body = new JSONObject();
            body.put("model", "mimo-v2.5-tts");

            // MiMo TTS 要求 messages 必须包含 assistant 角色，assistant 的 content 即为朗读内容
            JSONArray messages = new JSONArray();
            JSONObject userMsg = new JSONObject();
            userMsg.put("role", "user");
            userMsg.put("content", "请朗读以下文字");
            messages.add(userMsg);
            JSONObject assistantMsg = new JSONObject();
            assistantMsg.put("role", "assistant");
            assistantMsg.put("content", ttsText);
            messages.add(assistantMsg);
            body.put("messages", messages);

            // 音频参数
            JSONObject audio = new JSONObject();
            audio.put("voice", voiceId);
            audio.put("format", "mp3");
            body.put("audio", audio);

            Request request = new Request.Builder()
                    .url(baseUrl + "/chat/completions")
                    .addHeader("Authorization", "Bearer " + apiKey)
                    .addHeader("Content-Type", "application/json")
                    .post(RequestBody.create(body.toJSONString(), MediaType.parse("application/json")))
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    log.error("MiMo TTS HTTP错误: {}", response.code());
                    return null;
                }
                String respBody = response.body() != null ? response.body().string() : "";
                return extractAudioData(respBody);
            }
        } catch (Exception e) {
            log.error("MiMo TTS调用失败", e);
            return null;
        }
    }

    /**
     * 声音克隆 — 上传 10-30 秒音频样本
     * @param audioBytes 音频样本 (WAV/PCM, 16kHz mono 最佳)
     * @return 克隆的声音ID，失败返回 null
     */
    public String cloneVoice(byte[] audioBytes) {
        if (apiKey == null || apiKey.isEmpty()) {
            log.error("MiMo声音克隆未配置");
            return null;
        }
        if (audioBytes == null || audioBytes.length < 10000) {
            log.warn("声音克隆音频太短，至少需要10秒");
            return null;
        }

        try {
            // 音频以 base64 形式传给模型
            String audioBase64 = Base64.getEncoder().encodeToString(audioBytes);

            JSONObject body = new JSONObject();
            body.put("model", "mimo-v2.5-tts-voicedesign");

            JSONArray messages = new JSONArray();
            JSONObject msg = new JSONObject();
            msg.put("role", "user");
            msg.put("content", "请克隆这段声音，生成一个声音克隆ID");
            messages.add(msg);
            body.put("messages", messages);

            // 音频设计参数
            JSONObject audio = new JSONObject();
            audio.put("input_audio", audioBase64);
            body.put("audio", audio);

            Request request = new Request.Builder()
                    .url(baseUrl + "/chat/completions")
                    .addHeader("Authorization", "Bearer " + apiKey)
                    .addHeader("Content-Type", "application/json")
                    .post(RequestBody.create(body.toJSONString(), MediaType.parse("application/json")))
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    log.error("MiMo声音克隆HTTP错误: {}", response.code());
                    return null;
                }
                String respBody = response.body() != null ? response.body().string() : "";
                String voiceId = extractVoiceId(respBody);
                if (voiceId != null) {
                    persistVoiceId(voiceId);
                    log.info("声音克隆成功: voiceId={}", voiceId);
                }
                return voiceId;
            }
        } catch (Exception e) {
            log.error("MiMo声音克隆失败", e);
            return null;
        }
    }

    /**
     * 获取当前已克隆的声音ID
     */
    public String getClonedVoiceId() {
        return clonedVoiceId;
    }

    /**
     * 设置克隆声音ID（用于恢复之前克隆的声音）
     */
    public void setClonedVoiceId(String voiceId) {
        persistVoiceId(voiceId);
    }

    /**
     * 持久化声音ID到文件
     */
    private void persistVoiceId(String voiceId) {
        this.clonedVoiceId = voiceId;
        try {
            Files.createDirectories(VOICE_FILE.getParent());
            Files.writeString(VOICE_FILE, voiceId);
            log.info("声音ID已持久化: {}", voiceId);
        } catch (Exception e) {
            log.error("持久化声音ID失败", e);
        }
    }

    /**
     * 从 MiMo 响应中提取音频数据
     * OpenAI 兼容格式：response.audio.data (base64) 或 response.choices[0].message.audio.data
     */
    private byte[] extractAudioData(String respBody) {
        try {
            JSONObject json = JSON.parseObject(respBody);

            // 方式1：顶层 audio 字段 (OpenAI 标准格式)
            JSONObject audio = json.getJSONObject("audio");
            if (audio != null) {
                String data = audio.getString("data");
                if (data != null && !data.isEmpty()) {
                    return Base64.getDecoder().decode(data);
                }
            }

            // 方式2：choices[0].message.audio.data
            JSONArray choices = json.getJSONArray("choices");
            if (choices != null && !choices.isEmpty()) {
                JSONObject choice = choices.getJSONObject(0);
                JSONObject message = choice.getJSONObject("message");
                if (message != null) {
                    JSONObject msgAudio = message.getJSONObject("audio");
                    if (msgAudio != null) {
                        String data = msgAudio.getString("data");
                        if (data != null && !data.isEmpty()) {
                            return Base64.getDecoder().decode(data);
                        }
                    }
                    // 方式3：message.content 里嵌入了 audio 信息
                }
            }

            // 方式4：直接检查是否有任何 base64 音频数据
            log.warn("无法从MiMo响应中提取音频数据: {}", respBody.substring(0, Math.min(200, respBody.length())));
            return null;
        } catch (Exception e) {
            log.warn("解析MiMo TTS响应异常", e);
            return null;
        }
    }

    /**
     * 从 MiMo 声音克隆响应中提取 voice_id
     */
    private String extractVoiceId(String respBody) {
        try {
            JSONObject json = JSON.parseObject(respBody);

            // 检查 audio.voice_id
            JSONObject audio = json.getJSONObject("audio");
            if (audio != null) {
                String voiceId = audio.getString("voice_id");
                if (voiceId != null && !voiceId.isEmpty()) return voiceId;
                voiceId = audio.getString("id");
                if (voiceId != null && !voiceId.isEmpty()) return voiceId;
            }

            // 检查 choices[0].message
            JSONArray choices = json.getJSONArray("choices");
            if (choices != null && !choices.isEmpty()) {
                JSONObject message = choices.getJSONObject(0).getJSONObject("message");
                if (message != null) {
                    String content = message.getString("content");
                    if (content != null && !content.isEmpty()) {
                        // 尝试从 content 中提取 voice_id
                        return content.trim();
                    }
                    JSONObject msgAudio = message.getJSONObject("audio");
                    if (msgAudio != null) {
                        String voiceId = msgAudio.getString("voice_id");
                        if (voiceId != null) return voiceId;
                        voiceId = msgAudio.getString("id");
                        if (voiceId != null) return voiceId;
                    }
                }
            }

            log.warn("无法从MiMo声音克隆响应中提取voice_id: {}", respBody.substring(0, Math.min(200, respBody.length())));
            return null;
        } catch (Exception e) {
            log.warn("解析MiMo声音克隆响应异常", e);
            return null;
        }
    }
}
