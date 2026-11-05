package com.histmersion.domain.media.imports

import com.histmersion.domain.catalogue.places.ExternalIds
import com.histmersion.domain.catalogue.places.Wikidata
import com.histmersion.util.database.Sql

import com.histmersion.util.database.given
import com.augustnagro.magnum.*

/** What the media imports ask about and what they recorded asking (#1446, V082): the places on the map and the events
  * not asked yet by a provider, the facts a collection's match reads of a place, and `media_checks`. Every method runs
  * on the connection it is given.
  */
private[imports] object ImportRepo:

  type PlaceQidResult = PlaceQidResult.T
  object PlaceQidResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PlaceQidId = PlaceQidId.T
  object PlaceQidId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PlaceCandidatesResult = PlaceCandidatesResult.T
  object PlaceCandidatesResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PlaceCandidatesProvider = PlaceCandidatesProvider.T
  object PlaceCandidatesProvider extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PlaceCandidatesSince = PlaceCandidatesSince.T
  object PlaceCandidatesSince extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PlaceCandidatesEveryOne = PlaceCandidatesEveryOne.T
  object PlaceCandidatesEveryOne extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LitResult = LitResult.T
  object LitResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LitS = LitS.T
  object LitS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EventCandidatesResult = EventCandidatesResult.T
  object EventCandidatesResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EventCandidatesProvider = EventCandidatesProvider.T
  object EventCandidatesProvider extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EventCandidatesSince = EventCandidatesSince.T
  object EventCandidatesSince extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EventCandidatesEveryOne = EventCandidatesEveryOne.T
  object EventCandidatesEveryOne extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TargetsResultPart1 = TargetsResultPart1.T
  object TargetsResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TargetsProvider = TargetsProvider.T
  object TargetsProvider extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TargetsPlace = TargetsPlace.T
  object TargetsPlace extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TargetsEvent = TargetsEvent.T
  object TargetsEvent extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TargetsLimit = TargetsLimit.T
  object TargetsLimit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TargetsRefresh = TargetsRefresh.T
  object TargetsRefresh extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TargetsSince = TargetsSince.T
  object TargetsSince extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PendingResult = PendingResult.T
  object PendingResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PendingProvider = PendingProvider.T
  object PendingProvider extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PendingSince = PendingSince.T
  object PendingSince extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MapPlacesResult = MapPlacesResult.T
  object MapPlacesResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MapPlacesCheck = MapPlacesCheck.T
  object MapPlacesCheck extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MapPlacesSince = MapPlacesSince.T
  object MapPlacesSince extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MapPlacesEveryOne = MapPlacesEveryOne.T
  object MapPlacesEveryOne extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MapPlacesPleiadesOnly = MapPlacesPleiadesOnly.T
  object MapPlacesPleiadesOnly extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CollectionTargetsResultPart1 = CollectionTargetsResultPart1.T
  object CollectionTargetsResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CollectionTargetsCheck = CollectionTargetsCheck.T
  object CollectionTargetsCheck extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CollectionTargetsPlaces = CollectionTargetsPlaces.T
  object CollectionTargetsPlaces extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CollectionTargetsPlace = CollectionTargetsPlace.T
  object CollectionTargetsPlace extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CollectionTargetsEvent = CollectionTargetsEvent.T
  object CollectionTargetsEvent extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CollectionTargetsLimit = CollectionTargetsLimit.T
  object CollectionTargetsLimit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CollectionTargetsRefresh = CollectionTargetsRefresh.T
  object CollectionTargetsRefresh extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CollectionTargetsSince = CollectionTargetsSince.T
  object CollectionTargetsSince extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CollectionTargetsPleiadesOnly = CollectionTargetsPleiadesOnly.T
  object CollectionTargetsPleiadesOnly extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CollectionPendingResult = CollectionPendingResult.T
  object CollectionPendingResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CollectionPendingCheck = CollectionPendingCheck.T
  object CollectionPendingCheck extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CollectionPendingPlaces = CollectionPendingPlaces.T
  object CollectionPendingPlaces extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CollectionPendingSince = CollectionPendingSince.T
  object CollectionPendingSince extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CollectionPendingPleiadesOnly = CollectionPendingPleiadesOnly.T
  object CollectionPendingPleiadesOnly extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PlaceFactsResultPart1 = PlaceFactsResultPart1.T
  object PlaceFactsResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PlaceFactsIds = PlaceFactsIds.T
  object PlaceFactsIds extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PleiadesIdsResultPart1 = PleiadesIdsResultPart1.T
  object PleiadesIdsResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PleiadesIdsResultPart2 = PleiadesIdsResultPart2.T
  object PleiadesIdsResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PleiadesIdsIds = PleiadesIdsIds.T
  object PleiadesIdsIds extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PleiadesIdsInResultPart1 = PleiadesIdsInResultPart1.T
  object PleiadesIdsInResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PleiadesIdsInResultPart2 = PleiadesIdsInResultPart2.T
  object PleiadesIdsInResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PleiadesIdsInIds = PleiadesIdsInIds.T
  object PleiadesIdsInIds extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PlaceExternalIdsResultPart1 = PlaceExternalIdsResultPart1.T
  object PlaceExternalIdsResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PlaceExternalIdsResultPart2Part1 = PlaceExternalIdsResultPart2Part1.T
  object PlaceExternalIdsResultPart2Part1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PlaceExternalIdsResultPart2Part2 = PlaceExternalIdsResultPart2Part2.T
  object PlaceExternalIdsResultPart2Part2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PlaceExternalIdsIds = PlaceExternalIdsIds.T
  object PlaceExternalIdsIds extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MapPlacesWithIdsResultPart1Part1 = MapPlacesWithIdsResultPart1Part1.T
  object MapPlacesWithIdsResultPart1Part1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MapPlacesWithIdsResultPart1Part2 = MapPlacesWithIdsResultPart1Part2.T
  object MapPlacesWithIdsResultPart1Part2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MapPlacesWithIdsIdsPart1 = MapPlacesWithIdsIdsPart1.T
  object MapPlacesWithIdsIdsPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MapPlacesWithIdsIdsPart2 = MapPlacesWithIdsIdsPart2.T
  object MapPlacesWithIdsIdsPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MapPlacesNamedResultPart1 = MapPlacesNamedResultPart1.T
  object MapPlacesNamedResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MapPlacesNamedKeys = MapPlacesNamedKeys.T
  object MapPlacesNamedKeys extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CommonsCategoryResult = CommonsCategoryResult.T
  object CommonsCategoryResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CommonsCategoryCategory = CommonsCategoryCategory.T
  object CommonsCategoryCategory extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CommonsCategoryQid = CommonsCategoryQid.T
  object CommonsCategoryQid extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CheckedProvider = CheckedProvider.T
  object CheckedProvider extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CheckedTargetType = CheckedTargetType.T
  object CheckedTargetType extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CheckedTargetId = CheckedTargetId.T
  object CheckedTargetId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CheckedFound = CheckedFound.T
  object CheckedFound extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CheckedQid = CheckedQid.T
  object CheckedQid extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  val Place = "place"
  val Event = "event"

  // ── which places and events to ask ───────────────────────────────────────────────────────────

  private val QidRe = "^Q[1-9][0-9]*$"

  /** One place's Wikidata item: an exact link first, else any; a `wikidata_q…` id names its own. */
  private def placeQid(id: com.histmersion.domain.media.imports.ImportRepo.PlaceQidId)(using DbCon): Option[com.histmersion.domain.media.imports.ImportRepo.PlaceQidResult] =
    sql"""SELECT external_id FROM place_links
          WHERE place_id = $id AND scheme = 'wikidata' AND external_id ~ $QidRe
          ORDER BY (relation = 'exact_match') DESC, external_id LIMIT 1"""
      .query[String]
      .run()
      .headOption
      .orElse(Wikidata.qidOfPlaceId(id))

  /** The places an import takes, best first: the places that hold findings (what the map shows), ONE per Wikidata item
    * — the place most sources cite stands for every place with that item, and the others reach its pictures through the
    * item — sites and features before regions and polities, then by sources citing, then by findings. Not asked yet by
    * this provider (neither that place nor that item), or — `since` set, a refresh — not since then; `everyOne` (a
    * refresh with no start) leaves none out.
    */
  private def placeCandidates(provider: com.histmersion.domain.media.imports.ImportRepo.PlaceCandidatesProvider, since: Option[com.histmersion.domain.media.imports.ImportRepo.PlaceCandidatesSince], everyOne: com.histmersion.domain.media.imports.ImportRepo.PlaceCandidatesEveryOne = false): com.histmersion.domain.media.imports.ImportRepo.PlaceCandidatesResult =
    s"""WITH used AS (
          SELECT fp.place_id, count(*) AS n, count(DISTINCT f.citekey) AS s
          FROM finding_places fp JOIN findings f ON f.id = fp.finding_id GROUP BY fp.place_id),
        cand AS (
          SELECT DISTINCT ON (l.qid) l.qid, u.place_id, p.label, u.n, u.s, p.kind
          FROM used u
          JOIN places p ON p.id = u.place_id
          CROSS JOIN LATERAL (
            SELECT pl.external_id AS qid FROM place_links pl
            WHERE pl.place_id = u.place_id AND pl.scheme = 'wikidata' AND pl.external_id ~ '$QidRe'
            ORDER BY (pl.relation = 'exact_match') DESC, pl.external_id LIMIT 1) l
          ORDER BY l.qid, u.s DESC, u.n DESC, u.place_id)
        SELECT c.place_id, c.qid, c.label, c.kind, c.s, c.n FROM cand c
        WHERE $everyOne OR NOT EXISTS (SELECT 1 FROM media_checks k WHERE k.provider = ${lit(provider)}
                AND ((k.target_type = 'place' AND k.target_id = c.place_id) OR k.qid = c.qid)
                ${since.fold("")(ms => s"AND k.checked_at >= to_timestamp($ms::double precision / 1000)")})"""

  private def lit(s: com.histmersion.domain.media.imports.ImportRepo.LitS): com.histmersion.domain.media.imports.ImportRepo.LitResult = "'" + s.replace("'", "''") + "'"

  private def eventCandidates(provider: com.histmersion.domain.media.imports.ImportRepo.EventCandidatesProvider, since: Option[com.histmersion.domain.media.imports.ImportRepo.EventCandidatesSince], everyOne: com.histmersion.domain.media.imports.ImportRepo.EventCandidatesEveryOne = false): com.histmersion.domain.media.imports.ImportRepo.EventCandidatesResult =
    s"""SELECT e.id, e.wikidata, e.label FROM events e
        WHERE e.wikidata IS NOT NULL
          AND ($everyOne OR NOT EXISTS (SELECT 1 FROM media_checks c WHERE c.provider = ${lit(provider)}
                AND ((c.target_type = 'event' AND c.target_id = e.id) OR (c.target_type = 'event' AND c.qid = e.wikidata))
                ${since.fold("")(ms => s"AND c.checked_at >= to_timestamp($ms::double precision / 1000)")}))"""

  /** The places and events an import asks about. `place`/`event` name one (an error when it has no Wikidata item);
    * otherwise the events with an item, then the places of [[placeCandidates]] — each only when this provider has not
    * been asked about it yet, or, with `refresh`, not since `since` (a job's start: a refresh job goes through every
    * one once).
    */
  def targets(
      provider: com.histmersion.domain.media.imports.ImportRepo.TargetsProvider,
      place: Option[com.histmersion.domain.media.imports.ImportRepo.TargetsPlace],
      event: Option[com.histmersion.domain.media.imports.ImportRepo.TargetsEvent],
      limit: com.histmersion.domain.media.imports.ImportRepo.TargetsLimit,
      refresh: com.histmersion.domain.media.imports.ImportRepo.TargetsRefresh,
      since: Option[com.histmersion.domain.media.imports.ImportRepo.TargetsSince]
  )(using DbCon): Either[com.histmersion.domain.media.imports.ImportRepo.TargetsResultPart1, List[Target]] =
    (place.map(_.trim).filter(_.nonEmpty), event.map(_.trim).filter(_.nonEmpty)) match
      case (Some(pid), _) =>
        sql"SELECT label FROM places WHERE id = $pid".query[String].run().headOption match
          case None        => Left(s"No place '$pid'")
          case Some(label) =>
            placeQid(pid)
              .map(q => List(Target(Place, pid, q, label)))
              .toRight(s"place '$pid' ($label) has no Wikidata link")
      case (None, Some(eid)) =>
        sql"SELECT label, wikidata FROM events WHERE id = $eid".query[(String, Option[String])].run().headOption match
          case None                   => Left(s"No event '$eid'")
          case Some((label, Some(q))) => Right(List(Target(Event, eid, q, label)))
          case Some((label, None))    =>
            Left(
              s"event '$eid' ($label) has no Wikidata item — set one with `histmersion events patch $eid --wikidata Q…`"
            )
      case (None, None) =>
        val n = limit.max(1)
        // a refresh without a job's start re-asks every one. Not as "not since now" by this
        // process's clock (#1567): that is whole milliseconds, a check is stamped with the
        // database's microseconds, so a check made in the same millisecond counted as newer
        val asOf = if refresh then since else None
        val everyOne = refresh && since.isEmpty
        val events = Sql
          .raw(eventCandidates(provider, asOf, everyOne) + s" ORDER BY e.id LIMIT $n")
          .frag
          .query[(String, String, String)]
          .run()
          .toList
          .map((id, q, l) => Target(Event, id, q, l))
        val rest = n - events.size
        val places =
          if rest <= 0 then Nil
          else
            Sql
              .raw(
                placeCandidates(provider, asOf, everyOne) +
                  s" ORDER BY (c.kind IN ('site', 'feature')) DESC, c.s DESC, c.n DESC, c.place_id LIMIT $rest"
              )
              .frag
              .query[(String, String, String, Option[String], Long, Long)]
              .run()
              .toList
              .map((id, q, l, _, _, _) => Target(Place, id, q, l))
        Right(events ++ places)

  /** How many places (one per Wikidata item) and events this provider has still to be asked about — since `since` for a
    * refresh job.
    */
  def pending(provider: com.histmersion.domain.media.imports.ImportRepo.PendingProvider, since: Option[com.histmersion.domain.media.imports.ImportRepo.PendingSince])(using DbCon): com.histmersion.domain.media.imports.ImportRepo.PendingResult =
    val e = Sql.raw(s"SELECT count(*)::int FROM (${eventCandidates(provider, since)}) x").frag.query[Int].run().head
    val p = Sql.raw(s"SELECT count(*)::int FROM (${placeCandidates(provider, since)}) x").frag.query[Int].run().head
    e + p

  // ── which places and events a collection or the depictions import asks (#1446) ──────────────

  /** The places on the map — those holding findings — not asked yet by `check` (by place id: a collection is searched
    * by name, so equivalents with another id are asked on their own).
    */
  private def mapPlaces(check: com.histmersion.domain.media.imports.ImportRepo.MapPlacesCheck, since: Option[com.histmersion.domain.media.imports.ImportRepo.MapPlacesSince], everyOne: com.histmersion.domain.media.imports.ImportRepo.MapPlacesEveryOne, pleiadesOnly: com.histmersion.domain.media.imports.ImportRepo.MapPlacesPleiadesOnly): com.histmersion.domain.media.imports.ImportRepo.MapPlacesResult =
    s"""WITH used AS (
          SELECT fp.place_id, count(*) AS n, count(DISTINCT f.citekey) AS s
          FROM finding_places fp JOIN findings f ON f.id = fp.finding_id GROUP BY fp.place_id)
        SELECT u.place_id, p.label, p.kind, u.s, u.n FROM used u JOIN places p ON p.id = u.place_id
        WHERE ($everyOne OR NOT EXISTS (SELECT 1 FROM media_checks k WHERE k.provider = ${lit(check)}
                AND k.target_type = 'place' AND k.target_id = u.place_id
                ${since.fold("")(ms => s"AND k.checked_at >= to_timestamp($ms::double precision / 1000)")}))
          ${if pleiadesOnly then s"AND ($HasPleiades)" else ""}"""

  /** A place `u.place_id` that carries a Pleiades id: its own (`pleiades_…`) or an exact link. */
  private val HasPleiades =
    """u.place_id ~ '^pleiades_[0-9]+$' OR EXISTS (SELECT 1 FROM place_links l WHERE l.place_id = u.place_id
         AND l.scheme = 'pleiades' AND l.relation = 'exact_match' AND l.external_id ~ '^[0-9]+$')"""

  /** What a collection (`places = true`) or the depictions import (`places = false`: events) asks about: one place or
    * event by id, else the ones not asked yet — sites and features first, then by sources citing — as [[targets]] does
    * for Wikidata. A place needs no Wikidata item here (`qid` is empty); an event does.
    */
  def collectionTargets(
      check: com.histmersion.domain.media.imports.ImportRepo.CollectionTargetsCheck,
      places: com.histmersion.domain.media.imports.ImportRepo.CollectionTargetsPlaces,
      place: Option[com.histmersion.domain.media.imports.ImportRepo.CollectionTargetsPlace],
      event: Option[com.histmersion.domain.media.imports.ImportRepo.CollectionTargetsEvent],
      limit: com.histmersion.domain.media.imports.ImportRepo.CollectionTargetsLimit,
      refresh: com.histmersion.domain.media.imports.ImportRepo.CollectionTargetsRefresh,
      since: Option[com.histmersion.domain.media.imports.ImportRepo.CollectionTargetsSince],
      pleiadesOnly: com.histmersion.domain.media.imports.ImportRepo.CollectionTargetsPleiadesOnly
  )(using DbCon): Either[com.histmersion.domain.media.imports.ImportRepo.CollectionTargetsResultPart1, List[Target]] =
    val n = limit.max(1)
    val asOf = if refresh then since else None
    val everyOne = refresh && since.isEmpty
    (place.map(_.trim).filter(_.nonEmpty), event.map(_.trim).filter(_.nonEmpty)) match
      case (Some(pid), _) =>
        if !places then Left("this import is about events: name one with --event")
        else
          sql"SELECT label FROM places WHERE id = $pid".query[String].run().headOption match
            case None                                                        => Left(s"No place '$pid'")
            case Some(l) if pleiadesOnly && pleiadesIdsIn(List(pid)).isEmpty =>
              Left(s"place '$pid' ($l) has no Pleiades id (its own, or an exact Pleiades link)")
            case Some(l) => Right(List(Target(Place, pid, "", l)))
      case (None, Some(eid)) =>
        if places then Left("this import is about places: name one with --place")
        else
          sql"SELECT label, wikidata FROM events WHERE id = $eid"
            .query[(String, Option[String])]
            .run()
            .headOption match
            case None                   => Left(s"No event '$eid'")
            case Some((label, Some(q))) => Right(List(Target(Event, eid, q, label)))
            case Some((label, None))    =>
              Left(
                s"event '$eid' ($label) has no Wikidata item — set one with `histmersion events patch $eid --wikidata Q…`"
              )
      case (None, None) =>
        if places then
          Right(
            Sql
              .raw(
                mapPlaces(check, asOf, everyOne, pleiadesOnly) +
                  s" ORDER BY (p.kind IN ('site', 'feature')) DESC, u.s DESC, u.n DESC, u.place_id LIMIT $n"
              )
              .frag
              .query[(String, String, Option[String], Long, Long)]
              .run()
              .toList
              .map((id, l, _, _, _) => Target(Place, id, "", l))
          )
        else
          Right(
            Sql
              .raw(eventCandidates(check, asOf, everyOne) + s" ORDER BY e.id LIMIT $n")
              .frag
              .query[(String, String, String)]
              .run()
              .toList
              .map((id, q, l) => Target(Event, id, q, l))
          )

  /** How many places (`places`) or events a collection or the depictions import has still to ask. */
  def collectionPending(check: com.histmersion.domain.media.imports.ImportRepo.CollectionPendingCheck, places: com.histmersion.domain.media.imports.ImportRepo.CollectionPendingPlaces, since: Option[com.histmersion.domain.media.imports.ImportRepo.CollectionPendingSince], pleiadesOnly: com.histmersion.domain.media.imports.ImportRepo.CollectionPendingPleiadesOnly)(using
      DbCon
  ): com.histmersion.domain.media.imports.ImportRepo.CollectionPendingResult =
    val q = if places then mapPlaces(check, since, everyOne = false, pleiadesOnly)
    else eventCandidates(check, since, everyOne = false)
    Sql.raw(s"SELECT count(*)::int FROM ($q) x").frag.query[Int].run().head

  def placeFacts(ids: List[com.histmersion.domain.media.imports.ImportRepo.PlaceFactsIds])(using DbCon): Map[com.histmersion.domain.media.imports.ImportRepo.PlaceFactsResultPart1, PlaceFacts] =
    if ids.isEmpty then Map.empty
    else
      sql"""SELECT p.id, p.label,
                 array(SELECT DISTINCT k FROM (
                     SELECT histmersion_place_key(p.label) AS k
                     UNION SELECT histmersion_place_key(n.name) FROM place_names n WHERE n.place_id = p.id) ks
                   WHERE k <> ''),
                 CASE WHEN NOT lower_inf(p.period) AND NOT upper_inf(p.period) THEN lower(p.period) ELSE fs.lo END,
                 CASE WHEN NOT lower_inf(p.period) AND NOT upper_inf(p.period) THEN upper(p.period) - 1 ELSE fs.hi END,
                 (SELECT pl.external_id FROM place_links pl WHERE pl.place_id = p.id AND pl.scheme = 'wikidata'
                    AND pl.relation = 'exact_match' ORDER BY pl.external_id LIMIT 1),
                 ST_Y(ST_PointOnSurface(p.geom)), ST_X(ST_PointOnSurface(p.geom))
          FROM places p
          LEFT JOIN LATERAL (
            SELECT min(lower(f.period)) AS lo, max(upper(f.period) - 1) AS hi
            FROM finding_places fp JOIN findings f ON f.id = fp.finding_id
            WHERE fp.place_id = p.id AND f.period IS NOT NULL AND NOT lower_inf(f.period) AND NOT upper_inf(f.period)) fs
            ON true
          WHERE p.id = ANY($ids::text[])"""
        .query[
          (String, String, List[String], Option[Long], Option[Long], Option[String], Option[Double], Option[Double])
        ]
        .run()
        .toList
        .map { (id, label, keys, lo, hi, q, lat, lon) =>
          id -> PlaceFacts(id, label, keys.toSet, lo.zip(hi), q, lat, lon)
        }
        .toMap

  /** The Pleiades ids of places: a `pleiades_<n>` place is that record alone; another place has its exact Pleiades
    * links, in order. Places without one are left out.
    */
  def pleiadesIds(ids: List[com.histmersion.domain.media.imports.ImportRepo.PleiadesIdsIds])(using DbCon): Map[com.histmersion.domain.media.imports.ImportRepo.PleiadesIdsResultPart1, List[com.histmersion.domain.media.imports.ImportRepo.PleiadesIdsResultPart2]] =
    if ids.isEmpty then Map.empty else pleiadesIdsIn(ids)

  private def pleiadesIdsIn(ids: List[com.histmersion.domain.media.imports.ImportRepo.PleiadesIdsInIds])(using DbCon): Map[com.histmersion.domain.media.imports.ImportRepo.PleiadesIdsInResultPart1, List[com.histmersion.domain.media.imports.ImportRepo.PleiadesIdsInResultPart2]] =
    val own =
      ids.flatMap(id => ExternalIds.ofPlaceId(id).filter(_._1 == ExternalIds.Pleiades).map(p => id -> List(p._2))).toMap
    val rest = ids.filterNot(own.contains)
    val linked =
      if rest.isEmpty then Map.empty
      else sql"""SELECT place_id, external_id FROM place_links WHERE place_id = ANY($rest::text[])
                  AND scheme = 'pleiades' AND relation = 'exact_match' AND external_id ~ '^[0-9]+$$'
                  ORDER BY place_id, external_id""".query[(String, String)].run().toList.groupMap(_._1)(_._2)
    own ++ linked

  /** The gazetteer ids each place carries — its own id's (`pleiades_570491` → `pleiades:570491`) and its exact
    * Pleiades, GeoNames and Wikidata links — as [[ExternalIds]] pairs, to match a record that names its place by such
    * an id.
    */
  def placeExternalIds(ids: List[com.histmersion.domain.media.imports.ImportRepo.PlaceExternalIdsIds])(using DbCon): Map[com.histmersion.domain.media.imports.ImportRepo.PlaceExternalIdsResultPart1, Set[(com.histmersion.domain.media.imports.ImportRepo.PlaceExternalIdsResultPart2Part1, com.histmersion.domain.media.imports.ImportRepo.PlaceExternalIdsResultPart2Part2)]] =
    if ids.isEmpty then Map.empty
    else
      val linked =
        sql"""SELECT place_id, scheme, external_id FROM place_links WHERE place_id = ANY($ids::text[])
              AND relation = 'exact_match' AND scheme IN ('pleiades', 'geonames', 'wikidata')"""
          .query[(String, String, String)]
          .run()
          .toList
      val fromLinks = linked.groupMap(_._1)(r => (r._2, r._3)).view.mapValues(_.toSet).toMap
      ids
        .map(id => id -> (fromLinks.getOrElse(id, Set.empty) ++ ExternalIds.ofPlaceId(id).toSet))
        .filter(_._2.nonEmpty)
        .toMap

  /** The places ON THE MAP (holding findings) carrying each gazetteer id — as their own id (`pleiades_697725`) or an
    * exact link.
    */
  def mapPlacesWithIds(ids: List[(com.histmersion.domain.media.imports.ImportRepo.MapPlacesWithIdsIdsPart1, com.histmersion.domain.media.imports.ImportRepo.MapPlacesWithIdsIdsPart2)])(using DbCon): Map[(com.histmersion.domain.media.imports.ImportRepo.MapPlacesWithIdsResultPart1Part1, com.histmersion.domain.media.imports.ImportRepo.MapPlacesWithIdsResultPart1Part2), List[PlaceFacts]] =
    val ws = ids
      .filter((s, e) => Set(ExternalIds.Pleiades, ExternalIds.GeoNames, ExternalIds.Wikidata)(s) && e.nonEmpty)
      .distinct
    if ws.isEmpty then Map.empty
    else
      val (schemes, exts) = (ws.map(_._1), ws.map(_._2))
      val ownIds = ws.map { (s, e) =>
        s match
          case ExternalIds.Wikidata => s"wikidata_${e.toLowerCase(java.util.Locale.ROOT)}" -> (s, e)
          case _                    => s"${s}_$e" -> (s, e)
      }.toMap
      val owned = ownIds.keys.toList
      val linked = sql"""SELECT l.scheme, l.external_id, l.place_id FROM place_links l
            JOIN unnest($schemes::text[], $exts::text[]) AS w(s, e) ON l.scheme = w.s AND l.external_id = w.e
            WHERE l.relation = 'exact_match'
              AND EXISTS (SELECT 1 FROM finding_places fp WHERE fp.place_id = l.place_id)"""
        .query[(String, String, String)]
        .run()
        .toList
        .map((s, e, p) => ((s, e), p))
      val self = sql"""SELECT p.id FROM places p WHERE p.id = ANY($owned::text[])
            AND EXISTS (SELECT 1 FROM finding_places fp WHERE fp.place_id = p.id)"""
        .query[String]
        .run()
        .toList
        .flatMap(id => ownIds.get(id).map(_ -> id))
      val hits = (linked ++ self).distinct
      val facts = placeFacts(hits.map(_._2).distinct)
      hits.groupMap(_._1)(_._2).view.mapValues(_.distinct.flatMap(facts.get)).toMap

  /** The places ON THE MAP (holding findings) each name key names — by label or an alternative name. */
  def mapPlacesNamed(keys: List[com.histmersion.domain.media.imports.ImportRepo.MapPlacesNamedKeys])(using DbCon): Map[com.histmersion.domain.media.imports.ImportRepo.MapPlacesNamedResultPart1, List[PlaceFacts]] =
    val ks = keys.filter(_.nonEmpty).distinct
    if ks.isEmpty then Map.empty
    else
      val hits =
        sql"""SELECT k, id FROM (
                SELECT histmersion_place_key(p.label) AS k, p.id FROM places p
                WHERE histmersion_place_key(p.label) = ANY($ks::text[])
                UNION SELECT histmersion_place_key(n.name), n.place_id FROM place_names n
                WHERE histmersion_place_key(n.name) = ANY($ks::text[])) x
              WHERE EXISTS (SELECT 1 FROM finding_places fp WHERE fp.place_id = x.id)"""
          .query[(String, String)]
          .run()
          .toList
      val facts = placeFacts(hits.map(_._2).distinct)
      hits.groupMap(_._1)(_._2).view.mapValues(_.distinct.flatMap(facts.get)).toMap

  // ── what was asked ───────────────────────────────────────────────────────────────────────────

  /** Record a place's Commons category (Wikidata P373) as a link of the place. Returns whether new. */
  def commonsCategory(placeId: com.histmersion.domain.catalogue.Place.Id, category: com.histmersion.domain.media.imports.ImportRepo.CommonsCategoryCategory, qid: com.histmersion.domain.media.imports.ImportRepo.CommonsCategoryQid)(using DbCon): com.histmersion.domain.media.imports.ImportRepo.CommonsCategoryResult =
    sql"""INSERT INTO place_links (place_id, scheme, external_id, relation, import_source, import_ref)
          VALUES ($placeId, 'commons', $category, 'see_also', 'media:wikidata', $qid)
          ON CONFLICT (place_id, scheme, external_id) DO NOTHING""".update.run() > 0

  /** Remember that `provider` was asked about a place, event or source (by its Wikidata item `qid`, when it has one),
    * and how many pictures it gave.
    */
  def checked(provider: com.histmersion.domain.media.imports.ImportRepo.CheckedProvider, targetType: com.histmersion.domain.media.imports.ImportRepo.CheckedTargetType, targetId: com.histmersion.domain.media.imports.ImportRepo.CheckedTargetId, found: com.histmersion.domain.media.imports.ImportRepo.CheckedFound, qid: Option[com.histmersion.domain.media.imports.ImportRepo.CheckedQid])(using
      DbCon
  ): Unit =
    val _ = sql"""INSERT INTO media_checks (provider, target_type, target_id, checked_at, found, qid)
          VALUES ($provider, $targetType, $targetId, clock_timestamp(), $found, $qid)
          ON CONFLICT (provider, target_type, target_id) DO UPDATE SET
            checked_at = EXCLUDED.checked_at, found = EXCLUDED.found,
            qid = coalesce(EXCLUDED.qid, media_checks.qid)""".update.run()
