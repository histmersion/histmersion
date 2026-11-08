package com.histmersion
package api.catalogue
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.constraint.any.Pure

final case class CatalogProviderInfo(id: CatalogProviderInfo.Id, title: CatalogProviderInfo.Title, licence: CatalogProviderInfo.Licence, citation: CatalogProviderInfo.Citation, home: CatalogProviderInfo.Home)
    derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema

object CatalogProviderInfo:
  export CatalogRecord.{Provider as Id}
  export EventCatalogueRef.{Title, Licence, Citation}
  type Home = Home.T
  object Home extends Newtype[String, Pure]
