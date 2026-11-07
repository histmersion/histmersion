package com.histmersion.domain.library.ingest

import com.histmersion.util.storage.{Blob, ByteSource, HandedFile}

/** A document handed to the ingest: its file name, its length and its bytes, read from the start as often as needed —
  * an upload the HTTP layer kept, a file the acquisition spooled, a payload a model sent.
  */
final case class Upload(name: Upload.Name, size: Upload.Size, bytes: ByteSource)

object Upload:
  type Bytes = Bytes.T
  object Bytes extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Array[_root_.scala.Byte], _root_.io.github.iltotore.iron.constraint.any.Pure]
  /** The file the HTTP layer streamed a request body into, where it put it. */
  type InlineName = _root_.com.histmersion.domain.library.ingest.Upload.Name
  val InlineName: _root_.com.histmersion.domain.library.ingest.Upload.Name.type = _root_.com.histmersion.domain.library.ingest.Upload.Name

  type Name = Name.T
  object Name extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Size = Size.T
  object Size extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def handed(file: HandedFile.Snapshot): Upload = Upload(Upload.Name(file.name.value), Upload.Size(file.size.value), file.bytes)

  /** A stored file. */
  def stored(blob: Blob): Upload = Upload(Upload.Name(blob.key.name.value), Upload.Size(blob.size.value), blob.asSource)

  /** Bytes in memory, under `name`. */
  def inline(name: com.histmersion.domain.library.ingest.Upload.InlineName, bytes: com.histmersion.domain.library.ingest.Upload.Bytes): Upload = Upload(name, Upload.Size(bytes.value.length.toLong), ByteSource.bytes(ByteSource.Content(bytes.value)))
