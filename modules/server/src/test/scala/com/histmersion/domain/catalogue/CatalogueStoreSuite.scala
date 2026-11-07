package com.histmersion.domain.catalogue

import com.histmersion.testsupport.ComparedByValue.given

import com.histmersion.api.Dataset
import com.histmersion.config.AppConfig
import com.histmersion.domain.catalogue.events.{CorrectionService, EventImportService, EventService}
import com.histmersion.domain.catalogue.evidence.{AttestationService, EventLinkService, LeadService}
import com.histmersion.domain.catalogue.periods.PeriodService
import com.histmersion.domain.catalogue.places.{HeldSite, PlaceService, SiteTypeService}
import com.histmersion.domain.library.search.FilterEventSources
import com.histmersion.util.database.Transactor
import com.augustnagro.magnum.*
import hearth.kindlings.optics.syntax.*

import java.util.concurrent.atomic.{AtomicLong, AtomicReference}
import scala.collection.concurrent.TrieMap

/** A contract of the catalogue's services, run once on the in-memory stores and once on the database (a scratch
  * database per test, gated on HISTMERSION_TEST_DB_URL): [[fresh]] gives each test empty stores, or none on a run with
  * no such store.
  */
abstract class CatalogueStoreSuite extends com.histmersion.db.DbSuite:
  protected def fresh(test: String): Option[CatalogueStores]

  protected def stest(title: String)(body: CatalogueStores => Unit): Unit =
    test(title)(fresh(title).foreach(body))

object CatalogueStoreSuite:

  /** The stores in memory, sharing one state; the library's sources are the citekeys seeded, titled by themselves. */
  trait InMemory extends CatalogueStoreSuite:
    protected def fresh(test: String): Option[CatalogueStores] =
      val state = AtomicReference(CatalogueState.empty)
      val sources = TrieMap.empty[String, Unit]
      val clock = AtomicLong(1_000L)
      val evs = EventService.InMemory(state)
      Some(new CatalogueStores:
        val periods = PeriodService.InMemory(state)
        val attestations = AttestationService.InMemory(state)
        val events = evs
        val corrections = CorrectionService.InMemory(state)
        val eventImports = EventImportService.InMemory(state)
        val eventLinks = EventLinkService.InMemory(state, ck => sources.get(ck).map(_ => Some(ck)))
        val leads = LeadService.InMemory(state, evs, ck => sources.get(ck).map(_ => (Some(ck), None)))
        val siteTypes = SiteTypeService.InMemory(state, () => clock.incrementAndGet())
        val places =
          PlaceService.InMemory(state, java.time.Clock.fixed(java.time.Instant.EPOCH, java.time.ZoneOffset.UTC))
        def source(citekey: String): Unit = sources.update(citekey, ())
        def site(id: String, onMap: Boolean, cls: Option[String]): Unit =
          val _ = state.updateAndGet(_.modify(_.sites).using(_ :+ HeldSite(id, onMap, cls)))
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
        val evs = EventService(db, FilterEventSources())
        new CatalogueStores:
          val periods = PeriodService(db)
          val attestations = AttestationService(db)
          val events = evs
          val corrections = CorrectionService(db)
          val eventImports = EventImportService(db)
          val eventLinks = EventLinkService(db)
          val leads = LeadService(db, FilterEventSources())
          val siteTypes = SiteTypeService(db)
          val places = PlaceService(db)
          def source(citekey: String): Unit =
            connect(ds)(sql"INSERT INTO sources (citekey, title) VALUES ($citekey, $citekey)".update.run()): Unit
          def site(id: String, onMap: Boolean, cls: Option[String]): Unit =
            val props = cls.fold("{}")(c => s"""{"class":"$c"}""")
            connect(ds):
              sql"""INSERT INTO places (id, label, kind, import_source, properties)
                    VALUES ($id, $id, 'site', 'wikidata', $props::jsonb)""".update.run()
              if onMap then
                val ck = s"zz_on_$id".replace("_q", "q")
                sql"INSERT INTO sources (citekey, title) VALUES ($ck, $ck) ON CONFLICT DO NOTHING".update.run()
                sql"INSERT INTO findings (id, citekey, claim) VALUES (${ck + "#1"}, $ck, 'here')".update.run()
                sql"INSERT INTO finding_places (finding_id, place_id) VALUES (${ck + "#1"}, $id)".update.run()
            : Unit
          def seedRows(): List[String] =
            connect(ds)(sql"SELECT id FROM periods WHERE dataset = 'seed'".query[String].run()).toList
      }
