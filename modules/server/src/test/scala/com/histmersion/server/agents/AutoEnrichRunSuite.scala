package com.histmersion
package server.agents

import com.histmersion.util.problem.Text.{sentence, sentences}
import com.histmersion.testsupport.ComparedByValue.given

import hearth.kindlings.optics.syntax.*

import com.histmersion.util.ocr.ConverterClient

import com.augustnagro.magnum.*
import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.api.automation.Agents.*
import com.histmersion.domain.automation.agents.AgentRepo
import com.histmersion.util.database.{Db, Migrate}
import com.histmersion.server.{Deps, ServerWiring}
import com.histmersion.util.ocr.ConverterClient

import java.nio.file.{Files, Path}
import com.histmersion.db.*
import com.histmersion.api.jobs.{JobInfo, JobState}
import com.histmersion.api.library.IngestMarkdown
import com.histmersion.api.ocr.ConvertedDoc
import com.histmersion.api.library.Finding
import com.histmersion.api.library.Ingest
import com.histmersion.api.library.Source
import com.histmersion.db.DbSuite
import com.histmersion.db.TestDb
import com.histmersion.domain.library.Citekey
import com.histmersion.api.automation.Managers
import com.histmersion.util.storage.Blob
import com.histmersion.config.DbConfig
import com.histmersion.config.ServerConfig
import com.histmersion.config.ConverterConfig
import com.histmersion.config.AgentsConfig
import com.histmersion.config.AgentRolesConfig
import com.histmersion.config.AgentManagersConfig
import com.histmersion.config.AgentEnrichConfig
import com.histmersion.api.exchange.Bundle
import com.histmersion.domain.library.ingest.Upload
import com.histmersion.util.storage.HandedFile
import com.histmersion.util.jobs.Job
import com.histmersion.util.nonempty.NonEmptyVector
import com.histmersion.domain.automation.managers.ManagerRepo
import com.histmersion.api.catalogue.Year
import com.histmersion.util.database.TestPermits.given
import com.histmersion.util.database.TestCreators.given

/** #986 `agents.auto-enrich` against a `*_test` database, the converter stubbed: an ingest job that stores a source
  * queues exactly one `enrich-source` run for it and says so in its progress line; nothing is queued with the switch
  * off, with agents off, for an already-enriched source, or by a second import of the same file; a chain with no ready
  * model is a note on the job, never a failed ingest. The run pool is never started, so no model is called. Skipped
  * without HISTMERSION_TEST_DB_URL.
  */
class AutoEnrichRunSuite extends DbSuite with RunTranscripts:
  private val testDb = TestDb.url
  override def munitIgnore: Boolean = testDb.isEmpty
  override val munitTimeout = scala.concurrent.duration.Duration(120, "s")

  private val Ck = Citekey("zzautoenrich2020_tin")
  private val IngestCk = api.library.Citekey("zzautoenrich2020_tin")
  private val RunTarget = RunInfo.Target("zzautoenrich2020_tin")
  private val ItemTarget = Managers.ManagerItem.Target("zzautoenrich2020_tin")

  private object Stub extends ConverterClient:
    def health = Right(ConverterClient.HealthSummary("ok"))
    def convert(file: Blob, filename: ConverterClient.Filename, quality: ConverterClient.HighQuality) =
      Right(
        ConvertedDoc(
          markdown = ConvertedDoc.Markdown("# Tin\n\nThe Uluburun ship carried ten tons of copper.\n"),
          pages = ConvertedDoc.Pages(1),
          filename = Some(ConvertedDoc.Filename(filename.value))
        )
      )

  private lazy val baseCfg =
    val b = AppConfig.load()
    b.modify(_.db)
      .setTo(b.db.modify(_.url).setTo(DbConfig.Url(testDb.get)))
      .modify(_.server)
      .setTo(b.server.modify(_.host).setTo(NonEmptyVector.of(ServerConfig.Host("127.0.0.1"))))
      .modify(_.converter)
      .setTo(
        b.converter
          .modify(_.spoolDir)
          .setTo(ConverterConfig.SpoolDir(Files.createTempDirectory("histmersion-autoenrich").toString))
      )
      .modify(_.agents)
      .setTo(
        b.agents.copy(
          enabled = AgentsConfig.Enabled(true),
          autoEnrich = AgentsConfig.AutoEnrich(true),
          prices = AgentsConfig.Prices("zzautoenrich=0/0"),
          roles = b.agents.roles.modify(_.extractor).setTo(AgentRolesConfig.Extractor("local:zzautoenrich")),
          // the direct run: auto-enrich as it was before the managers' phase 2 (still what managers off does)
          managers = b.agents.managers.modify(_.enabled).setTo(AgentManagersConfig.Enabled(false))
        )
      )
      .modify(_.libraryAgents)
      .setTo(
        b.libraryAgents
          .modify(_.enrich)
          .setTo(b.libraryAgents.enrich.modify(_.upstream).setTo(AgentEnrichConfig.Upstream(false)))
      )

  private lazy val ds = testPool(baseCfg.db)
  protected def transcriptDs: javax.sql.DataSource = ds

  private def clean(): Unit =
    Migrate.run(ds)
    connect(ds):
      sql"DELETE FROM jobs WHERE citekey LIKE 'zzautoenrich%' OR target_id LIKE 'zzautoenrich%'".update.run()
      sql"DELETE FROM queue_items WHERE text LIKE '%zzautoenrich%'".update.run()
      sql"DELETE FROM sources WHERE citekey LIKE 'zzautoenrich%'".update.run()
      sql"DELETE FROM manager_items WHERE target LIKE 'zzautoenrich%'".update.run()

  override def beforeEach(context: BeforeEach): Unit = clean()
  override def afterEach(context: AfterEach): Unit = clean()

  /** The handlers with their OCR pool running and the agent run pool NOT started. */
  private def withHandlers[A](cfg: AppConfig = baseCfg)(f: ServerWiring => A): A =
    val h = ServerWiring(Deps(cfg, ds), Stub)
    val _ = h.ingest.queue.start()
    try f(h)
    finally
      h.ingest.queue.stop()
      h.agents.stop()

  private lazy val pdf: Path =
    val p = Files.createTempFile("histmersion-autoenrich-", ".pdf")
    Files.writeString(p, s"%PDF-1.4 zzautoenrich ${System.nanoTime}")
    p

  /** Ingest the fixture file and wait for its job to settle. */
  private def ingest(h: ServerWiring): JobInfo =
    val accepted = h.ingest.controllers
      .ingest(
        Ingest.Params(IngestCk, Some(Bundle.Asset.FileName("tin.pdf"))),
        Upload.handed(HandedFile.snapshot(pdf.toFile))
      )
      .fold(e => fail(e.problems.sentences.mkString("; ")), identity)
    def go(n: Int): JobInfo =
      val j = h.jobs
        .job(Job.Id.applyUnsafe(accepted.jobId.value))
        .fold(e => fail(e.problems.sentences.mkString("; ")), identity)
      if j.isTerminal || n > 600 then j else { Thread.sleep(50); go(n + 1) }
    val j = go(0)
    assertEquals(j.state, JobState.Done, j.toString)
    j

  private def runs: List[RunInfo] = connect(ds)(AgentRepo.forTarget(Pipeline.EnrichSource, RunTarget)).toList

  test("an ingest queues exactly one enrich-source run and says so; a second import of the same file queues none"):
    withHandlers() { h =>
      val job = ingest(h)
      val rs = runs
      assertEquals(rs.size, 1, rs)
      val run = rs.head
      assertEquals(
        (run.status, run.origin.map(_.value: String), run.target.value: String),
        ("queued", Some(Origin.FromIngest.wire.value: String), Ck)
      )
      assert(job.progress.exists(_.value.contains(s"enrichment queued: run ${run.id}")), job.progress)
      assert(IngestMarkdown.job(job).contains(s"enrichment queued: run ${run.id}"), "jobs <id> shows it")
      // the same file again: the source is re-converted, the pending run covers it
      val again = ingest(h)
      assertEquals(runs.map(_.id), List(run.id))
      assert(
        again.progress.exists(_.value.contains(s"enrichment not queued: enrich-source run ${run.id} is queued")),
        again.progress
      )
    }

  test(
    "managers on (phase 2): the ingest hands the source to the enrich manager — an item, no run — and says it is paused"
  ):
    val on = baseCfg
      .modify(_.agents)
      .setTo(
        baseCfg.agents
          .modify(_.managers)
          .setTo(baseCfg.agents.managers.modify(_.enabled).setTo(AgentManagersConfig.Enabled(true)))
      )
    withHandlers(on) { h =>
      val job = ingest(h)
      assertEquals(runs, Nil, "the manager starts it, not the ingest")
      val item = com.augustnagro.magnum
        .connect(ds)(
          ManagerRepo
            .itemFor(
              Managers.ManagerInfo.Id(Managers.EnrichId),
              Managers.ManagerItem.Kind(Managers.SourceEnrich),
              ItemTarget
            )
        )
        .getOrElse(fail("no item"))
      assertEquals((item.origin, item.status), ("ingest", "pending"))
      assert(
        job.progress.exists(p =>
          p.value.contains(s"enrichment queued: item #${item.id} of the enrich manager") && p.value.contains("paused")
        ),
        job.progress
      )
    }

  test("switch off, or agents off: nothing queued and nothing said"):
    val off = baseCfg.modify(_.agents).setTo(baseCfg.agents.modify(_.autoEnrich).setTo(AgentsConfig.AutoEnrich(false)))
    withHandlers(off) { h =>
      val job = ingest(h)
      assertEquals(runs, Nil)
      assert(!job.progress.exists(_.value.contains("enrichment")), job.progress)
    }
    val disabled = baseCfg.modify(_.agents).setTo(baseCfg.agents.modify(_.enabled).setTo(AgentsConfig.Enabled(false)))
    withHandlers(disabled) { h =>
      val job = ingest(h)
      assertEquals(runs, Nil)
      assert(!job.progress.exists(_.value.contains("enrichment")), job.progress)
    }

  test("an already-enriched source (it has findings) is converted again but not re-enriched"):
    withHandlers() { h =>
      assert(
        h.library.sources
          .put(
            Ck,
            Source.Put(
              title = api.library.Title("Bronze Age tin"),
              year = (Some(2020)).map(Year.apply),
              findings = List(Finding.Input(Finding.Claim("The Uluburun ship carried copper.")))
            )
          )
          .isRight
      )
      val job = ingest(h)
      assertEquals(runs, Nil)
      assert(job.progress.exists(_.value.contains("enrichment not queued: it already has 1 finding")), job.progress)
    }

  test("a chain with no model that can run is a note on the job — the ingest is done all the same"):
    val unpriced = baseCfg.modify(_.agents).setTo(baseCfg.agents.modify(_.prices).setTo(AgentsConfig.Prices("")))
    withHandlers(unpriced) { h =>
      val job = ingest(h)
      assertEquals(runs, Nil)
      assert(
        job.progress.exists(p =>
          p.value.contains("enrichment not queued: no model of the extractor chain can run") &&
            p.value.contains("has no price")
        ),
        job.progress
      )
    }
