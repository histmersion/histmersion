package com.histmersion.plugin

/** Media collections, the second part of the app that plugins extend: an open-access collection of pictures — a
  * museum's objects, a library's maps, an excavation archive's photographs — searched for the places on the map. A
  * plugin adds one or more [[MediaCollection]]s to [[PluginContext.mediaCollections]]; `histmersion media import <id>`
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
  def title: String

  /** One sentence for `histmersion media providers`: what it brings and on what terms. */
  def description: String = ""

  /** The role its pictures play for a place: [[MediaCollection.Object]] (a museum's object), [[MediaCollection.Map]] or
    * [[MediaCollection.Image]] (a photograph of the place itself).
    */
  def role: String

  def matchRule: MediaMatchRule = MediaMatchRule.Museum

  /** Records kept per place at most, in the collection's own relevance order. */
  def perPlace: Int = 3

  /** The least time between two requests to one host, in milliseconds (the provider's own terms). */
  def requestIntervalMs: Int = 1000

  /** The records a search for `place` answers; one record may be unreadable on its own (`Left` inside). `http` is the
    * host's polite client of this import (its pace, the User-Agent, Retry-After; a bot challenge stops the request) —
    * never open a socket of your own.
    */
  def search(place: MediaPlace, http: PluginHttp): Either[String, List[Either[String, MediaItem]]]

  /** An item about to be stored, completed by what a search does not answer (its files, the licence read again from the
    * full record). Only items the host decided to store pay for it; the host judges the completed item's licence again.
    */
  def complete(item: MediaItem, http: PluginHttp): Either[String, MediaItem] = Right(item)

  /** The roles its pictures may play — [[role]] alone, unless its items name others ([[MediaItem.role]]): a Wikidata
    * item's image, locator map and plan are one collection's.
    */
  def roles: Set[String] = Set(role)

  /** What it asks about: places (the default), events with a Wikidata item, or both. A collection that asks about both
    * is asked about the places on the map one per Wikidata item, and the events with one.
    */
  def asks: Set[MediaTarget] = Set(MediaTarget.Places)

  /** The records a search for `event` answers ([[asks]] must name events); none by default. */
  def searchEvent(event: MediaEvent, http: PluginHttp): Either[String, List[Either[String, MediaItem]]] = Right(Nil)

  /** Whether it can be asked now — a key configured, its own switch on — asked before an import: its refusal is the
    * import's ("Europeana images needs a key …"), and nothing is sent.
    */
  def check: Either[String, Unit] = Right(())

  /** How long to wait after a host refuses (HTTP 403) before asking it again, each step in turn, in milliseconds: a
    * provider that rate-limits with refusals. Empty (the default): a refusal is final.
    */
  def refusalBackoffMs: List[Long] = Nil

  /** The last year an object may be made in, for the museum and gazetteer-id rules ([[matchRule]]); `None`: any. */
  def latestYear: Option[Year] = None

  /** Only places that carry an id of this gazetteer (`"pleiades"`: their own `pleiades_…` id or an exact link) are
    * asked about, each with its ids ([[MediaPlace.ids]]); `None` (the default): every place on the map.
    */
  def placesCarrying: Option[String] = None

  /** A collection of its targets' own pictures ([[MediaMatchRule.Own]]) is asked about a batch at once: what it found
    * for each. By default, [[search]] for each place and [[searchEvent]] for each event, in order.
    */
  def searchTargets(places: List[MediaPlace], events: List[MediaEvent], http: PluginHttp): MediaFound =
    val asked = places.map(p => MediaTargetId(MediaTarget.Places, p.id) -> search(p, http)) ++
      events.map(e => MediaTargetId(MediaTarget.Events, e.id) -> searchEvent(e, http))
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
  def completeAll(items: List[MediaItem], http: PluginHttp): MediaCompleted =
    val done = items.map(it => it.key -> complete(it, http))
    MediaCompleted(
      done.collect { case (k, Right(it)) => k -> it }.toMap,
      done.collect { case (k, Left(e)) => s"$k: $e" }
    )

object MediaCollection:
  val Object = "object"
  val Map = "map"
  val Image = "image"
  val LocatorMap = "locator-map"
  val Plan = "plan"
  val AerialView = "aerial-view"
  val RouteMap = "route-map"
  val Depiction = "depiction"

  /** The roles a collection's pictures may play: the ones the host stores. */
  val Roles: Set[String] = Set(Object, Map, Image, LocatorMap, Plan, AerialView, RouteMap, Depiction)
