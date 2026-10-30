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

object MediaCollection:
  val Object = "object"
  val Map = "map"
  val Image = "image"

  /** The roles a plugin's collection may declare. */
  val Roles: Set[String] = Set(Object, Map, Image)
