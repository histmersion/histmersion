package com.histmersion.api.personal

import com.histmersion.api.catalogue.{Latitude, Longitude}
import com.histmersion.api.ItemKind

class PersonalMarkdownSuite extends munit.FunSuite:

  private val id = Bookmark.Id("0190f3c2-1111-7000-8000-000000000001")
  private val nid = Note.Id("0190f3c2-1111-7000-8000-000000000002")

  test("a bookmark line: its name, the item's title when renamed, what and where, when, its id"):
    val b = Bookmark(
      id,
      Target.item(ItemKind.Source, com.histmersion.api.personal.ItemKey("cline2014_1177")),
      com.histmersion.api.library.Title("1177 B.C."),
      Some(Bookmark.Label("the collapse book")),
      com.histmersion.api.personal.Bookmark.CreatedAt("2026-10-30T12:40:00.123456Z")
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
      com.histmersion.api.library.Title("Place on the map"),
      com.histmersion.api.personal.Note.CreatedAt("2026-10-30T12:40:00Z"),
      com.histmersion.api.personal.Note.UpdatedAt("2026-10-31T09:00:00Z")
    )
    assert(PersonalMarkdown.noteLine(n).endsWith("\n  Bridge here?"), PersonalMarkdown.noteLine(n))
    assert(PersonalMarkdown.note(n).contains("place on the map · 37.50° N, 22.75° E"), PersonalMarkdown.note(n))
    assert(PersonalMarkdown.note(n).contains("changed 2026-10-31 09:00"))
    assertEquals(PersonalMarkdown.excerpt("x" * 200, com.histmersion.api.checks.FormatIssue.Excerpt.Length(10)), "x" * 9 + "…")

  test("read marks"):
    assertEquals(
      PersonalMarkdown.readLine(
        ReadMark(ItemKind.Article, ItemKey("a"), com.histmersion.api.personal.ReadMark.Title("A"), read = com.histmersion.api.personal.ReadMark.Read(true), readAt = Some(com.histmersion.api.personal.ReadMark.ReadAt("2026-10-30T12:40:00Z")))
      ),
      "- **A** — article `a` · read 2026-10-30 12:40"
    )
