package com.histmersion.util.data.exchange

import java.io.OutputStream

/** A blob a bundle carries for a kind's lines: its digest, its size, and where its bytes are read from as it is written
  * into the archive.
  */
final case class BlobOut(sha256: String, size: Long, copy: OutputStream => Unit)
