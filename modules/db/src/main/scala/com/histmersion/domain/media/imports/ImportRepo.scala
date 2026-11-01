package com.histmersion.domain.media.imports

import com.histmersion.domain.catalogue.places.{ExternalIds, Wikidata}
import com.histmersion.db.Sql

import com.histmersion.db.given
import com.augustnagro.magnum.*

/** What the media imports ask about and what they recorded asking (#1446, V082): the places on the map and the events
  * not asked yet by a provider, the facts a collection's match reads of a place, and `media_checks`. Every method runs
  * on the connection it is given.
  */
private[histmersion] object ImportRepo:

  val Place = "place"
  val Event = "event"

  // ── which places and events to ask ───────────────────────────────────────────────────────────

  private val QidRe = "^Q[1-9][0-9]*$"

  /** One place's Wikidata item: an exact link first, else any; a `wikidata_q…` id names its own. */
  private def placeQid(id: String)(using DbCon): Option[String] =
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
  private def placeCandidates(provider: String, since: Option[Long], everyOne: Boolean = false): String =
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

  private def lit(s: String): String = "'" + s.replace("'", "''") + "'"

  private def eventCandidates(provider: String, since: Option[Long], everyOne: Boolean = false): String =
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
      provider: String,
      place: Option[String],
      event: Option[String],
      limit: Int,
      refresh: Boolean,
      since: Option[Long]
  )(using DbCon): Either[String, List[Target]] =
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
  def pending(provider: String, since: Option[Long])(using DbCon): Int =
    val e = Sql.raw(s"SELECT count(*)::int FROM (${eventCandidates(provider, since)}) x").frag.query[Int].run().head
    val p = Sql.raw(s"SELECT count(*)::int FROM (${placeCandidates(provider, since)}) x").frag.query[Int].run().head
    e + p

  // ── which places and events a collection or the depictions import asks (#1446) ──────────────

  /** The places on the map — those holding findings — not asked yet by `check` (by place id: a collection is searched
    * by name, so equivalents with another id are asked on their own).
    */
  private def mapPlaces(check: String, since: Option[Long], everyOne: Boolean, pleiadesOnly: Boolean): String =
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
      check: String,
      places: Boolean,
      place: Option[String],
      event: Option[String],
      limit: Int,
      refresh: Boolean,
      since: Option[Long],
      pleiadesOnly: Boolean
  )(using DbCon): Either[String, List[Target]] =
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
  def collectionPending(check: String, places: Boolean, since: Option[Long], pleiadesOnly: Boolean)(using
      DbCon
  ): Int =
    val q = if places then mapPlaces(check, since, everyOne = false, pleiadesOnly)
    else eventCandidates(check, since, everyOne = false)
    Sql.raw(s"SELECT count(*)::int FROM ($q) x").frag.query[Int].run().head

  def placeFacts(ids: List[String])(using DbCon): Map[String, PlaceFacts] =
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
  def pleiadesIds(ids: List[String])(using DbCon): Map[String, List[String]] =
    if ids.isEmpty then Map.empty else pleiadesIdsIn(ids)

  private def pleiadesIdsIn(ids: List[String])(using DbCon): Map[String, List[String]] =
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
  def placeExternalIds(ids: List[String])(using DbCon): Map[String, Set[(String, String)]] =
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
  def mapPlacesWithIds(ids: List[(String, String)])(using DbCon): Map[(String, String), List[PlaceFacts]] =
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
  def mapPlacesNamed(keys: List[String])(using DbCon): Map[String, List[PlaceFacts]] =
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
  def commonsCategory(placeId: String, category: String, qid: String)(using DbCon): Boolean =
    sql"""INSERT INTO place_links (place_id, scheme, external_id, relation, import_source, import_ref)
          VALUES ($placeId, 'commons', $category, 'see_also', 'media:wikidata', $qid)
          ON CONFLICT (place_id, scheme, external_id) DO NOTHING""".update.run() > 0

  /** Remember that `provider` was asked about a place, event or source (by its Wikidata item `qid`, when it has one),
    * and how many pictures it gave.
    */
  def checked(provider: String, targetType: String, targetId: String, found: Int, qid: Option[String])(using
      DbCon
  ): Unit =
    val _ = sql"""INSERT INTO media_checks (provider, target_type, target_id, checked_at, found, qid)
          VALUES ($provider, $targetType, $targetId, clock_timestamp(), $found, $qid)
          ON CONFLICT (provider, target_type, target_id) DO UPDATE SET
            checked_at = EXCLUDED.checked_at, found = EXCLUDED.found,
            qid = coalesce(EXCLUDED.qid, media_checks.qid)""".update.run()
