package com.turkcell.identityservice.exception;

import org.springframework.http.HttpStatus;

import com.turkcell.commonlib.exception.BaseException;
import com.turkcell.identityservice.entity.UserStatus;

public class AuthException extends BaseException {
 
    private AuthException(String message, HttpStatus status, String errorCode) {
        super(message, status, errorCode);
    }
 
    public static AuthException invalidCredentials() {
        return new AuthException("Invalid username or password", HttpStatus.UNAUTHORIZED, "AUTH_INVALID_CREDENTIALS");
    }
 
    public static AuthException usernameAlreadyExists(String username) {
        return new AuthException("Username already taken: " + username, HttpStatus.CONFLICT, "AUTH_USERNAME_EXISTS");
    }
 
    public static AuthException emailAlreadyExists(String email) {
        return new AuthException("Email already registered: " + email, HttpStatus.CONFLICT, "AUTH_EMAIL_EXISTS");
    }
 
    public static AuthException invalidRefreshToken() {
        return new AuthException("Refresh token is invalid or expired", HttpStatus.UNAUTHORIZED, "AUTH_INVALID_REFRESH_TOKEN");
    }
 
    public static AuthException accountNotActive(UserStatus status) {
        return new AuthException("Account is not active, current status: " + status, HttpStatus.FORBIDDEN, "AUTH_ACCOUNT_NOT_ACTIVE");
    }
}
