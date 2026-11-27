package com.histmersion
package domain.catalogue.places

import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{tolerated, LintRule}

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
  object Id extends Newtype[String, Pure]
  type OnMap = OnMap.T
  object OnMap extends Newtype[Boolean, Pure]
  type ImportClass = ImportClass.T
  object ImportClass extends Newtype[String, Pure]
  type SiteType = SiteType.T
  object SiteType extends Newtype[String, Pure]
  type CheckedAt = CheckedAt.T
  @tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
  object CheckedAt extends Newtype[Long, Pure]
