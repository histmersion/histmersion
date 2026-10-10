package com.histmersion.db

import com.histmersion.core.*

import com.augustnagro.magnum.*
import javax.sql.DataSource

private[db] final case class MediaThumbRow(uid: String, role: String, credit: String, license: Option[String],
    licenseUrl: Option[String], sourceUrl: Option[String], width: Option[Int], height: Option[Int])
given DbCodec[MediaThumbRow] = DbCodec.derived

private[db] final case class MediaItemRow(uid: String, name: String, mime: String, bytes: Int,
    citekey: Option[String], provider: Option[String], providerKey: Option[String], width: Option[Int],
    height: Option[Int], kind: Option[String], caption: Option[String], license: Option[String],
    licenseUrl: Option[String], author: Option[String], credit: Option[String], sourceUrl: Option[String],
    originalUrl: Option[String], retrievedAt: Option[String], hasThumb: Boolean)
given DbCodec[MediaItemRow] = DbCodec.derived

private[db] final case class MediaGeoRow(citekey: Option[String], name: String, kind: Option[String], lat: Double,
    lon: Double, periodStart: Option[Long], periodEnd: Option[Long], caption: Option[String], uid: String,
    provider: Option[String], license: Option[String], credit: Option[String], hasThumb: Boolean)
given DbCodec[MediaGeoRow] = DbCodec.derived

private[db] final case class MediaTargetRow(targetType: String, targetId: String, role: String, label: String,
    item: String)
given DbCodec[MediaTargetRow] = DbCodec.derived

/** Pictures from outside providers (#1446, V082): one `assets` row each, with `provider` set instead of
  * a `citekey`, linked to the places and events they show through `media_links`; and the read paths
  * every stored picture shares — the media list and facets, the media layer, the cards' thumbnails.
  * Repos are stateless: the `DataSource` is a parameter. */
object MediaRepo:

  val Place = "place"
  val Event = "event"

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
      kind: Option[String])

  /** The licence fields a re-read refreshes on a stored picture (the bytes stay). */
  final case class Rights(license: String, licenseUrl: Option[String], author: Option[String], credit: String)

  /** Pure: the media kind (the layer's colour) of a picture shown in `role`. */
  def kindOf(role: String): String = role match
    case MediaRole.LocatorMap | MediaRole.RouteMap => AssetKind.Map
    case MediaRole.Plan                            => AssetKind.Plan
    case _                                         => AssetKind.Photo

  // ── which places and events to ask ───────────────────────────────────────────────────────────

  private val QidRe = "^Q[1-9][0-9]*$"

  /** One place's Wikidata item: an exact link first, else any; a `wikidata_q…` id names its own. */
  private def placeQid(id: String)(using DbCon): Option[String] =
    sql"""SELECT external_id FROM place_links
          WHERE place_id = $id AND scheme = 'wikidata' AND external_id ~ $QidRe
          ORDER BY (relation = 'exact_match') DESC, external_id LIMIT 1""".query[String].run().headOption
      .orElse(Wikidata.qidOfPlaceId(id))

  /** The places and events an import asks about. `place`/`event` name one (an error when it has no
    * Wikidata item); otherwise the events with an item, then the places that hold findings, most
    * findings first — each only when this provider has not been asked about it yet, unless `refresh`. */
  def targets(ds: DataSource, provider: String, place: Option[String], event: Option[String], limit: Int,
      refresh: Boolean): Either[String, List[Target]] =
    connect(ds):
      (place.map(_.trim).filter(_.nonEmpty), event.map(_.trim).filter(_.nonEmpty)) match
        case (Some(pid), _) =>
          sql"SELECT label FROM places WHERE id = $pid".query[String].run().headOption match
            case None => Left(s"No place '$pid'")
            case Some(label) =>
              placeQid(pid).map(q => List(Target(Place, pid, q, label)))
                .toRight(s"place '$pid' ($label) has no Wikidata link")
        case (None, Some(eid)) =>
          sql"SELECT label, wikidata FROM events WHERE id = $eid".query[(String, Option[String])].run().headOption match
            case None                    => Left(s"No event '$eid'")
            case Some((label, Some(q)))  => Right(List(Target(Event, eid, q, label)))
            case Some((label, None))     =>
              Left(s"event '$eid' ($label) has no Wikidata item — set one with `histmersion events patch $eid --wikidata Q…`")
        case (None, None) =>
          val n = limit.max(1)
          val events = sql"""SELECT 'event', e.id, e.wikidata, e.label FROM events e
                WHERE e.wikidata IS NOT NULL
                  AND ($refresh OR NOT EXISTS (SELECT 1 FROM media_checks c WHERE c.provider = $provider
                        AND c.target_type = 'event' AND c.target_id = e.id))
                ORDER BY e.id LIMIT $n""".query[(String, String, String, String)].run().toList
          val rest = n - events.size
          val places =
            if rest <= 0 then Nil
            else sql"""WITH used AS (SELECT place_id, count(*) AS n FROM finding_places GROUP BY place_id)
                  SELECT 'place', u.place_id, l.qid, p.label
                  FROM used u
                  JOIN places p ON p.id = u.place_id
                  CROSS JOIN LATERAL (
                    SELECT pl.external_id AS qid FROM place_links pl
                    WHERE pl.place_id = u.place_id AND pl.scheme = 'wikidata' AND pl.external_id ~ $QidRe
                    ORDER BY (pl.relation = 'exact_match') DESC, pl.external_id LIMIT 1) l
                  WHERE ($refresh OR NOT EXISTS (SELECT 1 FROM media_checks c WHERE c.provider = $provider
                          AND c.target_type = 'place' AND c.target_id = u.place_id))
                  ORDER BY u.n DESC, u.place_id LIMIT $rest""".query[(String, String, String, String)].run().toList
          Right((events ++ places).map(Target.apply))

  // ── writes ───────────────────────────────────────────────────────────────────────────────────

  /** The pictures of `provider` already stored under these keys. */
  def known(ds: DataSource, provider: String, keys: List[String]): Map[String, Known] =
    if keys.isEmpty then Map.empty
    else connect(ds):
      sql"""SELECT provider_key, uid::text, license FROM assets
            WHERE provider = $provider AND provider_key = ANY($keys::text[])"""
        .query[(String, String, Option[String])].run().map((k, u, l) => k -> Known(u, l)).toMap

  /** Store a provider's picture (or replace the bytes and rights of the one stored under its key);
    * its point and period come from the place or event it is first stored for. Returns its uid and
    * whether it is new. */
  def save(ds: DataSource, a: ProviderAsset, firstTarget: (String, String)): (String, Boolean) =
    transact(ds):
      val sha = AssetRepo.sha256(a.bytes)
      val (uid, inserted) =
        sql"""INSERT INTO assets (citekey, name, mime, bytes, sha256, width, height, kind, license, provider,
                provider_key, source_url, original_url, author, credit, license_url, retrieved_at,
                thumb, thumb_mime, thumb_width, thumb_height)
              VALUES (NULL, ${a.name}, ${a.mime}, ${a.bytes}, $sha, ${a.width}, ${a.height}, ${a.kind},
                ${a.license}, ${a.provider}, ${a.key}, ${a.sourceUrl}, ${a.originalUrl}, ${a.author},
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

  /** A picture's point and period, where it has none: the place's point on surface and period, or
    * the event's own point (else its place's) and outer span. An open period (a place with no dates
    * is stored as `(,)`) is not copied: a time-filtered media layer shows dated pictures only. */
  private def placeAt(uid: String, target: (String, String))(using DbCon): Unit =
    val (tt, id) = target
    if tt == Place then
      sql"""UPDATE assets a SET geom = coalesce(a.geom, t.g),
              period = coalesce(a.period, CASE WHEN lower_inf(t.p) AND upper_inf(t.p) THEN NULL ELSE t.p END)
            FROM (SELECT ST_PointOnSurface(coalesce(geom_clipped, geom)) AS g, period AS p FROM places WHERE id = $id) t
            WHERE a.uid = $uid::uuid""".update.run()
    else
      sql"""UPDATE assets a SET geom = coalesce(a.geom, t.g),
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

  /** Link a stored picture to a place or event in a role. Returns whether the link is new. */
  def link(ds: DataSource, uid: String, targetType: String, targetId: String, role: String,
      property: Option[String], item: Option[String], rank: Int): Boolean =
    transact(ds):
      sql"""INSERT INTO media_links (asset_uid, target_type, target_id, role, property, item, rank)
            VALUES ($uid::uuid, $targetType, $targetId, $role, $property, $item, $rank)
            ON CONFLICT (asset_uid, target_type, target_id, role) DO UPDATE SET
              property = EXCLUDED.property, item = EXCLUDED.item, rank = EXCLUDED.rank
            RETURNING (xmax = 0)""".query[Boolean].run().head

  /** Record a place's Commons category (Wikidata P373) as a link of the place. Returns whether new. */
  def commonsCategory(ds: DataSource, placeId: String, category: String, qid: String): Boolean =
    transact(ds):
      sql"""INSERT INTO place_links (place_id, scheme, external_id, relation, import_source, import_ref)
            VALUES ($placeId, 'commons', $category, 'see_also', 'media:wikidata', $qid)
            ON CONFLICT (place_id, scheme, external_id) DO NOTHING""".update.run() > 0

  /** Remember that `provider` was asked about a place or event, and how many pictures it gave. */
  def checked(ds: DataSource, provider: String, targetType: String, targetId: String, found: Int): Unit =
    val _ = transact(ds):
      sql"""INSERT INTO media_checks (provider, target_type, target_id, checked_at, found)
            VALUES ($provider, $targetType, $targetId, now(), $found)
            ON CONFLICT (provider, target_type, target_id) DO UPDATE SET
              checked_at = EXCLUDED.checked_at, found = EXCLUDED.found""".update.run()

  // ── reads ────────────────────────────────────────────────────────────────────────────────────

  /** A place's or event's pictures for its card, best first (the role order of [[MediaRole]], then
    * the provider's preference): one indexed lookup, so it fits the hover budget. */
  def thumbsIn(targetType: String, targetId: String, limit: Int = 4)(using DbCon): List[MediaThumb] =
    sql"""SELECT a.uid::text, m.role, coalesce(a.credit, ''), a.license, a.license_url, a.source_url,
                 a.thumb_width, a.thumb_height
          FROM media_links m JOIN assets a ON a.uid = m.asset_uid
          WHERE m.target_type = $targetType AND m.target_id = $targetId AND a.thumb IS NOT NULL
          ORDER BY array_position(${MediaRole.all}::text[], m.role), m.rank, a.uid
          LIMIT ${limit.max(1)}""".query[MediaThumbRow].run().toList.map(r =>
      MediaThumb(r.uid, r.role, MediaThumb.thumbPath(r.uid), r.credit, r.license, r.licenseUrl, r.sourceUrl,
        r.width, r.height))

  def thumbs(ds: DataSource, targetType: String, targetId: String, limit: Int = 4): List[MediaThumb] =
    connect(ds)(thumbsIn(targetType, targetId, limit))

  private val itemCols =
    """a.uid::text, a.name, a.mime, length(a.bytes), a.citekey, a.provider, a.provider_key, a.width, a.height,
       a.kind, a.caption, a.license, a.license_url, a.author, a.credit, a.source_url, a.original_url,
       to_char(a.retrieved_at AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS"Z"'), a.thumb IS NOT NULL"""

  private def item(r: MediaItemRow, targets: List[MediaTarget]): MediaItem =
    MediaItem(r.uid, r.name, r.mime, r.bytes, r.citekey, r.provider, r.providerKey, r.width, r.height, r.kind,
      r.caption, r.license, r.licenseUrl, r.author, r.credit, r.sourceUrl, r.originalUrl, r.retrievedAt,
      Option.when(r.hasThumb)(MediaThumb.thumbPath(r.uid)), MediaThumb.filePath(r.uid), targets)

  private def targetsOf(uids: List[String])(using DbCon): Map[String, List[MediaTarget]] =
    if uids.isEmpty then Map.empty
    else
      sql"""SELECT asset_uid::text, target_type, target_id, role, coalesce(property, ''), coalesce(item, '')
            FROM media_links WHERE asset_uid = ANY($uids::uuid[])
            ORDER BY asset_uid, target_type, target_id, rank"""
        .query[(String, String, String, String, String, String)].run().toList
        .groupMap(_._1)((_, tt, ti, role, p, q) =>
          MediaTarget(tt, ti, role, Option(p).filter(_.nonEmpty), Option(q).filter(_.nonEmpty)))

  private def clean(o: Option[String]): Option[String] = o.map(_.trim).filter(_.nonEmpty)

  /** The list: by provider (`source` = a source's figures), licence (`none` = no licence), the place
    * or event a picture is linked to, and a text match; newest first. `total` ignores the limit. */
  def list(ds: DataSource, q: Api.MediaQuery, limit: Int): MediaList =
    val (provider, license) = (clean(q.provider), clean(q.license))
    val (place, event) = (clean(q.place), clean(q.event))
    val text = clean(q.q).map(t => s"%$t%")
    connect(ds):
      val rows = sql"""SELECT ${SqlLiteral(itemCols)}, count(*) OVER ()::int FROM assets a
            WHERE (${provider}::text IS NULL OR (${provider}::text = ${MediaProvider.Source} AND a.provider IS NULL)
                   OR a.provider = ${provider}::text)
              AND (${license}::text IS NULL OR (${license}::text = 'none' AND coalesce(btrim(a.license), '') = '')
                   OR a.license = ${license}::text)
              AND (${place}::text IS NULL OR EXISTS (SELECT 1 FROM media_links m WHERE m.asset_uid = a.uid
                   AND m.target_type = 'place' AND m.target_id = ${place}::text))
              AND (${event}::text IS NULL OR EXISTS (SELECT 1 FROM media_links m WHERE m.asset_uid = a.uid
                   AND m.target_type = 'event' AND m.target_id = ${event}::text))
              AND (${text}::text IS NULL OR a.name ILIKE ${text}::text OR a.credit ILIKE ${text}::text
                   OR a.provider_key ILIKE ${text}::text)
            ORDER BY a.retrieved_at DESC NULLS LAST, a.citekey, a.name LIMIT ${limit.max(1)}"""
        .query[(MediaItemRow, Int)].run().toList
      val ts = targetsOf(rows.map(_._1.uid))
      MediaList(rows.headOption.fold(0)(_._2), rows.map((r, _) => item(r, ts.getOrElse(r.uid, Nil))))

  def show(ds: DataSource, uid: String): Option[MediaItem] =
    connect(ds):
      sql"SELECT ${SqlLiteral(itemCols)} FROM assets a WHERE a.uid = $uid::uuid".query[MediaItemRow].run()
        .headOption.map(r => item(r, targetsOf(List(r.uid)).getOrElse(r.uid, Nil)))

  /** The thumbnail's bytes and type. */
  def thumb(ds: DataSource, uid: String): Option[(String, Array[Byte])] =
    connect(ds):
      sql"SELECT coalesce(thumb_mime, mime), thumb FROM assets WHERE uid = $uid::uuid AND thumb IS NOT NULL"
        .query[(String, Array[Byte])].run().headOption

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
      sql"""SELECT a.citekey, a.name, a.kind, ST_Y(ST_PointOnSurface(a.geom)), ST_X(ST_PointOnSurface(a.geom)),
                   lower(a.period), upper(a.period) - 1, a.caption, a.uid::text, a.provider, a.license, a.credit,
                   a.thumb IS NOT NULL
            FROM assets a
            WHERE a.geom IS NOT NULL
              AND (${k}::text IS NULL OR a.kind = ${k}::text)
              AND (${q.from}::bigint IS NULL OR a.period && int8range(${q.from}, ${q.to}))
              AND (${provider}::text IS NULL OR (${provider}::text = ${MediaProvider.Source} AND a.provider IS NULL)
                   OR a.provider = ${provider}::text)
              AND (${license}::text IS NULL OR (${license}::text = 'none' AND coalesce(btrim(a.license), '') = '')
                   OR a.license = ${license}::text)
            ORDER BY lower(a.period) NULLS LAST LIMIT ${limit.max(1)}"""
        .query[MediaGeoRow].run().toList.map(r =>
          MediaGeoHit(r.citekey, r.name, r.kind, r.lat, r.lon, r.periodStart, r.periodEnd, r.caption, r.uid,
            r.provider, r.license, r.credit, Option.when(r.hasThumb)(MediaThumb.thumbPath(r.uid))))

  // ── a source's figures take the source's licence ─────────────────────────────────────────────

  private val figureCandidates =
    """FROM assets a JOIN sources s ON s.citekey = a.citekey
       WHERE a.provider IS NULL AND (a.license IS NULL OR btrim(a.license) = '')
         AND s.license IS NOT NULL AND btrim(s.license) <> ''"""

  /** Count (and with `apply`, write) the figures with no licence whose source records one. */
  def fillFigureLicences(ds: DataSource, apply: Boolean): FigureLicenceFill =
    transact(ds):
      val by = Sql.raw(s"SELECT a.citekey, count(*)::int $figureCandidates GROUP BY a.citekey ORDER BY 2 DESC, 1")
        .frag.query[(String, Int)].run().toList.map(MediaCount(_, _))
      val candidates = by.map(_.n).sum
      val updated =
        if !apply then 0
        else sql"""UPDATE assets a SET license = btrim(s.license) FROM sources s
              WHERE s.citekey = a.citekey AND a.provider IS NULL AND (a.license IS NULL OR btrim(a.license) = '')
                AND s.license IS NOT NULL AND btrim(s.license) <> ''""".update.run()
      FigureLicenceFill(candidates, updated, apply, by)
