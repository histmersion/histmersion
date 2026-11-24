package com.histmersion
package api.personal

import com.histmersion.api.catalogue.{Latitude, Longitude}
import com.histmersion.api.ItemKind
import com.histmersion.api.library.Title
import com.histmersion.api.checks.FormatIssue
import com.histmersion.lint.{LintRule, tolerated}

@tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
class PersonalMarkdownSuite extends munit.FunSuite:

  private val id = Bookmark.Id("0190f3c2-1111-7000-8000-000000000001")
  private val nid = Note.Id("0190f3c2-1111-7000-8000-000000000002")

  test("a bookmark line: its name, the item's title when renamed, what and where, when, its id"):
    val b = Bookmark(
      id,
      Target.item(ItemKind.Source, ItemKey("cline2014_1177")),
      Title("1177 B.C."),
      Some(Bookmark.Label("the collapse book")),
      Bookmark.CreatedAt("2026-10-30T12:40:00.123456Z")
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
      Title("Place on the map"),
      Note.At("2026-10-30T12:40:00Z"),
      Note.At("2026-10-31T09:00:00Z")
    )
    assert(PersonalMarkdown.noteLine(n).endsWith("\n  Bridge here?"), PersonalMarkdown.noteLine(n))
    assert(PersonalMarkdown.note(n).contains("place on the map · 37.50° N, 22.75° E"), PersonalMarkdown.note(n))
    assert(PersonalMarkdown.note(n).contains("changed 2026-10-31 09:00"))
    assertEquals(PersonalMarkdown.excerpt(Note.Text("x" * 200), FormatIssue.Excerpt.Length(10)), "x" * 9 + "…")

  test("read marks"):
    assertEquals(
      PersonalMarkdown.readLine(
        ReadMark(
          ItemKind.Article,
          ItemKey("a"),
          ReadMark.Title("A"),
          read = ReadMark.Read(true),
          readAt = Some(ReadMark.ReadAt("2026-10-30T12:40:00Z"))
        )
      ),
      "- **A** — article `a` · read 2026-10-30 12:40"
    )
