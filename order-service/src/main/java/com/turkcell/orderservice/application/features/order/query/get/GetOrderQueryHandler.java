package com.turkcell.orderservice.application.features.order.query.get;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.turkcell.commonlib.cqrs.QueryHandler;
import com.turkcell.orderservice.application.features.order.mapper.OrderMapper;
import com.turkcell.orderservice.dto.OrderResponse;
import com.turkcell.orderservice.entity.Order;
import com.turkcell.orderservice.exception.InvalidOrderException;
import com.turkcell.orderservice.repository.OrderRepository;

/** Siparis durumu sorgusu (saga asenkron ilerledigi icin poll edilir). */
@Component
public class GetOrderQueryHandler implements QueryHandler<GetOrderQuery, OrderResponse> {

    private final OrderRepository orderRepository;
    private final OrderMapper orderMapper;

    public GetOrderQueryHandler(OrderRepository orderRepository, OrderMapper orderMapper) {
        this.orderRepository = orderRepository;
        this.orderMapper = orderMapper;
    }

    @Override
    @Transactional(readOnly = true)
    public OrderResponse handle(GetOrderQuery query) {
        Order order = orderRepository.findById(query.id())
                .orElseThrow(() -> new InvalidOrderException("Siparis bulunamadi: " + query.id()));
        String tariffCode = order.getItems().isEmpty() ? null : order.getItems().get(0).getProductCode();
        return orderMapper.toResponse(order, tariffCode);
    }
}
