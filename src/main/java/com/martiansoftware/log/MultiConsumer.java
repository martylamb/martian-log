package com.martiansoftware.log;

import java.util.Set;
import java.util.function.Consumer;

/**
 * Helper class for distributing events to multiple consumers.
 * 
 * @author <a href="http://martylamb.com">Marty Lamb</a>
 */
class MultiConsumer<T> implements Consumer<T> {

    private final Set<Consumer<T>> _handlers = new java.util.LinkedHashSet<>();
    private final Object _lock = new Object();
    
    public MultiConsumer<T> add(Consumer<T> t) {
        synchronized(_lock) {
            _handlers.add(t);
            return this;
        }
    }
    
    public MultiConsumer<T> remove(Consumer<T> t) {
        synchronized(_lock) {
            _handlers.remove(t);
            return this;
        }
    }
    
    @Override
    public void accept(T t) {
        synchronized(_lock) {
            _handlers.forEach(h -> h.accept(t));
        }
    }
}
