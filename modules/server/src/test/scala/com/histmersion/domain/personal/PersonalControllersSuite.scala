package com.histmersion.domain.personal

import com.histmersion.api.Write
import com.histmersion.api.personal.*
import com.histmersion.server.Maintenance
import com.histmersion.values.{Latitude, Longitude, Year}

import java.time.Instant
import java.util.concurrent.atomic.AtomicReference

/** What the endpoints answer, over the in-memory services: inputs checked, the codes the statuses come from. */
class PersonalControllersSuite extends munit.FunSuite:

  private val clock = AtomicReference(Instant.parse("2026-10-30T12:00:00Z"))
  private def tick(): Unit = clock.updateAndGet(_.plusSeconds(60)): Unit

  private def fixture(maintenance: Maintenance = Maintenance()): PersonalControllers =
    val state = AtomicReference(PersonalState())
    PersonalControllers(BookmarkService.InMemory(state), NoteService.InMemory(state), ReadingService.InMemory(state),
      maintenance, () => clock.get)

  test("a bookmark's target is checked: an item names its id, a point its position, a period its years in order"):
    val c = fixture()
    assert(c.addBookmark(BookmarkNew(Target(ItemKind.Source))).left.exists(_.message.contains("its id")))
    assert(c.addBookmark(BookmarkNew(Target(ItemKind.Point, lat = Some(Latitude(1))))).left.exists(_.message.contains("lat and lon")))
    assert(c.addBookmark(BookmarkNew(Target(ItemKind.Period, from = Some(Year(-1000)), to = Some(Year(-1200)))))
      .left.exists(_.message.contains("before it starts")))
    val p = c.addBookmark(BookmarkNew(Target(ItemKind.Period, id = Some(ItemKey("dropped")), from = Some(Year(-1200)),
      to = Some(Year(-1150))), Some(BookmarkLabel("  the crisis   years "))))
    assertEquals(p.map(b => (b.name, b.target.id)), Right(("the crisis years", None)))
    assert(c.addBookmark(BookmarkNew(Target.item(ItemKind.Source, "x"), Some(BookmarkLabel("x" * 201)))).isLeft)

  test("a note goes on an item or a point, never a view; it needs text"):
    val c = fixture()
    assert(c.addNote(NoteNew(Target(ItemKind.View, view = Some(ViewState("a=b"))), NoteText("x"))).left.exists(_.message.contains("cannot")))
    assert(c.addNote(NoteNew(Target.item(ItemKind.Source, "zz"), NoteText("   "))).left.exists(_.message.contains("some text")))
    val n = c.addNote(NoteNew(Target(ItemKind.Point, lat = Some(Latitude(37)), lon = Some(Longitude(22))), NoteText("here\r\n")))
    assertEquals(n.map(_.text.value.toString), Right("here"))

  test("not found is 404, a stale edit 412, a bad id 400"):
    val c = fixture()
    val n = c.addNote(NoteNew(Target.item(ItemKind.Source, "zz"), NoteText("one"))).toOption.get
    tick()
    assertEquals(c.editNote(n.id.value, NoteEdit(NoteText("two"), Some(n.updatedAt))).map(_.text.value.toString), Right("two"))
    assertEquals(c.editNote(n.id.value, NoteEdit(NoteText("three"), Some(n.updatedAt))).left.map(_.code), Left(Some(Write.CodePrecondition)))
    assertEquals(c.note("not-an-id").left.map(_.code), Left(None))
    assertEquals(c.note("00000000-0000-0000-0000-000000000000").left.map(_.code), Left(Some(Write.CodeNotFound)))
    assertEquals(c.removeBookmark("00000000-0000-0000-0000-000000000000").left.map(_.code), Left(Some(Write.CodeNotFound)))
    assertEquals(c.removeNote(n.id.value.toUpperCase).map(_.id), Right(n.id))

  test("lists: kinds by name, a word search, a limit"):
    val c = fixture()
    List("a", "b", "c").foreach { k => c.addBookmark(BookmarkNew(Target.item(ItemKind.Source, s"zz_$k"), title = Some(s"Zz $k"))); tick() }
    assertEquals(c.listBookmarks(Some("sources"), None, Some(2)).map(_.map(_.title)), Right(List("Zz c", "Zz b")))
    assertEquals(c.listBookmarks(None, Some("zz B"), None).map(_.map(_.title)), Right(List("Zz b")))
    assert(c.listBookmarks(Some("nonsense"), None, None).isLeft)
    assert(c.listNotes(Some("view"), None, None, None).isLeft, "a view holds no notes")

  test("only sources and articles are marked read; show is read, opened or all"):
    val c = fixture()
    assert(c.setRead(ReadMarkSet(ItemKind.Place, ItemKey("p"), read = true)).isLeft)
    assertEquals(c.setRead(ReadMarkSet(ItemKind.Source, ItemKey("zz"), read = true, Some("Zz"))).map(m => (m.read, m.title)), Right((true, "Zz")))
    assertEquals(c.readMarks(None, Some("opened"), None).map(_.size), Right(0))
    assert(c.readMarks(None, Some("nope"), None).isLeft)
    assertEquals(c.opened(ReadOpened(ItemKind.Article, ItemKey("a"))).map(_.openedAt.isDefined), Right(true))
    assertEquals(c.unread(Some("goal"), None, None).isLeft, true)

  test("writes wait while a restore holds the maintenance lock"):
    val m = Maintenance()
    val c = fixture(m)
    val refused = m.exclusively("restore")(Right(c.addBookmark(BookmarkNew(Target.item(ItemKind.Source, "zz")))))
    assert(refused.toOption.get.isLeft)
    assertEquals(c.listBookmarks(None, None, None).map(_.size), Right(0))
