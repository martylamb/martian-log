package com.martiansoftware.log;

import java.io.PrintStream;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import org.fusesource.jansi.Ansi;
import org.fusesource.jansi.AnsiConsole;
import org.fusesource.jansi.AnsiRenderer;
import org.fusesource.jansi.AnsiString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;
import org.slf4j.helpers.MessageFormatter;

/**
 * An extension of the slf4j Logger that provides some additional functionality:
 * 
 * <ul>
 *   <li>slightly less boilerplate needed for construction(e.g. <code>Log log = Log.me();</code>)</li>
 *   <li>ability to add a fixed prefix to all of a Logger's messages via <code>Log.withPrefix(String prefix)</code></li>
 *   <li>ability to register both global and Log-specific handlers for any Throwables that are logged (handlers are not called if the corresponding logging level is not enabled)</li>
 *   <li>addition of an alternative SingleLevelLogger interface with extensions such as String.format() semantics and ability to just log bare Throwables</li>
 *   <li>addition of three SingleLevelLoggers:
 *     <ul>
 *       <li>cout sends output to stdout AND info</li>
 *       <li>cwarn sends output to stderr (colored yellow via <a href="http://fusesource.github.io/jansi/">jansi</a> when stderr supports it) and warn</li>
 *       <li>cerr sends output to stderr (colored red via <a href="http://fusesource.github.io/jansi/">jansi</a> when stderr supports it) and error</li>
 *     </ul>
 *   </li>
 *   <li>addition of an autocloseable StopwatchLog that measures the elapsed time it was open and optionally logs errors or warnings if a time threshold is exceeded</li>
 *   <li>don't duplicate exception message</li>
 * </ul>
 * 
 * @author <a href="http://martylamb.com">Marty Lamb</a>
 */
public class Log implements Logger {
       
    private static final MultiConsumer<Throwable> _globalThrowableHandlers = new MultiConsumer<>();
    private static final Function<String, String> DEFAULT_STRINGTWEAKER = s -> s;
    
    protected final Logger _delegate; 
    private final MultiConsumer<Throwable> _throwableHandlers = new MultiConsumer<>();
    private final Function<String, String> _stringTweaker;
    
    static {
        AnsiConsole.systemInstall();
    }
    
    Log(Logger delegate, Function<String, String> stringTweaker) {
        _delegate = delegate;
        _stringTweaker = stringTweaker;
    }
    
    public static Log me() {
        return new Log(LoggerFactory.getLogger(CallerOf.stackAncestor(1)), DEFAULT_STRINGTWEAKER);
    }
    
    public static Log named(String name) {
        return new Log(LoggerFactory.getLogger(name), DEFAULT_STRINGTWEAKER);
    }
    
    public static Log forClass(Class clazz) {
        return new Log(LoggerFactory.getLogger(clazz), DEFAULT_STRINGTWEAKER);
    }
    
    public Log withPrefix(String prefix) {
        return new Log(_delegate, s -> prefix + tweak(s));
    }
    
    @Override
    public String getName() {
        return _delegate.getName();
    }
    
    // performs any last-minute manipulation of log messages
    protected String tweak(String s) {
        return _stringTweaker == null ? s : _stringTweaker.apply(s);        
    }
    
    
// -----------------------------------------------------------------------------
// extra processing for logged throwables, allowing actions such as
// reporting to a server, uploading log files, etc.
    
    protected void handleThrowable(Throwable t) {
        _globalThrowableHandlers.accept(t);
        _throwableHandlers.accept(t);
    }

    static String defaultMessageForThrowable(Throwable t) {
        return String.format("%s: %s", t.getClass().getName(), t.getMessage());
    }

    public Log addThrowableHandler(Consumer<Throwable> handler) {
        _throwableHandlers.add(handler);
        return this;
    }
    
    public Log removeThrowableHandler(Consumer<Throwable> handler) {
        _throwableHandlers.remove(handler);
        return this;
    }

    public static void addGlobalThrowableHandler(Consumer<Throwable> handler) {
        _globalThrowableHandlers.add(handler);
    }
    
    public static void removeGlobalThrowableHandler(Consumer<Throwable> handler) {
        _globalThrowableHandlers.remove(handler);
    }

// -----------------------------------------------------------------------------

    // simple wrapper for slf4j loggers that outputs to an specific log level
    private class SLL implements SingleLevelLogger {

        private final Supplier<Boolean> _enabledChecker;
        private final Consumer<String> _logger;
        private final BiConsumer<String, Throwable> _throwableLogger;
        
        SLL(Supplier<Boolean> enabledChecker, Consumer<String> logger, BiConsumer<String, Throwable> throwableLogger) {
            _enabledChecker = enabledChecker;
            _logger = logger;
            _throwableLogger = throwableLogger;
        }
        
        @Override public boolean isEnabled() {
            return _enabledChecker.get();
        }

        @Override public SingleLevelLogger format(String format, Object... args) {
            print(String.format(format, args));
            return this;
        }

        @Override public SingleLevelLogger throwable(Throwable t, String format, Object... args) {
            _throwableLogger.accept(String.format(format, args), t);
            return this;
        }

        @Override
        public SingleLevelLogger print(String message) {
            _logger.accept(message);
            return this;
        }
        
        @Override public StopwatchLog stopwatch(String name) {
            return new StopwatchLog(name, Log.this._delegate, Log.this._stringTweaker, this);
        }
    }
    
// -----------------------------------------------------------------------------

    // special version of SingleLevelLogger that always goes to console (stdout or stderr as needed)
    // and optionally colors output (e.g. yellow for warnings and red for errors)
    private class AnsiLogger implements SingleLevelLogger {

        private final PrintStream _out; // console output
        private final Consumer<String> _logger; // slf4j output
        private final BiConsumer<String, Throwable> _throwableLogger; // what to do with throwable messages
        private final Function<Ansi, Ansi> _ansiConfig; // the ansi transform to use for console output
        
        AnsiLogger(PrintStream out, Function<Ansi, Ansi> ansiConfig, Consumer<String> logger, BiConsumer<String, Throwable> throwableLogger) {
            _out = out;
            _ansiConfig = ansiConfig;
            _logger = logger;
            _throwableLogger = throwableLogger;
        }
        
        @Override public boolean isEnabled() {
            return true; // ansi loggers are always enabled for console purposes, but their underlying slf4j loggers might not be 
        }

        private boolean hasJansiSequence(String s) {
            return s.contains("@|");
        }

        // process any ansi codes prior to console output
        private String ansify(String s) {
            if (hasJansiSequence(s)) { // if user specified a jansi sequence, let it override anything else we're doing (see http://fusesource.github.io/jansi/ )
                return AnsiRenderer.render(s);
            } else {
                return _ansiConfig.apply(Ansi.ansi()).render(s).reset().toString();
            }
        }

        // strip any ansi codes for slf4j output
        private String noAnsify(String s) {
            return new AnsiString(AnsiRenderer.render(s)).getPlain().toString();
        }
        
        @Override
        public SingleLevelLogger throwable(Throwable t, String format, Object... args) {
            if (isEnabled()) {
                String msg = String.format(format, args);
                print(msg);
                _throwableLogger.accept(noAnsify(msg), t);
            }
            return this;
        }        

        @Override
        public SingleLevelLogger print(String messge) {
            _out.println(ansify(messge));
            _logger.accept(noAnsify(messge));
            return this;
        }
        
        @Override public StopwatchLog stopwatch(String name) {
            return new StopwatchLog(name, Log.this._delegate, Log.this._stringTweaker, this);
        }
    }
    
    public final SingleLevelLogger trace = new SLL(() -> isTraceEnabled(), s -> trace(s), (s, t) -> trace(s, t));
    public final SingleLevelLogger debug = new SLL(() -> isDebugEnabled(), s -> debug(s), (s, t) -> debug(s, t));
    public final SingleLevelLogger info = new SLL(() -> isInfoEnabled(), s -> info(s), (s, t) -> info(s, t));
    public final SingleLevelLogger warn = new SLL(() -> isWarnEnabled(), s -> warn(s), (s, t) -> warn(s, t));
    public final SingleLevelLogger error = new SLL(() -> isErrorEnabled(), s -> error(s), (s, t) -> error(s, t));

    public final SingleLevelLogger cout = new AnsiLogger(System.out, a -> a, s -> info(s), (s, t) -> info(s, t));
    public final SingleLevelLogger cwarn = new AnsiLogger(System.err, a -> a.bold().fgBrightYellow(), s -> warn(s), (s, t) -> warn(s, t));
    public final SingleLevelLogger cerr = new AnsiLogger(System.err, a -> a.bold().fgBrightRed(), s -> error(s), (s, t) -> error(s, t));
    
// -----------------------------------------------------------------------------
    
// ## THE FOLLOWING CODE IS GENERATED BY scripts/updateLogJava
// ## BEGIN GENERATED CODE - DO NOT EDIT BELOW THIS LINE ##
    @Override public boolean isTraceEnabled() {
        return _delegate.isTraceEnabled();
    }

    @Override public boolean isTraceEnabled(Marker marker) {
        return _delegate.isTraceEnabled(marker);
    }

    @Override public void trace(String msg) {
        if(isTraceEnabled()) {
            _delegate.trace(tweak(msg));
        }
    }

    @Override public void trace(String format, Object arg) {
        if(isTraceEnabled()) {
            _delegate.trace(tweak(MessageFormatter.format(format, arg).getMessage()));
        }
    }

    @Override public void trace(String format, Object arg1, Object arg2) {
        if(isTraceEnabled()) {
            _delegate.trace(tweak(MessageFormatter.format(format, arg1, arg2).getMessage()));
        }
    }

    @Override public void trace(String format, Object... arguments) {
        if(isTraceEnabled()) {
            _delegate.trace(tweak(MessageFormatter.format(format, arguments).getMessage()));
        }
    }

    @Override public void trace(String msg, Throwable t) {
        if (isTraceEnabled()) {
            _delegate.trace(tweak(msg), t);
            handleThrowable(t);
        }
    }

    @Override public void trace(Marker marker, String msg) {
        if (isTraceEnabled()) {
            _delegate.trace(marker, tweak(msg));
        }
    }

    @Override public void trace(Marker marker, String format, Object arg) {
        if(isTraceEnabled()) {
            _delegate.trace(marker, tweak(MessageFormatter.format(format, arg).getMessage()));
        }
    }

    @Override public void trace(Marker marker, String format, Object arg1, Object arg2) {
        if(isTraceEnabled()) {
            _delegate.trace(marker, tweak(MessageFormatter.format(format, arg1, arg2).getMessage()));
        }
    }

    @Override public void trace(Marker marker, String format, Object... arguments) {
        if(isTraceEnabled()) {
            _delegate.trace(marker, tweak(MessageFormatter.format(format, arguments).getMessage()));
        }
    }

    @Override public void trace(Marker marker, String msg, Throwable t) {
        if (isTraceEnabled()) {
            _delegate.trace(marker, tweak(msg), t);
            handleThrowable(t);
        }
    }

    @Override public boolean isDebugEnabled() {
        return _delegate.isDebugEnabled();
    }

    @Override public boolean isDebugEnabled(Marker marker) {
        return _delegate.isDebugEnabled(marker);
    }

    @Override public void debug(String msg) {
        if(isDebugEnabled()) {
            _delegate.debug(tweak(msg));
        }
    }

    @Override public void debug(String format, Object arg) {
        if(isDebugEnabled()) {
            _delegate.debug(tweak(MessageFormatter.format(format, arg).getMessage()));
        }
    }

    @Override public void debug(String format, Object arg1, Object arg2) {
        if(isDebugEnabled()) {
            _delegate.debug(tweak(MessageFormatter.format(format, arg1, arg2).getMessage()));
        }
    }

    @Override public void debug(String format, Object... arguments) {
        if(isDebugEnabled()) {
            _delegate.debug(tweak(MessageFormatter.format(format, arguments).getMessage()));
        }
    }

    @Override public void debug(String msg, Throwable t) {
        if (isDebugEnabled()) {
            _delegate.debug(tweak(msg), t);
            handleThrowable(t);
        }
    }

    @Override public void debug(Marker marker, String msg) {
        if (isDebugEnabled()) {
            _delegate.debug(marker, tweak(msg));
        }
    }

    @Override public void debug(Marker marker, String format, Object arg) {
        if(isDebugEnabled()) {
            _delegate.debug(marker, tweak(MessageFormatter.format(format, arg).getMessage()));
        }
    }

    @Override public void debug(Marker marker, String format, Object arg1, Object arg2) {
        if(isDebugEnabled()) {
            _delegate.debug(marker, tweak(MessageFormatter.format(format, arg1, arg2).getMessage()));
        }
    }

    @Override public void debug(Marker marker, String format, Object... arguments) {
        if(isDebugEnabled()) {
            _delegate.debug(marker, tweak(MessageFormatter.format(format, arguments).getMessage()));
        }
    }

    @Override public void debug(Marker marker, String msg, Throwable t) {
        if (isDebugEnabled()) {
            _delegate.debug(marker, tweak(msg), t);
            handleThrowable(t);
        }
    }

    @Override public boolean isInfoEnabled() {
        return _delegate.isInfoEnabled();
    }

    @Override public boolean isInfoEnabled(Marker marker) {
        return _delegate.isInfoEnabled(marker);
    }

    @Override public void info(String msg) {
        if(isInfoEnabled()) {
            _delegate.info(tweak(msg));
        }
    }

    @Override public void info(String format, Object arg) {
        if(isInfoEnabled()) {
            _delegate.info(tweak(MessageFormatter.format(format, arg).getMessage()));
        }
    }

    @Override public void info(String format, Object arg1, Object arg2) {
        if(isInfoEnabled()) {
            _delegate.info(tweak(MessageFormatter.format(format, arg1, arg2).getMessage()));
        }
    }

    @Override public void info(String format, Object... arguments) {
        if(isInfoEnabled()) {
            _delegate.info(tweak(MessageFormatter.format(format, arguments).getMessage()));
        }
    }

    @Override public void info(String msg, Throwable t) {
        if (isInfoEnabled()) {
            _delegate.info(tweak(msg), t);
            handleThrowable(t);
        }
    }

    @Override public void info(Marker marker, String msg) {
        if (isInfoEnabled()) {
            _delegate.info(marker, tweak(msg));
        }
    }

    @Override public void info(Marker marker, String format, Object arg) {
        if(isInfoEnabled()) {
            _delegate.info(marker, tweak(MessageFormatter.format(format, arg).getMessage()));
        }
    }

    @Override public void info(Marker marker, String format, Object arg1, Object arg2) {
        if(isInfoEnabled()) {
            _delegate.info(marker, tweak(MessageFormatter.format(format, arg1, arg2).getMessage()));
        }
    }

    @Override public void info(Marker marker, String format, Object... arguments) {
        if(isInfoEnabled()) {
            _delegate.info(marker, tweak(MessageFormatter.format(format, arguments).getMessage()));
        }
    }

    @Override public void info(Marker marker, String msg, Throwable t) {
        if (isInfoEnabled()) {
            _delegate.info(marker, tweak(msg), t);
            handleThrowable(t);
        }
    }

    @Override public boolean isWarnEnabled() {
        return _delegate.isWarnEnabled();
    }

    @Override public boolean isWarnEnabled(Marker marker) {
        return _delegate.isWarnEnabled(marker);
    }

    @Override public void warn(String msg) {
        if(isWarnEnabled()) {
            _delegate.warn(tweak(msg));
        }
    }

    @Override public void warn(String format, Object arg) {
        if(isWarnEnabled()) {
            _delegate.warn(tweak(MessageFormatter.format(format, arg).getMessage()));
        }
    }

    @Override public void warn(String format, Object arg1, Object arg2) {
        if(isWarnEnabled()) {
            _delegate.warn(tweak(MessageFormatter.format(format, arg1, arg2).getMessage()));
        }
    }

    @Override public void warn(String format, Object... arguments) {
        if(isWarnEnabled()) {
            _delegate.warn(tweak(MessageFormatter.format(format, arguments).getMessage()));
        }
    }

    @Override public void warn(String msg, Throwable t) {
        if (isWarnEnabled()) {
            _delegate.warn(tweak(msg), t);
            handleThrowable(t);
        }
    }

    @Override public void warn(Marker marker, String msg) {
        if (isWarnEnabled()) {
            _delegate.warn(marker, tweak(msg));
        }
    }

    @Override public void warn(Marker marker, String format, Object arg) {
        if(isWarnEnabled()) {
            _delegate.warn(marker, tweak(MessageFormatter.format(format, arg).getMessage()));
        }
    }

    @Override public void warn(Marker marker, String format, Object arg1, Object arg2) {
        if(isWarnEnabled()) {
            _delegate.warn(marker, tweak(MessageFormatter.format(format, arg1, arg2).getMessage()));
        }
    }

    @Override public void warn(Marker marker, String format, Object... arguments) {
        if(isWarnEnabled()) {
            _delegate.warn(marker, tweak(MessageFormatter.format(format, arguments).getMessage()));
        }
    }

    @Override public void warn(Marker marker, String msg, Throwable t) {
        if (isWarnEnabled()) {
            _delegate.warn(marker, tweak(msg), t);
            handleThrowable(t);
        }
    }

    @Override public boolean isErrorEnabled() {
        return _delegate.isErrorEnabled();
    }

    @Override public boolean isErrorEnabled(Marker marker) {
        return _delegate.isErrorEnabled(marker);
    }

    @Override public void error(String msg) {
        if(isErrorEnabled()) {
            _delegate.error(tweak(msg));
        }
    }

    @Override public void error(String format, Object arg) {
        if(isErrorEnabled()) {
            _delegate.error(tweak(MessageFormatter.format(format, arg).getMessage()));
        }
    }

    @Override public void error(String format, Object arg1, Object arg2) {
        if(isErrorEnabled()) {
            _delegate.error(tweak(MessageFormatter.format(format, arg1, arg2).getMessage()));
        }
    }

    @Override public void error(String format, Object... arguments) {
        if(isErrorEnabled()) {
            _delegate.error(tweak(MessageFormatter.format(format, arguments).getMessage()));
        }
    }

    @Override public void error(String msg, Throwable t) {
        if (isErrorEnabled()) {
            _delegate.error(tweak(msg), t);
            handleThrowable(t);
        }
    }

    @Override public void error(Marker marker, String msg) {
        if (isErrorEnabled()) {
            _delegate.error(marker, tweak(msg));
        }
    }

    @Override public void error(Marker marker, String format, Object arg) {
        if(isErrorEnabled()) {
            _delegate.error(marker, tweak(MessageFormatter.format(format, arg).getMessage()));
        }
    }

    @Override public void error(Marker marker, String format, Object arg1, Object arg2) {
        if(isErrorEnabled()) {
            _delegate.error(marker, tweak(MessageFormatter.format(format, arg1, arg2).getMessage()));
        }
    }

    @Override public void error(Marker marker, String format, Object... arguments) {
        if(isErrorEnabled()) {
            _delegate.error(marker, tweak(MessageFormatter.format(format, arguments).getMessage()));
        }
    }

    @Override public void error(Marker marker, String msg, Throwable t) {
        if (isErrorEnabled()) {
            _delegate.error(marker, tweak(msg), t);
            handleThrowable(t);
        }
    }

}
