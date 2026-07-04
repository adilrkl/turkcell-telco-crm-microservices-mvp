package com.turkcell.ticketservice.controller;

import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.turkcell.commonlib.cache.RestPage;
import com.turkcell.commonlib.cqrs.Mediator;
import com.turkcell.commonlib.dto.ApiResponse;
import com.turkcell.ticketservice.application.features.ticket.command.assign.AssignTicketCommand;
import com.turkcell.ticketservice.application.features.ticket.command.comment.AddTicketCommentCommand;
import com.turkcell.ticketservice.application.features.ticket.command.create.CreateTicketCommand;
import com.turkcell.ticketservice.application.features.ticket.command.transition.TransitionTicketStatusCommand;
import com.turkcell.ticketservice.application.features.ticket.query.getbyid.GetTicketByIdQuery;
import com.turkcell.ticketservice.application.features.ticket.query.list.ListTicketsQuery;
import com.turkcell.ticketservice.dto.AddCommentRequest;
import com.turkcell.ticketservice.dto.AssignTicketRequest;
import com.turkcell.ticketservice.dto.CreateTicketRequest;
import com.turkcell.ticketservice.dto.TicketCommentResponse;
import com.turkcell.ticketservice.dto.TicketDetailResponse;
import com.turkcell.ticketservice.dto.TicketResponse;
import com.turkcell.ticketservice.dto.TransitionTicketRequest;

import jakarta.validation.Valid;

/** Destek talebi yonetimi (CSR/ADMIN). Durum makinesi: OPEN -> IN_PROGRESS -> RESOLVED -> CLOSED. */
@RestController
@RequestMapping("/api/tickets")
public class TicketController {

    private final Mediator mediator;

    public TicketController(Mediator mediator) {
        this.mediator = mediator;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('CSR','ADMIN')")
    public ApiResponse<TicketResponse> create(@Valid @RequestBody CreateTicketRequest request) {
        CreateTicketCommand command = new CreateTicketCommand(
                request.customerId(), request.category(), request.priority());
        return ApiResponse.ok(mediator.send(command), "Talep olusturuldu");
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('CSR','ADMIN')")
    public ApiResponse<TicketDetailResponse> getById(@PathVariable UUID id) {
        return ApiResponse.ok(mediator.send(new GetTicketByIdQuery(id)));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('CSR','ADMIN')")
    public ApiResponse<RestPage<TicketResponse>> list(Pageable pageable,
                                                      @RequestParam(required = false) String status,
                                                      @RequestParam(required = false) UUID customerId) {
        return ApiResponse.ok(mediator.send(new ListTicketsQuery(pageable, status, customerId)));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('CSR','ADMIN')")
    public ApiResponse<TicketResponse> transition(@PathVariable UUID id,
                                                  @Valid @RequestBody TransitionTicketRequest request) {
        return ApiResponse.ok(
                mediator.send(new TransitionTicketStatusCommand(id, request.targetStatus())),
                "Durum guncellendi");
    }

    @PatchMapping("/{id}/assignee")
    @PreAuthorize("hasAnyRole('CSR','ADMIN')")
    public ApiResponse<TicketResponse> assign(@PathVariable UUID id,
                                              @Valid @RequestBody AssignTicketRequest request) {
        return ApiResponse.ok(
                mediator.send(new AssignTicketCommand(id, request.assigneeId())),
                "Talep atandi");
    }

    @PostMapping("/{id}/comments")
    @PreAuthorize("hasAnyRole('CSR','ADMIN')")
    public ApiResponse<TicketCommentResponse> addComment(@PathVariable UUID id,
                                                         @Valid @RequestBody AddCommentRequest request,
                                                         @AuthenticationPrincipal Jwt jwt) {
        AddTicketCommentCommand command = new AddTicketCommentCommand(
                id, UUID.fromString(jwt.getSubject()), request.body());
        return ApiResponse.ok(mediator.send(command), "Yorum eklendi");
    }
}
