package com.turkcell.customerservice.controller;

import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import com.turkcell.commonlib.cache.RestPage;
import com.turkcell.commonlib.cqrs.Mediator;
import com.turkcell.commonlib.dto.ApiResponse;
import com.turkcell.customerservice.application.features.address.command.add.AddAddressCommand;
import com.turkcell.customerservice.application.features.address.query.list.ListAddressesQuery;
import com.turkcell.customerservice.application.features.customer.command.create.CreateCustomerCommand;
import com.turkcell.customerservice.application.features.customer.command.update.UpdateCustomerCommand;
import com.turkcell.customerservice.application.features.customer.query.getbyid.GetCustomerByIdQuery;
import com.turkcell.customerservice.application.features.customer.query.list.ListCustomersQuery;
import com.turkcell.customerservice.application.features.document.command.upload.UploadDocumentCommand;
import com.turkcell.customerservice.application.features.document.query.list.ListDocumentsQuery;
import com.turkcell.customerservice.application.features.kyc.command.approve.ApproveKycCommand;
import com.turkcell.customerservice.application.features.kyc.command.reject.RejectKycCommand;
import com.turkcell.customerservice.dto.AddAddressRequest;
import com.turkcell.customerservice.dto.AddressResponse;
import com.turkcell.customerservice.dto.CustomerResponse;
import com.turkcell.customerservice.dto.DocumentResponse;
import com.turkcell.customerservice.dto.RejectKycRequest;
import com.turkcell.customerservice.dto.UpdateCustomerRequest;
import com.turkcell.customerservice.dto.UploadDocumentRequest;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/customers")
public class CustomerController {

    private final Mediator mediator;

    public CustomerController(Mediator mediator) {
        this.mediator = mediator;
    }

    // Kimlik dogrulanmis her cagri (order-service Feign dahil) musteri okuyabilir.
    @GetMapping("/{id}")
    public ApiResponse<CustomerResponse> getById(@PathVariable UUID id) {
        return ApiResponse.ok(mediator.send(new GetCustomerByIdQuery(id)));
    }

    /** Sayfali musteri listesi; q ile ad/soyad/TCKN arama (CSR/ADMIN). */
    @GetMapping
    @PreAuthorize("hasAnyRole('CSR','ADMIN')")
    public ApiResponse<RestPage<CustomerResponse>> list(Pageable pageable,
                                                        @RequestParam(required = false) String q) {
        return ApiResponse.ok(mediator.send(new ListCustomersQuery(pageable, q)));
    }

    /** Yeni musteri (CSR/ADMIN). */
    @PostMapping
    @PreAuthorize("hasAnyRole('CSR','ADMIN')")
    public ApiResponse<CustomerResponse> create(@Valid @RequestBody CreateCustomerCommand command) {
        return ApiResponse.ok(mediator.send(command), "Musteri olusturuldu");
    }

    /** Musteri guncelleme; null alan degistirilmez (CSR/ADMIN). */
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('CSR','ADMIN')")
    public ApiResponse<CustomerResponse> update(@PathVariable UUID id,
                                                @Valid @RequestBody UpdateCustomerRequest request) {
        UpdateCustomerCommand command = new UpdateCustomerCommand(
                id, request.firstName(), request.lastName(), request.identityNumber(),
                request.dateOfBirth(), request.status());
        return ApiResponse.ok(mediator.send(command), "Musteri guncellendi");
    }

    // --- Adres & belge (KYC mini akisi, G3) ---

    /** Musteriye adres ekler (CSR/ADMIN). */
    @PostMapping("/{id}/addresses")
    @PreAuthorize("hasAnyRole('CSR','ADMIN')")
    public ApiResponse<AddressResponse> addAddress(@PathVariable UUID id,
                                                   @Valid @RequestBody AddAddressRequest request) {
        AddAddressCommand command = new AddAddressCommand(id, request.line1(), request.city(),
                request.district(), request.postalCode(), Boolean.TRUE.equals(request.isDefault()));
        return ApiResponse.ok(mediator.send(command), "Adres eklendi");
    }

    /** Musterinin adresleri (CSR/ADMIN). */
    @GetMapping("/{id}/addresses")
    @PreAuthorize("hasAnyRole('CSR','ADMIN')")
    public ApiResponse<List<AddressResponse>> listAddresses(@PathVariable UUID id) {
        return ApiResponse.ok(mediator.send(new ListAddressesQuery(id)));
    }

    /** KYC belge yuklemesi - mock: dosya alinmaz, fileName'den fileRef uretilir (CSR/ADMIN). */
    @PostMapping("/{id}/documents")
    @PreAuthorize("hasAnyRole('CSR','ADMIN')")
    public ApiResponse<DocumentResponse> uploadDocument(@PathVariable UUID id,
                                                        @Valid @RequestBody UploadDocumentRequest request) {
        return ApiResponse.ok(mediator.send(new UploadDocumentCommand(id, request.type(), request.fileName())),
                "Belge yuklendi");
    }

    /** Musterinin belgeleri (CSR/ADMIN). */
    @GetMapping("/{id}/documents")
    @PreAuthorize("hasAnyRole('CSR','ADMIN')")
    public ApiResponse<List<DocumentResponse>> listDocuments(@PathVariable UUID id) {
        return ApiResponse.ok(mediator.send(new ListDocumentsQuery(id)));
    }

    // --- KYC karari (yalniz ADMIN) ---

    /** KYC onayi: PENDING + en az bir belge -> ACTIVE + CustomerKYCApproved eventi (ADMIN). */
    @PostMapping("/{id}/kyc/approve")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<CustomerResponse> approveKyc(@PathVariable UUID id) {
        return ApiResponse.ok(mediator.send(new ApproveKycCommand(id)), "KYC onaylandi");
    }

    /** KYC reddi: PENDING -> REJECTED (ADMIN). Body opsiyonel: {"reason": "..."}. */
    @PostMapping("/{id}/kyc/reject")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<CustomerResponse> rejectKyc(@PathVariable UUID id,
                                                   @RequestBody(required = false) RejectKycRequest request) {
        String reason = request != null ? request.reason() : null;
        return ApiResponse.ok(mediator.send(new RejectKycCommand(id, reason)), "KYC reddedildi");
    }
}
