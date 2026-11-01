package com.histmersion.db

import com.histmersion.util.database.RangeSql

import com.histmersion.api.*
import com.histmersion.files.*
import com.histmersion.config.*
import com.histmersion.domain.*

import com.augustnagro.magnum.*
import javax.sql.DataSource
import com.histmersion.values.MediaRole

final private[db] case class MediaThumbRow(
    uid: String,
    role: String,
    credit: String,
    license: Option[String],
    licenseUrl: Option[String],
    sourceUrl: Option[String],
    width: Option[Int],
    height: Option[Int]
) derives DbCodec

final private[db] case class MediaItemRow(
    uid: String,
    name: String,
    mime: String,
    bytes: Int,
    citekey: Option[String],
    provider: Option[String],
    providerKey: Option[String],
    width: Option[Int],
    height: Option[Int],
    kind: Option[String],
    caption: Option[String],
    license: Option[String],
    licenseUrl: Option[String],
    author: Option[String],
    credit: Option[String],
    sourceUrl: Option[String],
    originalUrl: Option[String],
    retrievedAt: Option[String],
    hasThumb: Boolean
) derives DbCodec

final private[db] case class MediaGeoRow(
    citekey: Option[String],
    name: String,
    kind: Option[String],
    lat: Double,
    lon: Double,
    periodStart: Option[Long],
    periodEnd: Option[Long],
    caption: Option[String],
    uid: String,
    provider: Option[String],
    license: Option[String],
    credit: Option[String],
    hasThumb: Boolean,
    sourceUrl: Option[String],
    licenseUrl: Option[String],
    author: Option[String]
) derives DbCodec

/** Pictures from outside providers (#1446, V082): one `assets` row each, with `provider` set instead of a `citekey`,
  * linked to the places and events they show through `media_links`; and the read paths every stored picture shares —
  * the media list and facets, the media layer, the cards' thumbnails. Repos are stateless: the `DataSource` is a
  * parameter.
  */
object MediaRepo:

  val Place = "place"
  val Event = "event"
  val Source = "source"

  /** A place or event to ask a provider about: its Wikidata item and its name (for the report). */
  final case class Target(targetType: String, id: String, qid: String, label: String)

  /** A stored picture of a provider, by its key: its uid and the licence it was stored under. */
  final case class Known(uid: String, license: Option[String])

  /** A provider's picture to store. `bytes` are the capped rendition, `thumb` the card's thumbnail. */
  final case class ProviderAsset(
      provider: String,
      key: String,
      name: String,
      mime: String,
      bytes: Array[Byte],
      width: Option[Int],
      height: Option[Int],
      thumb: Option[Array[Byte]],
      thumbMime: Option[String],
      thumbWidth: Option[Int],
      thumbHeight: Option[Int],
      license: String,
      licenseUrl: Option[String],
      author: Option[String],
      credit: String,
      sourceUrl: String,
      originalUrl: Option[String],
      kind: Option[String]
  )

  /** The licence fields a re-read refreshes on a stored picture (the bytes stay). */
  final case class Rights(license: String, licenseUrl: Option[String], author: Option[String], credit: String)

  /** Pure: the media kind (the layer's colour) of a picture shown in `role`. */
  def kindOf(role: String): String = role match
    case MediaRole.LocatorMap | MediaRole.RouteMap | MediaRole.Map => AssetKind.Map
    case MediaRole.Plan                                            => AssetKind.Plan
    case _                                                         => AssetKind.Photo

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
    * item ([[placeThumbsIn]]) — sites and features before regions and polities, then by sources citing, then by
    * findings. Not asked yet by this provider (neither that place nor that item), or — `since` set, a refresh — not
    * since then; `everyOne` (a refresh with no start) leaves none out.
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
      ds: DataSource,
      provider: String,
      place: Option[String],
      event: Option[String],
      limit: Int,
      refresh: Boolean,
      since: Option[Long] = None
  ): Either[String, List[Target]] =
    connect(ds):
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
  def pending(ds: DataSource, provider: String, since: Option[Long] = None): Int =
    connect(ds):
      val e = Sql.raw(s"SELECT count(*)::int FROM (${eventCandidates(provider, since)}) x").frag.query[Int].run().head
      val p = Sql.raw(s"SELECT count(*)::int FROM (${placeCandidates(provider, since)}) x").frag.query[Int].run().head
      e + p

  // ── which places and events a collection or the depictions import asks (#1446) ──────────────

  /** Events with a Wikidata item not asked yet by `check` (or, `since` set, not since then; `everyOne`: none left out).
    */
  private def eventsOnly(check: String, asOf: Option[Long], everyOne: Boolean): String =
    eventCandidates(check, asOf, everyOne)

  /** The places on the map — those holding findings — not asked yet by `check` (by place id: a collection is searched
    * by name, so equivalents with another id are asked on their own).
    */
  private def mapPlaces(check: String, since: Option[Long], everyOne: Boolean, pleiadesOnly: Boolean = false): String =
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
      ds: DataSource,
      check: String,
      places: Boolean,
      place: Option[String],
      event: Option[String],
      limit: Int,
      refresh: Boolean,
      since: Option[Long] = None,
      pleiadesOnly: Boolean = false
  ): Either[String, List[Target]] =
    connect(ds):
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
                .raw(eventsOnly(check, asOf, everyOne) + s" ORDER BY e.id LIMIT $n")
                .frag
                .query[(String, String, String)]
                .run()
                .toList
                .map((id, q, l) => Target(Event, id, q, l))
            )

  /** How many places (`places`) or events a collection or the depictions import has still to ask. */
  def collectionPending(
      ds: DataSource,
      check: String,
      places: Boolean,
      since: Option[Long] = None,
      pleiadesOnly: Boolean = false
  ): Int =
    connect(ds):
      val q = if places then mapPlaces(check, since, everyOne = false, pleiadesOnly)
      else eventsOnly(check, since, everyOne = false)
      Sql.raw(s"SELECT count(*)::int FROM ($q) x").frag.query[Int].run().head

  /** A place as a collection match sees it: every name key it goes by (label and alternative names, [[Geocode.key]] =
    * `histmersion_place_key`), its period — its own when bounded at both ends, else the span of the findings it holds —
    * and its Wikidata item and point (to tell equivalents apart).
    */
  final case class PlaceFacts(
      id: String,
      label: String,
      keys: Set[String],
      period: Option[(Long, Long)],
      qid: Option[String],
      lat: Option[Double],
      lon: Option[Double]
  )

  def placeFacts(ds: DataSource, ids: List[String]): Map[String, PlaceFacts] =
    if ids.isEmpty then Map.empty
    else
      connect(ds):
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
  def pleiadesIds(ds: DataSource, ids: List[String]): Map[String, List[String]] =
    if ids.isEmpty then Map.empty else connect(ds)(pleiadesIdsIn(ids))

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
  def placeExternalIds(ds: DataSource, ids: List[String]): Map[String, Set[(String, String)]] =
    if ids.isEmpty then Map.empty
    else
      val linked = connect(ds):
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
  def mapPlacesWithIds(ds: DataSource, ids: List[(String, String)]): Map[(String, String), List[PlaceFacts]] =
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
      val hits = connect(ds):
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
        (linked ++ self).distinct
      val facts = placeFacts(ds, hits.map(_._2).distinct)
      hits.groupMap(_._1)(_._2).view.mapValues(_.distinct.flatMap(facts.get)).toMap

  /** The places ON THE MAP (holding findings) each name key names — by label or an alternative name. */
  def mapPlacesNamed(ds: DataSource, keys: List[String]): Map[String, List[PlaceFacts]] =
    val ks = keys.filter(_.nonEmpty).distinct
    if ks.isEmpty then Map.empty
    else
      val hits = connect(ds):
        sql"""SELECT k, id FROM (
                SELECT histmersion_place_key(p.label) AS k, p.id FROM places p
                WHERE histmersion_place_key(p.label) = ANY($ks::text[])
                UNION SELECT histmersion_place_key(n.name), n.place_id FROM place_names n
                WHERE histmersion_place_key(n.name) = ANY($ks::text[])) x
              WHERE EXISTS (SELECT 1 FROM finding_places fp WHERE fp.place_id = x.id)"""
          .query[(String, String)]
          .run()
          .toList
      val facts = placeFacts(ds, hits.map(_._2).distinct)
      hits.groupMap(_._1)(_._2).view.mapValues(_.distinct.flatMap(facts.get)).toMap

  // ── writes ───────────────────────────────────────────────────────────────────────────────────

  /** The pictures of `provider` already stored under these keys. */
  def known(ds: DataSource, provider: String, keys: List[String]): Map[String, Known] =
    if keys.isEmpty then Map.empty
    else
      connect(ds):
        sql"""SELECT provider_key, uid::text, license FROM assets
            WHERE provider = $provider AND provider_key = ANY($keys::text[])"""
          .query[(String, String, Option[String])]
          .run()
          .map((k, u, l) => k -> Known(u, l))
          .toMap

  /** Store a provider's picture (or replace the bytes and rights of the one stored under its key); its point and period
    * come from the place or event it is first stored for. Returns its uid and whether it is new.
    */
  def save(ds: DataSource, a: ProviderAsset, firstTarget: (String, String)): (String, Boolean) =
    transact(ds):
      val sha = AssetRepo.sha256(a.bytes)
      val (uid, inserted) =
        sql"""INSERT INTO assets (citekey, name, mime, bytes, sha256, width, height, kind, license, provider,
                provider_key, source_url, original_url, author, credit, license_url, retrieved_at,
                thumb, thumb_mime, thumb_width, thumb_height)
              VALUES (NULL, ${a.name}, ${a.mime}, ${a.bytes}, $sha, ${a.width}, ${a.height}, ${a.kind},
                nullif(${a.license}, ''), ${a.provider}, ${a.key}, ${a.sourceUrl}, ${a.originalUrl}, ${a.author},
                ${a.credit}, ${a.licenseUrl}, now(), ${a.thumb}, ${a.thumbMime}, ${a.thumbWidth}, ${a.thumbHeight})
              ON CONFLICT (provider, provider_key) WHERE provider IS NOT NULL DO UPDATE SET
                name = EXCLUDED.name, mime = EXCLUDED.mime, bytes = EXCLUDED.bytes, sha256 = EXCLUDED.sha256,
                width = EXCLUDED.width, height = EXCLUDED.height, license = EXCLUDED.license,
                source_url = EXCLUDED.source_url, original_url = EXCLUDED.original_url,
                author = EXCLUDED.author, credit = EXCLUDED.credit, license_url = EXCLUDED.license_url,
                retrieved_at = EXCLUDED.retrieved_at, thumb = EXCLUDED.thumb, thumb_mime = EXCLUDED.thumb_mime,
                thumb_width = EXCLUDED.thumb_width, thumb_height = EXCLUDED.thumb_height,
                kind = coalesce(assets.kind, EXCLUDED.kind)
              RETURNING uid::text, (xmax = 0)""".query[(String, Boolean)].run().head
      placeAt(uid, firstTarget)
      (uid, inserted)

  /** A picture's point and period, where it has none: the place's point on surface and period, or the event's own point
    * (else its place's) and outer span. An open period (a place with no dates is stored as `(,)`) is not copied: a
    * time-filtered media layer shows dated pictures only.
    */
  private def placeAt(uid: String, target: (String, String))(using DbCon): Unit =
    val (tt, id) = target
    if tt == Source then () // a source's picture (a cover) is not on the map
    else if tt == Place then sql"""UPDATE assets a SET geom = coalesce(a.geom, t.g),
              period = coalesce(a.period, CASE WHEN lower_inf(t.p) AND upper_inf(t.p) THEN NULL ELSE t.p END)
            FROM (SELECT ST_PointOnSurface(coalesce(geom_clipped, geom)) AS g, period AS p FROM places WHERE id = $id) t
            WHERE a.uid = $uid::uuid""".update.run()
    else sql"""UPDATE assets a SET geom = coalesce(a.geom, t.g),
              period = coalesce(a.period, CASE WHEN lower_inf(t.p) AND upper_inf(t.p) THEN NULL ELSE t.p END)
            FROM (SELECT ST_PointOnSurface(coalesce(e.geom, p.geom)) AS g, e.outer_span AS p
                  FROM events e LEFT JOIN places p ON p.id = e.place_id WHERE e.id = $id) t
            WHERE a.uid = $uid::uuid""".update.run()
    ()

  /** Re-read rights on a stored picture whose bytes are kept (a refresh that does not re-download). */
  def refreshRights(ds: DataSource, uid: String, r: Rights): Unit =
    val _ = transact(ds):
      sql"""UPDATE assets SET license = ${r.license}, license_url = ${r.licenseUrl}, author = ${r.author},
              credit = ${r.credit}, retrieved_at = now() WHERE uid = $uid::uuid""".update.run()

  /** Link a stored picture to a place or event in a role — with `basis`, why a collection's record is tied to it
    * (V094). Returns whether the link is new.
    */
  def link(
      ds: DataSource,
      uid: String,
      targetType: String,
      targetId: String,
      role: String,
      property: Option[String],
      item: Option[String],
      rank: Int,
      basis: Option[String] = None
  ): Boolean =
    transact(ds):
      sql"""INSERT INTO media_links (asset_uid, target_type, target_id, role, property, item, rank, basis)
            VALUES ($uid::uuid, $targetType, $targetId, $role, $property, $item, $rank, $basis)
            ON CONFLICT (asset_uid, target_type, target_id, role) DO UPDATE SET
              property = EXCLUDED.property, item = EXCLUDED.item, rank = EXCLUDED.rank,
              basis = coalesce(EXCLUDED.basis, media_links.basis)
            RETURNING (xmax = 0)""".query[Boolean].run().head

  /** Record a place's Commons category (Wikidata P373) as a link of the place. Returns whether new. */
  def commonsCategory(ds: DataSource, placeId: String, category: String, qid: String): Boolean =
    transact(ds):
      sql"""INSERT INTO place_links (place_id, scheme, external_id, relation, import_source, import_ref)
            VALUES ($placeId, 'commons', $category, 'see_also', 'media:wikidata', $qid)
            ON CONFLICT (place_id, scheme, external_id) DO NOTHING""".update.run() > 0

  /** Remember that `provider` was asked about a place, event or source (by its Wikidata item `qid`, when it has one),
    * and how many pictures it gave.
    */
  def checked(
      ds: DataSource,
      provider: String,
      targetType: String,
      targetId: String,
      found: Int,
      qid: Option[String] = None
  ): Unit =
    val _ = transact(ds):
      sql"""INSERT INTO media_checks (provider, target_type, target_id, checked_at, found, qid)
            VALUES ($provider, $targetType, $targetId, clock_timestamp(), $found, $qid)
            ON CONFLICT (provider, target_type, target_id) DO UPDATE SET
              checked_at = EXCLUDED.checked_at, found = EXCLUDED.found,
              qid = coalesce(EXCLUDED.qid, media_checks.qid)""".update.run()

  // ── reads ────────────────────────────────────────────────────────────────────────────────────

  private def thumbOf(r: MediaThumbRow): MediaThumb =
    MediaThumb(
      r.uid,
      r.role,
      MediaThumb.thumbPath(r.uid),
      r.credit,
      r.license,
      r.licenseUrl,
      r.sourceUrl,
      r.width,
      r.height
    )

  /** A place's or event's (or a source's) pictures for its card, best first (the role order of [[MediaRole]], then the
    * provider's preference): one indexed lookup, so it fits the hover budget. A source's picture without a stored
    * thumbnail is shown from its own bytes (a figure is already small enough); a provider's picture of a place needs
    * one.
    */
  def thumbsIn(targetType: String, targetId: String, limit: Int = 4)(using DbCon): List[MediaThumb] =
    // one row per picture (an event's P18 image may also be one of its depictions), in its best role
    sql"""SELECT uid, role, credit, license, license_url, source_url, w, h FROM (
            SELECT DISTINCT ON (a.uid) a.uid::text AS uid, m.role, coalesce(a.credit, '') AS credit, a.license,
                   a.license_url, a.source_url, coalesce(a.thumb_width, a.width) AS w,
                   coalesce(a.thumb_height, a.height) AS h, m.rank,
                   array_position(${MediaRole.all}::text[], m.role) AS ord
            FROM media_links m JOIN assets a ON a.uid = m.asset_uid
            WHERE m.target_type = $targetType AND m.target_id = $targetId
              AND (a.thumb IS NOT NULL OR m.target_type = 'source')
            ORDER BY a.uid, array_position(${MediaRole.all}::text[], m.role), m.rank) x
          ORDER BY ord, rank, uid
          LIMIT ${limit.max(1)}""".query[MediaThumbRow].run().toList.map(thumbOf)

  def thumbs(ds: DataSource, targetType: String, targetId: String, limit: Int = 4): List[MediaThumb] =
    connect(ds)(thumbsIn(targetType, targetId, limit))

  /** Pure: `km` as degrees of arc for `ST_DWithin` on lon/lat geometry — a latitude degree, so a little generous
    * east–west away from the equator, which a "same site" test can afford.
    */
  def kmToDegrees(km: Double): Double = (km.max(0) / 111.32)

  /** A PLACE's pictures for its card (#1446): its own, and those stored for its equivalents —
    *   - any place with the same Wikidata item (the picture's `item`): Mycenae is `pleiades_570491` and a Wikidata
    *     import, a polity is one place per time slice;
    *   - an old id merged or renamed into it (`place_aliases`);
    *   - another gazetteer's POINT of the same name (the geocoder's key, [[Geocode.key]]) within `sameSiteKm` —
    *     Pleiades and GeoNames listing one site twice — and that point's items.
    * Best role first, then the place's own before an equivalent's, then the provider's preference. Index lookups only
    * (label key, GiST on the point, the links' item), within the hover budget.
    */
  def placeThumbsIn(placeId: String, sameSiteKm: Double, limit: Int = 4)(using DbCon): List[MediaThumb] =
    val deg = kmToDegrees(sameSiteKm)
    sql"""WITH p AS (SELECT id, label, geom FROM places WHERE id = $placeId),
          eq AS (
            SELECT id FROM p
            UNION SELECT alias FROM place_aliases WHERE place_id = $placeId
            UNION SELECT q.id FROM p JOIN places q
                    ON histmersion_place_key(q.label) = histmersion_place_key(p.label)
                   AND q.id <> p.id
                   AND $deg > 0
                   AND ST_Dimension(p.geom) = 0 AND ST_Dimension(q.geom) = 0
                   AND ST_DWithin(q.geom, p.geom, $deg)),
          qids AS (
            SELECT pl.external_id AS qid FROM place_links pl JOIN eq ON pl.place_id = eq.id
            WHERE pl.scheme = 'wikidata' AND pl.relation = 'exact_match'
            UNION SELECT 'Q' || substr(id, 11) FROM eq WHERE id ~ '^wikidata_q[1-9][0-9]*$$'),
          hits AS (
            SELECT DISTINCT ON (a.uid) a.uid, m.role, m.rank, (m.target_id = $placeId) AS own,
                   coalesce(a.credit, '') AS credit, a.license, a.license_url, a.source_url,
                   coalesce(a.thumb_width, a.width) AS w, coalesce(a.thumb_height, a.height) AS h
            FROM media_links m JOIN assets a ON a.uid = m.asset_uid
            WHERE m.target_type = 'place' AND a.thumb IS NOT NULL
              AND (m.target_id IN (SELECT id FROM eq) OR m.item IN (SELECT qid FROM qids))
            ORDER BY a.uid, (m.target_id = $placeId) DESC, array_position(${MediaRole.all}::text[], m.role), m.rank)
          SELECT uid::text, role, credit, license, license_url, source_url, w, h FROM hits
          ORDER BY array_position(${MediaRole.all}::text[], role), own DESC, rank, uid
          LIMIT ${limit.max(1)}""".query[MediaThumbRow].run().toList.map(thumbOf)

  def placeThumbs(ds: DataSource, placeId: String, sameSiteKm: Double, limit: Int = 4): List[MediaThumb] =
    connect(ds)(placeThumbsIn(placeId, sameSiteKm, limit))

  /** A source's picture for its hover card: its cover, else its illustration. */
  def sourceThumb(ds: DataSource, citekey: String): Option[MediaThumb] =
    connect(ds)(thumbsIn(Source, citekey, 1).headOption)

  private val itemCols =
    """a.uid::text, a.name, a.mime, length(a.bytes), a.citekey, a.provider, a.provider_key, a.width, a.height,
       a.kind, a.caption, a.license, a.license_url, a.author, a.credit, a.source_url, a.original_url,
       to_char(a.retrieved_at AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS"Z"'), a.thumb IS NOT NULL"""

  private def item(r: MediaItemRow, targets: List[MediaTarget]): MediaItem =
    MediaItem(
      r.uid,
      r.name,
      r.mime,
      r.bytes,
      r.citekey,
      r.provider,
      r.providerKey,
      r.width,
      r.height,
      r.kind,
      r.caption,
      r.license,
      r.licenseUrl,
      r.author,
      r.credit,
      r.sourceUrl,
      r.originalUrl,
      r.retrievedAt,
      Option.when(r.hasThumb)(MediaThumb.thumbPath(r.uid)),
      MediaThumb.filePath(r.uid),
      targets
    )

  private def targetsOf(uids: List[String])(using DbCon): Map[String, List[MediaTarget]] =
    if uids.isEmpty then Map.empty
    else
      sql"""SELECT asset_uid::text, target_type, target_id, role, coalesce(property, ''), coalesce(item, ''),
                   coalesce(basis, '')
            FROM media_links WHERE asset_uid = ANY($uids::uuid[])
            ORDER BY asset_uid, target_type, target_id, rank"""
        .query[(String, String, String, String, String, String, String)]
        .run()
        .toList
        .groupMap(_._1)((_, tt, ti, role, p, q, b) =>
          MediaTarget(
            tt,
            ti,
            role,
            Option(p).filter(_.nonEmpty),
            Option(q).filter(_.nonEmpty),
            Option(b).filter(_.nonEmpty)
          )
        )

  private def clean(o: Option[String]): Option[String] = o.map(_.trim).filter(_.nonEmpty)

  /** The list: by provider (`source` = a source's figures), licence (`none` = no licence), the place or event a picture
    * is linked to, and a text match; newest first. `total` ignores the limit.
    */
  def list(ds: DataSource, q: Api.MediaQuery, limit: Int): MediaList =
    val (provider, license) = (clean(q.provider), clean(q.license))
    val (place, event) = (clean(q.place), clean(q.event))
    val text = clean(q.q).map(t => s"%$t%")
    val offset = q.offset.getOrElse(0).max(0)
    connect(ds):
      val rows = sql"""SELECT ${SqlLiteral(itemCols)}, count(*) OVER ()::int FROM assets a
            WHERE ($provider::text IS NULL OR ($provider::text = ${MediaProvider.Source} AND a.provider IS NULL)
                   OR a.provider = $provider::text)
              AND ($license::text IS NULL OR ($license::text = 'none' AND coalesce(btrim(a.license), '') = '')
                   OR a.license = $license::text)
              AND ($place::text IS NULL OR EXISTS (SELECT 1 FROM media_links m WHERE m.asset_uid = a.uid
                   AND m.target_type = 'place' AND m.target_id = $place::text))
              AND ($event::text IS NULL OR EXISTS (SELECT 1 FROM media_links m WHERE m.asset_uid = a.uid
                   AND m.target_type = 'event' AND m.target_id = $event::text))
              AND ($text::text IS NULL OR a.name ILIKE $text::text OR a.credit ILIKE $text::text
                   OR a.provider_key ILIKE $text::text)
            ORDER BY a.retrieved_at DESC NULLS LAST, a.citekey, a.name LIMIT ${limit.max(1)} OFFSET $offset"""
        .query[(MediaItemRow, Int)]
        .run()
        .toList
      val ts = targetsOf(rows.map(_._1.uid))
      MediaList.of(rows.headOption.fold(0)(_._2), rows.map((r, _) => item(r, ts.getOrElse(r.uid, Nil))), offset)

  def show(ds: DataSource, uid: String): Option[MediaItem] =
    connect(ds):
      sql"SELECT ${SqlLiteral(itemCols)} FROM assets a WHERE a.uid = $uid::uuid"
        .query[MediaItemRow]
        .run()
        .headOption
        .map(r => item(r, targetsOf(List(r.uid)).getOrElse(r.uid, Nil)))

  /** The thumbnail's bytes and type — a source's figure without one is its own thumbnail. */
  def thumb(ds: DataSource, uid: String): Option[(String, Array[Byte])] =
    connect(ds):
      sql"""SELECT CASE WHEN thumb IS NOT NULL THEN coalesce(thumb_mime, mime) ELSE mime END, coalesce(thumb, bytes)
            FROM assets WHERE uid = $uid::uuid
              AND (thumb IS NOT NULL OR provider IS NULL OR provider IN (${MediaProvider.Calibre}, ${MediaProvider.OpenLibrary}))"""
        .query[(String, Array[Byte])]
        .run()
        .headOption

  /** The stored rendition's (or figure's) bytes and type. */
  def file(ds: DataSource, uid: String): Option[(String, Array[Byte])] =
    connect(ds):
      sql"SELECT mime, bytes FROM assets WHERE uid = $uid::uuid".query[(String, Array[Byte])].run().headOption

  def facets(ds: DataSource): MediaFacets =
    connect(ds):
      val providers = sql"""SELECT coalesce(provider, ${MediaProvider.Source}), count(*)::int FROM assets
            GROUP BY 1 ORDER BY 2 DESC, 1""".query[(String, Int)].run().toList.map(MediaCount(_, _))
      val licences = sql"""SELECT coalesce(nullif(btrim(license), ''), 'none'), count(*)::int FROM assets
            GROUP BY 1 ORDER BY 2 DESC, 1""".query[(String, Int)].run().toList.map(MediaCount(_, _))
      MediaFacets(providers, licences)

  /** The media layer: geolocated pictures in the span, by kind, provider and licence. */
  def geo(ds: DataSource, q: Api.MediaGeoQuery, limit: Int): List[MediaGeoHit] =
    val k = q.kind.map(_.trim.toLowerCase).filter(_.nonEmpty)
    val (provider, license) = (clean(q.provider), clean(q.license))
    connect(ds):
      sql"""SELECT ${SqlLiteral(geoCols)}
            FROM assets a
            WHERE a.geom IS NOT NULL
              AND ($k::text IS NULL OR a.kind = $k::text)
              AND (${q.from}::bigint IS NULL OR a.period && int8range(${q.from}, ${q.to}))
              AND ($provider::text IS NULL OR ($provider::text = ${MediaProvider.Source} AND a.provider IS NULL)
                   OR a.provider = $provider::text)
              AND ($license::text IS NULL OR ($license::text = 'none' AND coalesce(btrim(a.license), '') = '')
                   OR a.license = $license::text)
            ORDER BY lower(a.period) NULLS LAST LIMIT ${limit.max(1)}"""
        .query[MediaGeoRow]
        .run()
        .toList
        .map(geoHit)

  /** The pictures of `uids` as the map's tiles draw one ([[geo]]'s hits), by uid. */
  def geoOf(ds: DataSource, uids: List[String]): Map[String, MediaGeoHit] =
    if uids.isEmpty then Map.empty
    else
      connect(ds):
        sql"""SELECT ${SqlLiteral(geoCols)} FROM assets a
            WHERE a.geom IS NOT NULL AND a.uid = ANY($uids::uuid[])"""
          .query[MediaGeoRow]
          .run()
          .map(r => r.uid -> geoHit(r))
          .toMap

  /** The pictures of the media layer as [[TileRepo]] selects them — [[geo]]'s selection, every one whose dates overlap
    * `window` (none: whatever their dates), in the tile's box `envelope`: uid, point and dates.
    */
  private[db] def tileCandidates(
      kind: Option[String],
      provider: Option[String],
      license: Option[String],
      window: Option[(Long, Long)],
      envelope: Sql
  ): Sql =
    val k = kind.map(_.trim.toLowerCase).filter(_.nonEmpty)
    val ps = List.newBuilder[Sql]
    k.foreach(v => ps += Sql.of("a.kind = ?", Param.S(v)))
    clean(provider).foreach(p =>
      ps += (if p == MediaProvider.Source then Sql.raw("a.provider IS NULL") else Sql.of("a.provider = ?", Param.S(p)))
    )
    clean(license).foreach(l =>
      ps += (if l == "none" then Sql.raw("coalesce(btrim(a.license), '') = ''")
             else Sql.of("a.license = ?", Param.S(l)))
    )
    window.foreach((lo, hi) => ps += Sql.raw(RangeSql.overlaps("a.period", s"$lo::bigint", s"$hi::bigint")))
    val rest = ps.result().foldLeft(Sql.empty)((acc, p) => acc ++ Sql.raw(" AND ") ++ p)
    Sql.raw(
      "SELECT a.uid::text AS key, ST_X(q.pt) AS lon, ST_Y(q.pt) AS lat, lower(a.period) AS ps, upper(a.period) - 1 AS pe," +
        " false AS ub" +
        " FROM assets a CROSS JOIN LATERAL (SELECT CASE WHEN GeometryType(a.geom) = 'POINT' THEN a.geom" +
        " ELSE ST_PointOnSurface(a.geom) END AS pt) q WHERE a.geom IS NOT NULL AND a.geom && "
    ) ++ envelope ++ rest

  private val geoCols =
    """a.citekey, a.name, a.kind, ST_Y(ST_PointOnSurface(a.geom)), ST_X(ST_PointOnSurface(a.geom)),
       lower(a.period), upper(a.period) - 1, a.caption, a.uid::text, a.provider, a.license, a.credit,
       a.thumb IS NOT NULL, a.source_url, a.license_url, a.author"""

  private def geoHit(r: MediaGeoRow): MediaGeoHit =
    MediaGeoHit(
      r.citekey,
      r.name,
      r.kind,
      r.lat,
      r.lon,
      r.periodStart,
      r.periodEnd,
      r.caption,
      r.uid,
      r.provider,
      r.license,
      r.credit,
      Option.when(r.hasThumb)(MediaThumb.thumbPath(r.uid)),
      fileUrl = Some(MediaThumb.filePath(r.uid)),
      sourceUrl = r.sourceUrl,
      licenseUrl = r.licenseUrl,
      author = r.author
    )

  // ── a source's pictures: its cover and its illustration (#1446) ──────────────────────────────

  /** One figure of a source, as [[FigurePick]] weighs it: uid, name, size. */
  final case class SourceFigure(uid: String, name: String, width: Option[Int], height: Option[Int], hasThumb: Boolean)

  /** A source's stored figures (images only). */
  def figures(ds: DataSource, citekey: String): List[SourceFigure] =
    connect(ds):
      sql"""SELECT uid::text, name, width, height, thumb IS NOT NULL FROM assets
            WHERE citekey = $citekey AND mime LIKE 'image/%' ORDER BY name"""
        .query[(String, String, Option[Int], Option[Int], Boolean)]
        .run()
        .toList
        .map(SourceFigure.apply)

  /** A source's converted text — where its figures are linked from. */
  def body(ds: DataSource, citekey: String): String =
    connect(ds):
      sql"SELECT coalesce(ocr_markdown, '') FROM sources WHERE citekey = $citekey"
        .query[String]
        .run()
        .headOption
        .getOrElse("")

  /** The bytes of a picture (to make its thumbnail from). */
  def bytesOf(ds: DataSource, uid: String): Option[(String, Array[Byte])] = file(ds, uid)

  /** Store a thumbnail for a stored picture (a figure chosen to illustrate its source). */
  def setThumb(
      ds: DataSource,
      uid: String,
      bytes: Array[Byte],
      mime: String,
      width: Option[Int],
      height: Option[Int]
  ): Unit =
    val _ = transact(ds):
      sql"""UPDATE assets SET thumb = $bytes, thumb_mime = $mime, thumb_width = $width, thumb_height = $height
            WHERE uid = $uid::uuid""".update.run()

  /** Make `uid` the source's picture in `role` — replacing the one it had in that role. */
  def setSourcePicture(ds: DataSource, citekey: String, uid: String, role: String, property: Option[String]): Unit =
    val _ = transact(ds):
      sql"""DELETE FROM media_links WHERE target_type = 'source' AND target_id = $citekey AND role = $role
              AND asset_uid <> $uid::uuid""".update.run()
      sql"""INSERT INTO media_links (asset_uid, target_type, target_id, role, property, item, rank)
            VALUES ($uid::uuid, 'source', $citekey, $role, $property, NULL, 0)
            ON CONFLICT (asset_uid, target_type, target_id, role) DO UPDATE SET property = EXCLUDED.property""".update
        .run()

  /** The source's picture in `role`, if it has one: its uid and the provider key or figure name. */
  def sourcePicture(ds: DataSource, citekey: String, role: String): Option[(String, String)] =
    connect(ds):
      sql"""SELECT a.uid::text, coalesce(a.provider_key, a.name) FROM media_links m JOIN assets a ON a.uid = m.asset_uid
            WHERE m.target_type = 'source' AND m.target_id = $citekey AND m.role = $role LIMIT 1"""
        .query[(String, String)]
        .run()
        .headOption

  /** Does the source exist (under this citekey or an old one)? Its current citekey. */
  def sourceKey(ds: DataSource, citekey: String): Option[String] =
    connect(ds):
      sql"""SELECT citekey FROM sources WHERE citekey = $citekey
            UNION ALL SELECT citekey FROM source_aliases WHERE alias = $citekey LIMIT 1"""
        .query[String]
        .run()
        .headOption

  /** Sources with figures, for illustrations: those without one yet (all with `refresh`), or `only`. */
  def illustrationCandidates(ds: DataSource, only: Option[String], refresh: Boolean, limit: Int): List[String] =
    connect(ds):
      sql"""SELECT s.citekey FROM sources s
            WHERE ($only::text IS NULL OR s.citekey = $only::text)
              AND EXISTS (SELECT 1 FROM assets a WHERE a.citekey = s.citekey AND a.mime LIKE 'image/%')
              AND ($refresh OR NOT EXISTS (SELECT 1 FROM media_links m WHERE m.target_type = 'source'
                    AND m.target_id = s.citekey AND m.role = ${MediaRole.Illustration}))
            ORDER BY s.citekey LIMIT ${limit.max(1)}""".query[String].run().toList

  /** A book's identifiers the Open Library Covers API answers by. */
  final case class CoverKeys(citekey: String, isbn: Option[String], oclc: Option[String])

  /** Sources with an ISBN or OCLC number and no cover (all with `refresh`), not asked of Open Library yet (unless
    * `refresh`), or `only`.
    */
  def coverCandidates(ds: DataSource, only: Option[String], refresh: Boolean, limit: Int): List[CoverKeys] =
    connect(ds):
      sql"""SELECT s.citekey,
                   (SELECT min(i.value) FROM source_identifiers i WHERE i.source_uid = s.uid AND i.scheme = 'isbn'),
                   (SELECT min(i.value) FROM source_identifiers i WHERE i.source_uid = s.uid AND i.scheme = 'oclc')
            FROM sources s
            WHERE ($only::text IS NULL OR s.citekey = $only::text)
              AND EXISTS (SELECT 1 FROM source_identifiers i WHERE i.source_uid = s.uid AND i.scheme IN ('isbn', 'oclc'))
              AND ($refresh OR NOT EXISTS (SELECT 1 FROM media_links m WHERE m.target_type = 'source'
                    AND m.target_id = s.citekey AND m.role = ${MediaRole.Cover}))
              AND ($refresh OR $only::text IS NOT NULL OR NOT EXISTS (SELECT 1 FROM media_checks c
                    WHERE c.provider = ${MediaProvider.OpenLibrary} AND c.target_type = 'source' AND c.target_id = s.citekey))
            ORDER BY s.citekey LIMIT ${limit.max(1)}"""
        .query[(String, Option[String], Option[String])]
        .run()
        .toList
        .map(CoverKeys.apply)

  // ── a source's figures take the source's licence ─────────────────────────────────────────────

  private val figureCandidates =
    """FROM assets a JOIN sources s ON s.citekey = a.citekey
       WHERE a.provider IS NULL AND (a.license IS NULL OR btrim(a.license) = '')
         AND s.license IS NOT NULL AND btrim(s.license) <> ''"""

  /** Count (and with `apply`, write) the figures with no licence whose source records one. */
  def fillFigureLicences(ds: DataSource, apply: Boolean): FigureLicenceFill =
    transact(ds):
      val by = Sql
        .raw(s"SELECT a.citekey, count(*)::int $figureCandidates GROUP BY a.citekey ORDER BY 2 DESC, 1")
        .frag
        .query[(String, Int)]
        .run()
        .toList
        .map(MediaCount(_, _))
      val candidates = by.map(_.n).sum
      val updated =
        if !apply then 0
        else sql"""UPDATE assets a SET license = btrim(s.license) FROM sources s
              WHERE s.citekey = a.citekey AND a.provider IS NULL AND (a.license IS NULL OR btrim(a.license) = '')
                AND s.license IS NOT NULL AND btrim(s.license) <> ''""".update.run()
      FigureLicenceFill(candidates, updated, apply, by)
