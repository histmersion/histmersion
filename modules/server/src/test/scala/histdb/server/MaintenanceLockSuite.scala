package histdb.server

import histdb.core.*
import histdb.db.{Db, Migrate}

/** The restore lock (plan §1.10) must cover EVERY write, not the handful it started with.
  *
  * A restore drops and recreates every table; a write that slips past the lock writes into a schema
  * that is about to be replaced, and the writer is told it succeeded. This drives the handlers
  * directly rather than over HTTP: the point is that no write path forgets the guard, and the route
  * layer only translates the answer into a 503. */
class MaintenanceLockSuite extends munit.FunSuite:
  private val testDb                = sys.env.get("HISTDB_TEST_DB_URL")
  override def munitIgnore: Boolean = testDb.isEmpty

  private lazy val cfg = { val b = AppConfig.load(); b.copy(db = b.db.copy(url = testDb.get)) }
  private lazy val deps =
    val ds = Db.dataSource(cfg.db)
    Migrate.run(ds)
    Deps(cfg, ds, Db.queryDataSource(cfg.db, cfg.query))

  private lazy val h = Handlers(deps, NoConverter)

  private object NoConverter extends ConverterClient:
    def health: Either[String, String] = Right("ok")
    def convert(file: java.nio.file.Path, filename: String, quality: Boolean)
        : Either[String, ConvertedDoc] = Left("not used")

  /** Run `f` while the maintenance lock is held, exactly as a restore holds it. */
  private def underLock[A](f: => Either[ApiError, A]): Either[ApiError, A] =
    var out: Either[ApiError, A] = Left(ApiError("never ran"))
    val _ = h.maintenance.exclusively("test-restore") { out = f; Right(()) }
    out

  private def refused(name: String)(f: => Either[ApiError, ?]): Unit =
    val r = underLock(f)
    val e = r.swap.toOption.getOrElse(fail(s"$name was NOT refused while a restore held the lock"))
    assertEquals(e.code, Some(Backup.CodeMaintenance), s"$name: ${e.message}")

  private val png = AssetInput("f.png", "image/png",
    java.util.Base64.getEncoder.encodeToString(Array[Byte](1, 2, 3)), None, None, None)

  test("every source write is refused while a restore holds the lock"):
    refused("put_source")(h.putSource("zzlock_src", SourcePut(title = "T")))
    refused("patch_source")(h.patchSource("zzlock_src", SourcePatch(summary = Some("x"))))
    refused("import")(h.importNote(NoteImport(text = "---\ncitekey: zzlock_src\n---\n")))
    refused("seed")(h.seed(SeedRequest(dir = "/tmp")))
    refused("reindex")(h.reindex(None, None))
    refused("edges/resolve")(h.resolveEdges)

  test("every ARTICLE write is refused too — these had no guard at all"):
    refused("put_article")(h.putArticle("zzlock-art", ArticlePut(title = "T", kind = "concept", body = "b")))
    refused("patch_article")(h.patchArticle("zzlock-art", ArticlePatch(body = Some("x"))))
    refused("delete_article")(h.deleteArticle("zzlock-art"))
    refused("import_article")(h.importArticle(ArticleImport(text = "---\nslug: zzlock-art\n---\n")))

  test("the gazetteer writes are refused"):
    refused("places/import")(h.importPlaces(None, """{"type":"FeatureCollection","features":[]}"""))
    refused("places/rebuild")(h.rebuildPlaces())

  test("the ingest surface is refused"):
    val f = java.io.File.createTempFile("zzlock", ".pdf")
    java.nio.file.Files.write(f.toPath, Array[Byte](1, 2, 3))
    try
      refused("ingest")(h.ingest.ingest(IngestParams("zzlock_src", Some("a.pdf")), f))
      refused("reocr")(h.ingest.reocr("zzlock_src", false))
      refused("put_assets")(h.ingest.putAssets("zzlock_src", List(png)))
    finally { val _ = f.delete() }

  test("the ops writes are refused, while the READS still answer"):
    refused("lint/fix")(h.ops.guardedLintFix())
    refused("queue/add")(h.ops.queueAdd("tasks", Queue.QueueAdd(text = "zzlock item")))
    refused("queue/patch")(h.ops.queuePatch(1, Queue.QueuePatch(status = Some("done"))))
    refused("queue/delete")(h.ops.queueDelete(1))
    refused("queue/import")(h.ops.queueImport("tasks", Queue.QueueImport(text = "")))
    // reads are NOT blocked: a restore stops writes, it does not take the service down
    val reads = underLock(h.ops.lint(Nil))
    assert(reads.isRight, s"lint (a read) must still answer: $reads")
    assert(underLock(h.ops.queueList("tasks", all = true)).isRight)

  test("the OCR pool is paused for the duration of a restore, and resumed after"):
    assert(!h.ingest.paused, "the pool starts running")
    val _ = h.maintenance.exclusively("test-restore") {
      h.ingest.pause()
      try
        assert(h.ingest.paused, "a restore must stop the workers writing into a dropped schema")
        Right(())
      finally h.ingest.resume()
    }
    assert(!h.ingest.paused, "…and it must come back, whatever the restore did")

  test("once the lock is released, the same writes go through"):
    assert(h.putSource("zzlock_src", SourcePut(title = "T")).isRight)
    assert(h.rebuildPlaces().isRight)
