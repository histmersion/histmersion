package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

/** @param lendingOnly
  *   the item is access-restricted: it must be BORROWED on archive.org and is never downloaded here (no DRM
  *   circumvention — the Python tool stopped at exactly this point and so do we).
  */
final case class ArchiveItem(
    identifier: String,
    title: Option[String] = None,
    creator: List[String] = Nil,
    year: Option[Int] = None,
    publisher: Option[String] = None,
    description: Option[String] = None,
    lendingOnly: Boolean = false,
    files: List[ArchiveFile] = Nil
) derives KindlingsCodecAsObject,
      KindlingsSchema
object ArchiveItem:
  given Schema[ArchiveItem] = summon[KindlingsSchema[ArchiveItem]].schema
