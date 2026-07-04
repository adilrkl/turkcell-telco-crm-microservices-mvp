package com.turkcell.customerservice.application.features.document.command.upload;

import java.util.UUID;

import com.turkcell.commonlib.cqrs.Command;
import com.turkcell.customerservice.dto.DocumentResponse;

public record UploadDocumentCommand(
        UUID customerId,
        String type,
        String fileName) implements Command<DocumentResponse> {
}
