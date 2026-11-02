package com.histmersion.upstreams.archiveorg
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
