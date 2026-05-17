package com.happymouse.cryd.repository;

import com.happymouse.cryd.model.entity.Student;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface StudentRepository extends JpaRepository<Student, Long> {
    java.util.Optional<Student> findByUsername(String username);
}
