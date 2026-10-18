package com.histmersion.server

import com.histmersion.core.*
import com.histmersion.db.{Db, Migrate, QueueRepo, SourceRepo, TestDb}
import java.nio.file.{Files, Path}

/** A real queue write can throw instead of returning Left. Never turn stored OCR into a failed job
  * because its optional acquisition bookkeeping failed. Uses only the disposable *_test database. */
class IngestQueueFailureSuite extends com.histmersion.db.DbSuite:
  override def munitIgnore: Boolean = TestDb.url.isEmpty

  test("a queue-write failure leaves the stored OCR job done, with a bookkeeping warning"):
    val base = AppConfig.load()
    val cfg = base.copy(
      db = base.db.copy(url = TestDb.url.get),
      converter = base.converter.copy(spoolDir = Files.createTempDirectory("ingest-queue-spool").toString))
    val ds = testPool(cfg.db)
    Migrate.run(ds)
    val converter = new ConverterClient:
      def health = Right("ok")
      def convert(file: Path, filename: String, quality: Boolean) =
        Right(ConvertedDoc(markdown = "# Fixture\n\nOCR succeeded.", pages = 1, images = Nil))
    val handler = IngestHandlers(Deps(cfg, ds), converter)
    val key = "zz_ingest_queue_failure"
    val item = QueueRepo.add(ds, Queue.Kind.Acquire, Queue.QueueAdd(text = key))
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
      handler.start()
      val accepted = handler.ingest(
        IngestParams(key, Some("fixture.pdf"), force = true, acquireItem = Some(item.id)),
        pdf.toFile).toOption.get
      var job = handler.job(accepted.jobId).toOption.get
      var attempts = 0
      while !job.isTerminal && attempts < 500 do
        Thread.sleep(10)
        job = handler.job(accepted.jobId).toOption.get
        attempts += 1
      assert(SourceRepo.load(ds, key).isDefined, "source must have been stored")
      assertEquals(QueueRepo.get(ds, item.id).map(_.status), Some(Queue.Open))
      assertEquals(job.state, JobState.Done, s"stored source but job error: ${job.error}")
      assertEquals(job.error, None)
      assert(job.progress.exists(_.contains(s"acquisition item ${item.id} NOT ticked off")), job.toString)
    finally
      handler.stop()
      sql("DROP TRIGGER IF EXISTS zz_ingest_reject_done ON queue_items")
      sql("DROP FUNCTION IF EXISTS zz_ingest_reject_done()")
      QueueRepo.delete(ds, item.id)
      Files.deleteIfExists(pdf)
      // the pool is this suite's own (DbSuite) and closes after it
