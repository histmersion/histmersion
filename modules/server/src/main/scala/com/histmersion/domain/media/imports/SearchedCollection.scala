package com.histmersion.domain.media.imports

import com.histmersion.pluginhost.{PluginMedia, Polite}

import com.histmersion.config.MediaCollectionConfig
import com.histmersion.domain.DeepTime

/** An open-access collection (#1446): how to search it for a place, read its records, and tie one to a place
  * ([[MediaMatch]]). The import around it is [[CollectionImport]]; a plugin's is a [[PluginCollection]].
  */
trait SearchedCollection:
  /** The asset provider and the `media_checks` name. */
  def provider: com.histmersion.domain.media.imports.ImportService.Provider

  /** The role its pictures play for a place, unless a record names its own: `object` (a museum's) or `map`. */
  def role: com.histmersion.domain.media.MediaRole.Wire
  def conf: MediaCollectionConfig

  /** The records a search for the place's name answers; one record may be unreadable on its own. */
  def search(fetch: Polite, place: PlaceFacts): Either[com.histmersion.domain.media.imports.SearchedCollection.CollectionKey, List[Either[com.histmersion.domain.media.imports.SearchedCollection.MatchText, MediaRecord]]]

  /** Why a record is tied to the place, or why not. */
  def matches(rec: MediaRecord, place: MediaMatch.PlaceInfo, resolve: MediaMatch.Resolve): Either[com.histmersion.domain.media.imports.SearchedCollection.CollectionKey, com.histmersion.domain.media.imports.SearchedCollection.MatchText] =
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
  ): Either[com.histmersion.domain.media.imports.SearchedCollection.CollectionKey, com.histmersion.domain.media.imports.SearchedCollection.MatchText] = matches(rec, place, resolve)

  /** A record about to be stored, completed by the request a search does not answer (Open Context's media item: the
    * files, and the licence read again); a `Left` refuses it. Only stored records pay for it.
    */
  def complete(fetch: Polite, rec: MediaRecord): Either[com.histmersion.domain.media.imports.SearchedCollection.CollectionKey, MediaRecord] = Right(rec)

object SearchedCollection:
  type CollectionKey = CollectionKey.T
  object CollectionKey extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CollectionName = CollectionName.T
  object CollectionName extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DistanceKm = DistanceKm.T
  object DistanceKm extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LicenceKey = LicenceKey.T
  object LicenceKey extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MatchText = MatchText.T
  object MatchText extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SameSiteKm = SameSiteKm.T
  object SameSiteKm extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  /** Pure: the equivalents of a place among the places a name names — itself, one with its Wikidata item, one within
    * `sameSiteKm` of its point — collapse to its id; others keep theirs.
    */
  type DistanceKmLat1 = DistanceKmLat1.T
  object DistanceKmLat1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DistanceKmLon1 = DistanceKmLon1.T
  object DistanceKmLon1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DistanceKmLat2 = DistanceKmLat2.T
  object DistanceKmLat2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DistanceKmLon2 = DistanceKmLon2.T
  object DistanceKmLon2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def resolveFor(target: PlaceFacts, named: Map[com.histmersion.domain.media.imports.SearchedCollection.CollectionName, List[PlaceFacts]], sameSiteKm: com.histmersion.domain.media.imports.SearchedCollection.SameSiteKm): MediaMatch.Resolve =
    key => named.getOrElse(key, Nil).map(p => if equivalent(target, p, sameSiteKm) then target.id else p.id).toSet

  /** Pure: what a gazetteer id names for `target` — the places on the map carrying it (`byId`), the target's
    * equivalents collapsed to its id, and the target itself when it carries the id (`own`: a place asked by `--place`
    * need not be on the map).
    */
  def resolveIdFor(
      target: PlaceFacts,
      own: Set[(com.histmersion.domain.catalogue.Place.Id, com.histmersion.domain.media.imports.ImportService.ExternalId)],
      byId: Map[(com.histmersion.domain.media.imports.ImportService.Provider, com.histmersion.domain.media.imports.ImportService.ExternalId), List[PlaceFacts]],
      sameSiteKm: com.histmersion.domain.media.imports.SearchedCollection.SameSiteKm
  ): MediaMatch.ResolveId =
    id =>
      byId.getOrElse(id, Nil).map(p => if equivalent(target, p, sameSiteKm) then target.id else p.id).toSet ++
        (if own(id) then Set(target.id) else Set.empty)

  def equivalent(a: PlaceFacts, b: PlaceFacts, km: com.histmersion.domain.media.imports.SearchedCollection.DistanceKm): Boolean =
    a.id == b.id || a.qid.exists(q => b.qid.contains(q)) ||
      (for la <- a.lat; oa <- a.lon; lb <- b.lat; ob <- b.lon yield distanceKm(la, oa, lb, ob) <= km).getOrElse(false)

  /** Pure: great-circle distance in km. */
  def distanceKm(lat1: com.histmersion.domain.media.imports.SearchedCollection.DistanceKmLat1, lon1: com.histmersion.domain.media.imports.SearchedCollection.DistanceKmLon1, lat2: com.histmersion.domain.media.imports.SearchedCollection.DistanceKmLat2, lon2: com.histmersion.domain.media.imports.SearchedCollection.DistanceKmLon2): com.histmersion.domain.media.imports.SearchedCollection.DistanceKm =
    val r = 6371.0
    val (p1, p2) = (math.toRadians(lat1), math.toRadians(lat2))
    val dp = p2 - p1
    val dl = math.toRadians(lon2 - lon1)
    val a = math.pow(math.sin(dp / 2), 2) + math.cos(p1) * math.cos(p2) * math.pow(math.sin(dl / 2), 2)
    2 * r * math.asin(math.sqrt(a.min(1.0)))

  /** Pure: a record's licence key for the counts — its SPDX id, or why it has none. */
  def licenceKey(rec: MediaRecord): com.histmersion.domain.media.imports.SearchedCollection.LicenceKey = rec.rights.fold(_ => com.histmersion.domain.media.imports.SearchedCollection.LicenceKey("not open access"), _.licence.id.value)
