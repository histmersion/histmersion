package com.histmersion
package domain.personal

import com.histmersion.testsupport.ComparedByValue.given

import hearth.kindlings.optics.syntax.*

import com.histmersion.api.personal.{MapZoom, Target, ViewState}
import com.histmersion.api.ItemKind as MarkKind
import com.histmersion.domain.{Described, ItemKey, ItemRef, ItemUid, RecordState}
import com.histmersion.{Latitude, Longitude, Year}
import com.histmersion.domain.Title

import java.time.Instant
import java.util.UUID
import com.histmersion.api.personal.Bookmark
import com.histmersion.api.personal.Note
import com.histmersion.api.ItemKind
import com.histmersion.lint.{tolerated, LintRule}

@tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
class PersonalTypesSuite extends munit.FunSuite:

  test("times are kept and written to the microsecond, as Postgres keeps them"):
    assertEquals(MarkTime.at(Instant.parse("2026-10-30T12:00:00.123456789Z")).iso, "2026-10-30T12:00:00.123456Z")
    assertEquals(MarkTime.at(Instant.parse("2026-10-30T12:00:00Z")).iso, "2026-10-30T12:00:00.000000Z")
    assertEquals(MarkTime.parse(MarkTime.Iso("2026-10-30T12:00:00.123456Z")).iso, "2026-10-30T12:00:00.123456Z")

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private val ref = ItemRef(MarkTarget.itemKind(MarkKind.Source), ItemKey("zz"), Some(ItemUid(UUID.randomUUID())))

  test("a title: the item's now, else the one kept, else its key or its kind"):
    val src = MarkTarget.Item(ref)
    def described(title: Option[String], key: String = "zz") =
      Map(ref -> Described(ItemKey(key), title.map(Title(_)), RecordState.Live))
    assertEquals(
      src.shown(described(Some(" Now ")), TargetLabel.tidy(TargetLabel.Label("then")))._2.value: String,
      "Now"
    )
    assertEquals(
      src.shown(Map.empty, TargetLabel.tidy(TargetLabel.Label("then  and\nnow")))._2.value: String,
      "then and now"
    )
    assertEquals(src.shown(described(Some(" ")), None)._2.value: String, "zz")
    val view = MarkTarget.View(None, None, None, None, None, None)
    assertEquals(view.shown(Map.empty, None)._2.value: String, "Saved view")

  test("an item shows the key its owner names it by now"):
    val now =
      MarkTarget.Item(ref).shown(Map(ref -> Described(ItemKey("zz2"), None, RecordState.Renamed(ItemKey("zz2")))), None)
    assertEquals(now._1.itemRef.map(_.key.value: String), Some("zz2"))
    assertEquals(now._2.value: String, "zz2")

  test("labels and texts tidied and bounded"):
    assertEquals(Bookmark.Label.tidy(Some(Bookmark.Label("  "))), Right(None))
    assert(Bookmark.Label.tidy(Some(Bookmark.Label("x" * 201))).isLeft)
    assertEquals(Note.Text.tidy(Note.Text("a\r\nb  \n\n")).map(_.value.toString), Right("a\nb"))
    assert(Note.Text.tidy(Note.Text("x" * (Note.Text.MaxChars + 1))).isLeft)
    assertEquals(TargetLabel.tidy(TargetLabel.Label(" a \t b ")).map(_.value: String), Some("a b"))
    assertEquals(TargetLabel.tidy(TargetLabel.Label("x" * 400)).map(_.value.length), Some(TargetLabel.MaxChars))
    assertEquals(TargetLabel.tidy(Some(TargetLabel.Label("   "))), None)

  test("search: every word, case ignored"):
    val ws = SearchWords.of(Some(SearchWords.Query("  Bronze  AGE ")))
    assertEquals(ws.words, List("bronze", "age"))
    assert(ws.matches("The Bronze", "Age"))
    assert(!ws.matches("bronze only"))
    assert(SearchWords.Everything.matches(""))

  test("newest first, then the higher id"):
    val t = MarkTime.at(Instant.parse("2026-10-30T12:00:00Z"))
    val later = MarkTime.at(Instant.parse("2026-10-30T12:00:01Z"))
    val rows = List(("a", t), ("c", t), ("b", later))
    assertEquals(PersonalState.newestFirst(rows)(_._2, _._1).map(_._1), List("b", "c", "a"))

  test("a target crosses the wire and the columns unchanged"):
    val targets = List(
      MarkTarget.Item(ref.modify(_.uid).setTo(None)),
      MarkTarget.Point(Latitude(37.0), Longitude(22.0), Some(MapZoom(5.0)), Some(Year(-1300)), None),
      MarkTarget.View(Some(ViewState("a=b")), Some(Latitude(1.0)), None, None, None, Some(Year(10))),
      MarkTarget.Span(Year(-1200), Year(-1150))
    )
    targets.foreach { t =>
      assertEquals(MarkTarget.fromWire(t.wire), Some(t))
      val c = TargetColumns.of(t)
      assertEquals(MarkTarget.ofColumns(c.kind, c.key, c.uid, c.lat, c.lon, c.zoom, c.from, c.to, c.view), Some(t))
    }
    assertEquals(
      MarkTarget.fromWire(Target.item(MarkKind.Goal, api.personal.ItemKey("g"))).map(_.kind),
      Some(MarkKind.Goal)
    )

  test("a stored row read back: an unknown kind or a point without its position is dropped, not guessed"):
    assertEquals(MarkTarget.ofColumns(ItemKind.Wire("nonsense"), None, None, None, None, None, None, None, None), None)
    assertEquals(
      MarkTarget.ofColumns(ItemKind.Wire("point"), None, None, None, Some(Longitude(22.0)), None, None, None, None),
      None
    )
    assertEquals(MarkTarget.ofColumns(ItemKind.Wire("source"), None, None, None, None, None, None, None, None), None)

  test("wire coordinates are checked before becoming domain coordinates"):
    def point(lat: Double, lon: Double) = Target(
      kind = MarkKind.Point,
      lat = Some(api.catalogue.Latitude(lat)),
      lon = Some(api.catalogue.Longitude(lon))
    )
    assert(MarkTarget.fromWire(point(90.0, 180.0)).nonEmpty)
    assert(MarkTarget.fromWire(point(-90.0, -180.0)).nonEmpty)
    assertEquals(MarkTarget.fromWire(point(90.1, 0.0)), None)
    assertEquals(MarkTarget.fromWire(point(0.0, -180.1)), None)
    assertEquals(MarkTarget.fromWire(point(Double.NaN, 0.0)), None)
    assertEquals(MarkTarget.fromWire(point(0.0, Double.PositiveInfinity)), None)
