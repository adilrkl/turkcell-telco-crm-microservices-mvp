package com.turkcell.orderservice.exception;

import org.springframework.http.HttpStatus;

import com.turkcell.commonlib.exception.BaseException;

/** Siparis is kurali ihlali -> 409. GlobalExceptionHandler (common-lib) yakalar. */
public class InvalidOrderException extends BaseException {
    public InvalidOrderException(String message) {
        super(message, HttpStatus.CONFLICT, "ORDER_INVALID");
    }
}
