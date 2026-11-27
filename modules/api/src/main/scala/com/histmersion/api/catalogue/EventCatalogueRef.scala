package com.histmersion
package api.catalogue
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{tolerated, LintRule}

@tolerated(LintRule.Prefix, "pending G9 (namespaces, not prefixes)")
@tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
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
) derives JsonCodec,
      KindlingsSchema,
      api.json.Schema

object EventCatalogueRef:
  export CatalogRecord.Provider
  type ProviderId = ProviderId.T
  object ProviderId extends Newtype[String, Pure]
  type Url = Url.T
  object Url extends Newtype[String, Pure]
  type Title = Title.T
  object Title extends Newtype[String, Pure]
  type Licence = Licence.T
  object Licence extends Newtype[String, Pure]
  type Citation = Citation.T
  object Citation extends Newtype[String, Pure]
  type Vei = Vei.T
  object Vei extends Newtype[Int, Pure]
  type Magnitude = Magnitude.T
  object Magnitude extends Newtype[Double, Pure]
  type Deaths = Deaths.T
  object Deaths extends Newtype[Long, Pure]
  type RetrievedAt = RetrievedAt.T
  object RetrievedAt extends Newtype[String, Pure]
