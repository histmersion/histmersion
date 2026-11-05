package com.histmersion.domain.media.assets

import com.histmersion.api.media.MediaGeoHit
import com.histmersion.api.catalogue.{TileProps as P, TilesApi}
import com.histmersion.util.database.Sql
import com.histmersion.domain.catalogue.maptiles.{TileCell, TileFeatures as F, TilePoint}
import com.histmersion.domain.catalogue.maptiles.layers.MapLayer

/** The pictures as a layer of the map: those placed in a tile's box, of the kind, provider and licence asked for, dated
  * in the span when one is asked for.
  */
final class MediaMapLayer(assets: AssetService) extends MapLayer:
  def candidates(q: TilesApi.TileQuery, window: Option[(com.histmersion.domain.media.assets.MediaMapLayer.CandidatesWindowPart1, com.histmersion.domain.media.assets.MediaMapLayer.CandidatesWindowPart2)], envelope: Sql): Sql =
    MediaRepo.tileCandidates(q.kind, q.provider, q.license, window, envelope)

  def features(keys: List[com.histmersion.domain.media.assets.MediaMapLayer.FeaturesKeys]): Map[com.histmersion.domain.media.assets.MediaMapLayer.FeaturesResultPart1, TileCell => TilePoint] =
    assets.geoOf(keys).view.mapValues(feature).toMap

  private[assets] def feature(h: MediaGeoHit)(c: TileCell): TilePoint =
    TilePoint(
      c.x,
      c.y,
      F.text(P.Key, c.key) ++ F.text(P.Uid, h.uid) ++ F.text(P.Citekey, h.citekey) ++ F
        .text(P.AssetName, h.assetName) ++
        F.text(P.Kind, h.kind) ++ F.whole(P.PeriodStart, h.periodStart) ++ F.whole(P.PeriodEnd, h.periodEnd) ++ F.text(
          P.Caption,
          h.caption
        ) ++
        F.text(P.Provider, h.provider) ++ F.text(P.License, h.license) ++ F.text(P.Credit, h.credit) ++ F.text(
          P.ThumbUrl,
          h.thumbUrl
        ) ++
        // its full size is this server's own `/media/{uid}/file`, which the map makes from the uid (not sent per picture)
        F.text(P.SourceUrl, h.sourceUrl) ++ F.text(P.LicenseUrl, h.licenseUrl) ++ F.text(P.Author, h.author)
    )

object MediaMapLayer:
  type CandidatesWindowPart1 = CandidatesWindowPart1.T
  object CandidatesWindowPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CandidatesWindowPart2 = CandidatesWindowPart2.T
  object CandidatesWindowPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FeaturesResultPart1 = FeaturesResultPart1.T
  object FeaturesResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FeaturesKeys = FeaturesKeys.T
  object FeaturesKeys extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
