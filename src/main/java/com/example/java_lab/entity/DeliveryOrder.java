package com.example.java_lab.entity;

import com.example.java_lab.entity.enums.OrderStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "delivery_order")
@Getter
@Setter
public class DeliveryOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "customer_tag", nullable = false)
    private String customerTag;

    @Column(nullable = false)
    private Long quantity;

    @Column(name = "total_price", nullable = false)
    private Double totalPrice;

    @Column(nullable = false)
    private String platform;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "virtual_asset_id", nullable = false)
    private VirtualAsset virtualAsset;
}