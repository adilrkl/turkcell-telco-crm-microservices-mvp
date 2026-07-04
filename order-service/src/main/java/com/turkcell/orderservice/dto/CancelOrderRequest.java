package com.turkcell.orderservice.dto;

/** Manuel iptal istegi (G5). Govde opsiyoneldir: {"reason": "..."}. */
public record CancelOrderRequest(String reason) {
}
