package com.histmersion.api.catalogue
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given

final case class CatalogProviderInfo(id: CatalogProviderInfo.Id, title: CatalogProviderInfo.Title, licence: CatalogProviderInfo.Licence, citation: CatalogProviderInfo.Citation, home: CatalogProviderInfo.Home)
    derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema

object CatalogProviderInfo:
  type Home = Home.T
  object Home extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
