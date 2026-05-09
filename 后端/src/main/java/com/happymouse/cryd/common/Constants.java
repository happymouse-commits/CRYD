package com.happymouse.cryd.common;

/**
 * 系统常量
 */
public class Constants {
    /** 学生画像维度 */
    public static final String[] PROFILE_DIMENSIONS = {
        "知识基础",     // 学生对知识点的掌握程度
        "认知风格",     // 视觉型/听觉型/动手型等
        "易错点",       // 常见错误模式
        "学习偏好",     // 偏好文档/视频/练习等
        "学习节奏",     // 快速推进/稳扎稳打
        "兴趣方向"      // 对哪方面更感兴趣
    };

    /** 资源类型 */
    public static final String[] RESOURCE_TYPES = {
        "文档讲解",     // 课程讲解文档
        "思维导图",     // 知识点思维导图
        "练习题",       // 各类练习题
        "拓展阅读",     // 延伸阅读材料
        "代码案例",     // 代码实操示例
        "教学视频"      // 教学视频/动画
    };

    /** 难度等级 */
    public static final String[] DIFFICULTY_LEVELS = {
        "入门", "基础", "进阶", "挑战"
    };

    /** 课程名称 */
    public static final String COURSE_NAME = "C语言程序设计";
}
