package com.histmersion.plugin

/** Media collections, the second part of the app that plugins extend (docs/research-plugins.md §6):
  * an open-access collection of pictures — a museum's objects, a library's maps, an excavation
  * archive's photographs — searched for the places on the map. A plugin adds one or more
  * [[MediaCollection]]s to [[PluginContext.mediaCollections]]; `histmersion media import <id>` then
  * runs it.
  *
  * The plugin only SEARCHES and DESCRIBES: it returns [[MediaItem]]s with the licence AS ITS PROVIDER
  * STATES IT. Everything that decides what is stored is the host's and cannot be changed by a plugin
  * (§11): the licence is read from the statement and judged against the host's allowlist
  * (`histmersion.media.collections`, the user's to widen), an item is tied to a place only by the
  * host's match rules (names compared by the gazetteer's name key, values marked uncertain never used,
  * the dates must overlap the place's, the reason stored on the link), and the host downloads the
  * rendition, makes the thumbnail, stores the credit line and records the place as asked. */

/** A place on the map the host asks a collection about.
  *
  * @param id       the place's id on the map (`pleiades_570491`)
  * @param label    its name, as the map shows it — what a collection searches for
  * @param wikidata its Wikidata item (`Q5788`), when it has one
  * @param lat      its latitude, when it has a point
  * @param lon      its longitude, when it has a point
  * @param period   its period, signed years (BCE negative), inclusive — the place's own, else the span
  *                 of the findings it holds */
final case class MediaPlace(
    id: String,
    label: String,
    wikidata: Option[String] = None,
    lat: Option[Double] = None,
    lon: Option[Double] = None,
    period: Option[(Long, Long)] = None
)

/** What a record says about reuse, as the provider states it — never judged by the plugin.
  *
  * @param licence    the licence as stated: an SPDX-like id (`CC-BY-4.0`, `CC0-1.0`), a short name
  *                   (`CC BY-SA 3.0`, `Public domain`) or a Creative Commons URL. The host classifies it;
  *                   a statement it cannot read is "unknown", which is never stored
  * @param licenceUrl the licence's URL, when the record gives one (read too when [[licence]] is unclear)
  * @param author     the creator to name, when the record names one
  * @param credit     the credit line in the provider's own form (required: shown with every use) */
final case class MediaRights(
    licence: String,
    licenceUrl: Option[String] = None,
    author: Option[String] = None,
    credit: String = ""
)

/** One record of a collection: an object, a map, a photograph.
  *
  * @param key         the provider's own id for it — stable, so a re-import finds it
  * @param title       what the record calls it
  * @param pageUrl     its page at the provider (the credit line's link)
  * @param imageUrl    the rendition the host stores (the provider's own web-size copy, not the original)
  * @param thumbUrl    the provider's small rendition for the card, when it has one (else the host makes one)
  * @param originalUrl the full-size original — recorded, never downloaded
  * @param rights      the licence as stated, or why the record is not reusable (`Left`, e.g. "not open access")
  * @param geography   the record's place fields (`find-spot`, `city`, `country` …) as (field, value),
  *                    MOST SPECIFIC FIRST; values are given as written (the host skips uncertain ones)
  * @param period      the object's date range, signed years, when the record gives one
  * @param ids         gazetteer ids the record gives for where it is from, as (scheme, id):
  *                    `("pleiades", "697725")`, `("geonames", "310858")`, `("wikidata", "Q5788")`
  * @param withheld    why the record must not be shown whatever its licence (a human-remains flag) */
final case class MediaItem(
    key: String,
    title: String,
    pageUrl: String,
    imageUrl: Option[String],
    rights: Either[String, MediaRights],
    thumbUrl: Option[String] = None,
    originalUrl: Option[String] = None,
    geography: List[(String, String)] = Nil,
    period: Option[(Long, Long)] = None,
    ids: List[(String, String)] = Nil,
    withheld: Option[String] = None
)

/** How the host ties an item to a place. The rules themselves are the host's; a collection only
  * chooses which one fits its records. */
enum MediaMatchRule:
  /** A museum object: the most specific place field naming any place on the map decides — it must name
    * this place and no other — and the object's dates must overlap the place's period. */
  case Museum
  /** A map: any of its locations naming this place alone ties it, and it must be dated no later than
    * `maxYear`. */
  case Map(maxYear: Int)
  /** A record that may carry gazetteer ids ([[MediaItem.ids]]): an id naming this place alone ties it
    * (unless one of its fields in `deeper` names another place on the map), else the museum rule;
    * the dates must overlap either way. */
  case PlaceIds(deeper: Set[String] = Set.empty)

/** One collection. Called by the host from one import at a time; keep no per-place state of your own.
  * Failures are values (`Left`); an exception is caught by the host and reported for that place. */
trait MediaCollection:
  /** What the import is called in jobs and lists: "Portal objects". */
  def title: String

  /** One sentence for `histmersion media providers`: what it brings and on what terms. */
  def description: String = ""

  /** The role its pictures play for a place: [[MediaCollection.Object]] (a museum's object),
    * [[MediaCollection.Map]] or [[MediaCollection.Image]] (a photograph of the place itself). */
  def role: String

  def matchRule: MediaMatchRule = MediaMatchRule.Museum

  /** Records kept per place at most, in the collection's own relevance order. */
  def perPlace: Int = 3

  /** The least time between two requests to one host, in milliseconds (the provider's own terms). */
  def requestIntervalMs: Int = 1000

  /** The records a search for `place` answers; one record may be unreadable on its own (`Left` inside).
    * `http` is the host's polite client of this import (its pace, the User-Agent, Retry-After; a bot
    * challenge stops the request) — never open a socket of your own. */
  def search(place: MediaPlace, http: PluginHttp): Either[String, List[Either[String, MediaItem]]]

  /** An item about to be stored, completed by what a search does not answer (its files, the licence
    * read again from the full record). Only items the host decided to store pay for it; the host judges
    * the completed item's licence again. */
  def complete(item: MediaItem, http: PluginHttp): Either[String, MediaItem] = Right(item)

object MediaCollection:
  val Object = "object"
  val Map    = "map"
  val Image  = "image"
  /** The roles a plugin's collection may declare. */
  val Roles: Set[String] = Set(Object, Map, Image)
