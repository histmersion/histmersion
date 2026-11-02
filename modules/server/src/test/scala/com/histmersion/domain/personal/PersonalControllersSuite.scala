package com.histmersion.domain.personal

import com.histmersion.api.Write
import com.histmersion.api.personal.*
import com.histmersion.domain.{Caller, Referable, Referables}
import com.histmersion.domain.library.sources.SourceReferable
import com.histmersion.domain.personal.bookmarks.BookmarkService
import com.histmersion.domain.personal.marks.Readable
import com.histmersion.domain.personal.notes.NoteService
import com.histmersion.domain.personal.reading.ReadingService
import com.histmersion.util.data.maintenance.WriteGate
import com.histmersion.{Latitude, Longitude, Year}

import java.time.{Clock, Instant, ZoneId, ZoneOffset}
import java.util.concurrent.atomic.AtomicReference
import com.histmersion.api.ItemKind
import com.histmersion.domain.automation.Run

/** What the endpoints answer, over the in-memory services: inputs checked, the codes the statuses come from, the
  * caller's own marks.
  */
class PersonalControllersSuite extends munit.FunSuite:

  /** A clock the test moves forward a minute at a time. */
  final private class Ticking extends Clock:
    val now = AtomicReference(Instant.parse("2026-10-30T12:00:00Z"))
    def tick(): Unit = now.updateAndGet(_.plusSeconds(60)): Unit
    override def instant(): Instant = now.get
    def getZone: ZoneId = ZoneOffset.UTC
    override def withZone(zone: ZoneId): Clock = this

  private val me = Caller.Local

  private def fixture(maintenance: WriteGate = WriteGate(), clock: Clock = Ticking()): PersonalControllers =
    val state = AtomicReference(PersonalState())
    val items = Referables(List(Referable.InMemory(SourceReferable.Kind)))
    PersonalControllers(
      BookmarkService.InMemory(state, items),
      NoteService.InMemory(state, items),
      ReadingService.InMemory(state, items, List(Readable.InMemory(SourceReferable.Kind))),
      maintenance,
      clock
    )

  test("a bookmark's target is checked: an item names its id, a point its position, a period its years in order"):
    val c = fixture()
    assert(c.addBookmark(me, BookmarkNew(Target(ItemKind.Source))).left.exists(_.message.contains("its id")))
    assert(
      c.addBookmark(me, BookmarkNew(Target(ItemKind.Point, lat = Some(Latitude(1)))))
        .left
        .exists(_.message.contains("lat and lon"))
    )
    assert(
      c.addBookmark(me, BookmarkNew(Target(ItemKind.Period, from = Some(Year(-1000)), to = Some(Year(-1200)))))
        .left
        .exists(_.message.contains("before it starts"))
    )
    val p = c.addBookmark(
      me,
      BookmarkNew(
        Target(ItemKind.Period, id = Some(ItemKey("dropped")), from = Some(Year(-1200)), to = Some(Year(-1150))),
        Some(BookmarkLabel("  the crisis   years "))
      )
    )
    assertEquals(p.map(b => (b.name, b.target.id)), Right(("the crisis years", None)))
    assert(c.addBookmark(me, BookmarkNew(Target.item(ItemKind.Source, "x"), Some(BookmarkLabel("x" * 201)))).isLeft)

  test("a note goes on an item or a point, never a view; it needs text"):
    val c = fixture()
    assert(
      c.addNote(me, NoteNew(Target(ItemKind.View, view = Some(ViewState("a=b"))), NoteText("x")))
        .left
        .exists(_.message.contains("cannot"))
    )
    assert(
      c.addNote(me, NoteNew(Target.item(ItemKind.Source, "zz"), NoteText("   ")))
        .left
        .exists(_.message.contains("some text"))
    )
    val n = c.addNote(
      me,
      NoteNew(Target(ItemKind.Point, lat = Some(Latitude(37)), lon = Some(Longitude(22))), NoteText("here\r\n"))
    )
    assertEquals(n.map(_.text.value.toString), Right("here"))

  test("not found is 404, a stale edit 412, a bad id 400; an edit seen at no time at all is a stale one"):
    val clock = Ticking()
    val c = fixture(clock = clock)
    val n = c.addNote(me, NoteNew(Target.item(ItemKind.Source, "zz"), NoteText("one"))).toOption.get
    clock.tick()
    assertEquals(
      c.editNote(me, n.id.value, NoteEdit(NoteText("two"), Some(n.updatedAt))).map(_.text.value.toString),
      Right("two")
    )
    val stale = c.editNote(me, n.id.value, NoteEdit(NoteText("three"), Some(n.updatedAt)))
    assertEquals(stale.left.map(_.code), Left(Some(Write.CodePrecondition)))
    assert(stale.left.exists(_.message.contains("2026-10-30T12:01:00.000000Z")), stale.toString)
    assertEquals(
      c.editNote(me, n.id.value, NoteEdit(NoteText("four"), Some("yesterday"))).left.map(_.code),
      Left(Some(Write.CodePrecondition))
    )
    assertEquals(c.note(me, "not-an-id").left.map(_.code), Left(None))
    assertEquals(c.note(me, "00000000-0000-0000-0000-000000000000").left.map(_.code), Left(Some(Write.CodeNotFound)))
    assertEquals(
      c.removeBookmark(me, "00000000-0000-0000-0000-000000000000").left.map(_.code),
      Left(Some(Write.CodeNotFound))
    )
    assertEquals(c.removeNote(me, n.id.value.toUpperCase).map(_.id), Right(n.id))

  test("lists: kinds by name, a word search, a limit"):
    val clock = Ticking()
    val c = fixture(clock = clock)
    List("a", "b", "c").foreach { k =>
      c.addBookmark(me, BookmarkNew(Target.item(ItemKind.Source, s"zz_$k"), title = Some(s"Zz $k"))); clock.tick()
    }
    assertEquals(c.listBookmarks(me, Some("sources"), None, Some(2)).map(_.map(_.title)), Right(List("Zz c", "Zz b")))
    assertEquals(c.listBookmarks(me, None, Some("zz B"), None).map(_.map(_.title)), Right(List("Zz b")))
    assert(c.listBookmarks(me, Some("nonsense"), None, None).isLeft)
    assert(c.listNotes(me, Some("view"), None, None, None).isLeft, "a view holds no notes")

  test("only sources and articles are marked read; show is read, opened or all"):
    val c = fixture()
    assert(c.setRead(me, ReadMarkSet(ItemKind.Place, ItemKey("p"), read = true)).isLeft)
    assertEquals(
      c.setRead(me, ReadMarkSet(ItemKind.Source, ItemKey("zz"), read = true, Some("Zz"))).map(m => (m.read, m.title)),
      Right((true, "Zz"))
    )
    assertEquals(c.readMarks(me, None, Some("opened"), None).map(_.size), Right(0))
    assert(c.readMarks(me, None, Some("nope"), None).isLeft)
    assertEquals(c.opened(me, ReadOpened(ItemKind.Article, ItemKey("a"))).map(_.openedAt.isDefined), Right(true))
    assertEquals(c.unread(me, Some("goal"), None, None).isLeft, true)
    assertEquals(c.unread(me, None, None, Some(-3)).map(p => (p.total, p.offset)), Right((0, 0)))

  test("the marks are the caller's own; an automated run has none"):
    val c = fixture()
    val run = Caller.Automation(Run.Id("0b3f6a4e-2c1d-4e5f-8a9b-0c1d2e3f4a5b"))
    assert(c.listBookmarks(run, None, None, None).isLeft)
    assert(c.addBookmark(run, BookmarkNew(Target.item(ItemKind.Source, "zz"))).isLeft)
    val someone = Caller.Person(com.histmersion.UserId("someone"), Caller.Role.Reader)
    c.addBookmark(someone, BookmarkNew(Target.item(ItemKind.Source, "zz")))
    assertEquals(c.listBookmarks(me, None, None, None).map(_.size), Right(0))
    assertEquals(c.listBookmarks(someone, None, None, None).map(_.size), Right(1))

  test("writes wait while a restore holds the maintenance lock"):
    val m = WriteGate()
    val c = fixture(m)
    val refused = m.exclusively("restore")(Right(c.addBookmark(me, BookmarkNew(Target.item(ItemKind.Source, "zz")))))
    assert(refused.toOption.get.isLeft)
    assertEquals(c.listBookmarks(me, None, None, None).map(_.size), Right(0))
