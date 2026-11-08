package com.histmersion
package web.library

import com.histmersion.testsupport.ComparedByValue.given

import com.histmersion.api.personal.*
import com.histmersion.api.catalogue.Year
import com.histmersion.web.LibrarySection
import com.histmersion.api.ItemKind
import com.histmersion.web.Pages
import com.histmersion.api.library.Title

class ShelfSuite extends munit.FunSuite:

  test("a shelf in the address, kept beside the chosen item"):
    assertEquals(LibraryAddress.shelf(Pages.UrlQuery("?shelf=notes&item=x")), Shelf.Notes)
    assertEquals(LibraryAddress.shelf(Pages.UrlQuery("?shelf=nonsense")), Shelf.Everything)
    assertEquals(LibraryAddress.search(Some(ItemKey("a b")), Shelf.Unread), "?shelf=unread&item=a%20b")
    assertEquals(
      LibraryAddress.href(LibrarySection.Articles, None, Shelf.Bookmarks),
      "/library/articles?shelf=bookmarks"
    )
    assertEquals(LibraryAddress.search(None), "")

  private val bid = Bookmark.Id("0190f3c2-1111-7000-8000-000000000001")

  test("a bookmark's row: its name, the item's title when renamed, a dot while a source is not read"):
    val b =
      Bookmark(
        bid,
        Target.item(ItemKind.Source, ItemKey("zz")),
        Title("Zz title"),
        Some(Bookmark.Label("mine")),
        Bookmark.CreatedAt("2026-10-30T12:00:00Z")
      )
    val r = ShelfRow.of(b, _ => false, _ => "now")
    assertEquals((r.name, r.kind, r.line, r.unread), ("mine", "Source", "Zz title", true))
    assertEquals(ShelfRow.of(b, _.value == "source:zz", _ => "now").unread, false)
    val p = Bookmark(
      bid,
      Target(ItemKind.Period, from = Some(Year(-1200)), to = Some(Year(-1150))),
      Title("Period"),
      Some(Bookmark.Label("crisis"))
    )
    assertEquals(
      (ShelfRow.of(p, _ => false, _ => "").line, ShelfRow.of(p, _ => false, _ => "").unread),
      ("1200 BCE–1150 BCE", false)
    )

  test("recently opened: the opened ones, last first"):
    val a = ReadMark(
      ItemKind.Source,
      ItemKey("a"),
      ReadMark.Title("A"),
      openedAt = Some(ReadMark.OpenedAt("2026-10-30T10:00:00Z"))
    )
    val b = ReadMark(
      ItemKind.Article,
      ItemKey("b"),
      ReadMark.Title("B"),
      openedAt = Some(ReadMark.OpenedAt("2026-10-30T11:00:00Z"))
    )
    val c = ReadMark(ItemKind.Source, ItemKey("c"), ReadMark.Title("C"), read = ReadMark.Read(true))
    assertEquals(ShelfRow.recent(List(a, b, c)).map(_.title), List("B", "A"))
