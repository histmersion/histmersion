package com.histmersion.domain.media.assets

import com.histmersion.util.database.RangeSql

import com.histmersion.api.Api
import com.histmersion.util.database.{Param, Sql}

import com.histmersion.util.database.given
import com.augustnagro.magnum.*
import com.histmersion.domain.media.MediaRole
import com.histmersion.api.media.{
  MediaCount,
  MediaFacets,
  MediaGeoHit,
  MediaItem,
  MediaList,
  MediaProvider,
  MediaTarget,
  MediaThumb
}

/** Pictures (#1446, V082): one `assets` row each — a provider's with `provider` set instead of a `citekey`, a source's
  * figure with its citekey — and the places, events and sources they are shown on (`media_links`); the read paths every
  * stored picture shares — the media list and facets, the media layer, the cards' thumbnails. Every method runs on the
  * connection it is given.
  */
private[assets] object MediaRepo:

  // ── writes ───────────────────────────────────────────────────────────────────────────────────

  /** The pictures of `provider` already stored under these keys. */
  type SetThumbBytes = SetThumbBytes.T
  object SetThumbBytes extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Array[_root_.scala.Byte], _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ThumbResultPart2 = ThumbResultPart2.T
  object ThumbResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Array[_root_.scala.Byte], _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FileResultPart2 = FileResultPart2.T
  object FileResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Array[_root_.scala.Byte], _root_.io.github.iltotore.iron.constraint.any.Pure]

  type KnownResultPart1 = KnownResultPart1.T
  object KnownResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type KnownProvider = KnownProvider.T
  object KnownProvider extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type KnownKeys = KnownKeys.T
  object KnownKeys extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SaveResultPart1 = SaveResultPart1.T
  object SaveResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SaveResultPart2 = SaveResultPart2.T
  object SaveResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SaveFirstTargetPart1 = SaveFirstTargetPart1.T
  object SaveFirstTargetPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SaveFirstTargetPart2 = SaveFirstTargetPart2.T
  object SaveFirstTargetPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PlaceAtUid = PlaceAtUid.T
  object PlaceAtUid extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PlaceAtTargetPart1 = PlaceAtTargetPart1.T
  object PlaceAtTargetPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PlaceAtTargetPart2 = PlaceAtTargetPart2.T
  object PlaceAtTargetPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RefreshRightsUid = RefreshRightsUid.T
  object RefreshRightsUid extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LinkResult = LinkResult.T
  object LinkResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LinkUid = LinkUid.T
  object LinkUid extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LinkTargetType = LinkTargetType.T
  object LinkTargetType extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LinkTargetId = LinkTargetId.T
  object LinkTargetId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LinkRole = LinkRole.T
  object LinkRole extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LinkProperty = LinkProperty.T
  object LinkProperty extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LinkItem = LinkItem.T
  object LinkItem extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LinkRank = LinkRank.T
  object LinkRank extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LinkBasis = LinkBasis.T
  object LinkBasis extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SetThumbUid = SetThumbUid.T
  object SetThumbUid extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SetThumbMime = SetThumbMime.T
  object SetThumbMime extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SetThumbWidth = SetThumbWidth.T
  object SetThumbWidth extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SetThumbHeight = SetThumbHeight.T
  object SetThumbHeight extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SetSourcePictureUid = SetSourcePictureUid.T
  object SetSourcePictureUid extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SetSourcePictureRole = SetSourcePictureRole.T
  object SetSourcePictureRole extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SetSourcePictureProperty = SetSourcePictureProperty.T
  object SetSourcePictureProperty extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SourcePictureResultPart1 = SourcePictureResultPart1.T
  object SourcePictureResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SourcePictureResultPart2 = SourcePictureResultPart2.T
  object SourcePictureResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SourcePictureRole = SourcePictureRole.T
  object SourcePictureRole extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ThumbsInTargetType = ThumbsInTargetType.T
  object ThumbsInTargetType extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ThumbsInTargetId = ThumbsInTargetId.T
  object ThumbsInTargetId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ThumbsInLimit = ThumbsInLimit.T
  object ThumbsInLimit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type KmToDegreesResult = KmToDegreesResult.T
  object KmToDegreesResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type KmToDegreesKm = KmToDegreesKm.T
  object KmToDegreesKm extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PlaceThumbsInSameSiteKm = PlaceThumbsInSameSiteKm.T
  object PlaceThumbsInSameSiteKm extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PlaceThumbsInLimit = PlaceThumbsInLimit.T
  object PlaceThumbsInLimit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TargetsOfResultPart1 = TargetsOfResultPart1.T
  object TargetsOfResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TargetsOfUids = TargetsOfUids.T
  object TargetsOfUids extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CleanResult = CleanResult.T
  object CleanResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CleanO = CleanO.T
  object CleanO extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ListLimit = ListLimit.T
  object ListLimit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ShowUid = ShowUid.T
  object ShowUid extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ThumbResultPart1 = ThumbResultPart1.T
  object ThumbResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ThumbUid = ThumbUid.T
  object ThumbUid extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FileResultPart1 = FileResultPart1.T
  object FileResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FileUid = FileUid.T
  object FileUid extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type GeoLimit = GeoLimit.T
  object GeoLimit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type GeoOfResultPart1 = GeoOfResultPart1.T
  object GeoOfResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type GeoOfUids = GeoOfUids.T
  object GeoOfUids extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TileCandidatesKind = TileCandidatesKind.T
  object TileCandidatesKind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TileCandidatesProvider = TileCandidatesProvider.T
  object TileCandidatesProvider extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TileCandidatesLicense = TileCandidatesLicense.T
  object TileCandidatesLicense extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TileCandidatesWindowPart1 = TileCandidatesWindowPart1.T
  object TileCandidatesWindowPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TileCandidatesWindowPart2 = TileCandidatesWindowPart2.T
  object TileCandidatesWindowPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LicenceGapsResultPart1 = LicenceGapsResultPart1.T
  object LicenceGapsResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LicenceGapsResultPart2 = LicenceGapsResultPart2.T
  object LicenceGapsResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def known(provider: com.histmersion.domain.media.assets.MediaRepo.KnownProvider, keys: List[com.histmersion.domain.media.assets.MediaRepo.KnownKeys])(using DbCon): Map[com.histmersion.domain.media.assets.MediaRepo.KnownResultPart1, Known] =
    if keys.isEmpty then Map.empty
    else
      sql"""SELECT provider_key, uid::text, license FROM assets
          WHERE provider = $provider AND provider_key = ANY($keys::text[])"""
        .query[(String, String, Option[String])]
        .run()
        .map((k, u, l) => k -> Known(u, l))
        .toMap

  /** Store a provider's picture (or replace the bytes and rights of the one stored under its key); its point and period
    * come from the place or event it is first stored for. Returns its uid and whether it is new.
    */
  def save(a: ProviderAsset, firstTarget: (com.histmersion.domain.media.assets.MediaRepo.SaveFirstTargetPart1, com.histmersion.domain.media.assets.MediaRepo.SaveFirstTargetPart2))(using DbCon): (com.histmersion.domain.media.assets.MediaRepo.SaveResultPart1, com.histmersion.domain.media.assets.MediaRepo.SaveResultPart2) =
    val sha = com.histmersion.domain.media.assets.NewFigure.sha256(a.bytes)
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
  private def placeAt(uid: com.histmersion.domain.media.assets.MediaRepo.PlaceAtUid, target: (com.histmersion.domain.media.assets.MediaRepo.PlaceAtTargetPart1, com.histmersion.domain.media.assets.MediaRepo.PlaceAtTargetPart2))(using DbCon): Unit =
    val (tt, id) = target
    if tt == PictureTarget.Source.wire then () // a source's picture (a cover) is not on the map
    else if tt == PictureTarget.Place.wire then sql"""UPDATE assets a SET geom = coalesce(a.geom, t.g),
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
  def refreshRights(uid: com.histmersion.domain.media.assets.MediaRepo.RefreshRightsUid, r: Rights)(using DbCon): Unit =
    val _ = sql"""UPDATE assets SET license = ${r.license}, license_url = ${r.licenseUrl}, author = ${r.author},
            credit = ${r.credit}, retrieved_at = now() WHERE uid = $uid::uuid""".update.run()

  /** Link a stored picture to a place or event in a role — with `basis`, why a collection's record is tied to it
    * (V094). Returns whether the link is new.
    */
  def link(
      uid: com.histmersion.domain.media.assets.MediaRepo.LinkUid,
      targetType: com.histmersion.domain.media.assets.MediaRepo.LinkTargetType,
      targetId: com.histmersion.domain.media.assets.MediaRepo.LinkTargetId,
      role: com.histmersion.domain.media.assets.MediaRepo.LinkRole,
      property: Option[com.histmersion.domain.media.assets.MediaRepo.LinkProperty],
      item: Option[com.histmersion.domain.media.assets.MediaRepo.LinkItem],
      rank: com.histmersion.domain.media.assets.MediaRepo.LinkRank,
      basis: Option[com.histmersion.domain.media.assets.MediaRepo.LinkBasis]
  )(using DbCon): com.histmersion.domain.media.assets.MediaRepo.LinkResult =
    sql"""INSERT INTO media_links (asset_uid, target_type, target_id, role, property, item, rank, basis)
          VALUES ($uid::uuid, $targetType, $targetId, $role, $property, $item, $rank, $basis)
          ON CONFLICT (asset_uid, target_type, target_id, role) DO UPDATE SET
            property = EXCLUDED.property, item = EXCLUDED.item, rank = EXCLUDED.rank,
            basis = coalesce(EXCLUDED.basis, media_links.basis)
          RETURNING (xmax = 0)""".query[Boolean].run().head

  /** Store a thumbnail for a stored picture (a figure chosen to illustrate its source). */
  def setThumb(uid: com.histmersion.domain.media.assets.MediaRepo.SetThumbUid, bytes: com.histmersion.domain.media.assets.MediaRepo.SetThumbBytes, mime: com.histmersion.domain.media.assets.MediaRepo.SetThumbMime, width: Option[com.histmersion.domain.media.assets.MediaRepo.SetThumbWidth], height: Option[com.histmersion.domain.media.assets.MediaRepo.SetThumbHeight])(using
      DbCon
  ): Unit =
    val _ = sql"""UPDATE assets SET thumb = $bytes, thumb_mime = $mime, thumb_width = $width, thumb_height = $height
          WHERE uid = $uid::uuid""".update.run()

  /** Make `uid` the source's picture in `role` — replacing the one it had in that role. */
  def setSourcePicture(citekey: com.histmersion.domain.library.Citekey, uid: com.histmersion.domain.media.assets.MediaRepo.SetSourcePictureUid, role: com.histmersion.domain.media.assets.MediaRepo.SetSourcePictureRole, property: Option[com.histmersion.domain.media.assets.MediaRepo.SetSourcePictureProperty])(using DbCon): Unit =
    val _ = sql"""DELETE FROM media_links WHERE target_type = 'source' AND target_id = $citekey AND role = $role
            AND asset_uid <> $uid::uuid""".update.run()
    val _ = sql"""INSERT INTO media_links (asset_uid, target_type, target_id, role, property, item, rank)
          VALUES ($uid::uuid, 'source', $citekey, $role, $property, NULL, 0)
          ON CONFLICT (asset_uid, target_type, target_id, role) DO UPDATE SET property = EXCLUDED.property""".update
      .run()

  /** The source's picture in `role`, if it has one: its uid and the provider key or figure name. */
  def sourcePicture(citekey: com.histmersion.domain.library.Citekey, role: com.histmersion.domain.media.assets.MediaRepo.SourcePictureRole)(using DbCon): Option[(com.histmersion.domain.media.assets.MediaRepo.SourcePictureResultPart1, com.histmersion.domain.media.assets.MediaRepo.SourcePictureResultPart2)] =
    sql"""SELECT a.uid::text, coalesce(a.provider_key, a.name) FROM media_links m JOIN assets a ON a.uid = m.asset_uid
          WHERE m.target_type = 'source' AND m.target_id = $citekey AND m.role = $role LIMIT 1"""
      .query[(String, String)]
      .run()
      .headOption

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
  def thumbsIn(targetType: com.histmersion.domain.media.assets.MediaRepo.ThumbsInTargetType, targetId: com.histmersion.domain.media.assets.MediaRepo.ThumbsInTargetId, limit: com.histmersion.domain.media.assets.MediaRepo.ThumbsInLimit)(using DbCon): List[MediaThumb] =
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

  /** Pure: `km` as degrees of arc for `ST_DWithin` on lon/lat geometry — a latitude degree, so a little generous
    * east–west away from the equator, which a "same site" test can afford.
    */
  def kmToDegrees(km: com.histmersion.domain.media.assets.MediaRepo.KmToDegreesKm): com.histmersion.domain.media.assets.MediaRepo.KmToDegreesResult = (km.max(0) / 111.32)

  /** A PLACE's pictures for its card (#1446): its own, and those stored for its equivalents —
    *   - any place with the same Wikidata item (the picture's `item`): Mycenae is `pleiades_570491` and a Wikidata
    *     import, a polity is one place per time slice;
    *   - an old id merged or renamed into it (`place_aliases`);
    *   - another gazetteer's POINT of the same name (the geocoder's key) within `sameSiteKm` — Pleiades and GeoNames
    *     listing one site twice — and that point's items.
    * Best role first, then the place's own before an equivalent's, then the provider's preference. Index lookups only
    * (label key, GiST on the point, the links' item), within the hover budget.
    */
  def placeThumbsIn(placeId: com.histmersion.domain.catalogue.Place.Id, sameSiteKm: com.histmersion.domain.media.assets.MediaRepo.PlaceThumbsInSameSiteKm, limit: com.histmersion.domain.media.assets.MediaRepo.PlaceThumbsInLimit)(using DbCon): List[MediaThumb] =
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

  private def targetsOf(uids: List[com.histmersion.domain.media.assets.MediaRepo.TargetsOfUids])(using DbCon): Map[com.histmersion.domain.media.assets.MediaRepo.TargetsOfResultPart1, List[MediaTarget]] =
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

  private def clean(o: Option[com.histmersion.domain.media.assets.MediaRepo.CleanO]): Option[com.histmersion.domain.media.assets.MediaRepo.CleanResult] = o.map(_.trim).filter(_.nonEmpty)

  /** The list: by provider (`source` = a source's figures), licence (`none` = no licence), the place or event a picture
    * is linked to, and a text match; newest first. `total` ignores the limit.
    */
  def list(q: Api.MediaQuery, limit: com.histmersion.domain.media.assets.MediaRepo.ListLimit)(using DbCon): MediaList =
    val (provider, license) = (clean(q.provider), clean(q.license))
    val (place, event) = (clean(q.place), clean(q.event))
    val text = clean(q.q).map(t => s"%$t%")
    val offset = q.offset.getOrElse(0).max(0)
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

  def show(uid: com.histmersion.domain.media.assets.MediaRepo.ShowUid)(using DbCon): Option[MediaItem] =
    sql"SELECT ${SqlLiteral(itemCols)} FROM assets a WHERE a.uid = $uid::uuid"
      .query[MediaItemRow]
      .run()
      .headOption
      .map(r => item(r, targetsOf(List(r.uid)).getOrElse(r.uid, Nil)))

  /** The thumbnail's bytes and type — a source's figure without one is its own thumbnail. */
  def thumb(uid: com.histmersion.domain.media.assets.MediaRepo.ThumbUid)(using DbCon): Option[(com.histmersion.domain.media.assets.MediaRepo.ThumbResultPart1, com.histmersion.domain.media.assets.MediaRepo.ThumbResultPart2)] =
    sql"""SELECT CASE WHEN thumb IS NOT NULL THEN coalesce(thumb_mime, mime) ELSE mime END, coalesce(thumb, bytes)
          FROM assets WHERE uid = $uid::uuid
            AND (thumb IS NOT NULL OR provider IS NULL OR provider IN (${MediaProvider.Calibre}, ${MediaProvider.OpenLibrary}))"""
      .query[(String, Array[Byte])]
      .run()
      .headOption

  /** The stored rendition's (or figure's) bytes and type. */
  def file(uid: com.histmersion.domain.media.assets.MediaRepo.FileUid)(using DbCon): Option[(com.histmersion.domain.media.assets.MediaRepo.FileResultPart1, com.histmersion.domain.media.assets.MediaRepo.FileResultPart2)] =
    sql"SELECT mime, bytes FROM assets WHERE uid = $uid::uuid".query[(String, Array[Byte])].run().headOption

  def facets(using DbCon): MediaFacets =
    val providers = sql"""SELECT coalesce(provider, ${MediaProvider.Source}), count(*)::int FROM assets
          GROUP BY 1 ORDER BY 2 DESC, 1""".query[(String, Int)].run().toList.map(MediaCount(_, _))
    val licences = sql"""SELECT coalesce(nullif(btrim(license), ''), 'none'), count(*)::int FROM assets
          GROUP BY 1 ORDER BY 2 DESC, 1""".query[(String, Int)].run().toList.map(MediaCount(_, _))
    MediaFacets(providers, licences)

  /** The media layer: geolocated pictures in the span, by kind, provider and licence. */
  def geo(q: Api.MediaGeoQuery, limit: com.histmersion.domain.media.assets.MediaRepo.GeoLimit)(using DbCon): List[MediaGeoHit] =
    val k = q.kind.map(_.trim.toLowerCase).filter(_.nonEmpty)
    val (provider, license) = (clean(q.provider), clean(q.license))
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
  def geoOf(uids: List[com.histmersion.domain.media.assets.MediaRepo.GeoOfUids])(using DbCon): Map[com.histmersion.domain.media.assets.MediaRepo.GeoOfResultPart1, MediaGeoHit] =
    if uids.isEmpty then Map.empty
    else
      sql"""SELECT ${SqlLiteral(geoCols)} FROM assets a
          WHERE a.geom IS NOT NULL AND a.uid = ANY($uids::uuid[])"""
        .query[MediaGeoRow]
        .run()
        .map(r => r.uid -> geoHit(r))
        .toMap

  /** The pictures of the media layer as the map's tiles select them — [[geo]]'s selection, every one whose dates
    * overlap `window` (none: whatever their dates), in the tile's box `envelope`: uid, point and dates.
    */
  def tileCandidates(
      kind: Option[com.histmersion.domain.media.assets.MediaRepo.TileCandidatesKind],
      provider: Option[com.histmersion.domain.media.assets.MediaRepo.TileCandidatesProvider],
      license: Option[com.histmersion.domain.media.assets.MediaRepo.TileCandidatesLicense],
      window: Option[(com.histmersion.domain.media.assets.MediaRepo.TileCandidatesWindowPart1, com.histmersion.domain.media.assets.MediaRepo.TileCandidatesWindowPart2)],
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

  /** `media-licence`: the pictures with no licence (a cover from Calibre or Open Library needs none) or a provider's
    * with no credit line — `citekey/name` (else `media:<uid>`) and what is missing, the provider's first.
    */
  def licenceGaps()(using DbCon): List[(com.histmersion.domain.media.assets.MediaRepo.LicenceGapsResultPart1, com.histmersion.domain.media.assets.MediaRepo.LicenceGapsResultPart2)] =
    sql"""SELECT coalesce(a.citekey || '/' || a.name, 'media:' || a.uid::text),
                concat_ws(' · ',
                  CASE WHEN coalesce(btrim(a.license), '') = ''
                        AND coalesce(a.provider, '') NOT IN ('calibre', 'open-library') THEN 'no licence' END,
                  CASE WHEN a.provider IS NOT NULL AND coalesce(btrim(a.credit), '') = '' THEN 'no credit line' END,
                  coalesce(a.provider || ' ' || a.provider_key, a.mime))
         FROM assets a
         WHERE (coalesce(btrim(a.license), '') = '' AND coalesce(a.provider, '') NOT IN ('calibre', 'open-library'))
            OR (a.provider IS NOT NULL AND coalesce(btrim(a.credit), '') = '')
         ORDER BY a.provider NULLS LAST, 1""".query[(String, String)].run().toList

  /** A source's stored figures (images only), as the illustration's choice weighs them. */
  def sourceFigures(citekey: com.histmersion.domain.library.Citekey)(using DbCon): List[SourceFigure] =
    sql"""SELECT uid::text, name, width, height, thumb IS NOT NULL FROM assets
          WHERE citekey = $citekey AND mime LIKE 'image/%' ORDER BY name"""
      .query[(String, String, Option[Int], Option[Int], Boolean)]
      .run()
      .toList
      .map(SourceFigure.apply)
