package com.turkcell.customerservice.application.features.document.query.list;

import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.turkcell.commonlib.cqrs.QueryHandler;
import com.turkcell.customerservice.dto.DocumentResponse;
import com.turkcell.customerservice.repository.DocumentRepository;

@Component
public class ListDocumentsQueryHandler implements QueryHandler<ListDocumentsQuery, List<DocumentResponse>> {

    private final DocumentRepository repository;

    public ListDocumentsQueryHandler(DocumentRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<DocumentResponse> handle(ListDocumentsQuery query) {
        return repository.findByCustomerId(query.customerId()).stream()
                .map(d -> new DocumentResponse(d.getId(), d.getCustomerId(), d.getType(),
                        d.getFileRef(), d.getVerifiedAt()))
                .toList();
    }
}
