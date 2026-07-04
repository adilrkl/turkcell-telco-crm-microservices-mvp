package com.turkcell.customerservice.application.features.kyc.command.approve;

import java.util.UUID;

import com.turkcell.commonlib.cqrs.Command;
import com.turkcell.customerservice.dto.CustomerResponse;

public record ApproveKycCommand(UUID customerId) implements Command<CustomerResponse> {
}
