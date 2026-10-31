package com.histmersion.upstreams

/** An archive.org item: its metadata, whether it is lending-only, and its PDF and EPUB files. */
final case class ArchiveItem(
    identifier: String,
    title: Option[String] = None,
    creator: List[String] = Nil,
    year: Option[Int] = None,
    publisher: Option[String] = None,
    description: Option[String] = None,
    lendingOnly: Boolean = false,
    files: List[ArchiveFile] = Nil
)
