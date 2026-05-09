package com.happymouse.cryd.controller;

import com.happymouse.cryd.common.Result;
import com.happymouse.cryd.model.entity.LearningPath;
import com.happymouse.cryd.model.entity.LearningResource;
import com.happymouse.cryd.repository.LearningPathRepository;
import com.happymouse.cryd.repository.LearningResourceRepository;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/learning")
@CrossOrigin(origins = "*")
public class LearningController {

    private final LearningResourceRepository resourceRepository;
    private final LearningPathRepository pathRepository;

    public LearningController(LearningResourceRepository resourceRepository, LearningPathRepository pathRepository) {
        this.resourceRepository = resourceRepository;
        this.pathRepository = pathRepository;
    }

    @GetMapping("/resources/{studentId}")
    public Result<List<LearningResource>> getResources(@PathVariable Long studentId) {
        return Result.success(resourceRepository.findByStudentId(studentId));
    }

    @GetMapping("/resources/{studentId}/{type}")
    public Result<List<LearningResource>> getResourcesByType(
            @PathVariable Long studentId, @PathVariable String type) {
        return Result.success(resourceRepository.findByStudentIdAndType(studentId, type));
    }

    @GetMapping("/path/{studentId}")
    public Result<LearningPath> getPath(@PathVariable Long studentId) {
        return pathRepository.findByStudentIdAndStatus(studentId, "active")
                .map(Result::success)
                .orElse(Result.error(404, "暂无活跃学习路径"));
    }
}
