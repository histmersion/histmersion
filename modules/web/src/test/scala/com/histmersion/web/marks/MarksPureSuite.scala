package com.histmersion.web.marks

import com.histmersion.api.personal.*
import com.histmersion.values.{Latitude, Longitude, Year}
import com.histmersion.web.Store
import com.histmersion.web.map.LatLon

class MarksPureSuite extends munit.FunSuite:

  private val src = ItemRef.source("cline2014_year", "1177 B.C. [revised]")

  test("an item copied as text, as Markdown and as a link"):
    assertEquals(ItemMenu.link("https://h.example.org/", src), "https://h.example.org/source/cline2014_year")
    assertEquals(ItemMenu.text(src, "Eric H. Cline, 2014"), "1177 B.C. [revised] — Eric H. Cline, 2014")
    assertEquals(
      ItemMenu.markdown("https://h.example.org", src, ""),
      "[1177 B.C. \\[revised\\]](https://h.example.org/source/cline2014_year) [[cline2014_year]]"
    )
    val art = ItemRef.article("sea-peoples", "Sea Peoples")
    assertEquals(
      ItemMenu.markdown("https://h.example.org", art, "Concept"),
      "[Sea Peoples](https://h.example.org/article/sea-peoples) — Concept"
    )

  test("where a target opens: its own page, and back where it was left"):
    assertEquals(ItemRef.back(src.target), "/library?item=cline2014_year")
    assertEquals(ItemRef.back(Target.item(ItemKind.Article, "a")), "/library/articles?item=a")
    assertEquals(ItemRef.back(Target(ItemKind.View, view = Some(ViewState("from=-1300")))), "/explore#from=-1300")
    assertEquals(ItemRef.page(Target.item(ItemKind.Place, "pleiades_570707")), "/place/pleiades_570707")
    assertEquals(ItemRef.page(Target.item(ItemKind.Picture, "u1")), "/media/u1/file")
    assertEquals(ItemRef.page(Target(ItemKind.Period, from = Some(Year(-1)), to = Some(Year(1)))), "/explore")

  test("a point from the map: on the world's first copy, its zoom rounded"):
    assertEquals(MapMenu.wrap(190), -170.0)
    assertEquals(MapMenu.wrap(-185), 175.0)
    val p = MapMenu.point(LatLon(37.123, 382.75), 6.04, Some((-1300L, -1100L)))
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
    assertEquals(PeriodMarks.target(-1100, -1300).from.map(_.value.toInt), Some(-1300))
    assertEquals(
      PeriodMarks.startChanges(-500, -3500),
      List("ui.time.default-from" -> "-3500", "ui.time.default-to" -> "-500")
    )

  test("a saved view's suggested name"):
    assertEquals(SavedViews.suggestedName(Some(" tin "), Some(-1300), Some(-1100)), "“tin”, 1300 BCE–1100 BCE")
    assertEquals(SavedViews.suggestedName(None, None, None), "My view")

  test("a draft is the note's own, or what it is being left on"):
    assertEquals(NoteDrafts.key(src.target, Some("n1")), "note:n1")
    assertEquals(NoteDrafts.key(src.target, None), "source:cline2014_year")
    assertEquals(
      NoteDrafts.key(Target(ItemKind.Point, lat = Some(Latitude(1.5)), lon = Some(Longitude(2))), None),
      "point:1.5,2"
    )
    val was = NoteDrafts.store
    NoteDrafts.store = Store.Memory()
    try
      NoteDrafts.put("k", "half a thought")
      assertEquals(NoteDrafts.get("k"), Some("half a thought"))
      NoteDrafts.put("k", "   ")
      assertEquals(NoteDrafts.get("k"), None, "a draft emptied is no draft")
    finally NoteDrafts.store = was

  test("when, as a person says it"):
    val now = 1_000_000_000_000.0
    assertEquals(Ago(now - 5_000, now, ""), "just now")
    assertEquals(Ago(now - 3 * 60_000, now, ""), "3 minutes ago")
    assertEquals(Ago(now - 3_600_000, now, ""), "1 hour ago")
    assertEquals(Ago(now - 3 * 86_400_000, now, ""), "3 days ago")
    assertEquals(Ago(now - 30 * 86_400_000.0, now, "2026-09-30T12:00:00Z"), "2026-09-30")

  test("the notes a map layer draws: those left at a point"):
    val id = NoteId.parse("0190f3c2-1111-7000-8000-000000000002").toOption.get
    val atPoint =
      Note(id, Target(ItemKind.Point, lat = Some(Latitude(1)), lon = Some(Longitude(2))), NoteText("x"), "p")
    val onItem = atPoint.copy(target = src.target)
    assertEquals(NoteMarkers.placed(List(atPoint, onItem)), List(atPoint))
