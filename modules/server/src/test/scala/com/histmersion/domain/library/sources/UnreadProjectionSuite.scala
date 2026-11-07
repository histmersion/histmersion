package com.histmersion.domain.library.sources

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
    controller.unread(Caller.Local, Some(com.histmersion.api.ItemKind.Wire("source")), None, None).toOption.get.rows.head

  test("every library status keeps its wire spelling in the personal projection"):
    List("foundational", "current", "contested", "superseded", "debunked", "retracted", "unassessed").foreach { spelling =>
      val item = SourceReadable.row(com.histmersion.domain.library.Citekey("reader2026_a"), com.histmersion.domain.Title("Title"), Nil, None, Some(com.histmersion.domain.library.Source.Status.Wire(spelling.toUpperCase(java.util.Locale.ROOT))), None, None)
      assertEquals(render(item).status, Some(spelling))
    }

  test("missing and unknown statuses stay absent, without trimming or inventing a default"):
    List(None, Some(com.histmersion.domain.library.Source.Status.Wire("unknown")), Some(com.histmersion.domain.library.Source.Status.Wire(" current "))).foreach { raw =>
      assertEquals(render(SourceReadable.row(com.histmersion.domain.library.Citekey("reader2026_a"), com.histmersion.domain.Title("Title"), Nil, None, raw, None, None)).status, None)
    }

  test("author spelling, order, duplicates and whitespace survive the projection"):
    val authors = List("  Doe, Ann  ", "Li", "Li", "").map(FullSourceRow.Authors(_))
    val shown = render(SourceReadable.row(com.histmersion.domain.library.Citekey("reader2026_a"), com.histmersion.domain.Title("Title"), authors, None, None, None, None))
    assertEquals(shown.authors, authors)

  test("identity, title, year and period survive the same conversion and rendering"):
    val shown = render(SourceReadable.row(com.histmersion.domain.library.Citekey("reader2026_a"), com.histmersion.domain.Title("  A title  "), Nil, Some(com.histmersion.Year(2026)), None, Some(com.histmersion.domain.library.sources.SourceReadable.RowStart(-1200L)), Some(com.histmersion.domain.library.sources.SourceReadable.RowEnd(-1100L))))
    assertEquals((shown.id.value: String, shown.title, shown.year, shown.periodStart, shown.periodEnd),
      ("reader2026_a", "  A title  ", Some(2026), Some(-1200L), Some(-1100L)))
