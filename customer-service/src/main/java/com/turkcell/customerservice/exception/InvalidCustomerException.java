package com.turkcell.customerservice.exception;

import org.springframework.http.HttpStatus;

import com.turkcell.commonlib.exception.BaseException;

/** Gecersiz musteri girdisi (orn. TCKN/VKN dogrulanamadi) -> 422. GlobalExceptionHandler (common-lib) yakalar. */
public class InvalidCustomerException extends BaseException {
    public InvalidCustomerException(String message) {
        super(message, HttpStatus.UNPROCESSABLE_ENTITY, "CUSTOMER_INVALID");
    }
}
