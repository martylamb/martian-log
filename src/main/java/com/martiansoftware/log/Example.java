package com.martiansoftware.log;

import java.time.Duration;

/**
 *
 * @author mlamb
 */
public class Example {
    
    // simplest factory method
    // identical to org.slf4j.LoggerFactory.getLogger(<current class>)
    private static final Log log = Log.me();
    
    // similar semantics to org.slf4j.LoggerFactory.getLogger(Class)
    private static final Log log2 = Log.forClass(Example.class);
    
    // similar semantics to org.slf4j.LoggerFactory.getLogger(String)
    private static final Log log3 = Log.named("mylog");
    
    public static void main(String[] args) throws Exception {
        
        // standard slf4j methods still work just fine        
        log.info("It is now {}", new java.util.Date());
        
        // add a throwable handler that only applies to this log
        log.addThrowableHandler(t -> System.err.format("Handled a throwable: %s%n", t.getMessage()));
        
        // add a throwable handler that applies to ALL logs
        Log.addGlobalThrowableHandler(t -> System.err.format("GLOBALLY handled a throwable: %s%n", t.getMessage()));
        
        // single-level logger
        log.info.format("This is a simple message going to the %s logger.", "info");
        
        // one of the new log levels
        log.cwarn.print("This warning goes to the log and to the console with coloring");
        
        // create a stopwatch that logs its duration to info level
        try (StopwatchLog sw1 = log.info.stopwatch("simpleStopwatch")) {
            // do something time consuming...
            for (int i = 0; i < 5; ++i) {
                sw1.warn("log message {}", i);
            }
        }

        // create a stopwatch that logs its duration to debug level but will auto-warn or auto-error
        // if operations take too long
        try (StopwatchLog sw2 = log.debug.stopwatch("stopwatchdemo")
                                            .warnOver(Duration.ofMillis(250))
                                            .errorOver(Duration.ofMillis(500))) {
            
            sw2.info("Going to sleep");
            Thread.sleep(1000); // takes over 500 ms, so results in an error log
            sw2.info("Woke up!");
        }

        // use jansi coloring to stdout
        log.cout.format("@|bold,blue This message is bright blue.|@");

        // oh yeah, what about those Throwable handlers?
        log.warn("uh-oh!", new Exception("let's test the throwable handlers"));
        
        // ...and prefixes
        Log lp = log.withPrefix("my prefix: ");
        lp.info("and don't forget about log prefixes.");
    }
}
