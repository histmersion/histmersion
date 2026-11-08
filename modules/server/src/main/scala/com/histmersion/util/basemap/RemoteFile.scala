package com.histmersion
package util.basemap

import io.github.iltotore.iron.constraint.any.Pure

/** What a range answer says about the file it came from: its whole size (from `Content-Range`) and its `ETag` — a fetch
  * that resumes compares both, so it never mixes two builds' bytes.
  */
final case class RemoteFile(size: RemoteFile.Size, etag: Option[RemoteFile.Etag])

object RemoteFile:
  type Size = Size.T
  object Size extends Newtype[Long, Pure]
  type Etag = Etag.T
  object Etag extends Newtype[String, Pure]
