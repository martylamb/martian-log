package com.martiansoftware.log;

import java.time.Duration;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import org.slf4j.Logger;

/**
 *
 * @author mlamb
 */
public class StopwatchLog extends Log implements AutoCloseable {
    
    private final String _name;
    private final SingleLevelLogger _sll;
    private final long _started;
    private Duration _warnThreshold, _errorThreshold;
    
    StopwatchLog(String name, Logger delegate, Function<String, String> stringTweaker, SingleLevelLogger logStopwatchTo) {
        super(delegate, s -> defaultName(name) + ": " + stringTweaker.apply(s));
        _name = defaultName(name);
        _sll = logStopwatchTo;
        _started = System.currentTimeMillis();        
        _sll.format("%s: started", _name);
    }

    private static String defaultName(String name) {
        return name == null ? "Stopwatch" : name;
    }
    
    public StopwatchLog warnOver(Duration warnThreshold) {
        _warnThreshold = warnThreshold;
        return this;
    }
    
    public StopwatchLog errorOver(Duration errorThreshold) {
        _errorThreshold = errorThreshold;
        return this;
    }
    
    private boolean checkThreshold(String thresholdType, Duration elapsed, Duration threshold, Consumer<String> logDest) {
        if (threshold != null && elapsed.compareTo(threshold) >= 0) {
            logDest.accept(String.format("%s threshold was %d ms, elapsed time was %d ms, exceeded by %d ms",
                                            thresholdType, 
                                            threshold.toMillis(),
                                            elapsed.toMillis(),
                                            elapsed.minus(threshold).toMillis()));
            return true;
        }
        return false;
    }
    
    @Override
    public void close() {        
        Duration elapsed = Duration.ofMillis(System.currentTimeMillis() - _started);
        _sll.format("%s finished in %d ms", _name, elapsed.toMillis());
        if (!checkThreshold("error", elapsed, _errorThreshold, s -> error(s)))
            checkThreshold("warning", elapsed, _warnThreshold, s -> warn(s));
    }
}
