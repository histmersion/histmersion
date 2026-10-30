package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

/** Where the PDF came from. `source` is `unpaywall|core|openalex|europepmc|pmc|arxiv|zenodo|repository|archive_org`, or
  * the channel a fetcher plugin names.
  */
final case class PdfInfo(source: String, url: Option[String], bytes: Long, spoolPath: Option[String])
    derives KindlingsCodecAsObject,
      KindlingsSchema
object PdfInfo:
  given Schema[PdfInfo] = summon[KindlingsSchema[PdfInfo]].schema
