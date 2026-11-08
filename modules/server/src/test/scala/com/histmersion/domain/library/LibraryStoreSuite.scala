package com.histmersion
package domain.library

import com.histmersion.config.{AppConfig, DbConfig}
import com.histmersion.domain.library.citations.CitationService
import com.histmersion.domain.library.sources.{SourceService, SourceSettings}
import com.histmersion.domain.library.sources.referrers.SourceReferrers
import com.histmersion.util.database.Transactor

import java.time.{Clock, Instant, ZoneId, ZoneOffset}
import java.util.concurrent.atomic.{AtomicLong, AtomicReference}
import com.histmersion.db.DbSuite
import com.histmersion.db.TestDb

/** A contract of the library's services, run once on the in-memory stores and once on the database (a scratch database
  * per test, gated on HISTMERSION_TEST_DB_URL), each holding the library's rows alone: no referrer is registered, as no
  * other domain's records are there. [[fresh]] gives each test empty stores, or none on a run with no such store.
  */
abstract class LibraryStoreSuite extends DbSuite:
  protected def fresh(test: String): Option[LibraryStores]

  protected def stest(title: String)(body: LibraryStores => Unit): Unit =
    test(title)(fresh(title).foreach(body))

object LibraryStoreSuite:

  val Settings: SourceSettings = SourceSettings(SourceSettings.ChunkChars(4000), SourceSettings.ToleranceKm(25.0))

  /** A clock that moves a millisecond each read, as the database's `now()` differs between transactions. */
  final class Ticking extends Clock:
    private val at = AtomicLong(1_700_000_000_000L)
    def getZone: ZoneId = ZoneOffset.UTC
    override def withZone(zone: ZoneId): Clock = this
    def instant(): Instant = Instant.ofEpochMilli(at.incrementAndGet())

  /** The stores in memory, sharing one state. */
  trait InMemory extends LibraryStoreSuite:
    protected def fresh(test: String): Option[LibraryStores] =
      val state = AtomicReference(LibraryState.empty)
      Some(new LibraryStores:
        val sources = SourceService.InMemory(state, Ticking(), () => Settings)
        val citations = CitationService.InMemory(state))

  /** The stores on a scratch database of their own per test. */
  trait OnDatabase extends LibraryStoreSuite:
    private val testDb = TestDb.url
    override def munitIgnore: Boolean = testDb.isEmpty
    private lazy val scratch =
      ScratchDatabases(
        s"histmersion_s10c_${getClass.getSimpleName.toLowerCase.take(24)}",
        AppConfig.load().db.copy(url = DbConfig.Url(testDb.get))
      )
    private val made = AtomicLong(0L)
    override def afterSuite(): Unit = if testDb.isDefined then scratch.dropAll()
    protected def fresh(test: String): Option[LibraryStores] =
      testDb.map { _ =>
        val db = Transactor(scratch(made.incrementAndGet().toString))
        new LibraryStores:
          val sources = SourceService(db, SourceReferrers(Nil), () => Settings)
          val citations = CitationService(db)
      }
