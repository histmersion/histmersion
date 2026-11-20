package com.histmersion
package web

import hearth.kindlings.optics.syntax.*
import com.histmersion.testsupport.ComparedByValue.given

import com.histmersion.api.TimeSpan
import com.histmersion.web.map.{GeoBounds, LatLon, TilePointFeature}
import com.histmersion.web.ui.SidebarTab

import scala.scalajs.js
import com.histmersion.api.UiLabels
import com.histmersion.api.RowCount
import com.histmersion.api.Years
import com.histmersion.api.Plural
import com.histmersion.api.catalogue.Year

/** Explore's chrome (the redesign's phase 3), its pure parts: the filter panel's fold and count, the layers button, the
  * timeline's speeds in words, the results' tabs, the detail's page and kind, the credits' fold, the places list's
  * lines, and the sites and events of no known date as the tiles mark them.
  */
class ExploreChromeSuite extends munit.FunSuite:

  private def feature(lat: LatLon.Lat, lon: LatLon.Lon, props: (String, js.Any)*): TilePointFeature =
    TilePointFeature(LatLon(lat, lon), js.Dictionary(props*))

  test("the filter panel is folded only when this browser left it folded; More filters opens when one in it is set"):
    assert(!FilterBar.foldedInitially(None))
    assert(!FilterBar.foldedInitially(Some(FilterBar.StoredText("0"))))
    assert(FilterBar.foldedInitially(Some(FilterBar.StoredText("1"))))
    assert(!FilterBar.moreOpenInitially(None, someMoreSet = false))
    assert(FilterBar.moreOpenInitially(Some(FilterBar.StoredText("0")), someMoreSet = true))
    assert(FilterBar.moreOpenInitially(Some(FilterBar.StoredText("1")), someMoreSet = false))

  test("the period counts as an active filter only away from the default span"):
    assert(!FilterChips.periodSet(None, None, -3000, 2000))
    assert(!FilterChips.periodSet(Some(-3000), Some(2000), -3000, 2000))
    assert(FilterChips.periodSet(Some(-1500), None, -3000, 2000))
    val chips = List(
      UiState.ActiveChip(
        Field.Key("topics"),
        UiState.ActiveChip.Label("topic"),
        UiState.ActiveChip.Value("bronze age")
      ),
      UiState.ActiveChip(
        Field.Key("q"),
        UiState.ActiveChip.Label("search"),
        UiState.ActiveChip.Value("troy")
      )
    )
    assertEquals(FilterChips.count(chips, period = true).value, 3)
    assertEquals(FilterChips.count(Nil, period = false).value, 0)

  test("the layers button says how many layers are on"):
    assertEquals(ViewportToggle.layersLabel(2, ViewportToggle.LayerCount), "Layers · 2 of 6 on")

  test("playback speeds read in plain words"):
    assertEquals(
      TimelinePlayback.Speeds.map(TimelinePlayback.speedWords).map(_.value),
      List("Very slow", "Slow", "Normal speed", "Fast", "Very fast")
    )

  test("the results' tabs: five, a kept key back to its tab, an old tree tab to the sources"):
    assertEquals(
      SidebarTab.values.toList.map(_.label.value),
      List("Sources", "Articles", "Events", "Places", "Pictures")
    )
    assertEquals(SidebarTab.of(Some(SidebarTab.Key("pictures"))), SidebarTab.Pictures)
    assertEquals(SidebarTab.of(Some(SidebarTab.Key("bytime"))), SidebarTab.Sources)
    assertEquals(SidebarTab.of(None), SidebarTab.Sources)
    assertEquals(ResultsPanel.tabLabel(UiLabels.Text("Places"), Some(RowCount(12))).value, "Places 12")
    assertEquals(ResultsPanel.tabLabel(UiLabels.Text("Places"), None).value, "Places")

  test("an item's detail opens its own page in a new tab"):
    assertEquals(
      ResultDetail.pageOf(Preview.Target.Source(Preview.Target.Source.Citekey("smith2020"))).value,
      "/source/smith2020"
    )
    assertEquals(
      ResultDetail
        .pageOf(Preview.Target.Place(Preview.Target.Place.Id("pleiades_570406"), Some(Years.Astronomical(-500))))
        .value,
      "/place/pleiades_570406"
    )
    assertEquals(ResultDetail.pageOf(Preview.Target.Event(Preview.Target.Event.Id("ev1"))).value, "/event/ev1")
    assertEquals(ResultDetail.kindOf(Preview.Target.Article(Preview.Target.Article.Slug("x"))).value, "article")

  test("the credits: open at first, folded after the first move, open when the map is missing; a choice wins"):
    assert(BaseMaps.footOpenNow(None, moved = false, warns = false))
    assert(!BaseMaps.footOpenNow(None, moved = true, warns = false))
    assert(BaseMaps.footOpenNow(None, moved = true, warns = true))
    assert(BaseMaps.footOpenNow(Some(true), moved = true, warns = false))
    assert(!BaseMaps.footOpenNow(Some(false), moved = false, warns = false))
    // the online OpenStreetMap tiles keep their credit in sight
    assert(BaseMaps.keepsOsmCredit(BaseMap.Standard))
    assert(!BaseMaps.keepsOsmCredit(BaseMap.Readable))
    assert(BaseMaps.allCreditsLink.value.contains("/settings/credits"))

  test("the places list: the count line on the map and everywhere; a site's note says when its date is unknown"):
    assertEquals(
      MapLists
        .countLine(
          MapLists.ListedCount(3),
          MapLists.TotalCount(3),
          Plural.Noun.Singular("place"),
          Plural.Noun.Plural("places"),
          onMap = MapLists.OnMap(true)
        )
        .value,
      "3 places on the map"
    )
    assertEquals(
      MapLists
        .countLine(
          MapLists.ListedCount(40),
          MapLists.TotalCount(312),
          Plural.Noun.Singular("place"),
          Plural.Noun.Plural("places"),
          onMap = MapLists.OnMap(true)
        )
        .value,
      "312 places on the map · 40 listed, zoom in to list the rest"
    )
    assertEquals(
      MapLists
        .countLine(
          MapLists.ListedCount(1),
          MapLists.TotalCount(1),
          Plural.Noun.Singular("picture"),
          Plural.Noun.Plural("pictures"),
          onMap = MapLists.OnMap(false)
        )
        .value,
      "1 picture"
    )
    val site = SitePoint(
      Some(SitePoint.Id("s1")),
      SitePoint.Label("Tower"),
      SitePoint.Kind("site"),
      Some(SitePoint.SiteType("tower")),
      None,
      LatLon.Lat(37),
      LatLon.Lon(22),
      None,
      undated = SitePoint.Undated(true)
    )
    assert(MapLists.siteNote(site).value.endsWith("date unknown"), MapLists.siteNote(site))

  test("a site or a cell of the tiles marked undated reads so; a bubble says how many have no known date"):
    val s = TileDecode
      .site(feature(LatLon.Lat(37), LatLon.Lon(22), "k" -> "s1", "id" -> "s1", "label" -> "Tower", "u" -> 1))
      .get
    assert(s.undated.value)
    assert(PointKinds.Sites.classes(s).contains(MapView.UndatedClass))
    assert(
      PointKinds.Sites.zIndex(s).value < PointKinds.Sites
        .zIndex(s.modify(_.undated).setTo(SitePoint.Undated(false)))
        .value
    )
    assertEquals(PointKinds.Sites.member(s).undated.value, 1)
    assert(
      !TileDecode.site(feature(LatLon.Lat(37), LatLon.Lon(22), "id" -> "s2", "label" -> "Dated")).get.undated.value
    )
    val c = TileDecode.cell(
      feature(
        LatLon.Lat(10),
        LatLon.Lon(20),
        "k" -> "c4/1/2",
        "n" -> 12,
        "u" -> 5,
        "w" -> 19,
        "s" -> 9,
        "e" -> 21,
        "nn" -> 11
      ),
      DotKind.Site
    )
    assertEquals(c.undated.value, 5)
    assertEquals(
      DotCluster.summary(DotCluster.Contents(List(CellKind(DotKind.Site).member(c)))).value,
      "12 sites, 5 with no known date"
    )
    assertEquals(
      DotCluster.summary(Map(DotKind.Site -> DotCluster.KindCount(3)), DotCluster.Undated(0)).value,
      "3 sites"
    )

  test("an event with no known date is drawn undated, beneath the dated"):
    val e = TileDecode
      .event(feature(LatLon.Lat(40), LatLon.Lon(26), "id" -> "ev9", "label" -> "Quake", "kind" -> "earthquake"))
      .get
    assert(PointKinds.Events.undated(e))
    assert(PointKinds.Events.classes(e).contains(MapView.UndatedClass))
    val dated = e.modify(_.timeSpan).setTo(TimeSpan(Some(Year(-1200L)), None, None, Some(Year(-1190L))))
    assert(!PointKinds.Events.undated(dated))
    assert(PointKinds.Events.zIndex(e).value < PointKinds.Events.zIndex(dated).value)

  test("the legend keys the dashed outline while undated places are drawn"):
    assert(
      MapKey
        .html(events = MapKey.Events(false), sites = MapKey.Sites(true), undated = MapKey.Undated(true))
        .value
        .contains("date unknown")
    )
    assert(!MapKey.html(events = MapKey.Events(false), sites = MapKey.Sites(true)).value.contains("date unknown"))
