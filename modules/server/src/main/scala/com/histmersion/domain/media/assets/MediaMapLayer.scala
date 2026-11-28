package com.histmersion
package domain.media.assets

import com.histmersion.api.media.MediaGeoHit
import com.histmersion.api.catalogue.{TileProps as P, TilesApi}
import com.histmersion.domain.catalogue.maptiles.{TileCell, TileFeatures as F, TilePoint, TileValue}
import com.histmersion.domain.catalogue.maptiles.layers.MapLayer
import com.histmersion.domain.catalogue.periods.BPeriodRow
import com.histmersion.domain.DeepTime
import com.histmersion.domain.catalogue.maptiles.MapTiles
import com.augustnagro.magnum.Frag
import com.histmersion.lint.{tolerated, LintRule}

/** The pictures as a layer of the map: those placed in a tile's box, of the kind, provider and licence asked for, dated
  * in the span when one is asked for.
  */
@tolerated(LintRule.Layer, "pending G7 (separate types per layer)")
final class MediaMapLayer(assets: AssetService) extends MapLayer:
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def candidates(q: TilesApi.TileQuery, window: Option[(BPeriodRow.PLo, BPeriodRow.PHi)], envelope: Frag): Frag =
    MediaRepo.tileCandidates(
      q.kind.map(k => MediaItemRow.Kind(k.value)),
      q.provider.map(p => MediaItemRow.Provider(p.value)),
      q.license.map(l => MediaItemRow.License(l.value)),
      window.map((lo, hi) => (DeepTime.SignedYear(lo.value), DeepTime.SignedYear(hi.value))),
      envelope
    )

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def features(keys: List[MapTiles.TileKey]): Map[MapTiles.TileKey, TileCell => TilePoint] =
    assets
      .geoOf(keys.map(k => AssetService.AssetUid(k.value)))
      .map((uid, h) => MapTiles.TileKey(uid.value) -> feature(h))

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(LintRule.G44PlainDef, "pending G1 (the parameter takes the newtype, not its raw value)")
  private[assets] def feature(h: MediaGeoHit)(c: TileCell): TilePoint =
    TilePoint(
      c.x,
      c.y,
      TileCell.text(P.Key, c.key.value) ++ TileCell.text(P.Uid, h.uid.value) ++ TileCell
        .text(P.Citekey, h.citekey.map(_.value)) ++ TileCell.text(P.AssetName, h.assetName.value) ++
        TileCell.text(P.Kind, h.kind.map(_.value)) ++ TileCell.whole(
          P.PeriodStart,
          h.periodStart.map(y => TileValue.Whole.Value(y.value))
        ) ++ TileCell.whole(P.PeriodEnd, h.periodEnd.map(y => TileValue.Whole.Value(y.value))) ++ TileCell.text(
          P.Caption,
          h.caption.map(_.value)
        ) ++
        TileCell.text(P.Provider, h.provider.map(_.value)) ++ TileCell
          .text(P.License, h.license.map(_.value)) ++ TileCell.text(P.Credit, h.credit.map(_.value)) ++ TileCell.text(
          P.ThumbUrl,
          h.thumbUrl.map(_.value)
        ) ++
        // its full size is this server's own `/media/{uid}/file`, which the map makes from the uid (not sent per picture)
        TileCell.text(P.SourceUrl, h.sourceUrl.map(_.value)) ++ TileCell
          .text(P.LicenseUrl, h.licenseUrl.map(_.value)) ++ TileCell.text(
          P.Author,
          h.author.map(_.value)
        )
    )
