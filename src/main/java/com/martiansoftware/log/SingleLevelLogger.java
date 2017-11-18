package com.martiansoftware.log;

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
