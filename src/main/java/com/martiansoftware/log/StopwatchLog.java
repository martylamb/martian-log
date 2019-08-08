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

import java.time.Duration;
import java.util.function.Consumer;
import java.util.function.Function;
import org.slf4j.Logger;

/**
 * A Log that measures elapsed time between its creating and its closing.
 * A StopwatchLog can log warnings or errors if user-set thresholds are exceeded.
 * 
 * @author <a href="http://martylamb.com">Marty Lamb</a>
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
    
    /**
     * Specifies a Duration that will be compared against the elapsed time
     * when the StopwatchLog is closed.  If the Duration is exceeded, a warning
     * will be logged.
     * 
     * @param warnThreshold
     * @return this StopwatchLog
     */
    public StopwatchLog warnOver(Duration warnThreshold) {
        _warnThreshold = warnThreshold;
        return this;
    }
    
    /**
     * Specifies a Duration that will be compared against the elapsed time
     * when the StopwatchLog is closed.  If the Duration is exceeded, an error
     * will be logged.
     * 
     * @param errorThreshold
     * @return this StopwatchLog
     */
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
