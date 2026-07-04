package com.turkcell.customerservice.application.features.kyc.command.reject;

import java.util.UUID;

import com.turkcell.commonlib.cqrs.Command;
import com.turkcell.customerservice.dto.CustomerResponse;

/** reason yalniz log/denetim icindir; durum makinesi PENDING -> REJECTED. */
public record RejectKycCommand(UUID customerId, String reason) implements Command<CustomerResponse> {
}
