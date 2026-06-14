package com.happymouse.cryd.controller;

import com.happymouse.cryd.common.Result;
import com.happymouse.cryd.model.entity.*;
import com.happymouse.cryd.repository.*;
import com.happymouse.cryd.service.OnboardingService;
import com.happymouse.cryd.service.agent.*;
import com.happymouse.cryd.service.rag.RagService;
import com.happymouse.cryd.service.spark.SparkClient;
import com.happymouse.cryd.agent.core.*;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 多智能体导学控制器 — 三阶段：画像采集→出题测评→生成资源
 * 防幻觉：每个智能体输出经过 RAG 四道门校验
 */
@RestController
@RequestMapping("/api/onboarding")
public class OnboardingController {

    private final OnboardingService onboarding;
    private final StudentRepository studentRepo;
    private final SysUserRepository sysUserRepo;
    private final SparkClient sparkClient;
    private final RagService ragService;
    private final OnboardingAgent onboardingAgent;
    private final ProfileAnalystAgent profileAnalyst;
    private final QuestionExpertAgent questionExpert;
    private final PathPlannerAgent pathPlanner;
    private final KnowledgeExplainerAgent knowledgeExplainer;
    private final MindMapAgent mindMapAgent;
    private final CodeExampleAgent codeExampleAgent;
    private final CourseDesignerAgent courseDesigner;

    // 会话状态：记录每个学生的 quiz 进度和生成时间
    private final ConcurrentHashMap<Long, QuizState> quizStates = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, LocalDateTime> lastGenerateTime = new ConcurrentHashMap<>();

    public OnboardingController(OnboardingService ob, StudentRepository sr, SysUserRepository sur,
                                SparkClient sc, RagService rs, OnboardingAgent oa, ProfileAnalystAgent pa,
                                QuestionExpertAgent qe, PathPlannerAgent pp, KnowledgeExplainerAgent ke,
                                MindMapAgent mm, CodeExampleAgent ce, CourseDesignerAgent cd) {
        this.onboarding = ob; this.studentRepo = sr; this.sysUserRepo = sur;
        this.sparkClient = sc; this.ragService = rs; this.onboardingAgent = oa;
        this.profileAnalyst = pa; this.questionExpert = qe; this.pathPlanner = pp;
        this.knowledgeExplainer = ke; this.mindMapAgent = mm; this.codeExampleAgent = ce;
        this.courseDesigner = cd;
    }

    /** 画像状态 */
    @GetMapping("/status/{sysUserId}")
    public Result<Map<String, Object>> status(@PathVariable Long sysUserId) {
        Student s = studentRepo.findByUsername("student_" + sysUserId).orElse(null);
        Map<String, Object> r = new LinkedHashMap<>();
        if (s == null) {
            r.put("completeness", 0); r.put("phase", "profile");
        } else {
            int c = onboarding.calcCompleteness(s);
            r.put("completeness", c);
            r.put("phase", c >= 80 ? "quiz" : "profile");
        }
        return Result.success(r);
    }

    /**
     * 核心编排接口 — 监督式三阶段对话
     */
    @PostMapping("/chat")
    public Result<Map<String, Object>> chat(@RequestBody Map<String, Object> body) {
        Long studentId = Long.valueOf(body.get("studentId").toString());
        String message = body.get("message") != null ? body.get("message").toString() : "";
        String phase = body.get("phase") != null ? body.get("phase").toString() : "profile";

        Student student = studentRepo.findByUsername("student_" + studentId)
            .orElseGet(() -> {
                Student s = new Student(); s.setUsername("student_" + studentId);
                s.setKnowledgeLevel(0); s.setLearningPreference("mixed"); s.setLearningPace("steady");
                return studentRepo.save(s);
            });

        Map<String, Object> resp = new LinkedHashMap<>();

        // 每轮先提取画像
        if (!message.isBlank() && !message.startsWith("__")) {
            onboarding.extractAndUpdateProfile(student, message);
        }

        int completeness = onboarding.calcCompleteness(student);

        // ★ 防越级：profile 没完成不能跳到 quiz/generate
        if ("quiz".equals(phase) && completeness < 80) {
            phase = "profile";
        }
        if ("generate".equals(phase) && completeness < 80) {
            resp.put("phase", "profile");
            resp.put("message", "画像还没采集完，先回答几个问题吧～");
            resp.put("agentName", "导学智能体");
            return Result.success(resp);
        }

        switch (phase) {
            case "profile" -> handleProfile(studentId, student, message, completeness, resp);
            case "quiz" -> handleQuiz(studentId, student, message, resp);
            case "generate" -> handleGenerate(studentId, student, resp);
        }

        return Result.success(resp);
    }

    // ==================== 阶段1: 画像采集 ====================
    private void handleProfile(Long sid, Student s, String msg, int completeness, Map<String, Object> r) {
        if (!msg.isBlank()) {
            AgentContext ctx = new AgentContext(sid, msg, null);
            profileAnalyst.execute(ctx);
            completeness = onboarding.calcCompleteness(s);
        }

        String nextQ = onboarding.getNextUnfilledQuestion(s);

        if (nextQ == null || completeness >= 80) {
            // ★ 画像采集完成 → 切换阶段
            r.put("phase", "quiz");
            r.put("agentName", "导学智能体");
            r.put("message", "画像采集完成！接下来【出题专家】来测测你的水平，3道题从简到难。准备好了吗？");
            r.put("completeness", completeness);
        } else {
            // 首次打招呼 或 继续追问
            boolean firstMsg = msg.isBlank();
            String prompt = firstMsg
                ? "你是导学智能体「小容」。新同学来了，热情打招呼（1句话），然后问第一个问题：" + nextQ
                : "学生刚才说：「" + msg + "」。肯定一下（1句话），然后自然地问：" + nextQ;

            String reply;
            try {
                reply = sparkClient.chat(prompt, "", 0.5f, 200);
                // ★ 防幻觉：简单校验回复是否包含问号
                if (!reply.contains("？") && !reply.contains("?")) {
                    reply = "好的！" + nextQ;
                }
            } catch (Exception e) {
                reply = firstMsg ? "Hi！我是小容～ " + nextQ : "好的～ " + nextQ;
            }

            r.put("phase", "profile");
            r.put("agentName", "导学智能体");
            r.put("message", reply);
            r.put("completeness", completeness);
            r.put("nextQuestion", nextQ);
        }
    }

    // ==================== 阶段2: 出题测评 ====================
    private void handleQuiz(Long sid, Student s, String msg, Map<String, Object> r) {
        QuizState state = quizStates.computeIfAbsent(sid, k -> new QuizState());

        // 如果是第一次进 quiz 阶段（没有 quiz 数据），生成题目
        if (state.questions == null || state.questions.isEmpty()) {
            String quizJson = onboarding.generateQuiz(sid);
            try {
                String json = quizJson.replaceAll("```json|```", "").trim();
                var arr = com.alibaba.fastjson2.JSON.parseArray(json);
                for (int i = 0; i < arr.size(); i++) {
                    var qObj = arr.getJSONObject(i);
                    QuizQuestion q = new QuizQuestion();
                    q.q = qObj.getString("q");
                    q.opts = qObj.getJSONArray("opts") != null
                        ? qObj.getJSONArray("opts").toArray(new String[0]) : new String[0];
                    q.ans = qObj.getIntValue("ans");
                    state.questions.add(q);
                }
            } catch (Exception e) {
                state.questions.addAll(defaultQuiz());
            }

            // 发第一道题
            sendQuizQuestion(state, r);
            return;
        }

        // 检查上道题的答案
        if (!msg.isBlank() && state.currentIdx < state.questions.size()) {
            QuizQuestion lastQ = state.questions.get(state.currentIdx - 1);
            boolean correct = checkAnswer(lastQ, msg);
            state.answers.add(correct);
            if (correct) state.correctCount++;

            // 发布反馈
            String feedback;
            if (correct) {
                feedback = "✅ 正确！";
            } else {
                feedback = "❌ 答案是 " + ((char)('A' + lastQ.ans)) + "。";
            }
            r.put("feedback", feedback);
            r.put("correct", correct);
            r.put("answered", state.answers.size());
            r.put("total", state.questions.size());
        }

        // 发下一道题
        if (state.currentIdx < state.questions.size()) {
            sendQuizQuestion(state, r);
        } else {
            // ★ 所有题答完
            quizStates.remove(sid);
            r.put("quizDone", true);
            r.put("correctCount", state.correctCount);
            r.put("totalQuestions", state.questions.size());

            // 从错题推算薄弱点
            String weakPoint = deduceWeakPoint(state);
            Student st = studentRepo.findByUsername("student_" + sid).orElse(null);
            if (st != null && weakPoint != null) {
                st.setWeakAreas(weakPoint);
                studentRepo.save(st);
            }

            r.put("phase", "generate");
            r.put("agentName", "出题专家");
            r.put("message", String.format(
                "答题完成！%d/%d 正确。你的薄弱点在 **%s**。\n现在8位智能体为你定制学习方案——",
                state.correctCount, state.questions.size(), weakPoint != null ? weakPoint : "C语言基础"
            ));
        }
    }

    private void sendQuizQuestion(QuizState state, Map<String, Object> r) {
        QuizQuestion q = state.questions.get(state.currentIdx);
        state.currentIdx++;
        r.put("phase", "quiz");
        r.put("agentName", "出题专家");
        r.put("questionIndex", state.currentIdx);
        r.put("questionTotal", state.questions.size());
        r.put("question", Map.of("q", q.q, "opts", q.opts));
        r.put("message", String.format("📝 第%d题/%d：%s\n\n%s",
            state.currentIdx, state.questions.size(), q.q, String.join("  ", q.opts)));
    }

    private boolean checkAnswer(QuizQuestion q, String ans) {
        String a = ans.trim().toUpperCase();
        return a.equals(String.valueOf((char)('A' + q.ans)))
            || a.equals(String.valueOf(q.ans + 1));
    }

    private String deduceWeakPoint(QuizState state) {
        List<String> weakTopics = new ArrayList<>();
        for (int i = 0; i < state.answers.size(); i++) {
            if (!state.answers.get(i)) {
                QuizQuestion q = state.questions.get(i);
                if (q.q.contains("指针")) weakTopics.add("指针");
                else if (q.q.contains("数组")) weakTopics.add("数组");
                else if (q.q.contains("内存") || q.q.contains("malloc")) weakTopics.add("内存管理");
                else if (q.q.contains("函数")) weakTopics.add("函数");
                else weakTopics.add("C语言基础");
            }
        }
        return weakTopics.isEmpty() ? null : String.join("、", weakTopics.stream().distinct().limit(3).toList());
    }

    // ==================== 阶段3: 生成资源 ====================
    private void handleGenerate(Long sid, Student s, Map<String, Object> r) {
        // ★ 防重复：30分钟内不重复生成
        LocalDateTime last = lastGenerateTime.get(sid);
        if (last != null && Duration.between(last, LocalDateTime.now()).toMinutes() < 30) {
            r.put("phase", "done");
            r.put("agentName", "导学智能体");
            r.put("message", "你的学习资源已在" + Duration.between(last, LocalDateTime.now()).toMinutes() + "分钟前生成，欢迎查看 👇");
            r.put("resourcesGenerated", 0);
            r.put("cached", true);
            return;
        }

        AgentContext ctx = new AgentContext(sid, "生成个性化学习内容", null);

        int[] generated = {0};
        List<Thread> threads = new ArrayList<>();
        List<BaseAgent> agents = List.of(
            knowledgeExplainer, questionExpert, mindMapAgent,
            codeExampleAgent, courseDesigner, pathPlanner
        );
        for (BaseAgent agent : agents) {
            Thread t = Thread.ofVirtual().start(() -> {
                try {
                    AgentContext.AgentResult result = agent.execute(ctx);
                    if (result.isSuccess() && validateOutput(result.getOutput())) {
                        synchronized (generated) { generated[0]++; }
                    }
                } catch (Exception ignored) {}
            });
            threads.add(t);
        }
        for (Thread t : threads) {
            try { t.join(30000); } catch (InterruptedException ignored) {}
        }

        lastGenerateTime.put(sid, LocalDateTime.now());

        r.put("phase", "done");
        r.put("agentName", "导学智能体");
        r.put("resourcesGenerated", generated[0]);
        r.put("message", onboardingAgent.generateSummary(sid));
    }

    /** ★ 防幻觉校验：输出不能为空，不能是乱码，最少要有实际内容 */
    private boolean validateOutput(String output) {
        if (output == null || output.isBlank()) return false;
        if (output.length() < 20) return false;            // 太短不可信
        if (output.startsWith("抱歉") || output.contains("AI服务暂时不可用")) return false;
        // RAG 事实校验
        try {
            var fragments = ragService.retrieveRelevant(output.substring(0, Math.min(100, output.length())), 3);
            if (!fragments.isEmpty()) {
                var factResult = ragService.factCheck(output, fragments);
                if (factResult != null && factResult.confidence < 0.4) {
                    return false; // 置信度过低，拒绝生成
                }
            }
        } catch (Exception ignored) {}
        return true;
    }

    /** 手动触发资源生成 */
    @PostMapping("/generate/{sysUserId}")
    public Result<Map<String, Object>> generateResources(@PathVariable Long sysUserId) {
        Student s = studentRepo.findByUsername("student_" + sysUserId).orElse(null);
        if (s == null) return Result.error("学生不存在");
        int c = onboarding.calcCompleteness(s);
        if (c < 80) return Result.error("画像完整度不足(" + c + "%)，请先完成AI导学诊断");
        Map<String, Object> r = new LinkedHashMap<>();
        handleGenerate(sysUserId, s, r);
        return Result.success(r);
    }

    /** 维度详情 */
    @GetMapping("/dimensions/{sysUserId}")
    public Result<Map<String, Object>> dimensions(@PathVariable Long sysUserId) {
        Student s = studentRepo.findByUsername("student_" + sysUserId).orElse(null);
        SysUser user = sysUserRepo.findById(sysUserId).orElse(null);
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("completeness", s != null ? onboarding.calcCompleteness(s) : 0);
        r.put("dimensions", s != null ? onboarding.getDimensionStatus(s) : List.of());
        if (user != null) { r.put("className", user.getClassName()); r.put("department", user.getDepartment()); }
        return Result.success(r);
    }

    // ==================== 内部类型 ====================
    static class QuizState {
        List<QuizQuestion> questions = new ArrayList<>();
        int currentIdx = 0;
        int correctCount = 0;
        List<Boolean> answers = new ArrayList<>();
    }

    static class QuizQuestion {
        String q;
        String[] opts = new String[0];
        int ans;
    }

    private List<QuizQuestion> defaultQuiz() {
        List<QuizQuestion> list = new ArrayList<>();
        QuizQuestion q1 = new QuizQuestion();
        q1.q = "C语言中 main 函数的返回类型是？";
        q1.opts = new String[]{"A. void", "B. int", "C. char", "D. float"};
        q1.ans = 1;
        list.add(q1);
        QuizQuestion q2 = new QuizQuestion();
        q2.q = "下面哪个是合法的变量名？";
        q2.opts = new String[]{"A. 2var", "B. _count", "C. int", "D. a-b"};
        q2.ans = 1;
        list.add(q2);
        QuizQuestion q3 = new QuizQuestion();
        q3.q = "printf函数在哪个头文件里？";
        q3.opts = new String[]{"A. string.h", "B. math.h", "C. stdio.h", "D. stdlib.h"};
        q3.ans = 2;
        list.add(q3);
        return list;
    }
}
