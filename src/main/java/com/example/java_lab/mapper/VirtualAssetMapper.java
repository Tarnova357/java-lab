package com.example.java_lab.mapper;

import com.example.java_lab.dto.VirtualAssetDto;
import com.example.java_lab.entity.VirtualAsset;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface VirtualAssetMapper {

    @Mapping(source = "storageAccount.id", target = "storageAccountId")
    VirtualAssetDto toDto(VirtualAsset entity);

    @Mapping(source = "storageAccountId", target = "storageAccount.id")
    VirtualAsset toEntity(VirtualAssetDto dto);
}