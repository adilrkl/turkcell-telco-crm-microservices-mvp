package com.turkcell.commonlib.web;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Import;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.turkcell.commonlib.exception.GlobalExceptionHandler;

/**
 * common-lib'deki {@link GlobalExceptionHandler} tum servislerin base paketinin
 * disinda (com.turkcell.commonlib.*) oldugu icin component-scan ile bulunmaz.
 * Bu auto-config onu bean olarak tum web servislerine ekler.
 */
@AutoConfiguration
@ConditionalOnClass(RestControllerAdvice.class)
@Import(GlobalExceptionHandler.class)
public class CommonWebAutoConfiguration {
}
