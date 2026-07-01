package com.turkcell.identityservice.exception;

import org.springframework.http.HttpStatus;

import com.turkcell.commonlib.exception.BaseException;

/** Profil is kurali ihlali (username/email cakismasi) -> 409. GlobalExceptionHandler (common-lib) yakalar. */
public class ProfileConflictException extends BaseException {
    public ProfileConflictException(String message) {
        super(message, HttpStatus.CONFLICT, "PROFILE_CONFLICT");
    }
}
