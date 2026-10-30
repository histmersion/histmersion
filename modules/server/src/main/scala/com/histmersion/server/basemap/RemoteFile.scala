package com.histmersion.server.basemap

/** What a range answer says about the file it came from: its whole size (from `Content-Range`) and its `ETag` — a fetch
  * that resumes compares both, so it never mixes two builds' bytes.
  */
final case class RemoteFile(size: Long, etag: Option[String])
