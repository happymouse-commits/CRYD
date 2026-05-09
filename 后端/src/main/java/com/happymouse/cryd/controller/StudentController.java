package com.happymouse.cryd.controller;

import com.happymouse.cryd.common.Result;
import com.happymouse.cryd.model.entity.Student;
import com.happymouse.cryd.repository.StudentRepository;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/student")
@CrossOrigin(origins = "*")
public class StudentController {

    private final StudentRepository studentRepository;

    public StudentController(StudentRepository studentRepository) {
        this.studentRepository = studentRepository;
    }

    @GetMapping("/{id}")
    public Result<Student> getProfile(@PathVariable Long id) {
        return studentRepository.findById(id)
                .map(Result::success)
                .orElse(Result.error(404, "学生不存在"));
    }

    @PostMapping("/register")
    public Result<Student> register(@RequestBody Map<String, String> body) {
        Student student = new Student();
        student.setUsername(body.get("username"));
        student.setNickname(body.getOrDefault("nickname", body.get("username")));
        student.setKnowledgeLevel(0);
        student.setCognitiveStyle("visual");
        student.setLearningPreference("mixed");
        student.setLearningPace("steady");
        student.setProgress(0);
        return Result.success(studentRepository.save(student));
    }
}
