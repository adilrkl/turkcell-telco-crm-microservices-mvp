package com.turkcell.customerservice.application.features.address.command.add;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.turkcell.commonlib.cqrs.CommandHandler;
import com.turkcell.commonlib.exception.ResourceNotFoundException;
import com.turkcell.customerservice.dto.AddressResponse;
import com.turkcell.customerservice.entity.Address;
import com.turkcell.customerservice.repository.AddressRepository;
import com.turkcell.customerservice.repository.CustomerRepository;

/** Musteriye adres ekler; isDefault ise onceki default adresler dusurulur. */
@Component
public class AddAddressCommandHandler implements CommandHandler<AddAddressCommand, AddressResponse> {

    private final AddressRepository addressRepository;
    private final CustomerRepository customerRepository;

    public AddAddressCommandHandler(AddressRepository addressRepository,
                                    CustomerRepository customerRepository) {
        this.addressRepository = addressRepository;
        this.customerRepository = customerRepository;
    }

    @Override
    @Transactional
    public AddressResponse handle(AddAddressCommand command) {
        if (!customerRepository.existsById(command.customerId())) {
            throw new ResourceNotFoundException("Customer", command.customerId());
        }

        if (command.isDefault()) {
            addressRepository.findByCustomerId(command.customerId()).forEach(a -> {
                a.setDefault(false);
                addressRepository.save(a);
            });
        }

        Address address = new Address();
        address.setCustomerId(command.customerId());
        address.setLine1(command.line1());
        address.setCity(command.city());
        address.setDistrict(command.district());
        address.setPostalCode(command.postalCode());
        address.setDefault(command.isDefault());
        Address saved = addressRepository.save(address);

        return new AddressResponse(saved.getId(), saved.getCustomerId(), saved.getLine1(),
                saved.getCity(), saved.getDistrict(), saved.getPostalCode(), saved.isDefault());
    }
}
