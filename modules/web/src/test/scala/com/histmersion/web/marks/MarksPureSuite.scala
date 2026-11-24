package com.histmersion
package web.marks

import com.histmersion.testsupport.ComparedByValue.given

import hearth.kindlings.optics.syntax.*

import com.histmersion.api.personal.*
import com.histmersion.api.catalogue.{Latitude, Longitude, Year}
import com.histmersion.web.Store
import com.histmersion.web.map.{LatLon, MapSurface}
import com.histmersion.api.ItemKind
import com.histmersion.api.library.Citekey
import com.histmersion.api.library.Title
import com.histmersion.api.research.Article
import com.histmersion.api.personal.ItemKey
import com.histmersion.api.personal.Note
import com.histmersion.lint.{LintRule, tolerated}

@tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
class MarksPureSuite extends munit.FunSuite:

  private val src = ItemRef.source(Citekey("cline2014_year"), Title("1177 B.C. [revised]"))

  test("an item copied as text, as Markdown and as a link"):
    assertEquals(
      ItemMenu.link(ItemMenu.Origin("https://h.example.org/"), src),
      "https://h.example.org/source/cline2014_year"
    )
    assertEquals(ItemMenu.text(src, "Eric H. Cline, 2014"), "1177 B.C. [revised] — Eric H. Cline, 2014")
    assertEquals(
      ItemMenu.markdown(ItemMenu.Origin("https://h.example.org"), src, ""),
      "[1177 B.C. \\[revised\\]](https://h.example.org/source/cline2014_year) [[cline2014_year]]"
    )
    val art = ItemRef.article(Article.Slug("sea-peoples"), Title("Sea Peoples"))
    assertEquals(
      ItemMenu.markdown(ItemMenu.Origin("https://h.example.org"), art, "Concept"),
      "[Sea Peoples](https://h.example.org/article/sea-peoples) — Concept"
    )

  test("where a target opens: its own page, and back where it was left"):
    assertEquals(ItemRef.back(src.target), "/library?item=cline2014_year")
    assertEquals(ItemRef.back(Target.item(ItemKind.Article, ItemKey("a"))), "/library/articles?item=a")
    assertEquals(ItemRef.back(Target(ItemKind.View, view = Some(ViewState("from=-1300")))), "/explore#from=-1300")
    assertEquals(ItemRef.page(Target.item(ItemKind.Place, ItemKey("pleiades_570707"))), "/place/pleiades_570707")
    assertEquals(ItemRef.page(Target.item(ItemKind.Picture, ItemKey("u1"))), "/media/u1/file")
    assertEquals(ItemRef.page(Target(ItemKind.Period, from = Some(Year(-1)), to = Some(Year(1)))), "/explore")

  test("a point from the map: on the world's first copy, its zoom rounded"):
    assertEquals(MapMenu.wrap(LatLon.Lon(190)), -170.0)
    assertEquals(MapMenu.wrap(LatLon.Lon(-185)), 175.0)
    val p = MapMenu.point(
      LatLon(LatLon.Lat(37.123), LatLon.Lon(382.75)),
      MapSurface.Zoom(6.04),
      Some((Year(-1300L), Year(-1100L)))
    )
    assertEquals(
      (
        p.lat.map(_.value.toDouble),
        p.lon.map(_.value.toDouble),
        p.zoom.map(_.value.toDouble),
        p.from.map(_.value.toInt)
      ),
      (Some(37.123), Some(22.75), Some(6.0), Some(-1300))
    )

  test("my periods and the timeline's start"):
    assertEquals(PeriodMarks.target(Year(-1100), Year(-1300)).from.map(_.value.toInt), Some(-1300))
    assertEquals(
      PeriodMarks.startChanges(Year(-500), Year(-3500)),
      List("ui.time.default-from" -> "-3500", "ui.time.default-to" -> "-500")
    )

  test("a saved view's suggested name"):
    assertEquals(
      SavedViews.suggestedName(Some(" tin "), Some(Year(-1300)), Some(Year(-1100))),
      "“tin”, 1300 BCE–1100 BCE"
    )
    assertEquals(SavedViews.suggestedName(None, None, None), "My view")

  test("a draft is the note's own, or what it is being left on"):
    assertEquals(NoteDrafts.key(src.target, Some(Note.Id("n1"))), "note:n1")
    assertEquals(NoteDrafts.key(src.target, None), "source:cline2014_year")
    assertEquals(
      NoteDrafts.key(Target(ItemKind.Point, lat = Some(Latitude(1.5)), lon = Some(Longitude(2))), None),
      "point:1.5,2"
    )
    val was = NoteDrafts.store
    NoteDrafts.store = Store.Memory()
    try
      NoteDrafts.put(NoteDrafts.Key("k"), Note.Text("half a thought"))
      assertEquals(NoteDrafts.get(NoteDrafts.Key("k")), Some("half a thought"))
      NoteDrafts.put(NoteDrafts.Key("k"), Note.Text("   "))
      assertEquals(NoteDrafts.get(NoteDrafts.Key("k")), None, "a draft emptied is no draft")
    finally NoteDrafts.store = was

  test("when, as a person says it"):
    val now = 1_000_000_000_000.0
    assertEquals(Ago(Ago.Millis(now - 5_000), Ago.Millis(now), Ago.IsoDate("")), "just now")
    assertEquals(Ago(Ago.Millis(now - 3 * 60_000), Ago.Millis(now), Ago.IsoDate("")), "3 minutes ago")
    assertEquals(Ago(Ago.Millis(now - 3_600_000), Ago.Millis(now), Ago.IsoDate("")), "1 hour ago")
    assertEquals(Ago(Ago.Millis(now - 3 * 86_400_000), Ago.Millis(now), Ago.IsoDate("")), "3 days ago")
    assertEquals(
      Ago(Ago.Millis(now - 30 * 86_400_000.0), Ago.Millis(now), Ago.IsoDate("2026-09-30T12:00:00Z")),
      "2026-09-30"
    )

  test("the notes a map layer draws: those left at a point"):
    val id = Note.Id("0190f3c2-1111-7000-8000-000000000002")
    val atPoint =
      Note(id, Target(ItemKind.Point, lat = Some(Latitude(1)), lon = Some(Longitude(2))), Note.Text("x"), Title("p"))
    val onItem = atPoint.modify(_.target).setTo(src.target)
    assertEquals(NoteMarkers.placed(List(atPoint, onItem)), List(atPoint))
