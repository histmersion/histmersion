package com.histmersion
package domain.media.assets

import com.histmersion.util.database.{literal, RangeSql}

import com.histmersion.api.Api

import com.histmersion.util.database.given
import com.augustnagro.magnum.*
import com.histmersion.util.database.NewtypeColumns.given
import com.histmersion.domain.media.MediaRole
import com.histmersion.api.exchange.Bundle
import com.histmersion.api.media.{
  ImageSize,
  MediaCount,
  MediaFacets,
  MediaGeoHit,
  MediaItem,
  MediaList,
  MediaProvider,
  MediaTarget,
  MediaThumb
}
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.domain.media.imports.Target
import com.histmersion.domain.media.imports.MediaMatch
import com.histmersion.domain.library.Citekey
import com.histmersion.domain.catalogue.evidence.EventMatcher
import com.histmersion.domain.catalogue.Place
import com.histmersion.api.ByteCount
import com.histmersion.api.RowCount
import com.histmersion.api.personal.UnreadPage
import com.histmersion.domain.DeepTime
import com.histmersion.api.catalogue.Latitude
import com.histmersion.api.catalogue.Longitude
import com.histmersion.api.catalogue.Year
import com.histmersion.util.database.Unused

/** Pictures (#1446, V082): one `assets` row each — a provider's with `provider` set instead of a `citekey`, a source's
  * figure with its citekey — and the places, events and sources they are shown on (`media_links`); the read paths every
  * stored picture shares — the media list and facets, the media layer, the cards' thumbnails. Every method runs on the
  * connection it is given.
  */
private[assets] object MediaRepo:
  type Degrees = Degrees.T
  object Degrees extends Newtype[Double, Pure]
  type Limit = Limit.T
  object Limit extends Newtype[Int, Pure]
  type PictureKey = PictureKey.T
  object PictureKey extends Newtype[String, Pure]
  type GapItem = GapItem.T
  object GapItem extends Newtype[String, Pure]
  type GapDetail = GapDetail.T
  object GapDetail extends Newtype[String, Pure]

  // ── writes ───────────────────────────────────────────────────────────────────────────────────

  /** The pictures of `provider` already stored under these keys. */

  type LinkProperty = LinkProperty.T
  object LinkProperty extends Newtype[String, Pure]
  type LinkItem = LinkItem.T
  object LinkItem extends Newtype[String, Pure]
  type LinkRank = LinkRank.T
  object LinkRank extends Newtype[Int, Pure]
  type SetSourcePictureProperty = SetSourcePictureProperty.T
  object SetSourcePictureProperty extends Newtype[String, Pure]

  def known(provider: MediaItemRow.Provider, keys: List[MediaItemRow.ProviderKey])(using
      DbCon
  ): Map[MediaItemRow.ProviderKey, Known] =
    if keys.isEmpty then Map.empty
    else
      sql"""SELECT provider_key, uid::text, license FROM assets
          WHERE provider = $provider AND provider_key = ANY($keys::text[])"""
        .query[(MediaItemRow.ProviderKey, Known.Uid, Option[Known.License])]
        .run()
        .map((k, u, l) => k -> Known(u, l))
        .toMap

  /** Store a provider's picture (or replace the bytes and rights of the one stored under its key); its point and period
    * come from the place or event it is first stored for. Returns its uid and whether it is new.
    */
  def save(a: ProviderAsset, firstTarget: (Target.TargetType, Target.Id))(using DbCon): (MediaItemRow.Uid, Boolean) =
    val sha = NewFigure.sha256(NewFigure.Bytes(a.bytes.value))
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
            RETURNING uid::text, (xmax = 0)""".query[(MediaItemRow.Uid, Boolean)].run().head
    placeAt(uid, firstTarget)
    (uid, inserted)

  /** A picture's point and period, where it has none: the place's point on surface and period, or the event's own point
    * (else its place's) and outer span. An open period (a place with no dates is stored as `(,)`) is not copied: a
    * time-filtered media layer shows dated pictures only.
    */
  private def placeAt(uid: MediaItemRow.Uid, target: (Target.TargetType, Target.Id))(using DbCon): Unit =
    val (tt, id) = target
    if tt == PictureTarget.Source.wire then () // a source's picture (a cover) is not on the map
    else if tt == PictureTarget.Place.wire then
      Unused(
        sql"""UPDATE assets a SET geom = coalesce(a.geom, t.g),
              period = coalesce(a.period, CASE WHEN lower_inf(t.p) AND upper_inf(t.p) THEN NULL ELSE t.p END)
            FROM (SELECT ST_PointOnSurface(coalesce(geom_clipped, geom)) AS g, period AS p FROM places WHERE id = $id) t
            WHERE a.uid = $uid::uuid""".update.run(),
        Unused.Why.Idempotent
      )
    else
      Unused(
        sql"""UPDATE assets a SET geom = coalesce(a.geom, t.g),
              period = coalesce(a.period, CASE WHEN lower_inf(t.p) AND upper_inf(t.p) THEN NULL ELSE t.p END)
            FROM (SELECT ST_PointOnSurface(coalesce(e.geom, p.geom)) AS g, e.outer_span AS p
                  FROM events e LEFT JOIN places p ON p.id = e.place_id WHERE e.id = $id) t
            WHERE a.uid = $uid::uuid""".update.run(),
        Unused.Why.Idempotent
      )
    ()

  /** Re-read rights on a stored picture whose bytes are kept (a refresh that does not re-download). */
  def refreshRights(uid: MediaItemRow.Uid, r: Rights)(using DbCon): Unit =
    Unused(
      sql"""UPDATE assets SET license = ${r.license}, license_url = ${r.licenseUrl}, author = ${r.author},
            credit = ${r.credit}, retrieved_at = now() WHERE uid = $uid::uuid""".update.run(),
      Unused.Why.Idempotent
    )

  /** Link a stored picture to a place or event in a role — with `basis`, why a collection's record is tied to it
    * (V094). Returns whether the link is new.
    */
  def link(
      uid: MediaItemRow.Uid,
      targetType: Target.TargetType,
      targetId: Target.Id,
      role: MediaRole.Wire,
      property: Option[LinkProperty],
      item: Option[LinkItem],
      rank: LinkRank,
      basis: Option[MediaMatch.Basis]
  )(using DbCon): Boolean =
    sql"""INSERT INTO media_links (asset_uid, target_type, target_id, role, property, item, rank, basis)
          VALUES ($uid::uuid, $targetType, $targetId, $role, $property, $item, $rank, $basis)
          ON CONFLICT (asset_uid, target_type, target_id, role) DO UPDATE SET
            property = EXCLUDED.property, item = EXCLUDED.item, rank = EXCLUDED.rank,
            basis = coalesce(EXCLUDED.basis, media_links.basis)
          RETURNING (xmax = 0)""".query[Boolean].run().head

  /** Store a thumbnail for a stored picture (a figure chosen to illustrate its source). */
  def setThumb(
      uid: MediaItemRow.Uid,
      bytes: ProviderAsset.Thumb,
      mime: ProviderAsset.ThumbMime,
      width: Option[MediaThumbRow.Width],
      height: Option[MediaThumbRow.Height]
  )(using
      DbCon
  ): Unit =
    Unused(
      sql"""UPDATE assets SET thumb = $bytes, thumb_mime = $mime, thumb_width = $width, thumb_height = $height
          WHERE uid = $uid::uuid""".update.run(),
      Unused.Why.Idempotent
    )

  /** Make `uid` the source's picture in `role` — replacing the one it had in that role. */
  def setSourcePicture(
      citekey: Citekey,
      uid: MediaItemRow.Uid,
      role: MediaRole.Wire,
      property: Option[SetSourcePictureProperty]
  )(using DbCon): Unit =
    Unused(
      sql"""DELETE FROM media_links WHERE target_type = 'source' AND target_id = $citekey AND role = $role
            AND asset_uid <> $uid::uuid""".update.run(),
      Unused.Why.Idempotent
    )
    val _ = sql"""INSERT INTO media_links (asset_uid, target_type, target_id, role, property, item, rank)
          VALUES ($uid::uuid, 'source', $citekey, $role, $property, NULL, 0)
          ON CONFLICT (asset_uid, target_type, target_id, role) DO UPDATE SET property = EXCLUDED.property""".update
      .run()

  /** The source's picture in `role`, if it has one: its uid and the provider key or figure name. */
  def sourcePicture(citekey: Citekey, role: MediaRole.Wire)(using DbCon): Option[(MediaItemRow.Uid, PictureKey)] =
    sql"""SELECT a.uid::text, coalesce(a.provider_key, a.name) FROM media_links m JOIN assets a ON a.uid = m.asset_uid
          WHERE m.target_type = 'source' AND m.target_id = $citekey AND m.role = $role LIMIT 1"""
      .query[(MediaItemRow.Uid, PictureKey)]
      .run()
      .headOption

  // ── reads ────────────────────────────────────────────────────────────────────────────────────

  private def thumbOf(r: MediaThumbRow): MediaThumb =
    MediaThumb(
      MediaItem.Uid(r.uid.value),
      MediaThumb.Role(r.role.value),
      MediaThumb.Url(MediaThumb.thumbPath(Bundle.Asset.Uid(r.uid.value)).value),
      MediaItem.Credit(r.credit.value),
      r.license.map(l => MediaItem.License(l.value)),
      r.licenseUrl.map(u => MediaItem.LicenseUrl(u.value)),
      r.sourceUrl.map(u => MediaItem.SourceUrl(u.value)),
      r.width.map(w => ImageSize.Width(w.value)),
      r.height.map(h => ImageSize.Height(h.value))
    )

  /** A place's or event's (or a source's) pictures for its card, best first (the role order of [[MediaRole]], then the
    * provider's preference): one indexed lookup, so it fits the hover budget. A source's picture without a stored
    * thumbnail is shown from its own bytes (a figure is already small enough); a provider's picture of a place needs
    * one.
    */
  def thumbsIn(targetType: Target.TargetType, targetId: Target.Id, limit: Limit)(using DbCon): List[MediaThumb] =
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
          LIMIT ${limit.value.max(1)}""".query[MediaThumbRow].run().toList.map(thumbOf)

  /** Pure: `km` as degrees of arc for `ST_DWithin` on lon/lat geometry — a latitude degree, so a little generous
    * east–west away from the equator, which a "same site" test can afford.
    */
  def kmToDegrees(km: EventMatcher.NearFinding.Km): Degrees = Degrees(km.value.max(0) / 111.32)

  /** A PLACE's pictures for its card (#1446): its own, and those stored for its equivalents —
    *   - any place with the same Wikidata item (the picture's `item`): Mycenae is `pleiades_570491` and a Wikidata
    *     import, a polity is one place per time slice;
    *   - an old id merged or renamed into it (`place_aliases`);
    *   - another gazetteer's POINT of the same name (the geocoder's key) within `sameSiteKm` — Pleiades and GeoNames
    *     listing one site twice — and that point's items.
    * Best role first, then the place's own before an equivalent's, then the provider's preference. Index lookups only
    * (label key, GiST on the point, the links' item), within the hover budget.
    */
  def placeThumbsIn(placeId: Place.Id, sameSiteKm: EventMatcher.NearFinding.Km, limit: Limit)(using
      DbCon
  ): List[MediaThumb] =
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
          LIMIT ${limit.value.max(1)}""".query[MediaThumbRow].run().toList.map(thumbOf)

  private val itemCols =
    sql"""a.uid::text, a.name, a.mime, length(a.bytes), a.citekey, a.provider, a.provider_key, a.width, a.height,
       a.kind, a.caption, a.license, a.license_url, a.author, a.credit, a.source_url, a.original_url,
       to_char(a.retrieved_at AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS"Z"'), a.thumb IS NOT NULL"""

  private def item(r: MediaItemRow, targets: List[MediaTarget]): MediaItem =
    MediaItem(
      MediaItem.Uid(r.uid.value),
      Bundle.Asset.FileName(r.name.value),
      Bundle.Asset.MimeText(r.mime.value),
      ByteCount(r.bytes.value.toLong),
      r.citekey.map(c => com.histmersion.api.library.Citekey(c.value)),
      r.provider.map(p => MediaItem.Provider(p.value)),
      r.providerKey.map(k => MediaItem.ProviderKey(k.value)),
      r.width.map(w => ImageSize.Width(w.value)),
      r.height.map(h => ImageSize.Height(h.value)),
      r.kind.map(k => MediaItem.Kind(k.value)),
      r.caption.map(c => MediaItem.Caption(c.value)),
      r.license.map(l => MediaItem.License(l.value)),
      r.licenseUrl.map(u => MediaItem.LicenseUrl(u.value)),
      r.author.map(a => MediaItem.Author(a.value)),
      r.credit.map(c => MediaItem.Credit(c.value)),
      r.sourceUrl.map(u => MediaItem.SourceUrl(u.value)),
      r.originalUrl.map(u => MediaItem.OriginalUrl(u.value)),
      r.retrievedAt.map(t => MediaItem.RetrievedAt(t.value)),
      Option.when(r.hasThumb.value)(MediaItem.ThumbUrl(MediaThumb.thumbPath(Bundle.Asset.Uid(r.uid.value)).value)),
      MediaItem.FileUrl(MediaThumb.filePath(Bundle.Asset.Uid(r.uid.value)).value),
      targets
    )

  private def targetsOf(uids: List[MediaItemRow.Uid])(using DbCon): Map[MediaItemRow.Uid, List[MediaTarget]] =
    if uids.isEmpty then Map.empty
    else
      sql"""SELECT asset_uid::text, target_type, target_id, role, coalesce(property, ''), coalesce(item, ''),
                   coalesce(basis, '')
            FROM media_links WHERE asset_uid = ANY($uids::uuid[])
            ORDER BY asset_uid, target_type, target_id, rank, role"""
        .query[(MediaItemRow.Uid, String, String, String, String, String, String)]
        .run()
        .toList
        .groupMap(_._1)((_, tt, ti, role, p, q, b) =>
          MediaTarget(
            MediaTarget.TargetType(tt),
            MediaTarget.TargetId(ti),
            MediaTarget.Role(role),
            Option(p).filter(_.nonEmpty).map(MediaTarget.Property(_)),
            Option(q).filter(_.nonEmpty).map(MediaTarget.Item(_)),
            Option(b).filter(_.nonEmpty).map(MediaTarget.Basis(_))
          )
        )

  private def clean(o: Option[String]): Option[String] = o.map(_.trim).filter(_.nonEmpty)

  /** The list: by provider (`source` = a source's figures), licence (`none` = no licence), the place or event a picture
    * is linked to, and a text match; newest first. `total` ignores the limit.
    */
  def list(q: Api.MediaQuery, limit: Limit)(using DbCon): MediaList =
    val (provider, license) = (clean(q.provider.map(_.value)), clean(q.license.map(_.value)))
    val (place, event) = (clean(q.place.map(_.value)), clean(q.event.map(_.value)))
    val text = clean(q.q.map(_.value)).map(t => s"%$t%")
    val offset = q.offset.map(_.value).getOrElse(0).max(0)
    val rows = sql"""SELECT $itemCols, count(*) OVER ()::int FROM assets a
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
          ORDER BY a.retrieved_at DESC NULLS LAST, a.citekey, a.name LIMIT ${limit.value.max(1)} OFFSET $offset"""
      .query[(MediaItemRow, Int)]
      .run()
      .toList
    val ts = targetsOf(rows.map(_._1.uid))
    MediaList.of(
      RowCount(rows.headOption.fold(0)(_._2)),
      rows.map((r, _) => item(r, ts.getOrElse(r.uid, Nil))),
      UnreadPage.Offset(offset)
    )

  def show(uid: MediaItemRow.Uid)(using DbCon): Option[MediaItem] =
    sql"SELECT $itemCols FROM assets a WHERE a.uid = $uid::uuid"
      .query[MediaItemRow]
      .run()
      .headOption
      .map(r => item(r, targetsOf(List(r.uid)).getOrElse(r.uid, Nil)))

  /** The thumbnail's bytes and type — a source's figure without one is its own thumbnail. */
  def thumb(uid: MediaItemRow.Uid)(using DbCon): Option[(ProviderAsset.ThumbMime, ProviderAsset.Thumb)] =
    sql"""SELECT CASE WHEN thumb IS NOT NULL THEN coalesce(thumb_mime, mime) ELSE mime END, coalesce(thumb, bytes)
          FROM assets WHERE uid = $uid::uuid
            AND (thumb IS NOT NULL OR provider IS NULL OR provider IN (${MediaProvider.Calibre}, ${MediaProvider.OpenLibrary}))"""
      .query[(ProviderAsset.ThumbMime, ProviderAsset.Thumb)]
      .run()
      .headOption

  /** The stored rendition's (or figure's) bytes and type. */
  def file(uid: MediaItemRow.Uid)(using DbCon): Option[(AssetBytesRow.Mime, AssetBytesRow.Bytes)] =
    sql"SELECT mime, bytes FROM assets WHERE uid = $uid::uuid"
      .query[(AssetBytesRow.Mime, AssetBytesRow.Bytes)]
      .run()
      .headOption

  def facets(using DbCon): MediaFacets =
    val providers = sql"""SELECT coalesce(provider, ${MediaProvider.Source}), count(*)::int FROM assets
          GROUP BY 1 ORDER BY 2 DESC, 1"""
      .query[(String, Int)]
      .run()
      .toList
      .map((k, n) => MediaCount(MediaCount.Key(k), MediaCount.N(n)))
    val licences = sql"""SELECT coalesce(nullif(btrim(license), ''), 'none'), count(*)::int FROM assets
          GROUP BY 1 ORDER BY 2 DESC, 1"""
      .query[(String, Int)]
      .run()
      .toList
      .map((k, n) => MediaCount(MediaCount.Key(k), MediaCount.N(n)))
    MediaFacets(providers, licences)

  /** The media layer: geolocated pictures in the span, by kind, provider and licence. */
  def geo(q: Api.MediaGeoQuery, limit: Limit)(using DbCon): List[MediaGeoHit] =
    val k = q.kind.map(_.value.trim.toLowerCase).filter(_.nonEmpty)
    val (provider, license) = (clean(q.provider.map(_.value)), clean(q.license.map(_.value)))
    sql"""SELECT $geoCols
          FROM assets a
          WHERE a.geom IS NOT NULL
            AND ($k::text IS NULL OR a.kind = $k::text)
            AND (${q.from}::bigint IS NULL OR a.period && int8range(${q.from}, ${q.to}))
            AND ($provider::text IS NULL OR ($provider::text = ${MediaProvider.Source} AND a.provider IS NULL)
                 OR a.provider = $provider::text)
            AND ($license::text IS NULL OR ($license::text = 'none' AND coalesce(btrim(a.license), '') = '')
                 OR a.license = $license::text)
          ORDER BY lower(a.period) NULLS LAST, a.uid LIMIT ${limit.value.max(1)}"""
      .query[MediaGeoRow]
      .run()
      .toList
      .map(geoHit)

  /** The pictures of `uids` as the map's tiles draw one ([[geo]]'s hits), by uid. */
  def geoOf(uids: List[MediaItemRow.Uid])(using DbCon): Map[MediaItemRow.Uid, MediaGeoHit] =
    if uids.isEmpty then Map.empty
    else
      sql"""SELECT $geoCols FROM assets a
          WHERE a.geom IS NOT NULL AND a.uid = ANY($uids::uuid[])"""
        .query[MediaGeoRow]
        .run()
        .map(r => MediaItemRow.Uid(r.uid.value) -> geoHit(r))
        .toMap

  /** The pictures of the media layer as the map's tiles select them — [[geo]]'s selection, every one whose dates
    * overlap `window` (none: whatever their dates), in the tile's box `envelope`: uid, point and dates.
    */
  def tileCandidates(
      kind: Option[MediaItemRow.Kind],
      provider: Option[MediaItemRow.Provider],
      license: Option[MediaItemRow.License],
      window: Option[(DeepTime.SignedYear, DeepTime.SignedYear)],
      envelope: Frag
  ): Frag =
    val k = kind.map(_.value.trim.toLowerCase).filter(_.nonEmpty)
    val ps = List.newBuilder[Frag]
    k.foreach(v => ps += sql"a.kind = $v")
    clean(provider.map(_.value)).foreach(p =>
      ps += (if p == MediaProvider.Source then sql"a.provider IS NULL"
             else sql"a.provider = $p")
    )
    clean(license.map(_.value)).foreach(l =>
      ps += (if l == "none" then sql"coalesce(btrim(a.license), '') = ''"
             else sql"a.license = $l")
    )
    window.foreach((lo, hi) =>
      ps += RangeSql.overlaps(sql"a.period", sql"${lo.literal}::bigint", sql"${hi.literal}::bigint")
    )
    val rest = ps.result().foldLeft(sql"")((acc, p) => sql"$acc AND $p")
    sql"SELECT a.uid::text AS key, ST_X(q.pt) AS lon, ST_Y(q.pt) AS lat, lower(a.period) AS ps, upper(a.period) - 1 AS pe, false AS ub FROM assets a CROSS JOIN LATERAL (SELECT CASE WHEN GeometryType(a.geom) = 'POINT' THEN a.geom ELSE ST_PointOnSurface(a.geom) END AS pt) q WHERE a.geom IS NOT NULL AND a.geom && $envelope$rest"

  private val geoCols =
    sql"""a.citekey, a.name, a.kind, ST_Y(ST_PointOnSurface(a.geom)), ST_X(ST_PointOnSurface(a.geom)),
       lower(a.period), upper(a.period) - 1, a.caption, a.uid::text, a.provider, a.license, a.credit,
       a.thumb IS NOT NULL, a.source_url, a.license_url, a.author"""

  private def geoHit(r: MediaGeoRow): MediaGeoHit =
    MediaGeoHit(
      r.citekey.map(c => com.histmersion.api.library.Citekey(c.value)),
      Bundle.Asset.FileName(r.name.value),
      r.kind.map(k => MediaItem.Kind(k.value)),
      Latitude(r.lat.value),
      Longitude(r.lon.value),
      r.periodStart.map(y => Year(y.value.toLong)),
      r.periodEnd.map(y => Year(y.value.toLong)),
      r.caption.map(c => MediaItem.Caption(c.value)),
      MediaItem.Uid(r.uid.value),
      r.provider.map(p => MediaItem.Provider(p.value)),
      r.license.map(l => MediaItem.License(l.value)),
      r.credit.map(c => MediaItem.Credit(c.value)),
      Option.when(r.hasThumb.value)(MediaItem.ThumbUrl(MediaThumb.thumbPath(Bundle.Asset.Uid(r.uid.value)).value)),
      fileUrl = Some(MediaItem.FileUrl(MediaThumb.filePath(Bundle.Asset.Uid(r.uid.value)).value)),
      sourceUrl = r.sourceUrl.map(u => MediaItem.SourceUrl(u.value)),
      licenseUrl = r.licenseUrl.map(u => MediaItem.LicenseUrl(u.value)),
      author = r.author.map(a => MediaItem.Author(a.value))
    )

  /** `media-licence`: the pictures with no licence (a cover from Calibre or Open Library needs none) or a provider's
    * with no credit line — `citekey/name` (else `media:<uid>`) and what is missing, the provider's first.
    */
  def licenceGaps()(using DbCon): List[(GapItem, GapDetail)] =
    sql"""SELECT coalesce(a.citekey || '/' || a.name, 'media:' || a.uid::text),
                concat_ws(' · ',
                  CASE WHEN coalesce(btrim(a.license), '') = ''
                        AND coalesce(a.provider, '') NOT IN ('calibre', 'open-library') THEN 'no licence' END,
                  CASE WHEN a.provider IS NOT NULL AND coalesce(btrim(a.credit), '') = '' THEN 'no credit line' END,
                  coalesce(a.provider || ' ' || a.provider_key, a.mime))
         FROM assets a
         WHERE (coalesce(btrim(a.license), '') = '' AND coalesce(a.provider, '') NOT IN ('calibre', 'open-library'))
            OR (a.provider IS NOT NULL AND coalesce(btrim(a.credit), '') = '')
         ORDER BY a.provider NULLS LAST, 1, a.uid""".query[(GapItem, GapDetail)].run().toList

  /** A source's stored figures (images only), as the illustration's choice weighs them. */
  def sourceFigures(citekey: Citekey)(using DbCon): List[SourceFigure] =
    sql"""SELECT uid::text, name, width, height, thumb IS NOT NULL FROM assets
          WHERE citekey = $citekey AND mime LIKE 'image/%' ORDER BY name"""
      .query[
        (
            SourceFigure.Uid,
            SourceFigure.Name,
            Option[SourceFigure.Width],
            Option[SourceFigure.Height],
            SourceFigure.HasThumb
        )
      ]
      .run()
      .toList
      .map(SourceFigure.apply)
