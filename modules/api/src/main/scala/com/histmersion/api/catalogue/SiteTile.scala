package com.histmersion.api.catalogue
import sttp.tapir.*
import sttp.tapir.json.circe.*
import com.histmersion.api.given

final case class SiteTile(
    id: SiteTile.Id,
    label: SiteTile.Label,
    kind: SiteTile.Kind,
    siteType: Option[SiteTile.SiteType],
    basis: SiteTile.Basis,
    periodStart: Option[com.histmersion.api.catalogue.Year],
    periodEnd: Option[com.histmersion.api.catalogue.Year]
)

object SiteTile:
  type Id = Id.T
  object Id extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Label = Label.T
  object Label extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Kind = Kind.T
  object Kind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SiteType = SiteType.T
  object SiteType extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Basis = Basis.T
  object Basis extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
