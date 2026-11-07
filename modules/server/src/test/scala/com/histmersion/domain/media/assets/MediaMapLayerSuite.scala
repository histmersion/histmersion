package com.histmersion.domain.media.assets

import com.histmersion.domain.catalogue.maptiles.{TileCell, TileValue}

/** The pictures' layer of the map: how a drawn picture reads. */
class MediaMapLayerSuite extends munit.FunSuite:

  test(
    "a picture's feature carries what its full-size view shows: the provider's page, the licence's text, the author"
  ):
    import com.histmersion.api.media.MediaGeoHit
    import com.histmersion.api.catalogue.TileProps as P
    val h = MediaGeoHit(
      None,
      com.histmersion.api.exchange.Bundle.Asset.FileName("Lions_Gate.jpg"),
      Some(com.histmersion.api.media.MediaItem.Kind("photo")),
      37.7,
      22.7,
      None,
      None,
      None,
      com.histmersion.api.media.MediaItem.Uid("u1"),
      Some(com.histmersion.api.media.MediaItem.Provider("wikimedia-commons")),
      Some(com.histmersion.api.media.MediaItem.License("CC-BY-SA-3.0")),
      Some(com.histmersion.api.media.MediaItem.Credit("Ann, via Commons")),
      Some(com.histmersion.api.media.MediaItem.ThumbUrl("/media/u1/thumb")),
      fileUrl = Some(com.histmersion.api.media.MediaItem.FileUrl("/media/u1/file")),
      sourceUrl = Some(com.histmersion.api.media.MediaItem.SourceUrl("https://commons.wikimedia.org/wiki/File:Lions_Gate.jpg")),
      licenseUrl = Some(com.histmersion.api.media.MediaItem.LicenseUrl("https://creativecommons.org/licenses/by-sa/3.0/")),
      author = Some(com.histmersion.api.media.MediaItem.Author("Ann"))
    )
    val props = MediaMapLayer(
      AssetService.InMemory(
        java.util.concurrent.atomic.AtomicReference(com.histmersion.domain.media.MediaState.empty),
        java.time.Clock.systemUTC()
      )
    )
      .feature(h)(TileCell(com.histmersion.domain.catalogue.maptiles.TileCell.Key("u1"), com.histmersion.domain.catalogue.maptiles.TileCell.X(10), com.histmersion.domain.catalogue.maptiles.TileCell.Y(20), com.histmersion.domain.catalogue.maptiles.TileCell.N(1), None, None, 22.7, 37.7, 22.7, 37.7))
      .props
      .toMap
    assertEquals(props.get(P.SourceUrl), Some(TileValue.Text(com.histmersion.domain.catalogue.maptiles.TileValue.Text.Value("https://commons.wikimedia.org/wiki/File:Lions_Gate.jpg"))))
    assertEquals(props.get(P.LicenseUrl), Some(TileValue.Text(com.histmersion.domain.catalogue.maptiles.TileValue.Text.Value("https://creativecommons.org/licenses/by-sa/3.0/"))))
    assertEquals(props.get(P.Author), Some(TileValue.Text(com.histmersion.domain.catalogue.maptiles.TileValue.Text.Value("Ann"))))
    // the full size is the uid's own file: the map makes its URL, the tile does not carry one per picture
    assertEquals(props.get(P.ThumbUrl), Some(TileValue.Text(com.histmersion.domain.catalogue.maptiles.TileValue.Text.Value("/media/u1/thumb"))))
    assert(!props.values.exists(_ == TileValue.Text("/media/u1/file")))
