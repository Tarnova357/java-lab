package com.example.java_lab.annotation;

import com.example.java_lab.config.IdempotencyKeyArgumentResolver;
import com.example.java_lab.dto.VirtualAssetDto;
import com.example.java_lab.entity.StorageAccount;
import com.example.java_lab.repository.StorageAccountRepository;
import jakarta.validation.ConstraintValidatorContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.context.request.NativeWebRequest;

import java.lang.reflect.Method;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomAnnotationsTest {

    @Mock
    private StorageAccountRepository storageAccountRepository;

    @Mock
    private ConstraintValidatorContext validatorContext;

    @Mock
    private NativeWebRequest webRequest;

    @InjectMocks
    private PlatformMatchValidator platformMatchValidator;

    private IdempotencyKeyArgumentResolver idempotencyResolver;

    @BeforeEach
    void setUp() {
        idempotencyResolver = new IdempotencyKeyArgumentResolver();
    }

    @Test
    @DisplayName("PlatformMatchValidator: повертає true, якщо платформи активу та сховища збігаються")
    void platformMatch_matchingPlatforms_returnsTrue() {
        StorageAccount account = new StorageAccount();
        account.setId(1L);
        account.setPlatform("ROBLOX");

        VirtualAssetDto dto = new VirtualAssetDto();
        dto.setStorageAccountId(1L);
        dto.setPlatform("ROBLOX");

        when(storageAccountRepository.findById(1L)).thenReturn(Optional.of(account));

        boolean isValid = platformMatchValidator.isValid(dto, validatorContext);

        assertTrue(isValid);
    }

    @Test
    @DisplayName("PlatformMatchValidator: повертає false, коли платформи різняться")
    void platformMatch_mismatchedPlatforms_returnsFalse() {
        StorageAccount account = new StorageAccount();
        account.setId(1L);
        account.setPlatform("ROBLOX");

        VirtualAssetDto dto = new VirtualAssetDto();
        dto.setStorageAccountId(1L);
        dto.setPlatform("STEAM");

        when(storageAccountRepository.findById(1L)).thenReturn(Optional.of(account));

        boolean isValid = platformMatchValidator.isValid(dto, validatorContext);

        assertFalse(isValid);
    }

    @Test
    @DisplayName("PlatformMatchValidator: повертає false, якщо акаунт сховища не існує")
    void platformMatch_accountNotFound_returnsFalse() {
        VirtualAssetDto dto = new VirtualAssetDto();
        dto.setStorageAccountId(999L);
        dto.setPlatform("ROBLOX");

        when(storageAccountRepository.findById(999L)).thenReturn(Optional.empty());

        boolean isValid = platformMatchValidator.isValid(dto, validatorContext);

        assertFalse(isValid);
    }

    @Test
    @DisplayName("PostCreatedJsonMapping: містить потрібні мета-анотації")
    void postCreatedJsonMapping_containsMetaAnnotations() {
        Class<PostCreatedJsonMapping> annotationClass = PostCreatedJsonMapping.class;

        assertTrue(annotationClass.isAnnotationPresent(RequestMapping.class));
        assertTrue(annotationClass.isAnnotationPresent(ResponseStatus.class));

        RequestMapping requestMapping = annotationClass.getAnnotation(RequestMapping.class);
        assertArrayEquals(new RequestMethod[]{RequestMethod.POST}, requestMapping.method());
        assertArrayEquals(new String[]{MediaType.APPLICATION_JSON_VALUE}, requestMapping.produces());

        ResponseStatus responseStatus = annotationClass.getAnnotation(ResponseStatus.class);
        assertEquals(HttpStatus.CREATED, responseStatus.value());
    }

    @Test
    @DisplayName("IdempotencyKeyResolver: підтримує лише аргументи @IdempotencyKey типу String")
    void idempotencyResolver_supportsParameterCorrectly() throws NoSuchMethodException {
        Method method = SampleController.class.getMethod("sampleMethod", String.class, String.class, Long.class);

        MethodParameter annotatedString = new MethodParameter(method, 0);
        MethodParameter regularString = new MethodParameter(method, 1);
        MethodParameter annotatedNonString = new MethodParameter(method, 2);

        assertTrue(idempotencyResolver.supportsParameter(annotatedString));
        assertFalse(idempotencyResolver.supportsParameter(regularString));
        assertFalse(idempotencyResolver.supportsParameter(annotatedNonString));
    }

    @Test
    @DisplayName("IdempotencyKeyResolver: повертає значення заголовка Idempotency-Key, якщо воно присутнє")
    void idempotencyResolver_returnsHeaderValueWhenPresent() {
        when(webRequest.getHeader("Idempotency-Key")).thenReturn("custom-uuid-key-12345");

        Object result = idempotencyResolver.resolveArgument(null, null, webRequest, null);

        assertEquals("custom-uuid-key-12345", result);
    }

    @Test
    @DisplayName("IdempotencyKeyResolver: генерує валідний UUID, якщо заголовок відсутній")
    void idempotencyResolver_generatesUuidWhenHeaderMissing() {
        when(webRequest.getHeader("Idempotency-Key")).thenReturn(null);

        Object result = idempotencyResolver.resolveArgument(null, null, webRequest, null);

        assertNotNull(result);
        assertTrue(result instanceof String);
        String uuidString = (String) result;
        assertFalse(uuidString.isBlank());
        assertEquals(36, uuidString.length());
    }

    @SuppressWarnings("unused")
    private static class SampleController {
        public void sampleMethod(@IdempotencyKey String key, String regularParam, @IdempotencyKey Long invalidType) {
        }
    }
}