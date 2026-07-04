package com.turkcell.customerservice.application.features.customer.rule;

import org.springframework.stereotype.Component;

import com.turkcell.customerservice.exception.InvalidCustomerException;
import com.turkcell.customerservice.util.TurkishIdentityValidator;

/** Musteri is kurallari. Kimlik dogrulama tip'e baglidir (FR-01). */
@Component
public class CustomerBusinessRules {

    private static final String INDIVIDUAL = "INDIVIDUAL";
    private static final String CORPORATE = "CORPORATE";

    /**
     * Bireysel musteri gecerli TCKN, kurumsal musteri gecerli VKN tasimali (FR-01).
     * identityNumber zorunludur (kayit TCKN/VKN dogrulamali).
     */
    public void validateIdentity(String type, String identityNumber) {
        if (identityNumber == null || identityNumber.isBlank()) {
            throw new InvalidCustomerException("Kimlik numarasi zorunlu (" + label(type) + ")");
        }
        String value = identityNumber.trim();
        if (INDIVIDUAL.equals(type)) {
            if (!TurkishIdentityValidator.isValidTckn(value)) {
                throw new InvalidCustomerException("Gecersiz TCKN");
            }
        } else if (CORPORATE.equals(type)) {
            if (!TurkishIdentityValidator.isValidVkn(value)) {
                throw new InvalidCustomerException("Gecersiz VKN");
            }
        } else {
            throw new InvalidCustomerException("Bilinmeyen musteri tipi: " + type);
        }
    }

    private static String label(String type) {
        return INDIVIDUAL.equals(type) ? "TCKN" : "VKN";
    }
}
