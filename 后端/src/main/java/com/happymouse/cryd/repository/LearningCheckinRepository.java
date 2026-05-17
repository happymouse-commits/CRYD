package com.happymouse.cryd.repository;

import com.happymouse.cryd.model.entity.LearningCheckin;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface LearningCheckinRepository extends JpaRepository<LearningCheckin, Long> {
    List<LearningCheckin> findByStudentIdOrderByCheckinDateDesc(Long studentId);
    List<LearningCheckin> findByStudentIdAndCheckinDateBetween(Long studentId, LocalDate start, LocalDate end);
    boolean existsByStudentIdAndCheckinDate(Long studentId, LocalDate date);
}
