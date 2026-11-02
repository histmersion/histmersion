package com.histmersion.server

import com.histmersion.util.ocr.ConverterClient

import com.histmersion.api.ApiError
import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.util.database.{Db, Migrate}
import com.histmersion.db.*
import com.histmersion.api.automation.Queue
import com.histmersion.api.backup.Backup
import com.histmersion.api.catalogue.{PlaceAdd, PlaceNameAdd, PlaceNameRemove}
import com.histmersion.api.library.{IngestParams, NoteImport, SeedRequest, SourceDeleteRequest, SourcePatch, SourcePut}
import com.histmersion.api.media.AssetInput
import com.histmersion.api.ocr.ConvertedDoc
import com.histmersion.api.research.{ArticleImport, ArticlePatch, ArticlePut}
import com.histmersion.domain.catalogue.Place

/** The restore lock (plan §1.10) must cover EVERY write, not the handful it started with.
  *
  * A restore drops and recreates every table; a write that slips past the lock writes into a schema that is about to be
  * replaced, and the writer is told it succeeded. This drives the handlers directly rather than over HTTP: the point is
  * that no write path forgets the guard, and the route layer only translates the answer into a 503.
  */
class MaintenanceLockSuite extends com.histmersion.db.DbSuite:
  private val testDb = com.histmersion.db.TestDb.url
  override def munitIgnore: Boolean = testDb.isEmpty

  private lazy val cfg = { val b = AppConfig.load(); b.copy(db = b.db.copy(url = testDb.get)) }
  private lazy val deps =
    val ds = testPool(cfg.db)
    Migrate.run(ds)
    Deps(cfg, ds, testQueryPool(cfg.db, cfg.query))

  private lazy val h = ServerWiring(deps, NoConverter)

  private object NoConverter extends ConverterClient:
    def health: Either[String, String] = Right("ok")
    def convert(
        file: com.histmersion.util.storage.Blob,
        filename: String,
        quality: Boolean
    ): Either[String, ConvertedDoc] = Left(
      "not used"
    )

  /** Run `f` while the maintenance lock is held, exactly as a restore holds it. */
  private def underLock[A](f: => Either[ApiError, A]): Either[ApiError, A] =
    var out: Either[ApiError, A] = Left(ApiError("never ran"))
    val _ = h.maintenance.exclusively("test-restore") { out = f; Right(()) }
    out

  private def refused(name: String)(f: => Either[ApiError, ?]): Unit =
    val r = underLock(f)
    val e = r.swap.toOption.getOrElse(fail(s"$name was NOT refused while a restore held the lock"))
    assertEquals(e.code, Some(Backup.CodeMaintenance), s"$name: ${e.message}")

  private val png =
    AssetInput("f.png", "image/png", java.util.Base64.getEncoder.encodeToString(Array[Byte](1, 2, 3)), None, None, None)

  test("every source write is refused while a restore holds the lock"):
    refused("put_source")(h.library.sources.put("zzlock_src", SourcePut(title = "T")))
    refused("patch_source")(h.library.sources.patch("zzlock_src", SourcePatch(summary = Some("x"))))
    refused("import")(h.library.sources.importNote(NoteImport(text = "---\ncitekey: zzlock_src\n---\n")))
    refused("seed")(h.seeding.seed(SeedRequest(dir = "/tmp")))
    refused("reindex")(h.upkeep.reindex(None, None))
    refused("edges/resolve")(h.library.citations.resolveEdges)
    refused("delete_source")(h.library.sources.delete("zzlock_src", SourceDeleteRequest(apply = true)))

  test("every ARTICLE write is refused too — these had no guard at all"):
    refused("put_article")(h.research.articles.put("zzlock-art", ArticlePut(title = "T", kind = "concept", body = "b")))
    refused("patch_article")(h.research.articles.patch("zzlock-art", ArticlePatch(body = Some("x"))))
    refused("delete_article")(h.research.articles.delete("zzlock-art"))
    refused("import_article")(h.research.articles.importNote(ArticleImport(text = "---\nslug: zzlock-art\n---\n")))

  test("the gazetteer writes are refused"):
    refused("places/import")(
      h.places.importPlaces(Caller.Local, None, None, """{"type":"FeatureCollection","features":[]}""")
    )
    refused("places/rebuild")(h.places.rebuild(Caller.Local))
    refused("places/names/add")(h.places.addName(Caller.Local, "zzlock_place", PlaceNameAdd("X")))
    refused("places/names/remove")(h.places.removeName(Caller.Local, "zzlock_place", PlaceNameRemove("X")))
    refused("places/add")(h.places.add(Caller.Local, PlaceAdd("Zzlock", Place.Kind.Site, 0, 0, "fixture")))

  test("the ingest surface is refused"):
    val f = java.io.File.createTempFile("zzlock", ".pdf")
    java.nio.file.Files.write(f.toPath, Array[Byte](1, 2, 3))
    try
      refused("ingest")(
        h.ingest.controllers
          .ingest(IngestParams("zzlock_src", Some("a.pdf")), com.histmersion.domain.library.ingest.Upload.handed(f))
      )
      refused("reocr")(h.ingest.controllers.reocr("zzlock_src", false))
      refused("put_assets")(h.media.assets.putFigures("zzlock_src", List(png)))
    finally { val _ = f.delete() }

  test("the ops writes are refused, while the READS still answer"):
    refused("lint/fix")(h.checks.guardedLintFix())
    refused("queue/add")(h.workLists.add("tasks", Queue.QueueAdd(text = "zzlock item")))
    refused("queue/patch")(h.workLists.patch(1, Queue.QueuePatch(status = Some("done"))))
    refused("queue/delete")(h.workLists.delete(1))
    refused("queue/import")(h.workLists.importText("tasks", Queue.QueueImport(text = "")))
    // reads are NOT blocked: a restore stops writes, it does not take the service down
    val reads = underLock(h.checks.lint(Nil))
    assert(reads.isRight, s"lint (a read) must still answer: $reads")
    assert(underLock(h.workListView.list("tasks", all = true)).isRight)

  test("the OCR pool is paused for the duration of a restore, and resumed after"):
    assert(!h.ingest.queue.paused, "the pool starts running")
    val _ = h.maintenance.exclusively("test-restore") {
      h.ingest.queue.pause()
      try
        assert(h.ingest.queue.paused, "a restore must stop the workers writing into a dropped schema")
        Right(())
      finally h.ingest.queue.resume()
    }
    assert(!h.ingest.queue.paused, "…and it must come back, whatever the restore did")

  test("once the lock is released, the same writes go through"):
    assert(h.library.sources.put("zzlock_src", SourcePut(title = "T")).isRight)
    assert(h.places.rebuild(Caller.Local).isRight)
