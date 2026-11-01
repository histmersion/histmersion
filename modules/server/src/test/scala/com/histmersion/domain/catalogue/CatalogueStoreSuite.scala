package com.histmersion.domain.catalogue

import com.histmersion.api.Dataset
import com.histmersion.config.AppConfig
import com.histmersion.domain.catalogue.evidence.AttestationService
import com.histmersion.domain.catalogue.periods.PeriodService
import com.histmersion.util.database.Transactor
import com.augustnagro.magnum.*

import java.util.concurrent.atomic.{AtomicLong, AtomicReference}

/** A contract of the catalogue's services, run once on the in-memory stores and once on the database (a scratch
  * database per test, gated on HISTMERSION_TEST_DB_URL): [[fresh]] gives each test empty stores, or none on a run with
  * no such store.
  */
abstract class CatalogueStoreSuite extends com.histmersion.db.DbSuite:
  protected def fresh(test: String): Option[CatalogueStores]

  protected def stest(title: String)(body: CatalogueStores => Unit): Unit =
    test(title)(fresh(title).foreach(body))

object CatalogueStoreSuite:

  /** The stores in memory, sharing one state. */
  trait InMemory extends CatalogueStoreSuite:
    protected def fresh(test: String): Option[CatalogueStores] =
      val state = AtomicReference(CatalogueState.empty)
      Some(new CatalogueStores:
        val periods = PeriodService.InMemory(state)
        val attestations = AttestationService.InMemory(state)
        def source(citekey: String): Unit = ()
        def seedRows(): List[String] =
          state.get.periods.filter(_.dataset == Dataset.Seed).map(_.period.id).toList)

  /** The stores on a scratch database of their own per test. */
  trait OnDatabase extends CatalogueStoreSuite:
    private val testDb = com.histmersion.db.TestDb.url
    override def munitIgnore: Boolean = testDb.isEmpty
    private lazy val scratch =
      ScratchDatabases(
        s"histmersion_s8c_${getClass.getSimpleName.toLowerCase.take(24)}",
        AppConfig.load().db.copy(url = testDb.get)
      )
    private val made = AtomicLong(0L)
    override def afterSuite(): Unit = if testDb.isDefined then scratch.dropAll()
    protected def fresh(test: String): Option[CatalogueStores] =
      testDb.map { _ =>
        val ds = scratch(made.incrementAndGet().toString)
        val db = Transactor(ds)
        new CatalogueStores:
          val periods = PeriodService(db)
          val attestations = AttestationService(db)
          def source(citekey: String): Unit =
            connect(ds)(sql"INSERT INTO sources (citekey, title) VALUES ($citekey, $citekey)".update.run()): Unit
          def seedRows(): List[String] =
            connect(ds)(sql"SELECT id FROM periods WHERE dataset = 'seed'".query[String].run()).toList
      }
