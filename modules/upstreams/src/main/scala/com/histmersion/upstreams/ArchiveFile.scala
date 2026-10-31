package com.histmersion.upstreams

/** One downloadable file of an archive.org item: its name, the format archive.org states, its size in megabytes. */
final case class ArchiveFile(name: String, format: Option[String], megabytes: Double)
