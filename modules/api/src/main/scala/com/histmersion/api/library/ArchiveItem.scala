package com.histmersion.api.library
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given

/** @param lendingOnly
  *   the item must be borrowed on archive.org; it is never downloaded (no DRM circumvention)
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
