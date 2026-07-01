package com.turkcell.orderservice.application.features.order.query.get;

import java.util.UUID;

import com.turkcell.commonlib.cqrs.Query;
import com.turkcell.orderservice.dto.OrderResponse;

/** Tekil siparis durumu sorgusu. */
public record GetOrderQuery(UUID id) implements Query<OrderResponse> {
}
