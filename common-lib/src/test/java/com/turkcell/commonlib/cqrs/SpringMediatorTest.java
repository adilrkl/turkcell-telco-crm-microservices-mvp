package com.turkcell.commonlib.cqrs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

import com.turkcell.commonlib.cqrs.pipeline.LoggingBehavior;

/**
 * {@link SpringMediator} icin izole (DB/Kafka/Redis'siz) dogrulama.
 * Iki kritik runtime davranisini kanitlar:
 *  1) Command/Query -> handler eslestirmesi reflection ile dogru calisir.
 *  2) {@code @Cacheable} ile proxy'lenen handler'lar da (a) dogru eslesir ve
 *     (b) record accessor'lu SpEL key'i ({@code #query.key}) ile cache advice'i korunur.
 */
class SpringMediatorTest {

    private AnnotationConfigApplicationContext context;
    private Mediator mediator;

    @BeforeEach
    void setUp() {
        context = new AnnotationConfigApplicationContext(TestConfig.class);
        mediator = context.getBean(Mediator.class);
    }

    @AfterEach
    void tearDown() {
        context.close();
    }

    @Test
    void send_command_resolvesHandlerAndReturnsResult() {
        String result = mediator.send(new GreetCommand("dunya"));
        assertThat(result).isEqualTo("merhaba:dunya");
    }

    @Test
    void send_query_honorsCacheableProxyWithRecordAccessorKey() {
        AtomicInteger counter = context.getBean("echoCounter", AtomicInteger.class);

        String first = mediator.send(new EchoQuery("k1"));
        String second = mediator.send(new EchoQuery("k1"));

        assertThat(first).isEqualTo("echo:k1");
        assertThat(second).isEqualTo("echo:k1");
        // Ayni key -> handler govdesi yalnizca 1 kez calisir (proxy + SpEL key dogru).
        assertThat(counter.get()).isEqualTo(1);

        mediator.send(new EchoQuery("k2"));
        assertThat(counter.get()).isEqualTo(2); // farkli key -> yeniden calisir
    }

    @Test
    void send_unknownRequest_throwsHandlerNotFound() {
        assertThatThrownBy(() -> mediator.send(new OrphanCommand()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Handler bulunamadi");
    }

    // --- test tipleri ---

    record GreetCommand(String name) implements Command<String> {
    }

    record EchoQuery(String key) implements Query<String> {
    }

    record OrphanCommand() implements Command<String> {
    }

    @Component
    static class GreetCommandHandler implements CommandHandler<GreetCommand, String> {
        @Override
        public String handle(GreetCommand command) {
            return "merhaba:" + command.name();
        }
    }

    @Component
    static class EchoQueryHandler implements QueryHandler<EchoQuery, String> {
        private final AtomicInteger counter;

        EchoQueryHandler(AtomicInteger echoCounter) {
            this.counter = echoCounter;
        }

        @Override
        @Cacheable(value = "echo", key = "#query.key")
        public String handle(EchoQuery query) {
            counter.incrementAndGet();
            return "echo:" + query.key();
        }
    }

    @Configuration
    @EnableCaching
    static class TestConfig {

        @Bean
        AtomicInteger echoCounter() {
            return new AtomicInteger();
        }

        @Bean
        ConcurrentMapCacheManager cacheManager() {
            return new ConcurrentMapCacheManager("echo");
        }

        @Bean
        LoggingBehavior loggingBehavior() {
            return new LoggingBehavior();
        }

        @Bean
        Mediator mediator(ApplicationContext ctx, java.util.List<com.turkcell.commonlib.cqrs.pipeline.PipelineBehavior> behaviors) {
            return new SpringMediator(ctx, behaviors);
        }

        @Bean
        GreetCommandHandler greetCommandHandler() {
            return new GreetCommandHandler();
        }

        @Bean
        EchoQueryHandler echoQueryHandler(AtomicInteger echoCounter) {
            return new EchoQueryHandler(echoCounter);
        }
    }
}
