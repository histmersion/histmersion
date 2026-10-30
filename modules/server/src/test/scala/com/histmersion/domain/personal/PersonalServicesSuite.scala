package com.histmersion.domain.personal

import com.histmersion.values.UserId

import com.histmersion.api.personal.*
import com.histmersion.config.AppConfig
import com.histmersion.values.{Latitude, Longitude, Year}

import com.augustnagro.magnum.*

import java.time.Instant
import java.util.concurrent.atomic.AtomicReference

/** Bookmarks, notes and read marks behave the same in memory and in the database (a scratch database of its own, gated
  * on HISTMERSION_TEST_DB_URL): the same scenario runs over both.
  */
class PersonalServicesSuite extends com.histmersion.db.DbSuite:

  private val t0 = Instant.parse("2026-10-30T12:00:00.123456789Z")
  private def at(min: Int): Instant = t0.plusSeconds(min * 60L)
  private val me = UserId.Local
  private val other = UserId("someone-else")

  /** The three services over one store, and how an item of the collection is added to it. */
  final private case class World(
      bookmarks: BookmarkService,
      notes: NoteService,
      reading: ReadingService,
      addSource: (String, String) => Unit,
      renameSource: (String, String) => Unit
  )

  private def inMemory: World =
    val state = AtomicReference(PersonalState())
    val reading = ReadingService.InMemory(state)
    World(
      BookmarkService.InMemory(state),
      NoteService.InMemory(state),
      reading,
      (ck, title) => reading.addItem(UnreadRow(ItemKind.Source, ItemKey(ck), title)),
      (ck, title) => reading.addItem(UnreadRow(ItemKind.Source, ItemKey(ck), title))
    )

  private val testDb = com.histmersion.db.TestDb.url
  private lazy val scratch = ScratchDatabases("personal", AppConfig.load().db.copy(url = testDb.get))
  override def afterSuite(): Unit = if testDb.isDefined then scratch.dropAll()

  private def onDatabase(name: String): World =
    val ds = scratch(name)
    World(
      BookmarkService(ds),
      NoteService(ds),
      ReadingService(ds),
      (ck, title) => connect(ds)(sql"INSERT INTO sources (citekey, title) VALUES ($ck, $title)".update.run(): Unit),
      (ck, title) => connect(ds)(sql"UPDATE sources SET title = $title WHERE citekey = $ck".update.run(): Unit)
    )

  private def both(name: String)(body: World => Unit): Unit =
    test(s"$name (in memory)")(body(inMemory))
    test(s"$name (database)") {
      assume(testDb.isDefined, "HISTMERSION_TEST_DB_URL is not set")
      body(onDatabase(name.toLowerCase.replaceAll("[^a-z]+", "_").take(30)))
    }

  private val point = Target(
    ItemKind.Point,
    lat = Some(Latitude(37.73)),
    lon = Some(Longitude(22.75)),
    from = Some(Year(-1300)),
    to = Some(Year(-1100))
  )

  both("an item is bookmarked once; a renamed source keeps its bookmark and shows its new title") { w =>
    w.addSource("zz_pers_a", "Zz the first title")
    val b1 = w.bookmarks.add(me, Target.item(ItemKind.Source, "zz_pers_a"), None, Some("as I saw it"), at(0))
    assertEquals(b1.title, "Zz the first title")
    val b2 = w.bookmarks.add(me, Target.item(ItemKind.Source, "zz_pers_a"), Some(BookmarkLabel("mine")), None, at(1))
    assertEquals(b2.id, b1.id)
    assertEquals(b2.label.map(_.value.toString), Some("mine"))
    w.renameSource("zz_pers_a", "Zz the second title")
    assertEquals(w.bookmarks.list(me, None, Nil, 10).map(_.title), List("Zz the second title"))
    assertEquals(w.bookmarks.list(other, None, Nil, 10), Nil)
  }

  both(
    "an item the collection does not hold keeps the name it was bookmarked with; views and points are each their own"
  ) { w =>
    w.bookmarks.add(me, Target.item(ItemKind.Place, "zz_place_1"), None, Some("Zz Tiryns"), at(0))
    val v = Target(
      ItemKind.View,
      lat = Some(Latitude(37.6)),
      lon = Some(Longitude(22.8)),
      zoom = Some(MapZoom(7.5)),
      view = Some(ViewState("from=-1300&to=-1100&tag=zz"))
    )
    w.bookmarks.add(me, v, Some(BookmarkLabel("Argolid")), None, at(1))
    w.bookmarks.add(me, v, Some(BookmarkLabel("Argolid again")), None, at(2))
    w.bookmarks.add(me, point, None, None, at(3))
    val all = w.bookmarks.list(me, None, Nil, 10)
    assertEquals(all.map(_.name), List("Place on the map", "Argolid again", "Argolid", "Zz Tiryns"))
    assertEquals(all(1).target.view.map(_.value.toString), Some("from=-1300&to=-1100&tag=zz"))
    assertEquals(all(1).target.zoom.map(_.value.toDouble), Some(7.5))
    assertEquals(w.bookmarks.list(me, Some(ItemKind.View), Nil, 10).size, 2)
    assertEquals(w.bookmarks.list(me, None, List("tiryns"), 10).map(_.name), List("Zz Tiryns"))
    assertEquals(w.bookmarks.list(me, None, Nil, 2).size, 2)
  }

  both("a removed bookmark leaves the list; bookmarking the item again makes a new one") { w =>
    val b = w.bookmarks.add(me, Target.item(ItemKind.Article, "zz_art"), None, Some("Zz article"), at(0))
    assertEquals(w.bookmarks.remove(other, b.id, at(1)), None)
    assertEquals(w.bookmarks.remove(me, b.id, at(1)).map(_.id), Some(b.id))
    assertEquals(w.bookmarks.remove(me, b.id, at(2)), None)
    assertEquals(w.bookmarks.list(me, None, Nil, 10), Nil)
    assertEquals(w.bookmarks.rename(me, b.id, None, at(2)), None)
    val again = w.bookmarks.add(me, Target.item(ItemKind.Article, "zz_art"), None, None, at(3))
    assertNotEquals(again.id, b.id)
  }

  both("notes: left on an item and on a point, edited with the time last seen, removed") { w =>
    w.addSource("zz_pers_n", "Zz noted source")
    val n1 =
      w.notes.add(me, Target.item(ItemKind.Source, "zz_pers_n"), NoteText("Compare with **Langgut**."), None, at(0))
    val n2 = w.notes.add(me, point, NoteText("Bridge here?"), None, at(1))
    assertEquals(n1.title, "Zz noted source")
    assertEquals(n2.title, "Place on the map")
    assertEquals(n2.target.from.map(_.value.toInt), Some(-1300))
    assertEquals(w.notes.list(me, None, None, Nil, 10).map(_.id), List(n2.id, n1.id))
    assertEquals(w.notes.list(me, Some(ItemKind.Source), Some("zz_pers_n"), Nil, 10).map(_.id), List(n1.id))
    assertEquals(w.notes.list(me, None, None, List("langgut"), 10).map(_.id), List(n1.id))
    assertEquals(n1.updatedAt, "2026-10-30T12:00:00.123456Z")
    val edited = w.notes.edit(me, n1.id, NoteText("Changed."), Some(n1.updatedAt), at(5))
    assertEquals(edited.map(_.text.value.toString), Right("Changed."))
    assertEquals(
      w.notes.edit(me, n1.id, NoteText("Stale."), Some(n1.updatedAt), at(6)),
      Left(NoteEditRefused.Changed(edited.toOption.get.updatedAt))
    )
    assertEquals(w.notes.list(me, None, None, Nil, 10).map(_.id), List(n1.id, n2.id))
    assertEquals(w.notes.remove(me, n1.id, at(7)).map(_.text.value.toString), Some("Changed."))
    assertEquals(w.notes.get(me, n1.id), None)
    assertEquals(w.notes.edit(me, n1.id, NoteText("x"), None, at(8)), Left(NoteEditRefused.Missing))
    assertEquals(w.notes.get(other, n2.id), None)
  }

  both("read marks: only marked read is read; opening is kept apart; unread lists the rest by title") { w =>
    w.addSource("zz_r_b", "Zz Beta")
    w.addSource("zz_r_a", "Zz Alpha")
    w.addSource("zz_r_c", "Zz Gamma")
    val o = w.reading.opened(me, ItemKind.Source, ItemKey("zz_r_b"), None, at(0))
    assertEquals((o.read, o.title), (false, "Zz Beta"))
    val r = w.reading.setRead(me, ItemKind.Source, ItemKey("zz_r_b"), read = true, None, at(1))
    assert(r.read && r.openedAt.isDefined)
    w.reading.setRead(me, ItemKind.Source, ItemKey("zz_r_b"), read = true, None, at(2))
    assertEquals(w.reading.list(me, None, ReadingShow.Read, 10).flatMap(_.readAt), List("2026-10-30T12:01:00.123456Z"))
    val zz = w.reading.unread(me, ItemKind.Source, 10, 0)
    assertEquals(zz.rows.map(_.id.value).filter(_.startsWith("zz_r_")), List("zz_r_a", "zz_r_c"))
    assertEquals(
      w.reading.unread(other, ItemKind.Source, 10, 0).rows.map(_.id.value).filter(_.startsWith("zz_r_")),
      List("zz_r_a", "zz_r_b", "zz_r_c")
    )
    w.reading.setRead(me, ItemKind.Source, ItemKey("zz_r_b"), read = false, None, at(3))
    assertEquals(w.reading.list(me, None, ReadingShow.Read, 10), Nil)
    assertEquals(w.reading.list(me, None, ReadingShow.Opened, 10).map(_.id.value), List("zz_r_b"))
    w.reading.opened(me, ItemKind.Article, ItemKey("zz_art_x"), Some("Zz article X"), at(4))
    assertEquals(w.reading.list(me, None, ReadingShow.Opened, 10).map(_.title), List("Zz article X", "Zz Beta"))
    assertEquals(w.reading.list(me, Some(ItemKind.Source), ReadingShow.All, 10).map(_.id.value), List("zz_r_b"))
    assertEquals(w.reading.unread(me, ItemKind.Source, 1, 1).rows.map(_.id.value), List("zz_r_b"))
  }
