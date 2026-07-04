package com.turkcell.customerservice.application.features.document.command.upload;

import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.turkcell.commonlib.cqrs.CommandHandler;
import com.turkcell.commonlib.exception.ResourceNotFoundException;
import com.turkcell.customerservice.dto.DocumentResponse;
import com.turkcell.customerservice.entity.Document;
import com.turkcell.customerservice.repository.CustomerRepository;
import com.turkcell.customerservice.repository.DocumentRepository;

/**
 * KYC belge yuklemesi (mock): dosyanin kendisi alinmaz; fileName'den benzersiz bir
 * fileRef uretilir (object-storage tasima isi Faz 6 / MinIO'da).
 */
@Component
public class UploadDocumentCommandHandler implements CommandHandler<UploadDocumentCommand, DocumentResponse> {

    private final DocumentRepository documentRepository;
    private final CustomerRepository customerRepository;

    public UploadDocumentCommandHandler(DocumentRepository documentRepository,
                                        CustomerRepository customerRepository) {
        this.documentRepository = documentRepository;
        this.customerRepository = customerRepository;
    }

    @Override
    @Transactional
    public DocumentResponse handle(UploadDocumentCommand command) {
        if (!customerRepository.existsById(command.customerId())) {
            throw new ResourceNotFoundException("Customer", command.customerId());
        }

        Document document = new Document();
        document.setCustomerId(command.customerId());
        document.setType(command.type());
        document.setFileRef("mock://documents/" + command.customerId() + "/"
                + UUID.randomUUID() + "-" + command.fileName());
        Document saved = documentRepository.save(document);

        return new DocumentResponse(saved.getId(), saved.getCustomerId(), saved.getType(),
                saved.getFileRef(), saved.getVerifiedAt());
    }
}
