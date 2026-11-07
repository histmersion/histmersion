package com.histmersion.server

import com.histmersion.testsupport.ComparedByValue.given

import hearth.kindlings.optics.syntax.*

import com.histmersion.util.ocr.ConverterClient

import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.util.database.{Db, Migrate}
import com.histmersion.db.*
import com.histmersion.api.automation.Queue
import com.histmersion.api.backup.Backup
import com.histmersion.api.library.{NoteImport, SeedRequest}
import com.histmersion.api.media.AssetInput
import com.histmersion.api.ocr.ConvertedDoc
import com.histmersion.domain.catalogue.Place
import com.histmersion.api.Api
import com.histmersion.api.library.Ingest
import com.histmersion.api.library.Source
import com.histmersion.api.research.Article

/** The restore lock (plan §1.10) must cover EVERY write, not the handful it started with.
  *
  * A restore drops and recreates every table; a write that slips past the lock writes into a schema that is about to be
  * replaced, and the writer is told it succeeded. This drives the handlers directly rather than over HTTP: the point is
  * that no write path forgets the guard, and the route layer only translates the answer into a 503.
  */
class MaintenanceLockSuite extends com.histmersion.db.DbSuite:
  private val testDb = com.histmersion.db.TestDb.url
  override def munitIgnore: Boolean = testDb.isEmpty

  private lazy val cfg = { val b = AppConfig.load(); b.modify(_.db).setTo(b.db.modify(_.url).setTo(testDb.get)) }
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
  private def underLock[A](f: => Either[Api.Error, A]): Either[Api.Error, A] =
    var out: Either[Api.Error, A] = Left(Api.Error(com.histmersion.api.Api.Error.Message("never ran")))
    val _ = h.maintenance.exclusively(com.histmersion.util.data.maintenance.WriteGate.OperationName("test-restore")) { out = f; Right(()) }
    out

  private def refused(name: String)(f: => Either[Api.Error, ?]): Unit =
    val r = underLock(f)
    val e = r.swap.toOption.getOrElse(fail(s"$name was NOT refused while a restore held the lock"))
    assertEquals(e.code, Some(Backup.CodeMaintenance), s"$name: ${e.message}")

  private val png =
    AssetInput(com.histmersion.api.exchange.Bundle.Asset.FileName("f.png"), com.histmersion.api.exchange.Bundle.Asset.MimeText("image/png"), java.util.Base64.getEncoder.encodeToString(Array[Byte](1, 2, 3)), None, None, None)

  test("every source write is refused while a restore holds the lock"):
    refused("put_source")(h.library.sources.put(com.histmersion.domain.library.Citekey("zzlock_src"), Source.Put(title = com.histmersion.api.library.Title("T"))))
    refused("patch_source")(h.library.sources.patch(com.histmersion.domain.library.Citekey("zzlock_src"), Source.Patch(summary = Some(com.histmersion.api.library.Source.Summary("x")))))
    refused("import")(h.library.sources.importNote(NoteImport(text = com.histmersion.api.library.NoteImport.Text("---\ncitekey: zzlock_src\n---\n"))))
    refused("seed")(h.seeding.seed(SeedRequest(dir = com.histmersion.api.library.SeedRequest.Dir("/tmp"))))
    refused("reindex")(h.upkeep.reindex(None, None))
    refused("edges/resolve")(h.library.citations.resolveEdges)
    refused("delete_source")(h.library.sources.delete(com.histmersion.domain.library.Citekey("zzlock_src"), Source.DeleteRequest(apply = com.histmersion.api.library.Source.DeleteRequest.Apply(true))))

  test("every ARTICLE write is refused too — these had no guard at all"):
    refused("put_article")(
      h.research.articles.put(com.histmersion.domain.research.Slug("zzlock-art"), Article.Put(title = com.histmersion.api.research.Article.Title("T"), kind = com.histmersion.api.research.Article.Kind.Wire("concept"), body = com.histmersion.api.research.Article.Body("b")))
    )
    refused("patch_article")(h.research.articles.patch(com.histmersion.domain.research.Slug("zzlock-art"), Article.Patch(body = Some(com.histmersion.api.research.Article.Body("x")))))
    refused("delete_article")(h.research.articles.delete(com.histmersion.domain.research.Slug("zzlock-art")))
    refused("import_article")(h.research.articles.importNote(Article.Import(text = com.histmersion.api.research.Article.Import.Text("---\nslug: zzlock-art\n---\n"))))

  test("the gazetteer writes are refused"):
    refused("places/import")(
      h.places.importPlaces(Caller.Local, None, None, """{"type":"FeatureCollection","features":[]}""")
    )
    refused("places/rebuild")(h.places.rebuild(Caller.Local))
    refused("places/names/add")(
      h.places.addName(Caller.Local, com.histmersion.domain.catalogue.Place.Id("zzlock_place"), com.histmersion.api.catalogue.Place.NameAdd(com.histmersion.api.catalogue.Place.NameAdd.Name("X")))
    )
    refused("places/names/remove")(
      h.places.removeName(Caller.Local, com.histmersion.domain.catalogue.Place.Id("zzlock_place"), com.histmersion.api.catalogue.Place.NameRemove(com.histmersion.api.catalogue.Place.NameRemove.Name("X")))
    )
    refused("places/add")(
      h.places.add(Caller.Local, com.histmersion.api.catalogue.Place.Add(com.histmersion.api.catalogue.Place.Add.Label("Zzlock"), Place.Kind.Site, com.histmersion.api.catalogue.Longitude(0), com.histmersion.api.catalogue.Latitude(0), com.histmersion.api.catalogue.Place.Add.Source("fixture")))
    )

  test("the ingest surface is refused"):
    val f = java.io.File.createTempFile("zzlock", ".pdf")
    java.nio.file.Files.write(f.toPath, Array[Byte](1, 2, 3))
    try
      refused("ingest")(
        h.ingest.controllers
          .ingest(Ingest.Params(com.histmersion.api.library.Citekey("zzlock_src"), Some(com.histmersion.api.exchange.Bundle.Asset.FileName("a.pdf"))), com.histmersion.domain.library.ingest.Upload.handed(com.histmersion.util.storage.HandedFile.snapshot(f)))
      )
      refused("reocr")(h.ingest.controllers.reocr(com.histmersion.domain.library.Citekey("zzlock_src"), com.histmersion.api.library.Ingest.Params.Quality(false)))
      refused("put_assets")(h.media.assets.putFigures(com.histmersion.domain.library.Citekey("zzlock_src"), List(png)))
    finally { val _ = f.delete() }

  test("the ops writes are refused, while the READS still answer"):
    refused("lint/fix")(h.checks.guardedLintFix())
    refused("queue/add")(h.workLists.add(com.histmersion.domain.automation.worklists.WorkListControllers.ListName("tasks"), Queue.QueueAdd(text = com.histmersion.api.automation.Queue.QueueItem.Text("zzlock item"))))
    refused("queue/patch")(h.workLists.patch(com.histmersion.domain.automation.worklists.QueueItemId(1), Queue.QueuePatch(status = Some(com.histmersion.api.automation.Queue.QueueItem.Status("done")))))
    refused("queue/delete")(h.workLists.delete(com.histmersion.domain.automation.worklists.QueueItemId(1)))
    refused("queue/import")(h.workLists.importText(com.histmersion.api.automation.Queue.Kind.Label("tasks"), Queue.QueueImport(text = com.histmersion.api.automation.Queue.QueueItem.Text(""))))
    // reads are NOT blocked: a restore stops writes, it does not take the service down
    val reads = underLock(h.checks.lint(Nil))
    assert(reads.isRight, s"lint (a read) must still answer: $reads")
    assert(underLock(h.workListView.list(com.histmersion.domain.automation.worklists.WorkListControllers.KindOfList("tasks"), all = com.histmersion.server.views.WorkListView.IncludeAll(true))).isRight)

  test("the OCR pool is paused for the duration of a restore, and resumed after"):
    assert(!h.ingest.queue.paused, "the pool starts running")
    val _ = h.maintenance.exclusively(com.histmersion.util.data.maintenance.WriteGate.OperationName("test-restore")) {
      h.ingest.queue.pause()
      try
        assert(h.ingest.queue.paused, "a restore must stop the workers writing into a dropped schema")
        Right(())
      finally h.ingest.queue.resume()
    }
    assert(!h.ingest.queue.paused, "…and it must come back, whatever the restore did")

  test("once the lock is released, the same writes go through"):
    assert(h.library.sources.put(com.histmersion.domain.library.Citekey("zzlock_src"), Source.Put(title = com.histmersion.api.library.Title("T"))).isRight)
    assert(h.places.rebuild(Caller.Local).isRight)
