package com.histmersion
package domain.media.imports
import com.histmersion.util.problem.Problem

import com.histmersion.plugin.Plugin
import com.histmersion.pluginhost.{PluginMedia, Polite}

import com.histmersion.config.{MediaCollectionConfig, MediaConfig}
import com.histmersion.domain.catalogue.places.ExternalIds
import com.histmersion.domain.DeepTime
import com.histmersion.domain.media.MediaRole
import io.github.iltotore.iron.constraint.any.Pure

/** An open-access collection (#1446): how to search it for a place, read its records, and tie one to a place
  * ([[MediaMatch]]). The import around it is [[CollectionImport]]; a plugin's is a [[PluginCollection]].
  */
trait SearchedCollection:
  /** The asset provider and the `media_checks` name. */
  def provider: ImportService.Provider

  /** The role its pictures play for a place, unless a record names its own: `object` (a museum's) or `map`. */
  def role: MediaRole.Wire
  def conf: MediaCollectionConfig

  /** The records a search for the place's name answers; one record may be unreadable on its own. */
  def search(
      fetch: Polite,
      place: PlaceFacts
  ): Either[Problem, List[Either[Problem, MediaRecord]]]

  /** Why a record is tied to the place, or why not. */
  def matches(
      rec: MediaRecord,
      place: MediaMatch.PlaceInfo,
      resolve: MediaMatch.Resolve
  ): Either[MediaMatch.Unmatched, MediaMatch.Basis] =
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
  ): Either[MediaMatch.Unmatched, MediaMatch.Basis] = matches(rec, place, resolve)

  /** A record about to be stored, completed by the request a search does not answer (Open Context's media item: the
    * files, and the licence read again); a `Left` refuses it. Only stored records pay for it.
    */
  def complete(fetch: Polite, rec: MediaRecord): Either[Problem, MediaRecord] = Right(rec)

object SearchedCollection:
  type DistanceKm = DistanceKm.T
  object DistanceKm extends Newtype[Double, Pure]
  type LicenceKey = LicenceKey.T
  object LicenceKey extends Newtype[String, Pure]

  private def infoId(p: PlaceFacts): MediaMatch.PlaceInfo.Id = MediaMatch.PlaceInfo.Id(p.id.value)

  /** Pure: the equivalents of a place among the places a name names — itself, one with its Wikidata item, one within
    * `sameSiteKm` of its point — collapse to its id; others keep theirs.
    */
  def resolveFor(
      target: PlaceFacts,
      named: Map[MediaMatch.PlaceInfo.Keys, List[PlaceFacts]],
      sameSiteKm: MediaConfig.SameSiteKm
  ): MediaMatch.Resolve =
    key =>
      named.getOrElse(key, Nil).map(p => if equivalent(target, p, sameSiteKm) then infoId(target) else infoId(p)).toSet

  /** Pure: what a gazetteer id names for `target` — the places on the map carrying it (`byId`), the target's
    * equivalents collapsed to its id, and the target itself when it carries the id (`own`: a place asked by `--place`
    * need not be on the map).
    */
  def resolveIdFor(
      target: PlaceFacts,
      own: Set[(ExternalIds.Scheme, ExternalIds.ExternalId)],
      byId: Map[(ExternalIds.Scheme, ExternalIds.ExternalId), List[PlaceFacts]],
      sameSiteKm: MediaConfig.SameSiteKm
  ): MediaMatch.ResolveId =
    id =>
      byId.getOrElse(id, Nil).map(p => if equivalent(target, p, sameSiteKm) then infoId(target) else infoId(p)).toSet ++
        (if own(id) then Set(infoId(target)) else Set.empty)

  def equivalent(a: PlaceFacts, b: PlaceFacts, km: MediaConfig.SameSiteKm): Boolean =
    a.id == b.id || a.qid.exists(q => b.qid.contains(q)) ||
      (for la <- a.lat; oa <- a.lon; lb <- b.lat; ob <- b.lon
      yield distanceKm(la, oa, lb, ob).value <= km.value).getOrElse(false)

  /** Pure: great-circle distance in km. */
  def distanceKm(lat1: Latitude, lon1: Longitude, lat2: Latitude, lon2: Longitude): DistanceKm =
    val r = 6371.0
    val (p1, p2) = (math.toRadians(lat1.value), math.toRadians(lat2.value))
    val dp = p2 - p1
    val dl = math.toRadians(lon2.value - lon1.value)
    val a = math.pow(math.sin(dp / 2), 2) + math.cos(p1) * math.cos(p2) * math.pow(math.sin(dl / 2), 2)
    DistanceKm(2 * r * math.asin(math.sqrt(a.min(1.0))))

  /** Pure: a record's licence key for the counts — its SPDX id, or why it has none. */
  def licenceKey(rec: MediaRecord): LicenceKey =
    rec.rights.fold(_ => LicenceKey("not open access"), r => LicenceKey(r.licence.id.value))
