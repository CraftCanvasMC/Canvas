package space.vectrix.ignite.launch.ember;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.logging.ILogger;
import org.spongepowered.asm.logging.Level;
import org.spongepowered.asm.logging.LoggerAdapterAbstract;
import org.tinylog.Logger;
import org.tinylog.TaggedLogger;

/* package */ final class EmberMixinLogger extends LoggerAdapterAbstract {
  private static final Map<String, ILogger> LOGGERS = new ConcurrentHashMap<>();

  /* package */ static @NotNull ILogger get(final @NotNull String name) {
    return EmberMixinLogger.LOGGERS.computeIfAbsent(name, EmberMixinLogger::new);
  }

  private final TaggedLogger logger;

  /* package */ EmberMixinLogger(final @NotNull String id) {
    super(id);

    this.logger = Logger.tag(id);
  }

  @Override
  public String getType() {
    return "TinyLogger (via Ignite)";
  }

  @Override
  public void catching(final @NotNull Level level, final @NotNull Throwable throwable) {
    switch(level) {
      case WARN: {
        this.logger.warn(throwable);
        break;
      }
      case INFO: {
        this.logger.info(throwable);
        break;
      }
      case DEBUG: {
        this.logger.debug(throwable);
        break;
      }
      case TRACE: {
        this.logger.trace(throwable);
        break;
      }
      default: {
        this.logger.error(throwable);
        break;
      }
    }
  }

  @Override
  public void log(final @NotNull Level level, final @NotNull String message, final @NotNull Object... args) {
    switch(level) {
      case WARN: {
        this.logger.warn(message, args);
        break;
      }
      case INFO: {
        this.logger.info(message, args);
        break;
      }
      case DEBUG: {
        this.logger.debug(message, args);
        break;
      }
      case TRACE: {
        this.logger.trace(message, args);
        break;
      }
      default: {
        this.logger.error(message, args);
        break;
      }
    }
  }

  @Override
  public void log(final @NotNull Level level, final @NotNull String message, final @NotNull Throwable throwable) {
    switch(level) {
      case WARN: {
        this.logger.warn(throwable, message);
        break;
      }
      case INFO: {
        this.logger.info(throwable, message);
        break;
      }
      case DEBUG: {
        this.logger.debug(throwable, message);
        break;
      }
      case TRACE: {
        this.logger.trace(throwable, message);
        break;
      }
      default: {
        this.logger.error(throwable, message);
        break;
      }
    }
  }

  @Override
  public <T extends Throwable> T throwing(final @NotNull T throwable) {
    this.logger.error(throwable);
    return throwable;
  }
}
