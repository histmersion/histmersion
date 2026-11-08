package com.histmersion
package domain.media.assets

import com.histmersion.api.media.MediaGeoHit
import com.histmersion.api.catalogue.{TileProps as P, TilesApi}
import com.histmersion.util.database.Sql
import com.histmersion.domain.catalogue.maptiles.{TileCell, TileFeatures as F, TilePoint, TileValue}
import com.histmersion.domain.catalogue.maptiles.layers.MapLayer
import com.histmersion.domain.catalogue.periods.BPeriodRow
import com.histmersion.domain.DeepTime
import com.histmersion.domain.catalogue.maptiles.MapTiles

/** The pictures as a layer of the map: those placed in a tile's box, of the kind, provider and licence asked for, dated
  * in the span when one is asked for.
  */
final class MediaMapLayer(assets: AssetService) extends MapLayer:
  def candidates(q: TilesApi.TileQuery, window: Option[(BPeriodRow.PLo, BPeriodRow.PHi)], envelope: Sql): Sql =
    MediaRepo.tileCandidates(
      q.kind.map(k => MediaItemRow.Kind(k.value)),
      q.provider.map(p => MediaItemRow.Provider(p.value)),
      q.license.map(l => MediaItemRow.License(l.value)),
      window.map((lo, hi) => (DeepTime.SignedYear(lo.value), DeepTime.SignedYear(hi.value))),
      envelope
    )

  def features(keys: List[MapTiles.TileKey]): Map[MapTiles.TileKey, TileCell => TilePoint] =
    assets
      .geoOf(keys.map(k => AssetService.AssetUid(k.value)))
      .map((uid, h) => MapTiles.TileKey(uid.value) -> feature(h))

  private[assets] def feature(h: MediaGeoHit)(c: TileCell): TilePoint =
    TilePoint(
      c.x,
      c.y,
      F.text(P.Key, c.key.value) ++ F.text(P.Uid, h.uid.value) ++ F.text(P.Citekey, h.citekey.map(_.value)) ++ F
        .text(P.AssetName, h.assetName.value) ++
        F.text(P.Kind, h.kind.map(_.value)) ++ F.whole(
          P.PeriodStart,
          h.periodStart.map(y => TileValue.Whole.Value(y.value))
        ) ++ F.whole(P.PeriodEnd, h.periodEnd.map(y => TileValue.Whole.Value(y.value))) ++ F.text(
          P.Caption,
          h.caption.map(_.value)
        ) ++
        F.text(P.Provider, h.provider.map(_.value)) ++ F.text(P.License, h.license.map(_.value)) ++ F
          .text(P.Credit, h.credit.map(_.value)) ++ F.text(
          P.ThumbUrl,
          h.thumbUrl.map(_.value)
        ) ++
        // its full size is this server's own `/media/{uid}/file`, which the map makes from the uid (not sent per picture)
        F.text(P.SourceUrl, h.sourceUrl.map(_.value)) ++ F.text(P.LicenseUrl, h.licenseUrl.map(_.value)) ++ F.text(
          P.Author,
          h.author.map(_.value)
        )
    )
