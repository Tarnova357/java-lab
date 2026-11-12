package com.example.java_lab.aspect;

import com.example.java_lab.annotation.MaskCustomerTag;
import com.example.java_lab.dto.DeliveryOrderDto;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Aspect
@Component
public class CustomerTagMaskingAspect {

    @Around("@annotation(maskCustomerTag)")
    public Object maskCustomerData(ProceedingJoinPoint joinPoint, MaskCustomerTag maskCustomerTag) throws Throwable {
        Object result = joinPoint.proceed();

        if (maskCustomerTag.bypassForAdmin() && isCurrentUserAdmin()) {
            return result;
        }

        int prefix = maskCustomerTag.visiblePrefix();
        int suffix = maskCustomerTag.visibleSuffix();

        if (result instanceof DeliveryOrderDto orderDto) {
            applyMask(orderDto, prefix, suffix);
        } else if (result instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof DeliveryOrderDto orderDto) {
                    applyMask(orderDto, prefix, suffix);
                }
            }
        }

        return result;
    }

    private boolean isCurrentUserAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return false;
        }
        return auth.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
    }

    private void applyMask(DeliveryOrderDto dto, int prefix, int suffix) {
        String tag = dto.getCustomerTag();
        if (tag == null || tag.isBlank()) {
            return;
        }

        String masked = maskString(tag, prefix, suffix);
        log.info("[AOP Privacy] Замасковано customerTag: '{}' -> '{}'", tag, masked);
        dto.setCustomerTag(masked);
    }

    private String maskString(String value, int prefix, int suffix) {
        int len = value.length();
        if (len <= prefix + suffix) {
            return value.charAt(0) + "*".repeat(Math.max(1, len - 1));
        }
        int maskLength = len - prefix - suffix;
        return value.substring(0, prefix)
                + "*".repeat(maskLength)
                + value.substring(len - suffix);
    }
}