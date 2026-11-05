package com.histmersion.domain.media.imports

import com.histmersion.pluginhost.{PluginMedia, Polite}

import com.histmersion.config.MediaCollectionConfig
import com.histmersion.domain.DeepTime

/** An open-access collection (#1446): how to search it for a place, read its records, and tie one to a place
  * ([[MediaMatch]]). The import around it is [[CollectionImport]]; a plugin's is a [[PluginCollection]].
  */
trait SearchedCollection:
  /** The asset provider and the `media_checks` name. */
  def provider: com.histmersion.domain.media.imports.SearchedCollection.ProviderResult

  /** The role its pictures play for a place, unless a record names its own: `object` (a museum's) or `map`. */
  def role: com.histmersion.domain.media.imports.SearchedCollection.RoleResult
  def conf: MediaCollectionConfig

  /** The records a search for the place's name answers; one record may be unreadable on its own. */
  def search(fetch: Polite, place: PlaceFacts): Either[com.histmersion.domain.media.imports.SearchedCollection.SearchResultPart1, List[Either[com.histmersion.domain.media.imports.SearchedCollection.SearchResultPart2Part1, MediaRecord]]]

  /** Why a record is tied to the place, or why not. */
  def matches(rec: MediaRecord, place: MediaMatch.PlaceInfo, resolve: MediaMatch.Resolve): Either[com.histmersion.domain.media.imports.SearchedCollection.MatchesResultPart1, com.histmersion.domain.media.imports.SearchedCollection.MatchesResultPart2] =
    MediaMatch.museum(rec, place, resolve)

  /** Does a match read the gazetteer ids the places carry ([[matchesWithIds]])? */
  def usesIds: com.histmersion.domain.media.imports.SearchedCollection.UsesIdsResult = false

  /** Why a record is tied to the place, given what its gazetteer ids name on the map — [[matches]] unless the
    * collection's records name places by id.
    */
  def matchesWithIds(
      rec: MediaRecord,
      place: MediaMatch.PlaceInfo,
      resolve: MediaMatch.Resolve,
      resolveId: MediaMatch.ResolveId
  ): Either[com.histmersion.domain.media.imports.SearchedCollection.MatchesWithIdsResultPart1, com.histmersion.domain.media.imports.SearchedCollection.MatchesWithIdsResultPart2] = matches(rec, place, resolve)

  /** A record about to be stored, completed by the request a search does not answer (Open Context's media item: the
    * files, and the licence read again); a `Left` refuses it. Only stored records pay for it.
    */
  def complete(fetch: Polite, rec: MediaRecord): Either[com.histmersion.domain.media.imports.SearchedCollection.CompleteResultPart1, MediaRecord] = Right(rec)

object SearchedCollection:
  /** Pure: the equivalents of a place among the places a name names — itself, one with its Wikidata item, one within
    * `sameSiteKm` of its point — collapse to its id; others keep theirs.
    */
  type ProviderResult = ProviderResult.T
  object ProviderResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RoleResult = RoleResult.T
  object RoleResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SearchResultPart1 = SearchResultPart1.T
  object SearchResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SearchResultPart2Part1 = SearchResultPart2Part1.T
  object SearchResultPart2Part1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MatchesResultPart1 = MatchesResultPart1.T
  object MatchesResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MatchesResultPart2 = MatchesResultPart2.T
  object MatchesResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type UsesIdsResult = UsesIdsResult.T
  object UsesIdsResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MatchesWithIdsResultPart1 = MatchesWithIdsResultPart1.T
  object MatchesWithIdsResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MatchesWithIdsResultPart2 = MatchesWithIdsResultPart2.T
  object MatchesWithIdsResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CompleteResultPart1 = CompleteResultPart1.T
  object CompleteResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ResolveForNamedPart1 = ResolveForNamedPart1.T
  object ResolveForNamedPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ResolveForSameSiteKm = ResolveForSameSiteKm.T
  object ResolveForSameSiteKm extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ResolveIdForOwnPart1 = ResolveIdForOwnPart1.T
  object ResolveIdForOwnPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ResolveIdForOwnPart2 = ResolveIdForOwnPart2.T
  object ResolveIdForOwnPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ResolveIdForByIdPart1Part1 = ResolveIdForByIdPart1Part1.T
  object ResolveIdForByIdPart1Part1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ResolveIdForByIdPart1Part2 = ResolveIdForByIdPart1Part2.T
  object ResolveIdForByIdPart1Part2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ResolveIdForSameSiteKm = ResolveIdForSameSiteKm.T
  object ResolveIdForSameSiteKm extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EquivalentResult = EquivalentResult.T
  object EquivalentResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EquivalentKm = EquivalentKm.T
  object EquivalentKm extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DistanceKmResult = DistanceKmResult.T
  object DistanceKmResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DistanceKmLat1 = DistanceKmLat1.T
  object DistanceKmLat1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DistanceKmLon1 = DistanceKmLon1.T
  object DistanceKmLon1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DistanceKmLat2 = DistanceKmLat2.T
  object DistanceKmLat2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DistanceKmLon2 = DistanceKmLon2.T
  object DistanceKmLon2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LicenceKeyResult = LicenceKeyResult.T
  object LicenceKeyResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def resolveFor(target: PlaceFacts, named: Map[com.histmersion.domain.media.imports.SearchedCollection.ResolveForNamedPart1, List[PlaceFacts]], sameSiteKm: com.histmersion.domain.media.imports.SearchedCollection.ResolveForSameSiteKm): MediaMatch.Resolve =
    key => named.getOrElse(key, Nil).map(p => if equivalent(target, p, sameSiteKm) then target.id else p.id).toSet

  /** Pure: what a gazetteer id names for `target` — the places on the map carrying it (`byId`), the target's
    * equivalents collapsed to its id, and the target itself when it carries the id (`own`: a place asked by `--place`
    * need not be on the map).
    */
  def resolveIdFor(
      target: PlaceFacts,
      own: Set[(com.histmersion.domain.media.imports.SearchedCollection.ResolveIdForOwnPart1, com.histmersion.domain.media.imports.SearchedCollection.ResolveIdForOwnPart2)],
      byId: Map[(com.histmersion.domain.media.imports.SearchedCollection.ResolveIdForByIdPart1Part1, com.histmersion.domain.media.imports.SearchedCollection.ResolveIdForByIdPart1Part2), List[PlaceFacts]],
      sameSiteKm: com.histmersion.domain.media.imports.SearchedCollection.ResolveIdForSameSiteKm
  ): MediaMatch.ResolveId =
    id =>
      byId.getOrElse(id, Nil).map(p => if equivalent(target, p, sameSiteKm) then target.id else p.id).toSet ++
        (if own(id) then Set(target.id) else Set.empty)

  def equivalent(a: PlaceFacts, b: PlaceFacts, km: com.histmersion.domain.media.imports.SearchedCollection.EquivalentKm): com.histmersion.domain.media.imports.SearchedCollection.EquivalentResult =
    a.id == b.id || a.qid.exists(q => b.qid.contains(q)) ||
      (for la <- a.lat; oa <- a.lon; lb <- b.lat; ob <- b.lon yield distanceKm(la, oa, lb, ob) <= km).getOrElse(false)

  /** Pure: great-circle distance in km. */
  def distanceKm(lat1: com.histmersion.domain.media.imports.SearchedCollection.DistanceKmLat1, lon1: com.histmersion.domain.media.imports.SearchedCollection.DistanceKmLon1, lat2: com.histmersion.domain.media.imports.SearchedCollection.DistanceKmLat2, lon2: com.histmersion.domain.media.imports.SearchedCollection.DistanceKmLon2): com.histmersion.domain.media.imports.SearchedCollection.DistanceKmResult =
    val r = 6371.0
    val (p1, p2) = (math.toRadians(lat1), math.toRadians(lat2))
    val dp = p2 - p1
    val dl = math.toRadians(lon2 - lon1)
    val a = math.pow(math.sin(dp / 2), 2) + math.cos(p1) * math.cos(p2) * math.pow(math.sin(dl / 2), 2)
    2 * r * math.asin(math.sqrt(a.min(1.0)))

  /** Pure: a record's licence key for the counts — its SPDX id, or why it has none. */
  def licenceKey(rec: MediaRecord): com.histmersion.domain.media.imports.SearchedCollection.LicenceKeyResult = rec.rights.fold(_ => "not open access", _.licence.id.value)
