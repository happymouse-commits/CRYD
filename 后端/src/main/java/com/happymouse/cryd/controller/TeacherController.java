package com.happymouse.cryd.controller;

import com.happymouse.cryd.common.Result;
import com.happymouse.cryd.model.entity.*;
import com.happymouse.cryd.repository.*;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 教师端API
 */
@RestController
@RequestMapping("/api/teacher")
@CrossOrigin(origins = "*")
public class TeacherController {

    private final TeacherRepository teacherRepository;
    private final CourseRepository courseRepository;
    private final AssignmentRepository assignmentRepository;
    private final SubmissionRepository submissionRepository;
    private final StudentRepository studentRepository;
    private final AiAnalysisRepository aiAnalysisRepository;
    private final SysUserRepository sysUserRepository;

    public TeacherController(TeacherRepository teacherRepository,
                             CourseRepository courseRepository,
                             AssignmentRepository assignmentRepository,
                             SubmissionRepository submissionRepository,
                             StudentRepository studentRepository,
                             AiAnalysisRepository aiAnalysisRepository,
                             SysUserRepository sysUserRepository) {
        this.teacherRepository = teacherRepository;
        this.courseRepository = courseRepository;
        this.assignmentRepository = assignmentRepository;
        this.submissionRepository = submissionRepository;
        this.studentRepository = studentRepository;
        this.aiAnalysisRepository = aiAnalysisRepository;
        this.sysUserRepository = sysUserRepository;
    }

    // ===== 教师信息 =====

    @GetMapping("/{id}")
    public Result<Teacher> getProfile(@PathVariable Long id) {
        return teacherRepository.findById(id)
                .map(Result::success)
                .orElse(Result.error(404, "教师不存在"));
    }

    @PostMapping("/register")
    public Result<Teacher> register(@RequestBody Map<String, String> body) {
        Teacher teacher = new Teacher();
        teacher.setUsername(body.get("username"));
        teacher.setNickname(body.getOrDefault("nickname", body.get("username")));
        teacher.setDepartment(body.getOrDefault("department", "计算机学院"));
        teacher.setCourse(body.getOrDefault("course", "C语言程序设计"));
        return Result.success(teacherRepository.save(teacher));
    }

    // ===== 课程管理 =====

    @GetMapping("/{teacherId}/courses")
    public Result<List<Course>> getCourses(@PathVariable Long teacherId) {
        return Result.success(courseRepository.findByTeacherId(teacherId));
    }

    @PostMapping("/course")
    public Result<Course> createCourse(@RequestBody Map<String, String> body) {
        Course course = new Course();
        course.setName(body.get("name"));
        course.setCode(body.getOrDefault("code", ""));
        course.setClassName(body.getOrDefault("className", ""));
        course.setTeacherId(Long.valueOf(body.getOrDefault("teacherId", "1")));
        course.setSemester(body.getOrDefault("semester", "2025-2026-2"));
        course.setDescription(body.getOrDefault("description", ""));
        return Result.success(courseRepository.save(course));
    }

    // ===== 作业管理 =====

    @GetMapping("/{teacherId}/assignments")
    public Result<List<Assignment>> getAssignments(@PathVariable Long teacherId) {
        return Result.success(assignmentRepository.findByTeacherId(teacherId));
    }

    @PostMapping("/assignment")
    public Result<Assignment> createAssignment(@RequestBody Map<String, String> body) {
        Assignment assignment = new Assignment();
        assignment.setCourseId(Long.valueOf(body.getOrDefault("courseId", "1")));
        assignment.setTeacherId(Long.valueOf(body.getOrDefault("teacherId", "1")));
        assignment.setTitle(body.get("title"));
        assignment.setContent(body.getOrDefault("content", ""));
        assignment.setRequirements(body.getOrDefault("requirements", ""));
        assignment.setDeadline(body.getOrDefault("deadline", ""));
        assignment.setDifficulty(body.getOrDefault("difficulty", "medium"));
        return Result.success(assignmentRepository.save(assignment));
    }

    // ===== 批改作业 =====

    @GetMapping("/assignment/{assignmentId}/submissions")
    public Result<List<Submission>> getSubmissions(@PathVariable Long assignmentId) {
        return Result.success(submissionRepository.findByAssignmentId(assignmentId));
    }

    @PostMapping("/submission/{submissionId}/grade")
    public Result<Submission> gradeSubmission(@PathVariable Long submissionId,
                                               @RequestBody Map<String, Object> body) {
        return submissionRepository.findById(submissionId).map(sub -> {
            sub.setScore((Integer) body.get("score"));
            sub.setFeedback((String) body.getOrDefault("feedback", ""));
            sub.setGradedBy((String) body.getOrDefault("gradedBy", "teacher"));
            sub.setStatus("graded");
            sub.setGradedAt(java.time.LocalDateTime.now());
            return Result.success(submissionRepository.save(sub));
        }).orElse(Result.error(404, "提交不存在"));
    }

    // ===== 班级学情 =====

    @GetMapping("/class/{className}/students")
    public Result<List<Student>> getClassStudents(@PathVariable String className) {
        // 通过SysUser找该班级的学生ID，再从Student查画像
        List<SysUser> users = sysUserRepository.findByClassName(className);
        List<Student> students = new ArrayList<>();
        for (SysUser u : users) {
            if ("student".equals(u.getRole())) {
                studentRepository.findById(u.getId()).ifPresent(students::add);
            }
        }
        return Result.success(students);
    }

    @GetMapping("/class/{className}/analysis")
    public Result<List<AiAnalysis>> getClassAnalysis(@PathVariable String className) {
        List<SysUser> users = sysUserRepository.findByClassName(className);
        List<AiAnalysis> analyses = new ArrayList<>();
        for (SysUser u : users) {
            if ("student".equals(u.getRole())) {
                analyses.addAll(aiAnalysisRepository.findByStudentId(u.getId()));
            }
        }
        return Result.success(analyses);
    }

    // ===== AI辅助出题 =====

    @PostMapping("/ai/generate-question")
    public Result<Map<String, Object>> aiGenerateQuestion(@RequestBody Map<String, String> body) {
        // 调用出题专家Agent生成题目
        // 这里返回一个占位结构，实际由前端调用chat接口触发
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("hint", "请通过学生端聊天发送出题请求，如'给我出一道指针的练习题'");
        result.put("knowledgePoint", body.getOrDefault("knowledgePoint", "指针"));
        result.put("difficulty", body.getOrDefault("difficulty", "medium"));
        return Result.success(result);
    }
}
