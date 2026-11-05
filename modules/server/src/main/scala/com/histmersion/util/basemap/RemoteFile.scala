package com.histmersion.util.basemap

/** What a range answer says about the file it came from: its whole size (from `Content-Range`) and its `ETag` — a fetch
  * that resumes compares both, so it never mixes two builds' bytes.
  */
final case class RemoteFile(size: RemoteFile.Size, etag: Option[RemoteFile.Etag])

object RemoteFile:
  type Size = Size.T
  object Size extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Etag = Etag.T
  object Etag extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
