package com.turkcell.commonlib.exception;

import org.springframework.http.HttpStatus;

/** Downstream servis erisilemiyor / devre acik -> 503. GlobalExceptionHandler yakalar. */
public class ServiceUnavailableException extends BaseException {
    public ServiceUnavailableException(String serviceName) {
        super(serviceName + " su an erisilemiyor, lutfen daha sonra tekrar deneyin",
                HttpStatus.SERVICE_UNAVAILABLE, "SERVICE_UNAVAILABLE");
    }
}
