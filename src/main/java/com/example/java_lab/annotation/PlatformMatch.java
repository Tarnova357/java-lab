package com.example.java_lab.annotation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = PlatformMatchValidator.class)
@Documented
public @interface PlatformMatch {

    String message() default "Virtual asset platform must match the storage account platform";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}