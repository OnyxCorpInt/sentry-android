package io.sentry.core;

import org.jetbrains.annotations.Nullable;

/** Sentry SDK internal logging interface. */
public interface ILogger {

  /**
   * This method is supposed to be statically imported and provides a shortcut for logging the
   * message only if logger is not null.
   *
   * @param logger the logger or null if none found
   * @param level the log level
   * @param message the message
   * @param args the formatting arguments
   * @see #log(SentryLevel, String, Object...)
   */
  static void logIfNotNull(
      @Nullable ILogger logger, SentryLevel level, String message, Object... args) {
    if (logger != null) {
      logger.log(level, message, args);
    }
  }

  /**
   * This method is supposed to be statically imported and provides a shortcut for logging the
   * message only if logger is not null.
   *
   * @param logger the logger or null if none found
   * @param level the log level
   * @param message the message
   * @param throwable the exception to log
   * @see #log(SentryLevel, String, Throwable)
   */
  static void logIfNotNull(ILogger logger, SentryLevel level, String message, Throwable throwable) {
    if (logger != null) {
      logger.log(level, message, throwable);
    }
  }

  /**
   * This method is supposed to be statically imported and provides a shortcut for logging the
   * message only if logger is not null.
   *
   * @param logger the logger or null if none found
   * @param level the log level
   * @param throwable the exception to log
   * @param message the message
   * @param args the formatting arguments
   * @see #log(SentryLevel, String, Throwable)
   */
  static void logIfNotNull(
      ILogger logger, SentryLevel level, Throwable throwable, String message, Object... args) {
    if (logger != null) {
      logger.log(level, throwable, message, args);
    }
  }

  /**
   * Logs a message with the specified level, message and optional arguments.
   *
   * @param level The SentryLevel.
   * @param message The message.
   * @param args The optional arguments to format the message.
   */
  void log(SentryLevel level, String message, Object... args);

  /**
   * Logs a message with the specified level, message and optional arguments.
   *
   * @param level The SentryLevel.
   * @param message The message.
   * @param throwable The throwable to log.
   */
  void log(SentryLevel level, String message, Throwable throwable);

  /**
   * Logs a message with the specified level, throwable, message and optional arguments.
   *
   * @param level The SentryLevel.
   * @param throwable The throwable to log.
   * @param message The message.
   * @param args the formatting arguments
   */
  void log(SentryLevel level, Throwable throwable, String message, Object... args);
}
