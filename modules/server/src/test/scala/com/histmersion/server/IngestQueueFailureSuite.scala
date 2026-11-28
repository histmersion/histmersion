package com.histmersion
package server

import com.histmersion.api.automation.queue.Docket
import com.histmersion.api.automation.queue.QueueAdd
import com.histmersion.api.automation.queue.QueueItem

import com.histmersion.testsupport.ComparedByValue.given

import hearth.kindlings.optics.syntax.*

import com.histmersion.domain.library.sources.SourceFixtures

import com.histmersion.util.ocr.ConverterClient

import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.domain.automation.worklists.QueueRows
import com.histmersion.db.TestDb
import com.histmersion.util.database.{Db, Migrate}
import java.nio.file.{Files, Path}
import com.histmersion.api.automation.Queue
import com.histmersion.api.jobs.JobState
import com.histmersion.api.ocr.ConvertedDoc
import com.histmersion.api.library.Ingest
import com.histmersion.db.DbSuite
import com.histmersion.config.DbConfig
import com.histmersion.config.ConverterConfig
import com.histmersion.util.storage.Blob
import com.histmersion.domain.media.assets.AssetService
import com.histmersion.util.jobs.JobListing
import com.histmersion.domain.library.Citekey
import com.histmersion.api.exchange.Bundle
import com.histmersion.domain.library.ingest.Upload
import com.histmersion.util.storage.HandedFile
import com.histmersion.util.jobs.Job
import com.histmersion.util.database.TestPermits.given
import com.histmersion.util.database.TestCreators.given
import com.histmersion.util.database.TestCreators
import com.histmersion.lint.{tolerated, LintRule}

/** A real queue write can throw instead of returning Left. Never turn stored OCR into a failed job because its optional
  * acquisition bookkeeping failed. Uses only the disposable *_test database.
  */
@tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
class IngestQueueFailureSuite extends DbSuite:
  override def munitIgnore: Boolean = TestDb.url.isEmpty

  test("a queue-write failure leaves the stored OCR job done, with a bookkeeping warning"):
    val base = AppConfig.load()
    val cfg = base
      .modify(_.db)
      .setTo(base.db.modify(_.url).setTo(DbConfig.Url(TestDb.url.get)))
      .modify(_.converter)
      .setTo(
        base.converter
          .modify(_.spoolDir)
          .setTo(ConverterConfig.SpoolDir(Files.createTempDirectory("ingest-queue-spool").toString))
      )
    val ds = testPool(cfg.db)
    Migrate.run(ds)
    val converter = new ConverterClient:
      def health = Right(ConverterClient.HealthSummary("ok"))
      def convert(file: Blob, filename: ConverterClient.Filename, quality: ConverterClient.HighQuality) =
        Right(
          ConvertedDoc(
            markdown = ConvertedDoc.Markdown("# Fixture\n\nOCR succeeded."),
            pages = ConvertedDoc.Pages(1),
            images = Nil
          )
        )
    val deps = Deps(cfg, ds)
    val handler = IngestWiring(
      deps,
      converter,
      AcquisitionTesting.sources(deps),
      AssetService(deps.transactor, TestCreators.clock),
      _ => None
    )
    val jobs = JobListing(ds, () => cfg)
    val key = Citekey("zz_ingest_queue_failure")
    val item =
      QueueRows.add(ds, Docket.Acquire, QueueAdd(text = QueueItem.Text("zz_ingest_queue_failure")))
    val pdf = Files.createTempFile("ingest-queue-upload", ".pdf")
    Files.writeString(pdf, "%PDF-1.4 queue failure fixture")
    def sql(statement: String): Unit =
      val connection = ds.getConnection()
      try
        val command = connection.createStatement()
        try { val _ = command.execute(statement) }
        finally command.close()
      finally connection.close()
    try
      sql("""CREATE FUNCTION zz_ingest_reject_done() RETURNS trigger LANGUAGE plpgsql AS $$
        BEGIN RAISE EXCEPTION 'fixture: acquisition queue update failed'; END $$""")
      sql("""CREATE TRIGGER zz_ingest_reject_done BEFORE UPDATE ON queue_items
        FOR EACH ROW WHEN (NEW.text = 'zz_ingest_queue_failure')
        EXECUTE FUNCTION zz_ingest_reject_done()""")
      handler.queue.start()
      val accepted = handler.controllers
        .ingest(
          Ingest.Params(
            api.library.Citekey("zz_ingest_queue_failure"),
            Some(Bundle.Asset.FileName("fixture.pdf")),
            force = Ingest.Params.Force.Force,
            acquireItem = Some(Ingest.Params.AcquireItem(item.id.value))
          ),
          Upload.handed(HandedFile.snapshot(pdf.toFile))
        )
        .toOption
        .get
      var job = jobs.job(Job.Id.applyUnsafe(accepted.jobId.value)).toOption.get
      var attempts = 0
      while !job.isTerminal && attempts < 500 do
        Thread.sleep(10)
        job = jobs.job(Job.Id.applyUnsafe(accepted.jobId.value)).toOption.get
        attempts += 1
      assert(SourceFixtures.load(ds, key).isDefined, "source must have been stored")
      assertEquals(QueueRows.get(ds, item.id).map(_.status), Some(Queue.Open))
      assertEquals(job.state, JobState.Done, s"stored source but job problems: ${job.problems}")
      assertEquals(job.problems, None)
      assert(job.progress.exists(_.value.contains(s"acquisition item ${item.id} NOT ticked off")), job.toString)
    finally
      handler.queue.stop()
      sql("DROP TRIGGER IF EXISTS zz_ingest_reject_done ON queue_items")
      sql("DROP FUNCTION IF EXISTS zz_ingest_reject_done()")
      QueueRows.delete(ds, item.id)
      Files.deleteIfExists(pdf)
      // the pool is this suite's own (DbSuite) and closes after it
