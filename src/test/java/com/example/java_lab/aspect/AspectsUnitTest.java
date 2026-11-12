package com.example.java_lab.aspect;

import com.example.java_lab.annotation.MaskCustomerTag;
import com.example.java_lab.dto.DeliveryOrderDto;
import com.example.java_lab.entity.StorageAccount;
import com.example.java_lab.entity.VirtualAsset;
import com.example.java_lab.entity.enums.AccountStatus;
import com.example.java_lab.repository.VirtualAssetRepository;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AspectsUnitTest {

    @Mock
    private VirtualAssetRepository virtualAssetRepository;

    @Mock
    private ProceedingJoinPoint proceedingJoinPoint;

    @InjectMocks
    private ActiveStorageValidationAspect activeStorageValidationAspect;

    @Test
    @DisplayName("ActiveStorageValidationAspect: дозволяє замовлення (викликає proceed), якщо сховище ACTIVE")
    void activeStorageAspect_whenAccountActive_callsProceed() throws Throwable {
        StorageAccount activeAccount = new StorageAccount();
        activeAccount.setUsername("bot_active");
        activeAccount.setStatus(AccountStatus.ACTIVE);

        VirtualAsset asset = new VirtualAsset();
        asset.setId(10L);
        asset.setStorageAccount(activeAccount);

        DeliveryOrderDto orderDto = new DeliveryOrderDto();
        orderDto.setVirtualAssetId(10L);

        when(proceedingJoinPoint.getArgs()).thenReturn(new Object[]{orderDto});
        when(virtualAssetRepository.findById(10L)).thenReturn(Optional.of(asset));
        when(proceedingJoinPoint.proceed()).thenReturn(orderDto);

        Object result = activeStorageValidationAspect.validateOrderStorageAccount(proceedingJoinPoint);

        assertEquals(orderDto, result);
        verify(proceedingJoinPoint, times(1)).proceed();
    }

    @Test
    @DisplayName("ActiveStorageValidationAspect: блокує замовлення (не викликає proceed), якщо сховище FROZEN")
    void activeStorageAspect_whenAccountFrozen_throwsExceptionAndSkipsProceed() throws Throwable {
        StorageAccount frozenAccount = new StorageAccount();
        frozenAccount.setUsername("bot_frozen");
        frozenAccount.setStatus(AccountStatus.FROZEN);

        VirtualAsset asset = new VirtualAsset();
        asset.setId(10L);
        asset.setStorageAccount(frozenAccount);

        DeliveryOrderDto orderDto = new DeliveryOrderDto();
        orderDto.setVirtualAssetId(10L);

        when(proceedingJoinPoint.getArgs()).thenReturn(new Object[]{orderDto});
        when(virtualAssetRepository.findById(10L)).thenReturn(Optional.of(asset));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> activeStorageValidationAspect.validateOrderStorageAccount(proceedingJoinPoint));

        assertTrue(ex.getMessage().contains("FROZEN"));
        verify(proceedingJoinPoint, never()).proceed();
    }

    @Test
    @DisplayName("IdempotencyAspect: при дублікаті ключа повертає кеш і не викликає proceed вдруге")
    void idempotencyAspect_whenDuplicateKey_returnsCachedAndSkipsSecondProceed() throws Throwable {
        IdempotencyAspect idempotencyAspect = new IdempotencyAspect();

        DeliveryOrderDto inputDto = new DeliveryOrderDto();
        inputDto.setCustomerTag("Player_One");

        DeliveryOrderDto expectedResponse = new DeliveryOrderDto();
        expectedResponse.setId(100L);
        expectedResponse.setCustomerTag("Player_One");

        when(proceedingJoinPoint.getArgs()).thenReturn(new Object[]{inputDto, "idem-key-777"});
        when(proceedingJoinPoint.proceed()).thenReturn(expectedResponse);

        Object firstResult = idempotencyAspect.enforceIdempotency(proceedingJoinPoint);
        Object secondResult = idempotencyAspect.enforceIdempotency(proceedingJoinPoint);

        assertSame(expectedResponse, firstResult);
        assertSame(expectedResponse, secondResult);
        verify(proceedingJoinPoint, times(1)).proceed();
    }

    @Test
    @DisplayName("CustomerTagMaskingAspect: маскує довгий customerTag, залишаючи префікс і суфікс")
    void customerTagMaskingAspect_whenStandardTag_masksMiddleCharacters() throws Throwable {
        CustomerTagMaskingAspect maskingAspect = new CustomerTagMaskingAspect();

        MaskCustomerTag annotation = mock(MaskCustomerTag.class);
        when(annotation.visiblePrefix()).thenReturn(2);
        when(annotation.visibleSuffix()).thenReturn(2);
        when(annotation.bypassForAdmin()).thenReturn(true);

        DeliveryOrderDto orderDto = new DeliveryOrderDto();
        orderDto.setCustomerTag("Player_One");

        when(proceedingJoinPoint.proceed()).thenReturn(orderDto);

        DeliveryOrderDto result = (DeliveryOrderDto) maskingAspect.maskCustomerData(proceedingJoinPoint, annotation);

        assertEquals("Pl******ne", result.getCustomerTag());
        verify(proceedingJoinPoint, times(1)).proceed();
    }

    @Test
    @DisplayName("CustomerTagMaskingAspect: коректно маскує короткий customerTag")
    void customerTagMaskingAspect_whenShortTag_masksAfterFirstChar() throws Throwable {
        CustomerTagMaskingAspect maskingAspect = new CustomerTagMaskingAspect();

        MaskCustomerTag annotation = mock(MaskCustomerTag.class);
        when(annotation.visiblePrefix()).thenReturn(2);
        when(annotation.visibleSuffix()).thenReturn(2);
        when(annotation.bypassForAdmin()).thenReturn(true);

        DeliveryOrderDto orderDto = new DeliveryOrderDto();
        orderDto.setCustomerTag("Bob");

        when(proceedingJoinPoint.proceed()).thenReturn(orderDto);

        DeliveryOrderDto result = (DeliveryOrderDto) maskingAspect.maskCustomerData(proceedingJoinPoint, annotation);

        assertEquals("B**", result.getCustomerTag());
        verify(proceedingJoinPoint, times(1)).proceed();
    }

    @Test
    @DisplayName("CustomerTagMaskingAspect: не маскує customerTag для користувача з роллю ROLE_ADMIN")
    void customerTagMaskingAspect_whenAdminAndBypassEnabled_doesNotMask() throws Throwable {
        CustomerTagMaskingAspect maskingAspect = new CustomerTagMaskingAspect();

        MaskCustomerTag annotation = mock(MaskCustomerTag.class);
        when(annotation.bypassForAdmin()).thenReturn(true);

        var auth = new UsernamePasswordAuthenticationToken(
                "admin", null,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
        );
        SecurityContextHolder.getContext().setAuthentication(auth);

        try {
            DeliveryOrderDto orderDto = new DeliveryOrderDto();
            orderDto.setCustomerTag("TEST_PLAYER");

            when(proceedingJoinPoint.proceed()).thenReturn(orderDto);

            DeliveryOrderDto result = (DeliveryOrderDto) maskingAspect.maskCustomerData(proceedingJoinPoint, annotation);

            assertEquals("TEST_PLAYER", result.getCustomerTag());
            verify(proceedingJoinPoint, times(1)).proceed();
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}