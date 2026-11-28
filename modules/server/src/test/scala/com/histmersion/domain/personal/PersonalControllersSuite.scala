package com.histmersion
package domain.personal

import com.histmersion.api.automation.activity.Show

import com.histmersion.util.problem.Text.{sentence, sentences}
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
import com.histmersion.api.library.Title
import com.histmersion.domain.automation.Run
import com.histmersion.api.Paging
import com.histmersion.api.automation.Activity
import com.histmersion.util.database.TestPermits.given
import com.histmersion.util.database.TestCreators.given
import com.histmersion.lint.{tolerated, LintRule}

/** What the endpoints answer, over the in-memory services: inputs checked, the codes the statuses come from, the
  * caller's own marks.
  */
@tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
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
    assert(
      c.addBookmark(me, Bookmark.New(Target(ItemKind.Source)))
        .left
        .exists(_.problems.sentences.mkString("; ").endsWith("id is required"))
    )
    assert(
      c.addBookmark(me, Bookmark.New(Target(ItemKind.Point, lat = Some(Latitude(1)))))
        .left
        .exists(_.problems.sentences.mkString("; ").endsWith("lon is required"))
    )
    assert(
      c.addBookmark(me, Bookmark.New(Target(ItemKind.Period, from = Some(Year(-1000)), to = Some(Year(-1200)))))
        .left
        .exists(_.problems.sentences.mkString("; ").contains("from, to must be in order"))
    )
    val p = c.addBookmark(
      me,
      Bookmark.New(
        Target(ItemKind.Period, id = Some(ItemKey("dropped")), from = Some(Year(-1200)), to = Some(Year(-1150))),
        Some(Bookmark.Label("  the crisis   years "))
      )
    )
    assertEquals(p.map(b => (b.name, b.target.id)), Right(("the crisis years", None)))
    assert(
      c.addBookmark(me, Bookmark.New(Target.item(ItemKind.Source, ItemKey("x")), Some(Bookmark.Label("x" * 201))))
        .isLeft
    )

  test("a note goes on an item or a point, never a view; it needs text"):
    val c = fixture()
    assert(
      c.addNote(me, Note.New(Target(ItemKind.View, view = Some(ViewState("a=b"))), Note.Text("x")))
        .left
        .exists(_.problems.sentences.mkString("; ").contains("unknown item kind 'view'"))
    )
    assert(
      c.addNote(me, Note.New(Target.item(ItemKind.Source, ItemKey("zz")), Note.Text("   ")))
        .left
        .exists(_.problems.sentences.mkString("; ").endsWith("text is required"))
    )
    val n = c.addNote(
      me,
      Note.New(Target(ItemKind.Point, lat = Some(Latitude(37)), lon = Some(Longitude(22))), Note.Text("here\r\n"))
    )
    assertEquals(n.map(_.text.value.toString), Right("here"))

  test("not found is 404, a stale edit 412, a bad id 400; an edit seen at no time at all is a stale one"):
    val clock = Ticking()
    val c = fixture(clock = clock)
    val n = c.addNote(me, Note.New(Target.item(ItemKind.Source, ItemKey("zz")), Note.Text("one"))).toOption.get
    clock.tick()
    assertEquals(
      c.editNote(me, n.id, Note.Edit(Note.Text("two"), Some(n.updatedAt))).map(_.text.value.toString),
      Right("two")
    )
    val stale = c.editNote(me, n.id, Note.Edit(Note.Text("three"), Some(n.updatedAt)))
    assertEquals(stale.left.map(_.code), Left(Some(Write.CodePrecondition)))
    assert(
      stale.left.exists(_.problems.sentences.mkString("; ").contains("2026-10-30T12:01:00.000000Z")),
      stale.toString
    )
    assertEquals(
      c.editNote(me, n.id, Note.Edit(Note.Text("four"), Some(Note.At("yesterday")))).left.map(_.code),
      Left(Some(Write.CodePrecondition))
    )
    assertEquals(c.note(me, Note.Id("not-an-id")).left.map(_.code), Left(None))
    assertEquals(
      c.note(me, Note.Id("00000000-0000-0000-0000-000000000000")).left.map(_.code),
      Left(Some(Write.CodeNotFound))
    )
    assertEquals(
      c.removeBookmark(me, Bookmark.Id("00000000-0000-0000-0000-000000000000")).left.map(_.code),
      Left(Some(Write.CodeNotFound))
    )
    assertEquals(c.removeNote(me, Note.Id(n.id.value.toUpperCase)).map(_.id), Right(n.id))

  test("lists: kinds by name, a word search, a limit"):
    val clock = Ticking()
    val c = fixture(clock = clock)
    List("a", "b", "c").foreach { k =>
      c.addBookmark(me, Bookmark.New(Target.item(ItemKind.Source, ItemKey(s"zz_$k")), title = Some(Title(s"Zz $k"))));
      clock.tick()
    }
    assertEquals(
      c.listBookmarks(me, Some(ItemKind.Wire("sources")), None, Some(Paging.Limit(2))).map(_.map(_.title)),
      Right(List("Zz c", "Zz b"))
    )
    assertEquals(
      c.listBookmarks(me, None, Some(PersonalApi.Search("zz B")), None).map(_.map(_.title)),
      Right(List("Zz b"))
    )
    assert(c.listBookmarks(me, Some(ItemKind.Wire("nonsense")), None, None).isLeft)
    assert(c.listNotes(me, Some(ItemKind.Wire("view")), None, None, None).isLeft, "a view holds no notes")

  test("only sources and articles are marked read; show is read, opened or all"):
    val c = fixture()
    assert(c.setRead(me, ReadMark.Set(ItemKind.Place, ItemKey("p"), read = ReadMark.Read.Read)).isLeft)
    assertEquals(
      c.setRead(me, ReadMark.Set(ItemKind.Source, ItemKey("zz"), read = ReadMark.Read.Read, Some(Title("Zz"))))
        .map(m => (m.read, m.title)),
      Right((true, "Zz"))
    )
    assertEquals(c.readMarks(me, None, Some(Show.Wire("opened")), None).map(_.size), Right(0))
    assert(c.readMarks(me, None, Some(Show.Wire("nope")), None).isLeft)
    assertEquals(c.opened(me, ReadOpened(ItemKind.Article, ItemKey("a"))).map(_.openedAt.isDefined), Right(true))
    assertEquals(c.unread(me, Some(ItemKind.Wire("goal")), None, None).isLeft, true)
    assertEquals(c.unread(me, None, None, Some(UnreadPage.Offset(-3))).map(p => (p.total, p.offset)), Right((0, 0)))

  test("the marks are the caller's own; an automated run has none"):
    val c = fixture()
    val run = Caller.Automation(Run.Id("0b3f6a4e-2c1d-4e5f-8a9b-0c1d2e3f4a5b"))
    assert(c.listBookmarks(run, None, None, None).isLeft)
    assert(c.addBookmark(run, Bookmark.New(Target.item(ItemKind.Source, ItemKey("zz")))).isLeft)
    val someone = Caller.Person(UserId("someone"), Caller.Role.Reader)
    c.addBookmark(someone, Bookmark.New(Target.item(ItemKind.Source, ItemKey("zz"))))
    assertEquals(c.listBookmarks(me, None, None, None).map(_.size), Right(0))
    assertEquals(c.listBookmarks(someone, None, None, None).map(_.size), Right(1))

  test("writes wait while a restore holds the maintenance lock"):
    val m = WriteGate()
    val c = fixture(m)
    val refused = m.exclusively(WriteGate.OperationName("restore"))(
      Right(c.addBookmark(me, Bookmark.New(Target.item(ItemKind.Source, ItemKey("zz")))))
    )
    assert(refused.toOption.get.isLeft)
    assertEquals(c.listBookmarks(me, None, None, None).map(_.size), Right(0))
