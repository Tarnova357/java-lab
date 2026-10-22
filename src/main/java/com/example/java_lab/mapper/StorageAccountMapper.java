package com.example.java_lab.mapper;

import com.example.java_lab.dto.StorageAccountDto;
import com.example.java_lab.entity.StorageAccount;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface StorageAccountMapper {
    StorageAccountDto toDto(StorageAccount entity);
    StorageAccount toEntity(StorageAccountDto dto);
}