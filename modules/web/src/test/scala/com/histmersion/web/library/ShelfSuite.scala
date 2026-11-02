package com.histmersion.web.library

import com.histmersion.api.personal.*
import com.histmersion.Year
import com.histmersion.web.LibrarySection
import com.histmersion.api.ItemKind

class ShelfSuite extends munit.FunSuite:

  test("a shelf in the address, kept beside the chosen item"):
    assertEquals(LibraryAddress.shelf("?shelf=notes&item=x"), Shelf.Notes)
    assertEquals(LibraryAddress.shelf("?shelf=nonsense"), Shelf.Everything)
    assertEquals(LibraryAddress.search(Some("a b"), Shelf.Unread), "?shelf=unread&item=a%20b")
    assertEquals(
      LibraryAddress.href(LibrarySection.Articles, None, Shelf.Bookmarks),
      "/library/articles?shelf=bookmarks"
    )
    assertEquals(LibraryAddress.search(None), "")

  private val bid = Bookmark.Id.parse("0190f3c2-1111-7000-8000-000000000001").toOption.get

  test("a bookmark's row: its name, the item's title when renamed, a dot while a source is not read"):
    val b =
      Bookmark(
        bid,
        Target.item(ItemKind.Source, "zz"),
        "Zz title",
        Some(Bookmark.Label("mine")),
        "2026-10-30T12:00:00Z"
      )
    val r = ShelfRow.of(b, _ => false, _ => "now")
    assertEquals((r.name, r.kind, r.line, r.unread), ("mine", "Source", "Zz title", true))
    assertEquals(ShelfRow.of(b, _ == "source:zz", _ => "now").unread, false)
    val p = Bookmark(
      bid,
      Target(ItemKind.Period, from = Some(Year(-1200)), to = Some(Year(-1150))),
      "Period",
      Some(Bookmark.Label("crisis"))
    )
    assertEquals(
      (ShelfRow.of(p, _ => false, _ => "").line, ShelfRow.of(p, _ => false, _ => "").unread),
      ("1200 BCE–1150 BCE", false)
    )

  test("recently opened: the opened ones, last first"):
    val a = ReadMark(ItemKind.Source, ItemKey("a"), "A", openedAt = Some("2026-10-30T10:00:00Z"))
    val b = ReadMark(ItemKind.Article, ItemKey("b"), "B", openedAt = Some("2026-10-30T11:00:00Z"))
    val c = ReadMark(ItemKind.Source, ItemKey("c"), "C", read = true)
    assertEquals(ShelfRow.recent(List(a, b, c)).map(_.title), List("B", "A"))
