package com.histmersion
package util.data.exchange

import java.io.InputStream

/** Where an import puts the bytes of one blob use. */
enum BlobSink:
  /** Into the database: the bytes are held in memory and checked against their digest first. */
  case Held(write: Array[Byte] => Unit)

  /** Into a file store, streamed; it checks the digest itself. The answer is what went wrong, when anything did. */
  case Streamed(store: InputStream => Option[ImportAbort.Problem])
