package com.turkcell.commonlib.security;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.turkcell.commonlib.dto.ApiResponse;

/**
 * @PreAuthorize / method-security reddini 403'e cevirir. Olmazsa GlobalExceptionHandler'in
 * genel @ExceptionHandler(Exception) yakalayicisi bunu 500'e dusururdu.
 * Yalnizca resource-server servislerine (security classpath'te) ResourceServerSecurityAutoConfiguration
 * tarafindan @Import edilir; bu yuzden GlobalExceptionHandler security'den bagimsiz kalir.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AccessDeniedExceptionHandler {

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error("Bu islem icin yetkiniz yok", "ACCESS_DENIED"));
    }
}
