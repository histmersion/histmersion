package com.histmersion
package domain.library.ingest

import com.histmersion.util.storage.{Blob, ByteSource, HandedFile}
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{tolerated, LintRule}
import com.histmersion.domain.library.acquisition.Spool
import com.histmersion.util.storage.HandedFile

/** A document handed to the ingest: its file name, its length and its bytes, read from the start as often as needed —
  * an upload the HTTP layer kept, a file the acquisition spooled, a payload a model sent.
  */
final case class Upload(name: Upload.Name, size: Upload.Size, bytes: ByteSource)

object Upload:
  export Spool.Bytes

  /** The file the HTTP layer streamed a request body into, where it put it. */
  @tolerated(LintRule.G46, "pending G46 (re-export, don't alias)")
  type InlineName = Name
  val InlineName: Name.type = Name
  export HandedFile.Snapshot.Name
  export HandedFile.Snapshot.Size

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def handed(file: HandedFile.Snapshot): Upload = Upload(Name(file.name.value), Size(file.size.value), file.bytes)

  /** A stored file. */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def stored(blob: Blob): Upload = Upload(Name(blob.key.name.value), Size(blob.size.value), blob.asSource)

  /** Bytes in memory, under `name`. */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def inline(name: InlineName, bytes: Bytes): Upload =
    Upload(name, Size(bytes.value.length.toLong), ByteSource.bytes(ByteSource.Content(bytes.value)))
