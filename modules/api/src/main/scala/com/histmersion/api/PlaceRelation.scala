package com.histmersion.api

/** A hierarchy edge with its OWN validity — "Hatti part_of Anatolia" holds only for its `period`. */
final case class PlaceRelation(
    childId: String,
    parentId: String,
    kind: String = PlaceRelation.PartOf,
    period: Option[Period] = None,
    open: Option[OpenSide] = None
):
  /** The inclusive bounds stored for it ([[OpenSide.bounds]]). */
  def bounds: (Option[Long], Option[Long]) = OpenSide.bounds(period, open)

object PlaceRelation:
  val PartOf = "part_of"
  val PartOfAdmin = "part_of_admin"
  val PartOfRegional = "part_of_regional"
  val PartOfPhysical = "part_of_physical"
  val Succeeds = "succeeds"
  val SameAs = "same_as"
  val VersionOf = "version_of"
  val CapitalOf = "capital_of"
  val all: List[String] =
    List(PartOf, PartOfAdmin, PartOfRegional, PartOfPhysical, Succeeds, SameAs, VersionOf, CapitalOf)
  val partOfKinds: Set[String] = Set(PartOf, PartOfAdmin, PartOfRegional, PartOfPhysical)
