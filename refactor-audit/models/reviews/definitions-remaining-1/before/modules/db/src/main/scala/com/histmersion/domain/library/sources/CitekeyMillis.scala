package com.histmersion.domain.library.sources

import com.augustnagro.magnum.*

/** A source's last write, in epoch milliseconds. */
final private[sources] case class CitekeyMillis(citekey: String, millis: Long) derives DbCodec
