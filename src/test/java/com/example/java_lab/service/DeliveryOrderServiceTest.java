package com.example.java_lab.service;

import com.example.java_lab.dto.DeliveryOrderDto;
import com.example.java_lab.entity.DeliveryOrder;
import com.example.java_lab.entity.VirtualAsset;
import com.example.java_lab.entity.enums.OrderStatus;
import com.example.java_lab.mapper.DeliveryOrderMapper;
import com.example.java_lab.repository.DeliveryOrderRepository;
import com.example.java_lab.repository.VirtualAssetRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeliveryOrderServiceTest {

    @Mock
    private DeliveryOrderRepository orderRepository;

    @Mock
    private VirtualAssetRepository assetRepository;

    @Mock
    private DeliveryOrderMapper mapper;

    @InjectMocks
    private DeliveryOrderService orderService;

    @Test
    @DisplayName("create: успішне створення замовлення, списання активу, розрахунок вартості та встановлення платформи з активу")
    void create_validRequestAndSufficientStock_createsOrderAndDeductsStock() {
        DeliveryOrderDto inputDto = new DeliveryOrderDto();
        inputDto.setVirtualAssetId(1L);
        inputDto.setQuantity(3L);
        inputDto.setCustomerTag("Player1");

        VirtualAsset asset = new VirtualAsset();
        asset.setId(1L);
        asset.setAmount(10L);
        asset.setPlatform("ROBLOX");
        asset.setUnitPrice(5.0);

        DeliveryOrder entity = new DeliveryOrder();
        entity.setCustomerTag("Player1");
        entity.setQuantity(3L);

        DeliveryOrder savedEntity = new DeliveryOrder();
        savedEntity.setId(100L);
        savedEntity.setStatus(OrderStatus.PENDING);
        savedEntity.setPlatform("ROBLOX");
        savedEntity.setTotalPrice(15.0);

        DeliveryOrderDto expectedDto = new DeliveryOrderDto();
        expectedDto.setId(100L);
        expectedDto.setStatus(OrderStatus.PENDING);
        expectedDto.setPlatform("ROBLOX");
        expectedDto.setTotalPrice(15.0);

        when(assetRepository.findById(1L)).thenReturn(Optional.of(asset));
        when(mapper.toEntity(inputDto)).thenReturn(entity);
        when(orderRepository.save(entity)).thenReturn(savedEntity);
        when(mapper.toDto(savedEntity)).thenReturn(expectedDto);

        DeliveryOrderDto actualDto = orderService.create(inputDto);

        assertNotNull(actualDto);
        assertEquals(100L, actualDto.getId());
        assertEquals(OrderStatus.PENDING, actualDto.getStatus());
        assertEquals("ROBLOX", actualDto.getPlatform());
        assertEquals(15.0, actualDto.getTotalPrice());

        ArgumentCaptor<VirtualAsset> assetCaptor = ArgumentCaptor.forClass(VirtualAsset.class);
        verify(assetRepository).save(assetCaptor.capture());
        assertEquals(7L, assetCaptor.getValue().getAmount());

        ArgumentCaptor<DeliveryOrder> orderCaptor = ArgumentCaptor.forClass(DeliveryOrder.class);
        verify(orderRepository).save(orderCaptor.capture());
        assertEquals(OrderStatus.PENDING, orderCaptor.getValue().getStatus());
        assertEquals(15.0, orderCaptor.getValue().getTotalPrice());
        assertEquals("ROBLOX", orderCaptor.getValue().getPlatform());
        assertEquals(asset, orderCaptor.getValue().getVirtualAsset());
    }

    @Test
    @DisplayName("create: викидає виняток, якщо актив не знайдено за ID")
    void create_assetNotFound_throwsRuntimeException() {
        DeliveryOrderDto inputDto = new DeliveryOrderDto();
        inputDto.setVirtualAssetId(999L);
        when(assetRepository.findById(999L)).thenReturn(Optional.empty());

        RuntimeException exception = assertThrows(
                RuntimeException.class,
                () -> orderService.create(inputDto)
        );
        assertTrue(exception.getMessage().contains("Virtual asset not found with id: 999"));

        verify(assetRepository, never()).save(any());
        verify(orderRepository, never()).save(any());
    }

    @Test
    @DisplayName("create: викидає виняток, якщо товару недостатньо на складі")
    void create_insufficientStock_throwsIllegalArgumentException() {
        DeliveryOrderDto inputDto = new DeliveryOrderDto();
        inputDto.setVirtualAssetId(1L);
        inputDto.setQuantity(20L);

        VirtualAsset asset = new VirtualAsset();
        asset.setId(1L);
        asset.setAmount(5L);

        when(assetRepository.findById(1L)).thenReturn(Optional.of(asset));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> orderService.create(inputDto)
        );
        assertTrue(exception.getMessage().contains("Недостатньо активів на складі"));

        verify(assetRepository, never()).save(any());
        verify(orderRepository, never()).save(any());
    }

    @Test
    @DisplayName("getAll: повертає список DTO замовлень")
    void getAll_ordersExist_returnsMappedDtoList() {
        DeliveryOrder order = new DeliveryOrder();
        order.setId(1L);
        DeliveryOrderDto dto = new DeliveryOrderDto();
        dto.setId(1L);

        when(orderRepository.findAll()).thenReturn(List.of(order));
        when(mapper.toDto(order)).thenReturn(dto);

        List<DeliveryOrderDto> result = orderService.getAll();

        assertEquals(1, result.size());
        assertEquals(1L, result.get(0).getId());
        verify(orderRepository).findAll();
    }

    @Test
    @DisplayName("getById: повертає DTO для існуючого ID")
    void getById_existingId_returnsOrderDto() {
        DeliveryOrder order = new DeliveryOrder();
        order.setId(1L);
        DeliveryOrderDto dto = new DeliveryOrderDto();
        dto.setId(1L);

        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(mapper.toDto(order)).thenReturn(dto);

        DeliveryOrderDto result = orderService.getById(1L);

        assertNotNull(result);
        assertEquals(1L, result.getId());
        verify(orderRepository).findById(1L);
    }

    @Test
    @DisplayName("getById: викидає виняток для неіснуючого ID")
    void getById_nonExistingId_throwsRuntimeException() {
        when(orderRepository.findById(999L)).thenReturn(Optional.empty());

        RuntimeException exception = assertThrows(
                RuntimeException.class,
                () -> orderService.getById(999L)
        );
        assertEquals("Order not found with id: 999", exception.getMessage());
    }

    @Test
    @DisplayName("update: оновлює поля, синхронізує платформу з нового активу та перераховує totalPrice")
    void update_validData_updatesAndSavesOrder() {
        Long orderId = 1L;
        DeliveryOrder existing = new DeliveryOrder();
        existing.setId(orderId);
        existing.setCustomerTag("OldTag");
        existing.setPlatform("ROBLOX");

        DeliveryOrderDto updateDto = new DeliveryOrderDto();
        updateDto.setCustomerTag("NewTag");
        updateDto.setQuantity(5L);
        updateDto.setStatus(OrderStatus.COMPLETED);
        updateDto.setVirtualAssetId(2L);

        VirtualAsset newAsset = new VirtualAsset();
        newAsset.setId(2L);
        newAsset.setPlatform("STEAM");
        newAsset.setUnitPrice(10.0);

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(existing));
        when(assetRepository.findById(2L)).thenReturn(Optional.of(newAsset));
        when(orderRepository.save(existing)).thenReturn(existing);
        when(mapper.toDto(existing)).thenReturn(updateDto);

        DeliveryOrderDto result = orderService.update(orderId, updateDto);

        assertEquals("NewTag", result.getCustomerTag());
        verify(orderRepository).save(existing);
        assertEquals("NewTag", existing.getCustomerTag());
        assertEquals(OrderStatus.COMPLETED, existing.getStatus());
        assertEquals("STEAM", existing.getPlatform());
        assertEquals(50.0, existing.getTotalPrice());
        assertEquals(newAsset, existing.getVirtualAsset());
    }

    @Test
    @DisplayName("update: викидає виняток, коли замовлення не знайдено")
    void update_orderNotFound_throwsRuntimeException() {
        DeliveryOrderDto updateDto = new DeliveryOrderDto();
        when(orderRepository.findById(999L)).thenReturn(Optional.empty());

        RuntimeException exception = assertThrows(
                RuntimeException.class,
                () -> orderService.update(999L, updateDto)
        );
        assertEquals("Order not found with id: 999", exception.getMessage());
        verify(orderRepository, never()).save(any());
    }

    @Test
    @DisplayName("update: викидає виняток, коли новий віртуальний актив не знайдено")
    void update_assetNotFound_throwsRuntimeException() {
        DeliveryOrder existing = new DeliveryOrder();
        DeliveryOrderDto updateDto = new DeliveryOrderDto();
        updateDto.setVirtualAssetId(999L);

        when(orderRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(assetRepository.findById(999L)).thenReturn(Optional.empty());

        RuntimeException exception = assertThrows(
                RuntimeException.class,
                () -> orderService.update(1L, updateDto)
        );
        assertEquals("Virtual asset not found with id: 999", exception.getMessage());
        verify(orderRepository, never()).save(any());
    }

    @Test
    @DisplayName("delete: перевіряє існування через existsById та видаляє")
    void delete_validId_callsRepositoryDelete() {
        when(orderRepository.existsById(1L)).thenReturn(true);

        orderService.delete(1L);

        verify(orderRepository).deleteById(1L);
    }

    @Test
    @DisplayName("delete: викидає виняток, якщо сутності з таким ID не існує")
    void delete_nonExistingId_throwsRuntimeException() {
        when(orderRepository.existsById(999L)).thenReturn(false);

        RuntimeException exception = assertThrows(
                RuntimeException.class,
                () -> orderService.delete(999L)
        );
        assertEquals("Order not found with id: 999", exception.getMessage());
        verify(orderRepository, never()).deleteById(any());
    }
}