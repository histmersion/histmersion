package com.histmersion.api.catalogue
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given

final case class CatalogProviderInfo(id: String, title: String, licence: String, citation: String, home: String)
    derives KindlingsCodecAsObject,
      KindlingsSchema
object CatalogProviderInfo:
  given Schema[CatalogProviderInfo] = summon[KindlingsSchema[CatalogProviderInfo]].schema
