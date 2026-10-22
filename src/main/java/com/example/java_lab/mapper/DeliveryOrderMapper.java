package com.example.java_lab.mapper;

import com.example.java_lab.dto.DeliveryOrderDto;
import com.example.java_lab.entity.DeliveryOrder;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface DeliveryOrderMapper {

    @Mapping(source = "virtualAsset.id", target = "virtualAssetId")
    DeliveryOrderDto toDto(DeliveryOrder entity);

    @Mapping(source = "virtualAssetId", target = "virtualAsset.id")
    DeliveryOrder toEntity(DeliveryOrderDto dto);
}