package com.happymouse.cryd.service.agent;

import com.happymouse.cryd.agent.core.AgentCapability;
import com.happymouse.cryd.agent.core.AgentContext;
import com.happymouse.cryd.agent.core.BaseAgent;
import com.happymouse.cryd.model.entity.LearningResource;
import com.happymouse.cryd.model.entity.Student;
import com.happymouse.cryd.repository.LearningResourceRepository;
import com.happymouse.cryd.repository.StudentRepository;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.alibaba.fastjson2.JSONArray;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 思维导图师 — 根据学生画像生成精美知识结构思维导图
 *
 * 输出两层格式：
 *   1. JSON 树结构 — 前端 ECharts 树图渲染
 *   2. Markdown 嵌套列表 — 文本展示兜底
 */
@Component("mindMapAgent")
public class MindMapAgent extends BaseAgent {

    private static final String SYSTEM_PROMPT = """
        你是「思维导图师」，专门为C语言学习者生成精美知识结构图。

        ## 输出要求
        输出一个JSON树结构，格式如下：
        {
          "name": "根节点名",
          "icon": "📚",
          "difficulty": "★",
          "tip": "学习建议",
          "children": [
            {
              "name": "子节点名",
              "icon": "📦",
              "difficulty": "★★",
              "tip": "该章节的简短学习技巧(10字内)",
              "children": [...]
            }
          ]
        }

        ## 规则
        1. 根节点 = 学生的薄弱知识点或C语言核心主题
        2. 展开 3-4 层，共 8-15 个节点
        3. 每个节点配 emoji 图标（📚📦🔧⚙️💡🎯⚠️📝🔄🔗🧩📊）
        4. difficulty: ★(入门) ★★(基础) ★★★(进阶) ★★★★(难点)
        5. tip: 给该节点的简短学习建议，匹配学生水平
        6. 弱点章节重点展开（更多子节点 + 更详细tip）
        7. 只输出JSON，不要其他文字
        """;

    private final StudentRepository studentRepo;
    private final LearningResourceRepository resourceRepo;

    public MindMapAgent(StudentRepository studentRepo, LearningResourceRepository resourceRepo) {
        this.studentRepo = studentRepo;
        this.resourceRepo = resourceRepo;
    }

    @Override public String getName() { return "思维导图师"; }

    @Override
    public List<AgentCapability> getCapabilities() {
        return List.of(AgentCapability.of("resource:mindmap", "生成精美知识结构思维导图", 0.92));
    }

    @Override protected String getSystemPrompt() { return SYSTEM_PROMPT; }

    @Override
    protected String doExecute(AgentContext context) {
        Long studentId = context.getStudentId();
        Student s = studentRepo.findByUsername("student_" + studentId).orElse(null);
        if (s == null) return "";

        String topic = s.getWeakAreas() != null ? s.getWeakAreas() : "C语言基础入门";
        int level = s.getKnowledgeLevel() != null ? s.getKnowledgeLevel() : 30;
        String pref = s.getLearningPreference() != null ? s.getLearningPreference() : "mixed";
        String goal = s.getInterestDirection() != null ? s.getInterestDirection() : "打好基础";

        String prompt = buildPrompt(topic, level, pref, goal);
        String rawJson = callLLM(context, prompt, 0.3f, 2048);

        // 清理 JSON
        String json = rawJson.replaceAll("```json\\s*", "").replaceAll("```\\s*", "").trim();
        if (!json.startsWith("{")) {
            // LLM 没返回JSON，用默认模板
            json = buildDefaultTree(topic, level);
        }

        // 保存资源：content 存 JSON + 纯文本双重格式
        LearningResource res = new LearningResource();
        res.setStudentId(studentId);
        res.setTitle("📊 " + topic + " 知识结构图");
        res.setType("mindmap");
        res.setKnowledgePoint(topic);
        // content 格式: JSON树 + "\n---\n" + Markdown文本
        res.setContent(json + "\n---\n" + jsonToMarkdown(json));
        res.setDifficulty(level < 30 ? "easy" : level < 60 ? "medium" : "hard");
        res.setGeneratedBy("思维导图师");
        res.setCreatedAt(LocalDateTime.now());
        resourceRepo.save(res);

        return json;
    }

    /** 根据画像构建个性化Prompt */
    private String buildPrompt(String topic, int level, String pref, String goal) {
        String styleHint = "";
        if ("video".equals(pref)) {
            styleHint = "提示风格：口语化，像老师在黑板前讲解";
        } else if ("doc".equals(pref)) {
            styleHint = "提示风格：像教科书注解，严谨准确";
        } else if ("exercise".equals(pref)) {
            styleHint = "提示风格：加入动手引导，如'试试声明3个变量'";
        }
        return String.format(
            "请为C语言学生生成知识结构树。\n主题: %s\n学生水平: %d/100\n学习目标: %s\n%s\n\n要求:\n- 水平<=30: 从最基础展开，4层节点\n- 水平30-60: 突出薄弱环节和进阶内容\n- 水平>60: 快速概览基础，重点展开高级话题",
            topic, level, goal, styleHint);
    }

    /** 默认兜底树结构 */
    private String buildDefaultTree(String topic, int level) {
        return String.format("""
            {"name":"%s","icon":"📚","difficulty":"★","tip":"从这里开始你的C语言之旅",
            "children":[
              {"name":"基本语法","icon":"📦","difficulty":"★","tip":"变量、数据类型、运算符",
               "children":[
                 {"name":"数据类型","icon":"🏷️","difficulty":"★","tip":"int/float/char/double"},
                 {"name":"输入输出","icon":"🖨️","difficulty":"★","tip":"printf和scanf"}
               ]},
              {"name":"控制结构","icon":"🔀","difficulty":"★★","tip":"程序流程控制",
               "children":[
                 {"name":"if-else","icon":"🔀","difficulty":"★★","tip":"条件判断"},
                 {"name":"循环","icon":"🔄","difficulty":"★★★","tip":"for/while/do-while"}
               ]},
              {"name":"函数","icon":"⚙️","difficulty":"★★★","tip":"模块化编程核心",
               "children":[
                 {"name":"定义与调用","icon":"📝","difficulty":"★★★","tip":"返回类型、参数传递"},
                 {"name":"作用域","icon":"🔗","difficulty":"★★★","tip":"局部变量vs全局变量"}
               ]}
            ]}
            """, topic);
    }

    /** JSON树 → Markdown嵌套列表（文本展示） */
    private String jsonToMarkdown(String jsonStr) {
        try {
            JSONObject root = JSON.parseObject(jsonStr);
            StringBuilder sb = new StringBuilder();
            renderNode(root, sb, 0);
            return sb.toString();
        } catch (Exception e) {
            return "# " + jsonStr + "\n(思维导图文本预览)";
        }
    }

    private void renderNode(JSONObject node, StringBuilder sb, int depth) {
        String name = node.getString("name");
        String icon = node.getString("icon") != null ? node.getString("icon") : "";
        String diff = node.getString("difficulty") != null ? " " + node.getString("difficulty") : "";
        String tip = node.getString("tip") != null ? " — " + node.getString("tip") : "";

        sb.append("  ".repeat(depth))
          .append("- ")
          .append(icon).append(" **").append(name).append("**")
          .append(diff)
          .append(tip)
          .append("\n");

        JSONArray children = node.getJSONArray("children");
        if (children != null) {
            for (int i = 0; i < children.size(); i++) {
                renderNode(children.getJSONObject(i), sb, depth + 1);
            }
        }
    }
}
