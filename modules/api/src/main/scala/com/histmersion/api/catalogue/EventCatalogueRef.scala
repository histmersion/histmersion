package com.histmersion
package api.catalogue
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

final case class EventCatalogueRef(
    provider: EventCatalogueRef.Provider,
    providerId: EventCatalogueRef.ProviderId,
    url: EventCatalogueRef.Url,
    title: EventCatalogueRef.Title,
    licence: EventCatalogueRef.Licence,
    citation: EventCatalogueRef.Citation,
    vei: Option[EventCatalogueRef.Vei] = None,
    magnitude: Option[EventCatalogueRef.Magnitude] = None,
    deaths: Option[EventCatalogueRef.Deaths] = None,
    retrievedAt: Option[EventCatalogueRef.RetrievedAt] = None
) derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema

object EventCatalogueRef:
  export CatalogRecord.Provider
  type ProviderId = ProviderId.T
  object ProviderId extends RefinedType[String, Pure]
  type Url = Url.T
  object Url extends RefinedType[String, Pure]
  type Title = Title.T
  object Title extends RefinedType[String, Pure]
  type Licence = Licence.T
  object Licence extends RefinedType[String, Pure]
  type Citation = Citation.T
  object Citation extends RefinedType[String, Pure]
  type Vei = Vei.T
  object Vei extends RefinedType[Int, Pure]
  type Magnitude = Magnitude.T
  object Magnitude extends RefinedType[Double, Pure]
  type Deaths = Deaths.T
  object Deaths extends RefinedType[Long, Pure]
  type RetrievedAt = RetrievedAt.T
  object RetrievedAt extends RefinedType[String, Pure]
