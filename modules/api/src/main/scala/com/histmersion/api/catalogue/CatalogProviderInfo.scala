package com.histmersion
package api.catalogue
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{LintRule, tolerated}

@tolerated(LintRule.Direction, "pending G12 (a domain names only lower places)")
final case class CatalogProviderInfo(
    id: CatalogProviderInfo.Id,
    title: CatalogProviderInfo.Title,
    licence: CatalogProviderInfo.Licence,
    citation: CatalogProviderInfo.Citation,
    home: CatalogProviderInfo.Home
) derives JsonCodec,
      KindlingsSchema,
      api.json.Schema

object CatalogProviderInfo:
  export CatalogRecord.Provider as Id
  export EventCatalogueRef.{Citation, Licence, Title}
  type Home = Home.T
  object Home extends Newtype[String, Pure]
