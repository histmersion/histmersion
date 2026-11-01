package com.histmersion.domain.media.assets

import com.histmersion.domain.catalogue.maptiles.{TileCell, TileValue}

/** The pictures' layer of the map: how a drawn picture reads. */
class MediaMapLayerSuite extends munit.FunSuite:

  test(
    "a picture's feature carries what its full-size view shows: the provider's page, the licence's text, the author"
  ):
    import com.histmersion.api.{MediaGeoHit, TileProps as P}
    val h = MediaGeoHit(
      None,
      "Lions_Gate.jpg",
      Some("photo"),
      37.7,
      22.7,
      None,
      None,
      None,
      "u1",
      Some("wikimedia-commons"),
      Some("CC-BY-SA-3.0"),
      Some("Ann, via Commons"),
      Some("/media/u1/thumb"),
      fileUrl = Some("/media/u1/file"),
      sourceUrl = Some("https://commons.wikimedia.org/wiki/File:Lions_Gate.jpg"),
      licenseUrl = Some("https://creativecommons.org/licenses/by-sa/3.0/"),
      author = Some("Ann")
    )
    val props = MediaMapLayer(
      AssetService.InMemory(
        java.util.concurrent.atomic.AtomicReference(com.histmersion.domain.media.MediaState.empty),
        java.time.Clock.systemUTC()
      )
    )
      .feature(h)(TileCell("u1", 10, 20, 1, None, None, 22.7, 37.7, 22.7, 37.7))
      .props
      .toMap
    assertEquals(props.get(P.SourceUrl), Some(TileValue.Text("https://commons.wikimedia.org/wiki/File:Lions_Gate.jpg")))
    assertEquals(props.get(P.LicenseUrl), Some(TileValue.Text("https://creativecommons.org/licenses/by-sa/3.0/")))
    assertEquals(props.get(P.Author), Some(TileValue.Text("Ann")))
    // the full size is the uid's own file: the map makes its URL, the tile does not carry one per picture
    assertEquals(props.get(P.ThumbUrl), Some(TileValue.Text("/media/u1/thumb")))
    assert(!props.values.exists(_ == TileValue.Text("/media/u1/file")))
