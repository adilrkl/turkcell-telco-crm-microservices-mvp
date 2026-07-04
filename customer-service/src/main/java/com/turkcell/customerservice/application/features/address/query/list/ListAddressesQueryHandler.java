package com.turkcell.customerservice.application.features.address.query.list;

import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.turkcell.commonlib.cqrs.QueryHandler;
import com.turkcell.customerservice.dto.AddressResponse;
import com.turkcell.customerservice.repository.AddressRepository;

@Component
public class ListAddressesQueryHandler implements QueryHandler<ListAddressesQuery, List<AddressResponse>> {

    private final AddressRepository repository;

    public ListAddressesQueryHandler(AddressRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<AddressResponse> handle(ListAddressesQuery query) {
        return repository.findByCustomerId(query.customerId()).stream()
                .map(a -> new AddressResponse(a.getId(), a.getCustomerId(), a.getLine1(),
                        a.getCity(), a.getDistrict(), a.getPostalCode(), a.isDefault()))
                .toList();
    }
}
