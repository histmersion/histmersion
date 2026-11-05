package com.histmersion.domain.media.imports

import com.histmersion.pluginhost.{PluginMedia, Polite}

import com.histmersion.config.MediaCollectionConfig
import com.histmersion.domain.DeepTime

/** An open-access collection (#1446): how to search it for a place, read its records, and tie one to a place
  * ([[MediaMatch]]). The import around it is [[CollectionImport]]; a plugin's is a [[PluginCollection]].
  */
trait SearchedCollection:
  /** The asset provider and the `media_checks` name. */
  def provider: String

  /** The role its pictures play for a place, unless a record names its own: `object` (a museum's) or `map`. */
  def role: String
  def conf: MediaCollectionConfig

  /** The records a search for the place's name answers; one record may be unreadable on its own. */
  def search(fetch: Polite, place: PlaceFacts): Either[String, List[Either[String, MediaRecord]]]

  /** Why a record is tied to the place, or why not. */
  def matches(rec: MediaRecord, place: MediaMatch.PlaceInfo, resolve: MediaMatch.Resolve): Either[String, String] =
    MediaMatch.museum(rec, place, resolve)

  /** Does a match read the gazetteer ids the places carry ([[matchesWithIds]])? */
  def usesIds: Boolean = false

  /** Why a record is tied to the place, given what its gazetteer ids name on the map — [[matches]] unless the
    * collection's records name places by id.
    */
  def matchesWithIds(
      rec: MediaRecord,
      place: MediaMatch.PlaceInfo,
      resolve: MediaMatch.Resolve,
      resolveId: MediaMatch.ResolveId
  ): Either[String, String] = matches(rec, place, resolve)

  /** A record about to be stored, completed by the request a search does not answer (Open Context's media item: the
    * files, and the licence read again); a `Left` refuses it. Only stored records pay for it.
    */
  def complete(fetch: Polite, rec: MediaRecord): Either[String, MediaRecord] = Right(rec)

object SearchedCollection:
  /** Pure: the equivalents of a place among the places a name names — itself, one with its Wikidata item, one within
    * `sameSiteKm` of its point — collapse to its id; others keep theirs.
    */
  def resolveFor(target: PlaceFacts, named: Map[String, List[PlaceFacts]], sameSiteKm: Double): MediaMatch.Resolve =
    key => named.getOrElse(key, Nil).map(p => if equivalent(target, p, sameSiteKm) then target.id else p.id).toSet

  /** Pure: what a gazetteer id names for `target` — the places on the map carrying it (`byId`), the target's
    * equivalents collapsed to its id, and the target itself when it carries the id (`own`: a place asked by `--place`
    * need not be on the map).
    */
  def resolveIdFor(
      target: PlaceFacts,
      own: Set[(String, String)],
      byId: Map[(String, String), List[PlaceFacts]],
      sameSiteKm: Double
  ): MediaMatch.ResolveId =
    id =>
      byId.getOrElse(id, Nil).map(p => if equivalent(target, p, sameSiteKm) then target.id else p.id).toSet ++
        (if own(id) then Set(target.id) else Set.empty)

  def equivalent(a: PlaceFacts, b: PlaceFacts, km: Double): Boolean =
    a.id == b.id || a.qid.exists(q => b.qid.contains(q)) ||
      (for la <- a.lat; oa <- a.lon; lb <- b.lat; ob <- b.lon yield distanceKm(la, oa, lb, ob) <= km).getOrElse(false)

  /** Pure: great-circle distance in km. */
  def distanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double =
    val r = 6371.0
    val (p1, p2) = (math.toRadians(lat1), math.toRadians(lat2))
    val dp = p2 - p1
    val dl = math.toRadians(lon2 - lon1)
    val a = math.pow(math.sin(dp / 2), 2) + math.cos(p1) * math.cos(p2) * math.pow(math.sin(dl / 2), 2)
    2 * r * math.asin(math.sqrt(a.min(1.0)))

  /** Pure: a record's licence key for the counts — its SPDX id, or why it has none. */
  def licenceKey(rec: MediaRecord): String = rec.rights.fold(_ => "not open access", _.licence.id.value)
