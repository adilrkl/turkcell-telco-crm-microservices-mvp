package com.turkcell.commonlib.cqrs;

import java.util.List;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;

import com.turkcell.commonlib.cqrs.pipeline.LoggingBehavior;
import com.turkcell.commonlib.cqrs.pipeline.PipelineBehavior;

/**
 * CQRS mediator altyapisini (Mediator + varsayilan pipeline behavior'lari) tum servislere
 * auto-configuration ile saglar. {@link SpringMediator} ve {@link LoggingBehavior}
 * com.turkcell.commonlib.* altinda oldugu icin servislerin component-scan'i ile bulunmaz;
 * bu auto-config onlari bean olarak ekler.
 *
 * <p>Handler'lar/feature'lar ise her servisin kendi base paketinde component-scan ile bulunur.
 * Servisler kendi {@link PipelineBehavior} bean'lerini ekleyerek zinciri genisletebilir.
 */
@AutoConfiguration
public class CqrsAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(Mediator.class)
    public Mediator mediator(ApplicationContext context, List<PipelineBehavior> behaviors) {
        return new SpringMediator(context, behaviors);
    }

    @Bean
    @ConditionalOnMissingBean
    public LoggingBehavior cqrsLoggingBehavior() {
        return new LoggingBehavior();
    }
}
