package com.happymouse.cryd.repository;

import com.happymouse.cryd.model.entity.Assignment;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface AssignmentRepository extends JpaRepository<Assignment, Long> {
    List<Assignment> findByCourseId(Long courseId);
    List<Assignment> findByTeacherId(Long teacherId);
}
