package com.histmersion
package domain.catalogue

import com.histmersion.testsupport.ComparedByValue.given

import com.histmersion.api.Dataset
import com.histmersion.config.{AppConfig, DbConfig}
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
import com.histmersion.db.DbSuite
import com.histmersion.domain.Title
import com.histmersion.api.catalogue.EventSourceRef
import com.histmersion.db.TestDb
import com.histmersion.util.database.TestCreators

/** A contract of the catalogue's services, run once on the in-memory stores and once on the database (a scratch
  * database per test, gated on HISTMERSION_TEST_DB_URL): [[fresh]] gives each test empty stores, or none on a run with
  * no such store.
  */
abstract class CatalogueStoreSuite extends DbSuite:
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
        @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
        val eventLinks = EventLinkService.InMemory(state, ck => sources.get(ck.value).map(_ => Some(Title(ck.value))))
        @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
        val leads = LeadService.InMemory(
          state,
          evs,
          ck => sources.get(ck.value).map(_ => (Some(EventSourceRef.Title(ck.value)), None))
        )
        @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
        val siteTypes = SiteTypeService.InMemory(state, () => HeldSite.CheckedAt(clock.incrementAndGet()))
        val places =
          PlaceService.InMemory(state, java.time.Clock.fixed(java.time.Instant.EPOCH, java.time.ZoneOffset.UTC))
        def source(citekey: String): Unit = sources.update(citekey, ())
        @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
        def site(id: String, onMap: Boolean, cls: Option[String]): Unit =
          val _ = state.updateAndGet(
            _.modify(_.sites)
              .using(_ :+ HeldSite(HeldSite.Id(id), HeldSite.OnMap(onMap), cls.map(HeldSite.ImportClass(_))))
          )
        @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
        def seedRows(): List[String] =
          state.get.periods.filter(_.dataset == Dataset.Seed).map(_.period.id.value).toList)

  /** The stores on a scratch database of their own per test. */
  trait OnDatabase extends CatalogueStoreSuite:
    private val testDb = TestDb.url
    override def munitIgnore: Boolean = testDb.isEmpty
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    private lazy val scratch =
      ScratchDatabases(
        s"histmersion_s8c_${getClass.getSimpleName.toLowerCase.take(24)}",
        AppConfig.load().db.copy(url = DbConfig.Url(testDb.get))
      )
    private val made = AtomicLong(0L)
    override def afterSuite(): Unit = if testDb.isDefined then scratch.dropAll()
    protected def fresh(test: String): Option[CatalogueStores] =
      testDb.map { _ =>
        val ds = scratch(made.incrementAndGet().toString)
        val db = Transactor(ds)
        val evs = EventService(db, FilterEventSources(), TestCreators.clock)
        new CatalogueStores:
          val periods = PeriodService(db, TestCreators.clock)
          val attestations = AttestationService(db, TestCreators.clock)
          val events = evs
          val corrections = CorrectionService(db)
          val eventImports = EventImportService(db, TestCreators.clock)
          val eventLinks = EventLinkService(db)
          val leads = LeadService(db, FilterEventSources())
          val siteTypes = SiteTypeService(db)
          val places = PlaceService(db, TestCreators.clock)
          def source(citekey: String): Unit =
            connect(ds)(
              sql"INSERT INTO sources (citekey, title, creator) VALUES ($citekey, $citekey, jsonb_build_object('kind', 'unknown'))".update
                .run()
            ): Unit
          def site(id: String, onMap: Boolean, cls: Option[String]): Unit =
            val props = cls.fold("{}")(c => s"""{"class":"$c"}""")
            connect(ds):
              sql"""INSERT INTO places (id, label, kind, import_source, properties, creator)
                    VALUES ($id, $id, 'site', 'wikidata', $props::jsonb, jsonb_build_object('kind', 'unknown'))""".update
                .run()
              if onMap then
                val ck = s"zz_on_$id".replace("_q", "q")
                sql"INSERT INTO sources (citekey, title, creator) VALUES ($ck, $ck, jsonb_build_object('kind', 'unknown')) ON CONFLICT DO NOTHING".update
                  .run()
                sql"INSERT INTO findings (id, citekey, claim, creator) VALUES (${ck + "#1"}, $ck, 'here', jsonb_build_object('kind', 'unknown'))".update
                  .run()
                sql"INSERT INTO finding_places (finding_id, place_id) VALUES (${ck + "#1"}, $id)".update.run()
            : Unit
          def seedRows(): List[String] =
            connect(ds)(sql"SELECT id FROM periods WHERE dataset = 'seed'".query[String].run()).toList
      }
