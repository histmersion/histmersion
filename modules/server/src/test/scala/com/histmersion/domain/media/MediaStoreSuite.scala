package com.histmersion.domain.media

import com.histmersion.testsupport.ComparedByValue.given

import com.histmersion.config.{AppConfig, DbConfig}
import com.histmersion.domain.media.assets.AssetService
import com.histmersion.domain.media.covers.CoverService
import com.histmersion.domain.media.figures.FigureService
import com.histmersion.domain.media.imports.ImportService
import com.histmersion.domain.media.links.AssetLinkService
import com.histmersion.util.database.Transactor
import com.augustnagro.magnum.*

import java.time.{Clock, Instant, ZoneId, ZoneOffset}
import java.util.concurrent.atomic.{AtomicLong, AtomicReference}

/** A contract of media's services, run once on the in-memory stores and once on the database (a scratch database per
  * test, gated on HISTMERSION_TEST_DB_URL): [[fresh]] gives each test empty stores, or none on a run with no such
  * store.
  */
abstract class MediaStoreSuite extends com.histmersion.db.DbSuite:
  protected def fresh(test: String): Option[MediaStores]

  protected def stest(title: String)(body: MediaStores => Unit): Unit =
    test(title)(fresh(title).foreach(body))

object MediaStoreSuite:

  /** A clock that moves a millisecond each read, as the database's `now()` differs between statements. */
  final class Ticking extends Clock:
    private val at = AtomicLong(1_700_000_000_000L)
    def getZone: ZoneId = ZoneOffset.UTC
    override def withZone(zone: ZoneId): Clock = this
    def instant(): Instant = Instant.ofEpochMilli(at.incrementAndGet())

  /** The stores in memory, sharing one state. */
  trait InMemory extends MediaStoreSuite:
    protected def fresh(test: String): Option[MediaStores] =
      val state = AtomicReference(MediaState.empty)
      val clock = Ticking()
      Some(new MediaStores:
        val assets = AssetService.InMemory(state, clock)
        val links = AssetLinkService.InMemory(state)
        val imports = ImportService.InMemory(state, clock)
        val covers = CoverService.InMemory()
        val figures = FigureService.InMemory()
        def source(citekey: String): Unit = ())

  /** The stores on a scratch database of their own per test. */
  trait OnDatabase extends MediaStoreSuite:
    private val testDb = com.histmersion.db.TestDb.url
    override def munitIgnore: Boolean = testDb.isEmpty
    private lazy val scratch =
      ScratchDatabases(
        s"histmersion_s9c_${getClass.getSimpleName.toLowerCase.take(24)}",
        AppConfig.load().db.copy(url = DbConfig.Url(testDb.get))
      )
    private val made = AtomicLong(0L)
    override def afterSuite(): Unit = if testDb.isDefined then scratch.dropAll()
    protected def fresh(test: String): Option[MediaStores] =
      testDb.map { _ =>
        val ds = scratch(made.incrementAndGet().toString)
        val db = Transactor(ds)
        new MediaStores:
          val assets = AssetService(db)
          val links = AssetLinkService(db)
          val imports = ImportService(db)
          val covers = CoverService(db)
          val figures = FigureService(db)
          def source(citekey: String): Unit =
            connect(ds):
              val _ = sql"INSERT INTO sources (citekey, title) VALUES ($citekey, $citekey)".update.run()
      }
