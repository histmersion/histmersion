package com.histmersion.plugin

/** Media collections, the second part of the app that plugins extend: an open-access collection of pictures — a
  * museum's objects, a library's maps, an excavation archive's photographs — searched for the places on the map. A
  * plugin adds one or more [[MediaCollection]]s to [[Plugin.Context.mediaCollections]]; `histmersion media import <id>`
  * then runs it.
  *
  * The plugin only SEARCHES and DESCRIBES: it returns [[MediaItem]]s with the licence AS ITS PROVIDER STATES IT.
  * Everything that decides what is stored is the host's and cannot be changed by a plugin: the licence is read from the
  * statement and judged against the host's allowlist (`histmersion.media.collections`, the user's to widen), an item is
  * tied to a place only by the host's match rules (names compared by the gazetteer's name key, values marked uncertain
  * never used, the dates must overlap the place's, the reason stored on the link), and the host downloads the
  * rendition, makes the thumbnail, stores the credit line and records the place as asked.
  */

/** One collection. Called by the host from one import at a time; keep no per-place state of your own. Failures are
  * values (`Left`); an exception is caught by the host and reported for that place.
  */
trait MediaCollection:
  /** What the import is called in jobs and lists: "Portal objects". */
  def title: com.histmersion.plugin.MediaCollection.TitleResult

  /** One sentence for `histmersion media providers`: what it brings and on what terms. */
  def description: com.histmersion.plugin.MediaCollection.DescriptionResult = _root_.com.histmersion.plugin.MediaCollection.DescriptionResult("")

  /** The role its pictures play for a place: [[MediaCollection.Object]] (a museum's object), [[MediaCollection.Map]] or
    * [[MediaCollection.Image]] (a photograph of the place itself).
    */
  def role: com.histmersion.plugin.MediaCollection.RoleResult

  def matchRule: MediaMatchRule = MediaMatchRule.Museum

  /** Records kept per place at most, in the collection's own relevance order. */
  def perPlace: com.histmersion.plugin.MediaCollection.PerPlaceResult = _root_.com.histmersion.plugin.MediaCollection.PerPlaceResult(3)

  /** The least time between two requests to one host, in milliseconds (the provider's own terms). */
  def requestIntervalMs: com.histmersion.plugin.MediaCollection.RequestIntervalMsResult = _root_.com.histmersion.plugin.MediaCollection.RequestIntervalMsResult(1000)

  /** The records a search for `place` answers; one record may be unreadable on its own (`Left` inside). `http` is the
    * host's polite client of this import (its pace, the User-Agent, Retry-After; a bot challenge stops the request) —
    * never open a socket of your own.
    */
  def search(place: MediaPlace, http: Plugin.Http): Either[com.histmersion.plugin.MediaCollection.SearchResultPart1, List[Either[com.histmersion.plugin.MediaCollection.SearchResultPart2Part1, MediaItem]]]

  /** An item about to be stored, completed by what a search does not answer (its files, the licence read again from the
    * full record). Only items the host decided to store pay for it; the host judges the completed item's licence again.
    */
  def complete(item: MediaItem, http: Plugin.Http): Either[com.histmersion.plugin.MediaCollection.CompleteResultPart1, MediaItem] = Right(item)

  /** The roles its pictures may play — [[role]] alone, unless its items name others ([[MediaItem.role]]): a Wikidata
    * item's image, locator map and plan are one collection's.
    */
  def roles: Set[com.histmersion.plugin.MediaCollection.RolesResult] = Set(role)

  /** What it asks about: places (the default), events with a Wikidata item, or both. A collection that asks about both
    * is asked about the places on the map one per Wikidata item, and the events with one.
    */
  def asks: Set[MediaTarget] = Set(MediaTarget.Places)

  /** The records a search for `event` answers ([[asks]] must name events); none by default. */
  def searchEvent(event: MediaEvent, http: Plugin.Http): Either[com.histmersion.plugin.MediaCollection.SearchEventResultPart1, List[Either[com.histmersion.plugin.MediaCollection.SearchEventResultPart2Part1, MediaItem]]] = Right(Nil)

  /** Whether it can be asked now — a key configured, its own switch on — asked before an import: its refusal is the
    * import's ("Europeana images needs a key …"), and nothing is sent.
    */
  def check: Either[com.histmersion.plugin.MediaCollection.CheckResultPart1, Unit] = Right(())

  /** How long to wait after a host refuses (HTTP 403) before asking it again, each step in turn, in milliseconds: a
    * provider that rate-limits with refusals. Empty (the default): a refusal is final.
    */
  def refusalBackoffMs: List[com.histmersion.plugin.MediaCollection.RefusalBackoffMsResult] = Nil

  /** The last year an object may be made in, for the museum and gazetteer-id rules ([[matchRule]]); `None`: any. */
  def latestYear: Option[Year] = None

  /** Only places that carry an id of this gazetteer (`"pleiades"`: their own `pleiades_…` id or an exact link) are
    * asked about, each with its ids ([[MediaPlace.ids]]); `None` (the default): every place on the map.
    */
  def placesCarrying: Option[com.histmersion.plugin.MediaCollection.PlacesCarryingResult] = None

  /** A collection of its targets' own pictures ([[MediaMatchRule.Own]]) is asked about a batch at once: what it found
    * for each. By default, [[search]] for each place and [[searchEvent]] for each event, in order.
    */
  def searchTargets(places: List[MediaPlace], events: List[MediaEvent], http: Plugin.Http): MediaFound =
    val asked = places.map(p => MediaTarget.Id(MediaTarget.Places, p.id) -> search(p, http)) ++
      events.map(e => MediaTarget.Id(MediaTarget.Events, e.id) -> searchEvent(e, http))
    MediaFound(
      uses = asked.flatMap((t, r) =>
        r.toOption.toList.flatMap(_.collect { case Right(it) => it }.zipWithIndex.map((it, i) => MediaUse(t, it, i)))
      ),
      asked = asked.map(_._1),
      unanswered = asked.collect { case (t, Left(_)) => t }.toSet,
      read = asked.map(_._2.fold(_ => 0, _.size)).sum,
      failed = asked.flatMap((t, r) =>
        r.fold(e => List(s"$title search for ${t.id}: $e"), _.collect { case Left(e) => s"${t.id}: $e" })
      )
    )

  /** The items about to be stored of a collection of its targets' own pictures, completed in one batch: by default
    * [[complete]] for each.
    */
  def completeAll(items: List[MediaItem], http: Plugin.Http): MediaCompleted =
    val done = items.map(it => it.key -> complete(it, http))
    MediaCompleted(
      done.collect { case (k, Right(it)) => k -> it }.toMap,
      done.collect { case (k, Left(e)) => _root_.com.histmersion.plugin.MediaCompleted.Failed(s"$k: $e") }
    )

object MediaCollection:
  type TitleResult = TitleResult.T
  object TitleResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DescriptionResult = DescriptionResult.T
  object DescriptionResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RoleResult = RoleResult.T
  object RoleResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PerPlaceResult = PerPlaceResult.T
  object PerPlaceResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RequestIntervalMsResult = RequestIntervalMsResult.T
  object RequestIntervalMsResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SearchResultPart1 = SearchResultPart1.T
  object SearchResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SearchResultPart2Part1 = SearchResultPart2Part1.T
  object SearchResultPart2Part1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CompleteResultPart1 = CompleteResultPart1.T
  object CompleteResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RolesResult = RolesResult.T
  object RolesResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SearchEventResultPart1 = SearchEventResultPart1.T
  object SearchEventResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SearchEventResultPart2Part1 = SearchEventResultPart2Part1.T
  object SearchEventResultPart2Part1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CheckResultPart1 = CheckResultPart1.T
  object CheckResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RefusalBackoffMsResult = RefusalBackoffMsResult.T
  object RefusalBackoffMsResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PlacesCarryingResult = PlacesCarryingResult.T
  object PlacesCarryingResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Roles = Roles.T
  object Roles extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  val Object = "object"
  val Map = "map"
  val Image = "image"
  val LocatorMap = "locator-map"
  val Plan = "plan"
  val AerialView = "aerial-view"
  val RouteMap = "route-map"
  val Depiction = "depiction"

  /** The roles a collection's pictures may play: the ones the host stores. */
  val Roles: Set[com.histmersion.plugin.MediaCollection.Roles] = Set(_root_.com.histmersion.plugin.MediaCollection.Roles(Object), _root_.com.histmersion.plugin.MediaCollection.Roles(Map), _root_.com.histmersion.plugin.MediaCollection.Roles(Image), _root_.com.histmersion.plugin.MediaCollection.Roles(LocatorMap), _root_.com.histmersion.plugin.MediaCollection.Roles(Plan), _root_.com.histmersion.plugin.MediaCollection.Roles(AerialView), _root_.com.histmersion.plugin.MediaCollection.Roles(RouteMap), _root_.com.histmersion.plugin.MediaCollection.Roles(Depiction))
