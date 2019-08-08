package com.martiansoftware.log;

//   Copyright 2017 Martian Software, Inc.
//
//   Licensed under the Apache License, Version 2.0 (the "License");
//   you may not use this file except in compliance with the License.
//   You may obtain a copy of the License at
//
//       http://www.apache.org/licenses/LICENSE-2.0
//
//   Unless required by applicable law or agreed to in writing, software
//   distributed under the License is distributed on an "AS IS" BASIS,
//   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//   See the License for the specific language governing permissions and
//   limitations under the License.

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
