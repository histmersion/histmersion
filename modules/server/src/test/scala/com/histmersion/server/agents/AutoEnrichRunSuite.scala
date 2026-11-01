package com.histmersion.server.agents

import com.histmersion.util.ocr.ConverterClient

import com.augustnagro.magnum.*
import com.histmersion.api.*
import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.api.Agents.*
import com.histmersion.db.{AgentRepo, Db, Migrate}
import com.histmersion.server.{Deps, Handlers}
import com.histmersion.util.ocr.ConverterClient

import java.nio.file.{Files, Path}

/** #986 `agents.auto-enrich` against a `*_test` database, the converter stubbed: an ingest job that stores a source
  * queues exactly one `enrich-source` run for it and says so in its progress line; nothing is queued with the switch
  * off, with agents off, for an already-enriched source, or by a second import of the same file; a chain with no ready
  * model is a note on the job, never a failed ingest. The run pool is never started, so no model is called. Skipped
  * without HISTMERSION_TEST_DB_URL.
  */
class AutoEnrichRunSuite extends com.histmersion.db.DbSuite with RunTranscripts:
  private val testDb = com.histmersion.db.TestDb.url
  override def munitIgnore: Boolean = testDb.isEmpty
  override val munitTimeout = scala.concurrent.duration.Duration(120, "s")

  private val Ck = "zzautoenrich2020_tin"

  private object Stub extends ConverterClient:
    def health = Right("ok")
    def convert(file: com.histmersion.util.storage.Blob, filename: String, quality: Boolean) =
      Right(
        ConvertedDoc(
          markdown = "# Tin\n\nThe Uluburun ship carried ten tons of copper.\n",
          pages = 1,
          filename = Some(filename)
        )
      )

  private lazy val baseCfg =
    val b = AppConfig.load()
    b.copy(
      db = b.db.copy(url = testDb.get),
      server = b.server.copy(host = "127.0.0.1"),
      converter = b.converter.copy(spoolDir = Files.createTempDirectory("histmersion-autoenrich").toString),
      agents = b.agents.copy(
        enabled = true,
        autoEnrich = true,
        prices = "zzautoenrich=0/0",
        enrich = b.agents.enrich.copy(upstream = false),
        roles = b.agents.roles.copy(extractor = "local:zzautoenrich"),
        // the direct run: auto-enrich as it was before the managers' phase 2 (still what managers off does)
        managers = b.agents.managers.copy(enabled = false)
      )
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
  private def withHandlers[A](cfg: AppConfig = baseCfg)(f: Handlers => A): A =
    val h = Handlers(Deps(cfg, ds), Stub)
    val _ = h.ingest.start()
    try f(h)
    finally
      h.ingest.stop()
      h.agents.stop()

  private lazy val pdf: Path =
    val p = Files.createTempFile("histmersion-autoenrich-", ".pdf")
    Files.writeString(p, s"%PDF-1.4 zzautoenrich ${System.nanoTime}")
    p

  /** Ingest the fixture file and wait for its job to settle. */
  private def ingest(h: Handlers): JobInfo =
    val accepted = h.ingest.ingest(IngestParams(Ck, Some("tin.pdf")), pdf.toFile).fold(e => fail(e.message), identity)
    def go(n: Int): JobInfo =
      val j = h.ingest.job(accepted.jobId).fold(e => fail(e.message), identity)
      if j.isTerminal || n > 600 then j else { Thread.sleep(50); go(n + 1) }
    val j = go(0)
    assertEquals(j.state, JobState.Done, j.toString)
    j

  private def runs: List[RunInfo] = connect(ds)(AgentRepo.forTarget(Pipeline.EnrichSource, Ck)).toList

  test("an ingest queues exactly one enrich-source run and says so; a second import of the same file queues none"):
    withHandlers() { h =>
      val job = ingest(h)
      val rs = runs
      assertEquals(rs.size, 1, rs)
      val run = rs.head
      assertEquals((run.status, run.origin, run.target), ("queued", Some(Origin.Ingest.wire), Ck))
      assert(job.progress.exists(_.contains(s"enrichment queued: run ${run.id}")), job.progress)
      assert(IngestMarkdown.job(job).contains(s"enrichment queued: run ${run.id}"), "jobs <id> shows it")
      // the same file again: the source is re-converted, the pending run covers it
      val again = ingest(h)
      assertEquals(runs.map(_.id), List(run.id))
      assert(
        again.progress.exists(_.contains(s"enrichment not queued: enrich-source run ${run.id} is queued")),
        again.progress
      )
    }

  test(
    "managers on (phase 2): the ingest hands the source to the enrich manager — an item, no run — and says it is paused"
  ):
    val on = baseCfg.copy(agents = baseCfg.agents.copy(managers = baseCfg.agents.managers.copy(enabled = true)))
    withHandlers(on) { h =>
      val job = ingest(h)
      assertEquals(runs, Nil, "the manager starts it, not the ingest")
      val item = com.histmersion.db.ManagerRepo
        .itemFor(ds, com.histmersion.api.Managers.EnrichId, com.histmersion.api.Managers.SourceEnrich, Ck)
        .getOrElse(fail("no item"))
      assertEquals((item.origin, item.status), ("ingest", "pending"))
      assert(
        job.progress.exists(p =>
          p.contains(s"enrichment queued: item #${item.id} of the enrich manager") && p.contains("paused")
        ),
        job.progress
      )
    }

  test("switch off, or agents off: nothing queued and nothing said"):
    val off = baseCfg.copy(agents = baseCfg.agents.copy(autoEnrich = false))
    withHandlers(off) { h =>
      val job = ingest(h)
      assertEquals(runs, Nil)
      assert(!job.progress.exists(_.contains("enrichment")), job.progress)
    }
    val disabled = baseCfg.copy(agents = baseCfg.agents.copy(enabled = false))
    withHandlers(disabled) { h =>
      val job = ingest(h)
      assertEquals(runs, Nil)
      assert(!job.progress.exists(_.contains("enrichment")), job.progress)
    }

  test("an already-enriched source (it has findings) is converted again but not re-enriched"):
    withHandlers() { h =>
      assert(
        h.putSource(
          Ck,
          SourcePut(
            title = "Bronze Age tin",
            year = Some(2020),
            findings = List(FindingInput("The Uluburun ship carried copper."))
          )
        ).isRight
      )
      val job = ingest(h)
      assertEquals(runs, Nil)
      assert(job.progress.exists(_.contains("enrichment not queued: it already has 1 finding")), job.progress)
    }

  test("a chain with no model that can run is a note on the job — the ingest is done all the same"):
    val unpriced = baseCfg.copy(agents = baseCfg.agents.copy(prices = ""))
    withHandlers(unpriced) { h =>
      val job = ingest(h)
      assertEquals(runs, Nil)
      assert(
        job.progress.exists(p =>
          p.contains("enrichment not queued: no model of the extractor chain can run") &&
            p.contains("has no price")
        ),
        job.progress
      )
    }
