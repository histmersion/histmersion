package com.histmersion.domain.personal

import com.histmersion.api.personal.MapZoom
import com.histmersion.api.ItemKind as MarkKind
import com.histmersion.config.AppConfig
import com.histmersion.domain.library.sources.{SourceReadable, SourceReferable}
import com.histmersion.domain.personal.bookmarks.BookmarkService
import com.histmersion.domain.personal.marks.{Readable, UnreadItem}
import com.histmersion.domain.personal.notes.NoteService
import com.histmersion.domain.personal.reading.ReadingService
import com.histmersion.domain.personal.seen.SeenService
import com.histmersion.domain.{ItemKey, ItemRef, ItemUid, Referable, Referables}
import com.histmersion.util.database.Transactor
import com.histmersion.values.{Latitude, Longitude, PageSize, Title, UserId, Year}

import com.augustnagro.magnum.*

import java.time.{Clock, Instant, ZoneOffset}
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference

/** The contract a service of the person's keeps: it behaves the same in memory and in the database (a scratch database
  * of the suite's own, gated on HISTMERSION_TEST_DB_URL) — each scenario runs over both, the collection's sources
  * changing under the marks as a rename, a merge or a deletion changes them.
  */
abstract class PersonalServiceContract(prefix: String) extends com.histmersion.db.DbSuite:

  protected val t0 = Instant.parse("2026-10-30T12:00:00.123456789Z")
  protected def at(min: Int): MarkTime = MarkTime.at(t0.plusSeconds(min * 60L))
  protected val me = UserId.Local
  protected val other = UserId("someone-else")
  protected val all = PageSize(10)
  protected val source = SourceReferable.Kind

  /** The services over one store, and how the collection's sources change under them. */
  final protected case class World(
      bookmarks: BookmarkService,
      notes: NoteService,
      reading: ReadingService,
      seen: SeenService,
      addSource: (String, String) => Unit,
      retitle: (String, String) => Unit,
      rename: (String, String) => Unit,
      merge: (String, String) => Unit,
      delete: String => Unit
  )

  protected def inMemory: World =
    val state = AtomicReference(PersonalState())
    val sources = Referable.InMemory(source)
    val readable = Readable.InMemory(source)
    val uids = AtomicReference(Map.empty[String, ItemUid])
    val items = Referables(List(sources))
    def put(ck: String, title: String): Unit =
      val uid = uids.updateAndGet(m => if m.contains(ck) then m else m + (ck -> ItemUid(UUID.randomUUID())))(ck)
      sources.put(ItemKey(ck), Title(title), Some(uid))
      readable.add(UnreadItem(ItemKey(ck), Title(title)), Some(uid))
    World(
      BookmarkService.InMemory(state, items),
      NoteService.InMemory(state, items),
      ReadingService.InMemory(state, items, List(readable)),
      SeenService.InMemory(state, Clock.fixed(t0.plusSeconds(3600), ZoneOffset.UTC)),
      put,
      put,
      (a, b) => { sources.rename(ItemKey(a), ItemKey(b)); readable.rename(ItemKey(a), ItemKey(b)) },
      (a, b) => { sources.merge(ItemKey(a), ItemKey(b)); readable.remove(ItemKey(a)) },
      ck => { sources.delete(ItemKey(ck), t0); readable.remove(ItemKey(ck)) }
    )

  private val testDb = com.histmersion.db.TestDb.url
  private lazy val scratch = ScratchDatabases(prefix, AppConfig.load().db.copy(url = testDb.get))
  override def afterSuite(): Unit = if testDb.isDefined then scratch.dropAll()

  private def onDatabase(name: String): World =
    val db = Transactor(scratch(name))
    val items = Referables(List(SourceReferable(db)))
    def run(f: DbCon ?=> Unit): Unit = db.transact(f)
    World(
      BookmarkService(db, items),
      NoteService(db, items),
      ReadingService(db, items, List(SourceReadable(db))),
      SeenService(db),
      (ck, title) => run(sql"INSERT INTO sources (citekey, title) VALUES ($ck, $title)".update.run(): Unit),
      (ck, title) => run(sql"UPDATE sources SET title = $title WHERE citekey = $ck".update.run(): Unit),
      (a, b) =>
        run {
          sql"UPDATE sources SET citekey = $b WHERE citekey = $a".update.run(): Unit
          sql"INSERT INTO source_aliases (alias, citekey) VALUES ($a, $b)".update.run(): Unit
        },
      (a, b) =>
        run {
          sql"""INSERT INTO uid_aliases (alias_uid, uid, kind)
                SELECT f.uid, i.uid, 'source' FROM sources f, sources i WHERE f.citekey = $a AND i.citekey = $b""".update
            .run(): Unit
          sql"DELETE FROM sources WHERE citekey = $a".update.run(): Unit
        },
      ck => run(sql"DELETE FROM sources WHERE citekey = $ck".update.run(): Unit)
    )

  protected def both(name: String)(body: World => Unit): Unit =
    test(s"$name (in memory)")(body(inMemory))
    test(s"$name (database)") {
      assume(testDb.isDefined, "HISTMERSION_TEST_DB_URL is not set")
      body(onDatabase(name.toLowerCase.replaceAll("[^a-z]+", "_").take(30)))
    }

  protected def item(kind: MarkKind, key: String): MarkTarget =
    MarkTarget.Item(ItemRef(MarkTarget.itemKind(kind), ItemKey(key), None))

  protected val point =
    MarkTarget.Point(Latitude(37.73), Longitude(22.75), Some(MapZoom(6.0)), Some(Year(-1300)), Some(Year(-1100)))

  protected def keyOf(t: MarkTarget): String = t.itemRef.fold("")(_.key.value)
