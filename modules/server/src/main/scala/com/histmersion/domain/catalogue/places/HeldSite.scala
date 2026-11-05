package com.histmersion.domain.catalogue.places

/** A Wikidata site as the in-memory catalogue keeps it for the refinement of its type: whether it is on the map (a
  * finding or a picture is placed there), the class its import kept, the type refined and when it was asked (epoch ms).
  */
final case class HeldSite(
    id: HeldSite.Id,
    onMap: HeldSite.OnMap,
    importClass: Option[HeldSite.ImportClass],
    siteType: Option[HeldSite.SiteType] = None,
    checkedAt: Option[HeldSite.CheckedAt] = None
)

object HeldSite:
  type Id = Id.T
  object Id extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OnMap = OnMap.T
  object OnMap extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ImportClass = ImportClass.T
  object ImportClass extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SiteType = SiteType.T
  object SiteType extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CheckedAt = CheckedAt.T
  object CheckedAt extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
