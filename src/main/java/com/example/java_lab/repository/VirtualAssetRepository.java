package com.example.java_lab.repository;

import com.example.java_lab.entity.VirtualAsset;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface VirtualAssetRepository extends JpaRepository<VirtualAsset, Long> {
    List<VirtualAsset> findByStorageAccountId(Long storageAccountId);
    boolean existsByStorageAccountId(Long storageAccountId);
}