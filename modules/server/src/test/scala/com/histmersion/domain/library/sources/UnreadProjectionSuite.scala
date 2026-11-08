package com.histmersion
package domain.library.sources

import com.histmersion.testsupport.ComparedByValue.given

import com.histmersion.domain.{Caller, Referable, Referables}
import com.histmersion.domain.personal.{PersonalControllers, PersonalState}
import com.histmersion.domain.personal.bookmarks.BookmarkService
import com.histmersion.domain.personal.notes.NoteService
import com.histmersion.domain.personal.reading.ReadingService
import com.histmersion.domain.personal.marks.{Readable, UnreadItem}
import com.histmersion.util.data.maintenance.WriteGate
import java.time.{Clock, Instant, ZoneOffset}
import java.util.concurrent.atomic.AtomicReference
import com.histmersion.api.ItemKind
import com.histmersion.domain.library.Citekey
import com.histmersion.domain.Title
import com.histmersion.domain.library.Source

class UnreadProjectionSuite extends munit.FunSuite:
  private def render(item: UnreadItem) =
    val state = AtomicReference(PersonalState())
    val items = Referables(List(Referable.InMemory(SourceReferable.Kind)))
    val readable = Readable.InMemory(SourceReferable.Kind)
    readable.add(item, None)
    val controller = PersonalControllers(
      BookmarkService.InMemory(state, items),
      NoteService.InMemory(state, items),
      ReadingService.InMemory(state, items, List(readable)),
      WriteGate(),
      Clock.fixed(Instant.parse("2026-11-01T00:00:00Z"), ZoneOffset.UTC)
    )
    controller.unread(Caller.Local, Some(ItemKind.Wire("source")), None, None).toOption.get.rows.head

  test("every library status keeps its wire spelling in the personal projection"):
    List("foundational", "current", "contested", "superseded", "debunked", "retracted", "unassessed").foreach { spelling =>
      val item = SourceReadable.row(Citekey("reader2026_a"), Title("Title"), Nil, None, Some(Source.Status.Wire(spelling.toUpperCase(java.util.Locale.ROOT))), None, None)
      assertEquals(render(item).status, Some(spelling))
    }

  test("missing and unknown statuses stay absent, without trimming or inventing a default"):
    List(None, Some(Source.Status.Wire("unknown")), Some(Source.Status.Wire(" current "))).foreach { raw =>
      assertEquals(render(SourceReadable.row(Citekey("reader2026_a"), Title("Title"), Nil, None, raw, None, None)).status, None)
    }

  test("author spelling, order, duplicates and whitespace survive the projection"):
    val authors = List("  Doe, Ann  ", "Li", "Li", "").map(FullSourceRow.Authors(_))
    val shown = render(SourceReadable.row(Citekey("reader2026_a"), Title("Title"), authors, None, None, None, None))
    assertEquals(shown.authors, authors)

  test("identity, title, year and period survive the same conversion and rendering"):
    val shown = render(SourceReadable.row(Citekey("reader2026_a"), Title("  A title  "), Nil, Some(Year(2026)), None, Some(SourceReadable.RowStart(-1200L)), Some(SourceReadable.RowEnd(-1100L))))
    assertEquals((shown.id.value: String, shown.title, shown.year, shown.periodStart, shown.periodEnd),
      ("reader2026_a", "  A title  ", Some(2026), Some(-1200L), Some(-1100L)))
