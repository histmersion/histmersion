package com.histmersion
package plugin

import io.github.iltotore.iron.constraint.any.Pure

/** One edge of the place hierarchy, with its own validity: a time-slice of a polity is part of the polity, a city part
  * of a province while it was.
  *
  * @param childId
  *   the place that is part of the other (usually the place that states the relation)
  * @param parentId
  *   the place it is part of; a relation whose parent is not stored once the import is in is left out by the host
  * @param kind
  *   [[GazetteerRelation.PartOf]] (the default), `part_of_admin`, `part_of_regional`, `part_of_physical`, `succeeds`,
  *   `same_as`, `version_of` or `capital_of`
  * @param period
  *   when the relation held; `None`: always
  * @param open
  *   the end of [[period]] the dataset does not know, if one
  */
final case class GazetteerRelation(
    childId: GazetteerRelation.ChildId,
    parentId: GazetteerRelation.ParentId,
    kind: GazetteerRelation.Kind = GazetteerRelation.Kind(GazetteerRelation.PartOf),
    period: Option[Period] = None,
    open: Option[GazetteerOpenSide] = None
)

object GazetteerRelation:
  type ChildId = ChildId.T
  object ChildId extends Newtype[String, Pure]
  type ParentId = ParentId.T
  object ParentId extends Newtype[String, Pure]
  type Kind = Kind.T
  object Kind extends Newtype[String, Pure]

  val PartOf = "part_of"
