package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

/** A catalogue: its key, what to call it, the licence its data comes under and the citation it asks for. The terms were
  * read on 2026-10-14 (see each).
  */
final case class CatalogProviderInfo(id: String, title: String, licence: String, citation: String, home: String)
    derives KindlingsCodecAsObject,
      KindlingsSchema
object CatalogProviderInfo:
  given Schema[CatalogProviderInfo] = summon[KindlingsSchema[CatalogProviderInfo]].schema
