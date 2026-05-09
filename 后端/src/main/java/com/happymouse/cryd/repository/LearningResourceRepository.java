package com.happymouse.cryd.repository;

import com.happymouse.cryd.model.entity.LearningResource;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LearningResourceRepository extends JpaRepository<LearningResource, Long> {
    List<LearningResource> findByStudentId(Long studentId);
    List<LearningResource> findByStudentIdAndType(Long studentId, String type);
}
