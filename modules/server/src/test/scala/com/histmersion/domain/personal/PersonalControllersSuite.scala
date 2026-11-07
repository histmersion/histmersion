package com.histmersion.domain.personal

import com.histmersion.testsupport.ComparedByValue.given

import com.histmersion.api.Write
import com.histmersion.api.personal.*
import com.histmersion.domain.{Caller, Referable, Referables}
import com.histmersion.domain.library.sources.SourceReferable
import com.histmersion.domain.personal.bookmarks.BookmarkService
import com.histmersion.domain.personal.marks.Readable
import com.histmersion.domain.personal.notes.NoteService
import com.histmersion.domain.personal.reading.ReadingService
import com.histmersion.util.data.maintenance.WriteGate
import com.histmersion.api.catalogue.{Latitude, Longitude, Year}

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
    assert(c.addBookmark(me, Bookmark.New(Target(ItemKind.Source))).left.exists(_.message.value.contains("its id")))
    assert(
      c.addBookmark(me, Bookmark.New(Target(ItemKind.Point, lat = Some(Latitude(1)))))
        .left
        .exists(_.message.value.contains("lat and lon"))
    )
    assert(
      c.addBookmark(me, Bookmark.New(Target(ItemKind.Period, from = Some(Year(-1000)), to = Some(Year(-1200)))))
        .left
        .exists(_.message.value.contains("before it starts"))
    )
    val p = c.addBookmark(
      me,
      Bookmark.New(
        Target(ItemKind.Period, id = Some(ItemKey("dropped")), from = Some(Year(-1200)), to = Some(Year(-1150))),
        Some(Bookmark.Label("  the crisis   years "))
      )
    )
    assertEquals(p.map(b => (b.name, b.target.id)), Right(("the crisis years", None)))
    assert(c.addBookmark(me, Bookmark.New(Target.item(ItemKind.Source, com.histmersion.api.personal.ItemKey("x")), Some(Bookmark.Label("x" * 201)))).isLeft)

  test("a note goes on an item or a point, never a view; it needs text"):
    val c = fixture()
    assert(
      c.addNote(me, Note.New(Target(ItemKind.View, view = Some(ViewState("a=b"))), Note.Text("x")))
        .left
        .exists(_.message.value.contains("cannot"))
    )
    assert(
      c.addNote(me, Note.New(Target.item(ItemKind.Source, com.histmersion.api.personal.ItemKey("zz")), Note.Text("   ")))
        .left
        .exists(_.message.value.contains("some text"))
    )
    val n = c.addNote(
      me,
      Note.New(Target(ItemKind.Point, lat = Some(Latitude(37)), lon = Some(Longitude(22))), Note.Text("here\r\n"))
    )
    assertEquals(n.map(_.text.value.toString), Right("here"))

  test("not found is 404, a stale edit 412, a bad id 400; an edit seen at no time at all is a stale one"):
    val clock = Ticking()
    val c = fixture(clock = clock)
    val n = c.addNote(me, Note.New(Target.item(ItemKind.Source, com.histmersion.api.personal.ItemKey("zz")), Note.Text("one"))).toOption.get
    clock.tick()
    assertEquals(
      c.editNote(me, n.id, Note.Edit(Note.Text("two"), Some(n.updatedAt))).map(_.text.value.toString),
      Right("two")
    )
    val stale = c.editNote(me, n.id, Note.Edit(Note.Text("three"), Some(n.updatedAt)))
    assertEquals(stale.left.map(_.code), Left(Some(Write.CodePrecondition)))
    assert(stale.left.exists(_.message.value.contains("2026-10-30T12:01:00.000000Z")), stale.toString)
    assertEquals(
      c.editNote(me, n.id, Note.Edit(Note.Text("four"), Some(com.histmersion.api.personal.Note.UpdatedAt("yesterday")))).left.map(_.code),
      Left(Some(Write.CodePrecondition))
    )
    assertEquals(c.note(me, com.histmersion.api.personal.Note.Id("not-an-id")).left.map(_.code), Left(None))
    assertEquals(c.note(me, com.histmersion.api.personal.Note.Id("00000000-0000-0000-0000-000000000000")).left.map(_.code), Left(Some(Write.CodeNotFound)))
    assertEquals(
      c.removeBookmark(me, com.histmersion.api.personal.Bookmark.Id("00000000-0000-0000-0000-000000000000")).left.map(_.code),
      Left(Some(Write.CodeNotFound))
    )
    assertEquals(c.removeNote(me, Note.Id(n.id.value.toUpperCase)).map(_.id), Right(n.id))

  test("lists: kinds by name, a word search, a limit"):
    val clock = Ticking()
    val c = fixture(clock = clock)
    List("a", "b", "c").foreach { k =>
      c.addBookmark(me, Bookmark.New(Target.item(ItemKind.Source, ItemKey(s"zz_$k")), title = Some(s"Zz $k"))); clock.tick()
    }
    assertEquals(c.listBookmarks(me, Some(com.histmersion.api.ItemKind.Wire("sources")), None, Some(com.histmersion.api.Paging.Limit(2))).map(_.map(_.title)), Right(List("Zz c", "Zz b")))
    assertEquals(c.listBookmarks(me, None, Some(com.histmersion.api.personal.PersonalApi.Search("zz B")), None).map(_.map(_.title)), Right(List("Zz b")))
    assert(c.listBookmarks(me, Some(com.histmersion.api.ItemKind.Wire("nonsense")), None, None).isLeft)
    assert(c.listNotes(me, Some(com.histmersion.api.ItemKind.Wire("view")), None, None, None).isLeft, "a view holds no notes")

  test("only sources and articles are marked read; show is read, opened or all"):
    val c = fixture()
    assert(c.setRead(me, ReadMark.Set(ItemKind.Place, ItemKey("p"), read = com.histmersion.api.personal.ReadMark.Read(true))).isLeft)
    assertEquals(
      c.setRead(me, ReadMark.Set(ItemKind.Source, ItemKey("zz"), read = com.histmersion.api.personal.ReadMark.Read(true), Some(com.histmersion.api.library.Title("Zz")))).map(m => (m.read, m.title)),
      Right((true, "Zz"))
    )
    assertEquals(c.readMarks(me, None, Some(com.histmersion.api.automation.Activity.Show.Wire("opened")), None).map(_.size), Right(0))
    assert(c.readMarks(me, None, Some(com.histmersion.api.automation.Activity.Show.Wire("nope")), None).isLeft)
    assertEquals(c.opened(me, ReadOpened(ItemKind.Article, ItemKey("a"))).map(_.openedAt.isDefined), Right(true))
    assertEquals(c.unread(me, Some(com.histmersion.api.ItemKind.Wire("goal")), None, None).isLeft, true)
    assertEquals(c.unread(me, None, None, Some(com.histmersion.api.personal.UnreadPage.Offset(-3))).map(p => (p.total, p.offset)), Right((0, 0)))

  test("the marks are the caller's own; an automated run has none"):
    val c = fixture()
    val run = Caller.Automation(Run.Id("0b3f6a4e-2c1d-4e5f-8a9b-0c1d2e3f4a5b"))
    assert(c.listBookmarks(run, None, None, None).isLeft)
    assert(c.addBookmark(run, Bookmark.New(Target.item(ItemKind.Source, com.histmersion.api.personal.ItemKey("zz")))).isLeft)
    val someone = Caller.Person(com.histmersion.UserId("someone"), Caller.Role.Reader)
    c.addBookmark(someone, Bookmark.New(Target.item(ItemKind.Source, com.histmersion.api.personal.ItemKey("zz"))))
    assertEquals(c.listBookmarks(me, None, None, None).map(_.size), Right(0))
    assertEquals(c.listBookmarks(someone, None, None, None).map(_.size), Right(1))

  test("writes wait while a restore holds the maintenance lock"):
    val m = WriteGate()
    val c = fixture(m)
    val refused = m.exclusively(com.histmersion.util.data.maintenance.WriteGate.OperationName("restore"))(Right(c.addBookmark(me, Bookmark.New(Target.item(ItemKind.Source, com.histmersion.api.personal.ItemKey("zz"))))))
    assert(refused.toOption.get.isLeft)
    assertEquals(c.listBookmarks(me, None, None, None).map(_.size), Right(0))
