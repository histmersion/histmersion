package com.histmersion.plugin

/** How the host ties an item to a place. The rules themselves are the host's; a collection only chooses which one fits
  * its records.
  */
enum MediaMatchRule:
  /** A museum object: the most specific place field naming any place on the map decides — it must name this place and
    * no other — and the object's dates must overlap the place's period.
    */
  case Museum

  /** A map: any of its locations naming this place alone ties it, and it must be dated no later than `maxYear`.
    */
  case Map(maxYear: Year)

  /** A record that may carry gazetteer ids ([[MediaItem.ids]]): an id naming this place alone ties it (unless one of
    * its fields in `deeper` names another place on the map), else the museum rule; the dates must overlap either way.
    */
  case PlaceIds(deeper: Set[PlaceIds.Deeper] = Set.empty)

  /** The target's own pictures: the collection finds them FOR the place or event it is asked about (a Wikidata item's
    * image statements, the files a gazetteer record links), so no name is matched — the host ties each to the target
    * the collection found it for ([[MediaUse]]). A collection with this rule is asked about a batch of targets at once
    * ([[MediaCollection.searchTargets]]), and the host completes its items in one batch
    * ([[MediaCollection.completeAll]]).
    */
  case Own

object MediaMatchRule:
  object PlaceIds:
    type Deeper = Deeper.T
    object Deeper extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
