package com.turkcell.orderservice.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import com.turkcell.commonlib.exception.ServiceUnavailableException;

import feign.FeignException;

/**
 * customer-service erisilemediginde (down / timeout / devre acik) devreye girer.
 * 4xx cevaplar is hatasidir (orn. 404 -> musteri yok), oldugu gibi geri firlatilir;
 * gerisi ServiceUnavailableException (503) olur.
 */
@Component
public class CustomerClientFallbackFactory implements FallbackFactory<CustomerClient> {

    private static final Logger log = LoggerFactory.getLogger(CustomerClientFallbackFactory.class);

    @Override
    public CustomerClient create(Throwable cause) {
        return id -> {
            // Status bazli kontrol: Retry-After header'li 4xx'ler RetryableException
            // olarak gelir (FeignClientException degil), onlar da is hatasi sayilir.
            if (cause instanceof FeignException fe && fe.status() >= 400 && fe.status() < 500) {
                throw fe;
            }
            log.warn("customer-service fallback devrede (id={}): {}", id, cause.toString());
            throw new ServiceUnavailableException("customer-service");
        };
    }
}
