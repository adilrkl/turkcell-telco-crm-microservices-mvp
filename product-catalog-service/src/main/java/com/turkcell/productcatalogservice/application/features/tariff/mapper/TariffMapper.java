package com.turkcell.productcatalogservice.application.features.tariff.mapper;

import java.time.LocalDate;

import org.springframework.stereotype.Component;

import com.turkcell.productcatalogservice.application.features.tariff.command.create.CreateTariffCommand;
import com.turkcell.productcatalogservice.dto.TariffResponse;
import com.turkcell.productcatalogservice.entity.Tariff;

/** Tariff entity <-> command/response donusumleri. */
@Component
public class TariffMapper {

    /** Komuttan yeni ACTIVE tarife olusturur (effectiveFrom = bugun). */
    public Tariff toTariff(CreateTariffCommand command) {
        Tariff tariff = new Tariff();
        tariff.setCode(command.code());
        tariff.setName(command.name());
        tariff.setType(command.type());
        tariff.setMonthlyFee(command.monthlyFee());
        tariff.setMinutesIncluded(command.minutesIncluded());
        tariff.setSmsIncluded(command.smsIncluded());
        tariff.setDataMbIncluded(command.dataMbIncluded());
        tariff.setStatus("ACTIVE");
        tariff.setEffectiveFrom(LocalDate.now());
        return tariff;
    }

    public TariffResponse toResponse(Tariff t) {
        return new TariffResponse(t.getId(), t.getCode(), t.getName(), t.getType(), t.getMonthlyFee(),
                t.getMinutesIncluded(), t.getSmsIncluded(), t.getDataMbIncluded(), t.getStatus());
    }
}
