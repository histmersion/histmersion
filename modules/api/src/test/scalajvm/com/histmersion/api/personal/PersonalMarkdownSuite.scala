package com.histmersion.api.personal

import com.histmersion.{Latitude, Longitude}
import com.histmersion.api.ItemKind

class PersonalMarkdownSuite extends munit.FunSuite:

  private val id = Bookmark.Id.parse("0190f3c2-1111-7000-8000-000000000001").toOption.get
  private val nid = Note.Id.parse("0190f3c2-1111-7000-8000-000000000002").toOption.get

  test("a bookmark line: its name, the item's title when renamed, what and where, when, its id"):
    val b = Bookmark(
      id,
      Target.item(ItemKind.Source, "cline2014_1177"),
      "1177 B.C.",
      Some(Bookmark.Label("the collapse book")),
      "2026-10-30T12:40:00.123456Z"
    )
    assertEquals(
      PersonalMarkdown.bookmarkLine(b),
      "- **the collapse book** (1177 B.C.) — source `cline2014_1177` · 2026-10-30 12:40 · id `0190f3c2-1111-7000-8000-000000000001`"
    )
    assertEquals(PersonalMarkdown.bookmarks(Nil), "_No bookmarks._")

  test("a note: its first line as the excerpt, in full with what it was left on"):
    val n = Note(
      nid,
      Target(ItemKind.Point, lat = Some(Latitude(37.5)), lon = Some(Longitude(22.75))),
      Note.Text("\n\nBridge here?\nSecond line."),
      "Place on the map",
      "2026-10-30T12:40:00Z",
      "2026-10-31T09:00:00Z"
    )
    assert(PersonalMarkdown.noteLine(n).endsWith("\n  Bridge here?"), PersonalMarkdown.noteLine(n))
    assert(PersonalMarkdown.note(n).contains("place on the map · 37.50° N, 22.75° E"), PersonalMarkdown.note(n))
    assert(PersonalMarkdown.note(n).contains("changed 2026-10-31 09:00"))
    assertEquals(PersonalMarkdown.excerpt("x" * 200, 10), "x" * 9 + "…")

  test("read marks"):
    assertEquals(
      PersonalMarkdown.readLine(
        ReadMark(ItemKind.Article, ItemKey("a"), "A", read = true, readAt = Some("2026-10-30T12:40:00Z"))
      ),
      "- **A** — article `a` · read 2026-10-30 12:40"
    )
