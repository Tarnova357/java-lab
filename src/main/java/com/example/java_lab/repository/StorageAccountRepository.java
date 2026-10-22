package com.example.java_lab.repository;

import com.example.java_lab.entity.StorageAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface StorageAccountRepository extends JpaRepository<StorageAccount, Long> {
}