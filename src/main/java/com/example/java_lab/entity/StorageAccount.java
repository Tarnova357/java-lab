package com.example.java_lab.entity;

import com.example.java_lab.entity.enums.AccountStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "storage_account")
@Getter
@Setter
public class StorageAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String username;

    @Column(nullable = false)
    private String platform;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AccountStatus status;
}