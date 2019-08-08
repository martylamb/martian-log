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

import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * An interface providing additional logging methods for a specific logging level.
 * Each slf4j logging level provided by a Log (and some console-specific "levels")
 * are also available as a SingleLevelLogger.
 * 
 * SingleLevelLoggers can be helpful to change logging levels of multiple
 * log calls at the same time; if logging to a SingleLevelLogger, all you
 * need to do is change the reference of the SingleLevelLogger you are using.
 * 
 * @author <a href="http://martylamb.com">Marty Lamb</a>
 */
public interface SingleLevelLogger {
    
    public boolean isEnabled();
    
    public SingleLevelLogger print(String message);
    
    public default SingleLevelLogger print(Supplier<String> messageSupplier) {
        if (isEnabled()) print(messageSupplier.get());
        return this;
    }
    
    public default SingleLevelLogger format(String format, Object... args) {
        if (isEnabled()) print(String.format(format, args));
        return this;
    }
    
    public SingleLevelLogger throwable(Throwable t, String msg, Object... args);
    
    public default SingleLevelLogger throwable(Throwable t) {
        return throwable(t, Log.defaultMessageForThrowable(t));
    }
    
    public default SingleLevelLogger print(Stream<String> messages) {
        if (isEnabled()) messages.forEach(s -> print(s));
        return this;
    }
    
    public StopwatchLog stopwatch(String name);
}
