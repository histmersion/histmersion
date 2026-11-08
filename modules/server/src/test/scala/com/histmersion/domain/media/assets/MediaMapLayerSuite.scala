package com.histmersion
package domain.media.assets

import com.histmersion.domain.catalogue.maptiles.{TileCell, TilePoint, TileValue}
import com.histmersion.api.exchange.Bundle
import com.histmersion.api.media.MediaItem
import com.histmersion.api.catalogue.Latitude
import com.histmersion.api.catalogue.Longitude
import com.histmersion.domain.media.MediaState

/** The pictures' layer of the map: how a drawn picture reads. */
class MediaMapLayerSuite extends munit.FunSuite:

  test(
    "a picture's feature carries what its full-size view shows: the provider's page, the licence's text, the author"
  ):
    import com.histmersion.api.media.MediaGeoHit
    import com.histmersion.api.catalogue.TileProps as P
    val h = MediaGeoHit(
      None,
      Bundle.Asset.FileName("Lions_Gate.jpg"),
      Some(MediaItem.Kind("photo")),
      Latitude(37.7),
      Longitude(22.7),
      None,
      None,
      None,
      MediaItem.Uid("u1"),
      Some(MediaItem.Provider("wikimedia-commons")),
      Some(MediaItem.License("CC-BY-SA-3.0")),
      Some(MediaItem.Credit("Ann, via Commons")),
      Some(MediaItem.ThumbUrl("/media/u1/thumb")),
      fileUrl = Some(MediaItem.FileUrl("/media/u1/file")),
      sourceUrl = Some(MediaItem.SourceUrl("https://commons.wikimedia.org/wiki/File:Lions_Gate.jpg")),
      licenseUrl = Some(MediaItem.LicenseUrl("https://creativecommons.org/licenses/by-sa/3.0/")),
      author = Some(MediaItem.Author("Ann"))
    )
    val props = MediaMapLayer(
      AssetService.InMemory(
        java.util.concurrent.atomic.AtomicReference(MediaState.empty),
        java.time.Clock.systemUTC()
      )
    )
      .feature(h)(TileCell(TileCell.Key("u1"), TileCell.X(10), TileCell.Y(20), TileCell.N(1), None, None, TileCell.West(22.7), TileCell.South(37.7), TileCell.East(22.7), TileCell.North(37.7)))
      .props
      .toMap
    assertEquals(props.get(TilePoint.PropKey(P.SourceUrl)), Some(TileValue.Text(TileValue.Text.Value("https://commons.wikimedia.org/wiki/File:Lions_Gate.jpg"))))
    assertEquals(props.get(TilePoint.PropKey(P.LicenseUrl)), Some(TileValue.Text(TileValue.Text.Value("https://creativecommons.org/licenses/by-sa/3.0/"))))
    assertEquals(props.get(TilePoint.PropKey(P.Author)), Some(TileValue.Text(TileValue.Text.Value("Ann"))))
    // the full size is the uid's own file: the map makes its URL, the tile does not carry one per picture
    assertEquals(props.get(TilePoint.PropKey(P.ThumbUrl)), Some(TileValue.Text(TileValue.Text.Value("/media/u1/thumb"))))
    assert(!props.values.exists(_ == TileValue.Text(TileValue.Text.Value("/media/u1/file"))))
