package com.turkcell.commonlib.cqrs;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.aop.framework.AopProxyUtils;
import org.springframework.context.ApplicationContext;
import org.springframework.core.ResolvableType;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;

import com.turkcell.commonlib.cqrs.pipeline.PipelineBehavior;
import com.turkcell.commonlib.cqrs.pipeline.RequestHandlerDelegate;

/**
 * {@link Mediator}'un Spring implementasyonu. Command/Query tipini, o tipi isleyen
 * handler bean'i ile reflection uzerinden eslestirir ve istegi once pipeline
 * behavior'larindan gecirip handler'a ulastirir.
 *
 * <p>Handler'lar {@code @Cacheable}/{@code @Transactional} ile proxy'lenebildiginden
 * gercek tip {@link AopProxyUtils#ultimateTargetClass} ile cozulur; boylece proxy'li
 * handler'lar da dogru eslesir ve dondurulen (proxy) bean uzerinde cache/transaction
 * advice'i korunur. Eslestirme sonucu (handler'lar singleton oldugu icin) cache'lenir.
 */
public class SpringMediator implements Mediator {

    private final ApplicationContext context;
    private final List<PipelineBehavior> behaviors;
    private final Map<Class<?>, Object> handlerCache = new ConcurrentHashMap<>();

    public SpringMediator(ApplicationContext context, List<PipelineBehavior> behaviors) {
        this.context = context;
        this.behaviors = behaviors.stream().sorted(AnnotationAwareOrderComparator.INSTANCE).toList();
    }

    @Override
    @SuppressWarnings("unchecked")
    public <R> R send(Command<R> command) {
        CommandHandler<Command<R>, R> handler =
                (CommandHandler<Command<R>, R>) resolveHandler(command.getClass(), CommandHandler.class);
        return invokePipeline(command, () -> handler.handle(command));
    }

    @Override
    @SuppressWarnings("unchecked")
    public <R> R send(Query<R> query) {
        QueryHandler<Query<R>, R> handler =
                (QueryHandler<Query<R>, R>) resolveHandler(query.getClass(), QueryHandler.class);
        return invokePipeline(query, () -> handler.handle(query));
    }

    /** Hangi command/query -> hangi handler? Sonuc cache'lenir. */
    private Object resolveHandler(Class<?> requestType, Class<?> handlerInterface) {
        Object cached = handlerCache.get(requestType);
        if (cached != null) {
            return cached;
        }
        for (String beanName : context.getBeanNamesForType(handlerInterface)) {
            Object bean = context.getBean(beanName);
            Class<?> targetClass = AopProxyUtils.ultimateTargetClass(bean); // proxy'i ac
            for (ResolvableType iface : ResolvableType.forClass(targetClass).getInterfaces()) {
                Class<?> raw = iface.getRawClass();
                if (raw != null && handlerInterface.isAssignableFrom(raw)) {
                    Class<?> requestGeneric = iface.getGeneric(0).resolve();
                    if (requestType.equals(requestGeneric)) {
                        handlerCache.put(requestType, bean);
                        return bean; // proxy'li bean don -> cache/tx advice korunur
                    }
                }
            }
        }
        throw new IllegalStateException("Handler bulunamadi: " + requestType.getName());
    }

    /** Behavior zincirini tersten kurar; en icteki adim handler'in kendisidir. */
    private <R> R invokePipeline(Object request, RequestHandlerDelegate<R> handlerInvocation) {
        RequestHandlerDelegate<R> next = handlerInvocation;
        for (int i = behaviors.size() - 1; i >= 0; i--) {
            PipelineBehavior behavior = behaviors.get(i);
            if (!behavior.supports(request)) {
                continue;
            }
            RequestHandlerDelegate<R> current = next;
            next = () -> behavior.handle(request, current);
        }
        return next.invoke();
    }
}
