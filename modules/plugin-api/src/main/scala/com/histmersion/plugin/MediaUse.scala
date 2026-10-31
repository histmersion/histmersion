package com.histmersion.plugin

/** One picture a collection found for one of its targets ([[MediaMatchRule.Own]]): the host ties the item to that
  * target as it is, matching no name.
  *
  * @param target
  *   the place or event it was found for
  * @param item
  *   the picture, its licence as stated; its role is `item.role`, else the collection's
  * @param rank
  *   its place among the target's pictures of that kind (0 = first)
  * @param statement
  *   the statement it was read from, recorded on the link (`P18`, `P180`), when it is one
  * @param wikidata
  *   the Wikidata item the link records, when it is one
  * @param basis
  *   why it is tied to the target, recorded on the link, when no statement says so ("Pleiades place 570491 links it")
  * @param note
  *   what a dry run lists beside it for a person to judge; `None`: it is not listed
  */
final case class MediaUse(
    target: MediaTargetId,
    item: MediaItem,
    rank: Int,
    statement: Option[String] = None,
    wikidata: Option[WikidataId] = None,
    basis: Option[String] = None,
    note: Option[String] = None
)
