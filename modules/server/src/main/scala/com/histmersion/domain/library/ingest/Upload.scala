package com.histmersion.domain.library.ingest

import com.histmersion.util.storage.{Blob, ByteSource, HandedFile}

/** A document handed to the ingest: its file name, its length and its bytes, read from the start as often as needed —
  * an upload the HTTP layer kept, a file the acquisition spooled, a payload a model sent.
  */
final case class Upload(name: String, size: Long, bytes: ByteSource)

object Upload:
  /** The file the HTTP layer streamed a request body into, where it put it. */
  def handed(file: HandedFile): Upload = Upload(file.getName, file.length(), HandedFile.source(file))

  /** A stored file. */
  def stored(blob: Blob): Upload = Upload(blob.key.name, blob.size.value, blob.asSource)

  /** Bytes in memory, under `name`. */
  def inline(name: String, bytes: Array[Byte]): Upload = Upload(name, bytes.length.toLong, ByteSource.bytes(bytes))
