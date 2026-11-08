package com.histmersion
package api.catalogue
import sttp.tapir.*
import sttp.tapir.json.circe.*
import com.histmersion.api.given
import io.github.iltotore.iron.constraint.any.Pure

final case class SiteTile(
    id: SiteTile.Id,
    label: SiteTile.Label,
    kind: SiteTile.Kind,
    siteType: Option[SiteTile.SiteType],
    basis: SiteTile.Basis,
    periodStart: Option[Year],
    periodEnd: Option[Year]
)

object SiteTile:
  type Id = Id.T
  object Id extends Newtype[String, Pure]
  type Label = Label.T
  object Label extends Newtype[String, Pure]
  type Kind = Kind.T
  object Kind extends Newtype[String, Pure]
  type SiteType = SiteType.T
  object SiteType extends Newtype[String, Pure]
  type Basis = Basis.T
  object Basis extends Newtype[String, Pure]
