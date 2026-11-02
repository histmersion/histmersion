package com.histmersion.domain.personal

import com.histmersion.api.personal.{MapZoom, Target, ViewState}
import com.histmersion.api.ItemKind as MarkKind
import com.histmersion.domain.{Described, ItemKey, ItemRef, ItemUid, RecordState}
import com.histmersion.{Latitude, Longitude, Year}
import com.histmersion.domain.library.Title

import java.time.Instant
import java.util.UUID
import com.histmersion.api.personal.Bookmark
import com.histmersion.api.personal.Note

class PersonalTypesSuite extends munit.FunSuite:

  test("times are kept and written to the microsecond, as Postgres keeps them"):
    assertEquals(MarkTime.at(Instant.parse("2026-10-30T12:00:00.123456789Z")).iso, "2026-10-30T12:00:00.123456Z")
    assertEquals(MarkTime.at(Instant.parse("2026-10-30T12:00:00Z")).iso, "2026-10-30T12:00:00.000000Z")
    assertEquals(MarkTime.parse("2026-10-30T12:00:00.123456Z").iso, "2026-10-30T12:00:00.123456Z")

  private val ref = ItemRef(MarkTarget.itemKind(MarkKind.Source), ItemKey("zz"), Some(ItemUid(UUID.randomUUID())))

  test("a title: the item's now, else the one kept, else its key or its kind"):
    val src = MarkTarget.Item(ref)
    def described(title: Option[String], key: String = "zz") =
      Map(ref -> Described(ItemKey(key), title.map(Title(_)), RecordState.Live))
    assertEquals(src.shown(described(Some(" Now ")), TargetLabel.tidy("then"))._2.value: String, "Now")
    assertEquals(src.shown(Map.empty, TargetLabel.tidy("then  and\nnow"))._2.value: String, "then and now")
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
    assertEquals(TargetLabel.tidy(" a \t b ").map(_.value: String), Some("a b"))
    assertEquals(TargetLabel.tidy("x" * 400).map(_.value.length), Some(TargetLabel.MaxChars))
    assertEquals(TargetLabel.tidy(Some("   ")), None)

  test("search: every word, case ignored"):
    val ws = SearchWords.of(Some("  Bronze  AGE "))
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
      MarkTarget.Item(ref.copy(uid = None)),
      MarkTarget.Point(Latitude(37.0), Longitude(22.0), Some(MapZoom(5.0)), Some(Year(-1300)), None),
      MarkTarget.View(Some(ViewState("a=b")), Some(Latitude(1.0)), None, None, None, Some(Year(10))),
      MarkTarget.Span(Year(-1200), Year(-1150))
    )
    targets.foreach { t =>
      assertEquals(MarkTarget.fromWire(t.wire), Some(t))
      val c = TargetColumns.of(t)
      assertEquals(MarkTarget.ofColumns(c.kind, c.key, c.uid, c.lat, c.lon, c.zoom, c.from, c.to, c.view), Some(t))
    }
    assertEquals(MarkTarget.fromWire(Target.item(MarkKind.Goal, "g")).map(_.kind), Some(MarkKind.Goal))

  test("a stored row read back: an unknown kind or a point without its position is dropped, not guessed"):
    assertEquals(MarkTarget.ofColumns("nonsense", None, None, None, None, None, None, None, None), None)
    assertEquals(MarkTarget.ofColumns("point", None, None, None, Some(Longitude(22.0)), None, None, None, None), None)
    assertEquals(MarkTarget.ofColumns("source", None, None, None, None, None, None, None, None), None)
