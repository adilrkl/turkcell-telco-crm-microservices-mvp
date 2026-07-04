package com.turkcell.customerservice.application.features.document.query.list;

import java.util.List;
import java.util.UUID;

import com.turkcell.commonlib.cqrs.Query;
import com.turkcell.customerservice.dto.DocumentResponse;

public record ListDocumentsQuery(UUID customerId) implements Query<List<DocumentResponse>> {
}
