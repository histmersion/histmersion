package com.histmersion.util.data.exchange

import java.io.OutputStream

/** A blob a bundle carries for a kind's lines: its digest, its size, and where its bytes are read from as it is written
  * into the archive.
  */
final case class BlobOut(sha256: BlobOut.Sha256, size: BlobOut.Size, copy: OutputStream => Unit)

object BlobOut:
  type Sha256 = com.histmersion.Sha256
  val Sha256: _root_.com.histmersion.Sha256.type = _root_.com.histmersion.Sha256
  type Size = com.histmersion.ByteCount
  val Size: _root_.com.histmersion.ByteCount.type = _root_.com.histmersion.ByteCount
