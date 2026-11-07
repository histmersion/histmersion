package com.histmersion.web

import com.histmersion.testsupport.ComparedByValue.given

import hearth.kindlings.optics.syntax.*

import com.histmersion.api.TimeSpan
import com.histmersion.web.map.{GeoBounds, LatLon, TilePointFeature}
import com.histmersion.web.ui.SidebarTab

import scala.scalajs.js

/** Explore's chrome (the redesign's phase 3), its pure parts: the filter panel's fold and count, the layers button, the
  * timeline's speeds in words, the results' tabs, the detail's page and kind, the credits' fold, the places list's
  * lines, and the sites and events of no known date as the tiles mark them.
  */
class ExploreChromeSuite extends munit.FunSuite:

  private def feature(lat: Double, lon: Double, props: (String, js.Any)*): TilePointFeature =
    TilePointFeature(LatLon(lat, lon), js.Dictionary(props*))

  test("the filter panel is folded only when this browser left it folded; More filters opens when one in it is set"):
    assert(!FilterBar.foldedInitially(None))
    assert(!FilterBar.foldedInitially(Some(com.histmersion.web.FilterBar.StoredText("0"))))
    assert(FilterBar.foldedInitially(Some(com.histmersion.web.FilterBar.StoredText("1"))))
    assert(!FilterBar.moreOpenInitially(None, someMoreSet = false))
    assert(FilterBar.moreOpenInitially(Some(com.histmersion.web.FilterBar.StoredText("0")), someMoreSet = true))
    assert(FilterBar.moreOpenInitially(Some(com.histmersion.web.FilterBar.StoredText("1")), someMoreSet = false))

  test("the period counts as an active filter only away from the default span"):
    assert(!FilterChips.periodSet(None, None, -3000, 2000))
    assert(!FilterChips.periodSet(Some(-3000), Some(2000), -3000, 2000))
    assert(FilterChips.periodSet(Some(-1500), None, -3000, 2000))
    val chips = List(UiState.ActiveChip(com.histmersion.web.UiState.ActiveChip.Key("topics"), com.histmersion.web.UiState.ActiveChip.Label("topic"), com.histmersion.web.UiState.ActiveChip.Value("bronze age")), UiState.ActiveChip(com.histmersion.web.UiState.ActiveChip.Key("q"), com.histmersion.web.UiState.ActiveChip.Label("search"), com.histmersion.web.UiState.ActiveChip.Value("troy")))
    assertEquals(FilterChips.count(chips, period = true), 3)
    assertEquals(FilterChips.count(Nil, period = false), 0)

  test("the layers button says how many layers are on"):
    assertEquals(ViewportToggle.layersLabel(2, ViewportToggle.LayerCount), "Layers · 2 of 6 on")

  test("playback speeds read in plain words"):
    assertEquals(
      TimelinePlayback.Speeds.map(TimelinePlayback.speedWords),
      List("Very slow", "Slow", "Normal speed", "Fast", "Very fast")
    )

  test("the results' tabs: five, a kept key back to its tab, an old tree tab to the sources"):
    assertEquals(SidebarTab.values.toList.map(_.label), List("Sources", "Articles", "Events", "Places", "Pictures"))
    assertEquals(SidebarTab.of(Some(com.histmersion.web.ui.SidebarTab.Key("pictures"))), SidebarTab.Pictures)
    assertEquals(SidebarTab.of(Some(com.histmersion.web.ui.SidebarTab.Key("bytime"))), SidebarTab.Sources)
    assertEquals(SidebarTab.of(None), SidebarTab.Sources)
    assertEquals(ResultsPanel.tabLabel(com.histmersion.api.UiLabels.Text("Places"), Some(com.histmersion.api.RowCount(12))), "Places 12")
    assertEquals(ResultsPanel.tabLabel(com.histmersion.api.UiLabels.Text("Places"), None), "Places")

  test("an item's detail opens its own page in a new tab"):
    assertEquals(ResultDetail.pageOf(Preview.Target.Source(com.histmersion.web.Preview.Target.Source.Citekey("smith2020"))), "/source/smith2020")
    assertEquals(ResultDetail.pageOf(Preview.Target.Place(com.histmersion.web.Preview.Target.Place.Id("pleiades_570406"), Some(com.histmersion.api.Years.Astronomical(-500)))), "/place/pleiades_570406")
    assertEquals(ResultDetail.pageOf(Preview.Target.Event(com.histmersion.web.Preview.Target.Event.Id("ev1"))), "/event/ev1")
    assertEquals(ResultDetail.kindOf(Preview.Target.Article(com.histmersion.web.Preview.Target.Article.Slug("x"))), "article")

  test("the credits: open at first, folded after the first move, open when the map is missing; a choice wins"):
    assert(BaseMaps.footOpenNow(None, moved = false, warns = false))
    assert(!BaseMaps.footOpenNow(None, moved = true, warns = false))
    assert(BaseMaps.footOpenNow(None, moved = true, warns = true))
    assert(BaseMaps.footOpenNow(Some(true), moved = true, warns = false))
    assert(!BaseMaps.footOpenNow(Some(false), moved = false, warns = false))
    // the online OpenStreetMap tiles keep their credit in sight
    assert(BaseMaps.keepsOsmCredit(BaseMap.Standard))
    assert(!BaseMaps.keepsOsmCredit(BaseMap.Readable))
    assert(BaseMaps.allCreditsLink.contains("/settings/credits"))

  test("the places list: the count line on the map and everywhere; a site's note says when its date is unknown"):
    assertEquals(MapLists.countLine(com.histmersion.web.MapLists.ListedCount(3), com.histmersion.web.MapLists.TotalCount(3), com.histmersion.api.Plural.Noun.Singular("place"), com.histmersion.api.Plural.Noun.Plural("places"), onMap = com.histmersion.web.MapLists.OnMap(true)), "3 places on the map")
    assertEquals(
      MapLists.countLine(com.histmersion.web.MapLists.ListedCount(40), com.histmersion.web.MapLists.TotalCount(312), com.histmersion.api.Plural.Noun.Singular("place"), com.histmersion.api.Plural.Noun.Plural("places"), onMap = com.histmersion.web.MapLists.OnMap(true)),
      "312 places on the map · 40 listed, zoom in to list the rest"
    )
    assertEquals(MapLists.countLine(com.histmersion.web.MapLists.ListedCount(1), com.histmersion.web.MapLists.TotalCount(1), com.histmersion.api.Plural.Noun.Singular("picture"), com.histmersion.api.Plural.Noun.Plural("pictures"), onMap = com.histmersion.web.MapLists.OnMap(false)), "1 picture")
    val site = SitePoint(Some(com.histmersion.web.SitePoint.Id("s1")), com.histmersion.web.SitePoint.Label("Tower"), com.histmersion.web.SitePoint.Kind("site"), Some(com.histmersion.web.SitePoint.SiteType("tower")), None, com.histmersion.web.map.LatLon.Lat(37), com.histmersion.web.map.LatLon.Lon(22), None, undated = com.histmersion.web.SitePoint.Undated(true))
    assert(MapLists.siteNote(site).endsWith("date unknown"), MapLists.siteNote(site))

  test("a site or a cell of the tiles marked undated reads so; a bubble says how many have no known date"):
    val s = TileDecode.site(feature(37, 22, "k" -> "s1", "id" -> "s1", "label" -> "Tower", "u" -> 1)).get
    assert(s.undated)
    assert(PointKinds.Sites.classes(s).contains(MapView.UndatedClass))
    assert(PointKinds.Sites.zIndex(s) < PointKinds.Sites.zIndex(s.modify(_.undated).setTo(false)))
    assertEquals(PointKinds.Sites.member(s).undated, 1)
    assert(!TileDecode.site(feature(37, 22, "id" -> "s2", "label" -> "Dated")).get.undated)
    val c = TileDecode.cell(
      feature(10, 20, "k" -> "c4/1/2", "n" -> 12, "u" -> 5, "w" -> 19, "s" -> 9, "e" -> 21, "nn" -> 11),
      DotKind.Site
    )
    assertEquals(c.undated, 5)
    assertEquals(
      DotCluster.summary(DotCluster.Contents(List(CellKind(DotKind.Site).member(c)))),
      "12 sites, 5 with no known date"
    )
    assertEquals(DotCluster.summary(Map(DotKind.Site -> com.histmersion.web.DotCluster.KindCount(3)), com.histmersion.web.DotCluster.Undated(0)), "3 sites")

  test("an event with no known date is drawn undated, beneath the dated"):
    val e = TileDecode.event(feature(40, 26, "id" -> "ev9", "label" -> "Quake", "kind" -> "earthquake")).get
    assert(PointKinds.Events.undated(e))
    assert(PointKinds.Events.classes(e).contains(MapView.UndatedClass))
    val dated = e.modify(_.timeSpan).setTo(TimeSpan(Some(com.histmersion.api.catalogue.Year(-1200L)), None, None, Some(com.histmersion.api.catalogue.Year(-1190L))))
    assert(!PointKinds.Events.undated(dated))
    assert(PointKinds.Events.zIndex(e) < PointKinds.Events.zIndex(dated))

  test("the legend keys the dashed outline while undated places are drawn"):
    assert(MapKey.html(events = com.histmersion.web.MapKey.Events(false), sites = com.histmersion.web.MapKey.Sites(true), undated = com.histmersion.web.MapKey.Undated(true)).contains("date unknown"))
    assert(!MapKey.html(events = com.histmersion.web.MapKey.Events(false), sites = com.histmersion.web.MapKey.Sites(true)).contains("date unknown"))
