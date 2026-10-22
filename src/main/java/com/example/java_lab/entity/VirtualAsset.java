package com.example.java_lab.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "virtual_asset")
@Getter
@Setter
public class VirtualAsset {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(name = "asset_type", nullable = false)
    private String assetType;

    @Column(nullable = false)
    private Long amount;

    @Column(nullable = false)
    private String platform;

    @Column(name = "unit_price", nullable = false)
    private Double unitPrice;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "storage_account_id", nullable = false)
    private StorageAccount storageAccount;
}