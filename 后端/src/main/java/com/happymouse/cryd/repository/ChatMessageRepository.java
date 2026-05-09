package com.happymouse.cryd.repository;

import com.happymouse.cryd.model.entity.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {
    List<ChatMessage> findByStudentIdOrderByCreatedAtAsc(Long studentId);
    List<ChatMessage> findTop20ByStudentIdOrderByCreatedAtDesc(Long studentId);
}
