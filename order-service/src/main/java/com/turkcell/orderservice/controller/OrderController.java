package com.turkcell.orderservice.controller;

import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.turkcell.commonlib.cache.RestPage;
import com.turkcell.commonlib.cqrs.Mediator;
import com.turkcell.commonlib.dto.ApiResponse;
import com.turkcell.orderservice.application.features.order.command.cancel.CancelOrderCommand;
import com.turkcell.orderservice.application.features.order.command.place.PlaceOrderCommand;
import com.turkcell.orderservice.application.features.order.query.get.GetOrderQuery;
import com.turkcell.orderservice.application.features.order.query.list.ListOrdersQuery;
import com.turkcell.orderservice.dto.CancelOrderRequest;
import com.turkcell.orderservice.dto.OrderResponse;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final Mediator mediator;

    public OrderController(Mediator mediator) {
        this.mediator = mediator;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('CUSTOMER','CSR')")
    public ApiResponse<OrderResponse> place(@Valid @RequestBody PlaceOrderCommand command) {
        return ApiResponse.ok(mediator.send(command), "Siparis alindi");
    }

    /** Saga asenkron ilerledigi icin son durumu (FULFILLED/CANCELLED) buradan poll et. */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('CUSTOMER','CSR')")
    public ApiResponse<OrderResponse> get(@PathVariable UUID id) {
        return ApiResponse.ok(mediator.send(new GetOrderQuery(id)), "Siparis durumu");
    }

    /**
     * Sayfali siparis listesi; customerId/status filtreli (FE orders tablosu).
     * Yalnizca CSR/ADMIN: platformda henuz kullanici->musteri baglantisi olmadigi icin
     * CUSTOMER'a "kendi siparisleri" filtrelemesi yapilamiyor (FRONTEND.md acik karar).
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('CSR','ADMIN')")
    public ApiResponse<RestPage<OrderResponse>> list(Pageable pageable,
                                                     @RequestParam(required = false) UUID customerId,
                                                     @RequestParam(required = false) String status) {
        return ApiResponse.ok(mediator.send(new ListOrdersQuery(pageable, customerId, status)));
    }

    /**
     * Manuel iptal (G5, docx §8.3): terminal-oncesi siparisi iptal eder; ulasilan adima gore
     * release/refund compensation tetiklenir. Govde opsiyonel: {"reason": "..."}.
     * Yalnizca CSR/ADMIN: kullanici->musteri baglantisi olmadigi icin CUSTOMER'a
     * "kendi siparisi" kontrolu yapilamiyor (list endpoint'iyle ayni gerekce).
     */
    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('CSR','ADMIN')")
    public ApiResponse<OrderResponse> cancel(@PathVariable UUID id,
                                             @RequestBody(required = false) CancelOrderRequest request,
                                             @AuthenticationPrincipal Jwt jwt) {
        UUID actorUserId = jwt != null ? UUID.fromString(jwt.getSubject()) : null;
        String reason = request != null ? request.reason() : null;
        return ApiResponse.ok(mediator.send(new CancelOrderCommand(id, actorUserId, reason)),
                "Siparis iptal edildi");
    }
}
