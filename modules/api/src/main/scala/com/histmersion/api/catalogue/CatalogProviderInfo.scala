package com.histmersion.api.catalogue
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given

final case class CatalogProviderInfo(id: CatalogProviderInfo.Id, title: CatalogProviderInfo.Title, licence: CatalogProviderInfo.Licence, citation: CatalogProviderInfo.Citation, home: CatalogProviderInfo.Home)
    derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema

object CatalogProviderInfo:
  type Id = com.histmersion.api.catalogue.CatalogRecord.Provider
  val Id: _root_.com.histmersion.api.catalogue.CatalogRecord.Provider.type = _root_.com.histmersion.api.catalogue.CatalogRecord.Provider
  type Title = com.histmersion.api.catalogue.EventCatalogueRef.Title
  val Title: _root_.com.histmersion.api.catalogue.EventCatalogueRef.Title.type = _root_.com.histmersion.api.catalogue.EventCatalogueRef.Title
  type Licence = com.histmersion.api.catalogue.EventCatalogueRef.Licence
  val Licence: _root_.com.histmersion.api.catalogue.EventCatalogueRef.Licence.type = _root_.com.histmersion.api.catalogue.EventCatalogueRef.Licence
  type Citation = com.histmersion.api.catalogue.EventCatalogueRef.Citation
  val Citation: _root_.com.histmersion.api.catalogue.EventCatalogueRef.Citation.type = _root_.com.histmersion.api.catalogue.EventCatalogueRef.Citation
  type Home = Home.T
  object Home extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
