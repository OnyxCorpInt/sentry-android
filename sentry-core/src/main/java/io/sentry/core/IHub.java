package io.sentry.core;

import io.sentry.core.protocol.SentryId;
import io.sentry.core.protocol.User;
import java.util.List;
import org.jetbrains.annotations.Nullable;

public interface IHub {

  boolean isEnabled();

  SentryId captureEvent(SentryEvent event, @Nullable Object hint);

  default SentryId captureEvent(SentryEvent event) {
    return captureEvent(event, null);
  }

  default SentryId captureMessage(String message) {
    return captureMessage(message, SentryLevel.INFO);
  }

  SentryId captureMessage(String message, SentryLevel level);

  SentryId captureException(Throwable throwable, @Nullable Object hint);

  default SentryId captureException(Throwable throwable) {
    return captureException(throwable, null);
  }

  void close();

  void addBreadcrumb(Breadcrumb breadcrumb, @Nullable Object hint);

  default void addBreadcrumb(Breadcrumb breadcrumb) {
    addBreadcrumb(breadcrumb, null);
  }

  void setLevel(SentryLevel level);

  void setTransaction(String transaction);

  void setUser(User user);

  void setFingerprint(List<String> fingerprint);

  void clearBreadcrumbs();

  void setTag(String key, String value);

  void setExtra(String key, String value);

  SentryId getLastEventId();

  void pushScope();

  void popScope();

  void withScope(ScopeCallback callback);

  void configureScope(ScopeCallback callback);

  void bindClient(ISentryClient client);

  void flush(long timeoutMills);

  IHub clone();
}
