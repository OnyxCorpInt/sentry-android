package io.sentry.core;

public final class SentryEnvelopeItem {
  private final SentryEnvelopeItemHeader header;
  // TODO: Can we have a slice or a reader here instead?
  private final byte[] data;

  SentryEnvelopeItem(SentryEnvelopeItemHeader header, byte[] data) {
    this.header = header;
    this.data = data;
  }

  public byte[] getData() {
    return data;
  }

  public SentryEnvelopeItemHeader getHeader() {
    return header;
  }
}
