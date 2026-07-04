package com.turkcell.customerservice.exception;

import org.springframework.http.HttpStatus;

import com.turkcell.commonlib.exception.BaseException;

/** Gecersiz KYC gecisi (PENDING disi durum, belge eksikligi vb.) -> 409. GlobalExceptionHandler (common-lib) yakalar. */
public class InvalidKycStateException extends BaseException {
    public InvalidKycStateException(String message) {
        super(message, HttpStatus.CONFLICT, "KYC_INVALID_STATE");
    }
}
