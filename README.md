# log

An extension of the slf4j Logger that provides some additional functionality:

  * Marginally less boilerplate for obtaining a Logger:  `Log log = Log.me()`
  * The ability to create "subloggers" with a fixed prefix via `Log.withPrefix(String)`
  * The ability to register both global and Log-specific handlers for any `Throwables` that are logged (handlers are not called if the corresponding logging level is not enabled).  This can be used to trigger error reporting to a server, uploading of logs, etc.
  * Addition of an alternative `SingleLevelLogger` interface available from the `Log` as fields, offering:
    * `String.format()` semantics, e.g. `log.debug.format(...)`
    * The ability to log a bare throwable
    * The ability to pass a level-specific logger as a parameter within your code
    * Support for ansi coloring via [jansi](http://fusesource.github.io/jansi/), e.g. `log.cout.print("@|red Hello|@ @|green World|@")`
    * Three new log "levels" in addition to the standard slf4j levels because sometimes you want output to the console as well as your log files (output always goes to console and conditionally goes to other loggers depending on whether they are enabled):
      * `cout` sends output to stdout **and** to info logger
      * `cwarn` sends output to stderr (colored yellow via [jansi](http://fusesource.github.io/jansi/) when stderr supports it) **and** to warning logger
      * `cerr` sends output to stderr (colored red via [jansi](http://fusesource.github.io/jansi/) when stderr supports it) **and** to error logger
    * An Autocloseable `stopwatch` logger that will log its total time (from creation to close) to the level that produced the stopwatch, and optionally log warnings or errors if specified threshold times are exceeded.
      
**It is a project goal to remain 100% compatible with slf4j, and to not impact any existing logging or log configuration.**

**Note:** For minimum impact on existing applications, this library does NOT declare a transitive dependency on slf4j (and thus cannot create any dependency conflicts).  Your project will still need to handle this dependency as if you were not using this library at all.

example usage
-------------

```java
import java.time.Duration;

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
        
        // single-level logger provides String.format() semantics
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
```
