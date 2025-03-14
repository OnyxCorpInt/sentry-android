package io.sentry.core.util;

public final class Objects {
  private Objects() {}

  public static <T> T requireNonNull(T obj, String message) {
    if (obj == null) throw new IllegalArgumentException(message);
    return obj;
  }
}
