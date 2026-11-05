package com.histmersion.domain.library.ingest

import com.histmersion.util.storage.{Blob, ByteSource, HandedFile}

/** A document handed to the ingest: its file name, its length and its bytes, read from the start as often as needed —
  * an upload the HTTP layer kept, a file the acquisition spooled, a payload a model sent.
  */
final case class Upload(name: Upload.Name, size: Upload.Size, bytes: ByteSource)

object Upload:
  /** The file the HTTP layer streamed a request body into, where it put it. */
  type InlineName = InlineName.T
  object InlineName extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type InlineBytes = InlineBytes.T
  object InlineBytes extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Byte, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type Name = Name.T
  object Name extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Size = Size.T
  object Size extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def handed(file: HandedFile.Snapshot): Upload = Upload(file.name, file.size, file.bytes)

  /** A stored file. */
  def stored(blob: Blob): Upload = Upload(blob.key.name, blob.size.value, blob.asSource)

  /** Bytes in memory, under `name`. */
  def inline(name: com.histmersion.domain.library.ingest.Upload.InlineName, bytes: Array[com.histmersion.domain.library.ingest.Upload.InlineBytes]): Upload = Upload(name, bytes.length.toLong, ByteSource.bytes(bytes))
