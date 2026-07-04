package com.turkcell.orderservice.application.features.order.command.cancel;

import java.util.UUID;

import com.turkcell.commonlib.cqrs.Command;
import com.turkcell.orderservice.dto.OrderResponse;

/** Manuel siparis iptali komutu (G5, docx §8.3). actorUserId JWT subject'ten gelir. */
public record CancelOrderCommand(
        UUID orderId,
        UUID actorUserId,
        String reason) implements Command<OrderResponse> {
}
