package com.histmersion
package server

import com.histmersion.api.Problem
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
import com.histmersion.db.DbSuite
import com.histmersion.db.TestDb
import com.histmersion.config.DbConfig
import com.histmersion.util.storage.Blob
import com.histmersion.util.data.maintenance.WriteGate
import com.histmersion.api.exchange.Bundle
import com.histmersion.domain.library.Citekey
import com.histmersion.domain.research.Slug
import com.histmersion.domain.catalogue.places.PlacesControllers
import com.histmersion.api.catalogue.Longitude
import com.histmersion.api.catalogue.Latitude
import com.histmersion.domain.library.ingest.Upload
import com.histmersion.util.storage.HandedFile
import com.histmersion.domain.automation.worklists.WorkListControllers
import com.histmersion.domain.automation.worklists.QueueItemId
import com.histmersion.server.views.WorkListView

/** The restore lock (plan §1.10) must cover EVERY write, not the handful it started with.
  *
  * A restore drops and recreates every table; a write that slips past the lock writes into a schema that is about to be
  * replaced, and the writer is told it succeeded. This drives the handlers directly rather than over HTTP: the point is
  * that no write path forgets the guard, and the route layer only translates the answer into a 503.
  */
class MaintenanceLockSuite extends DbSuite:
  private val testDb = TestDb.url
  override def munitIgnore: Boolean = testDb.isEmpty

  private lazy val cfg = {
    val b = AppConfig.load(); b.modify(_.db).setTo(b.db.modify(_.url).setTo(DbConfig.Url(testDb.get)))
  }
  private lazy val deps =
    val ds = testPool(cfg.db)
    Migrate.run(ds)
    Deps(cfg, ds, testQueryPool(cfg.db, cfg.query))

  private lazy val h = ServerWiring(deps, NoConverter)

  private object NoConverter extends ConverterClient:
    def health: Either[Problem, ConverterClient.HealthSummary] = Right(
      ConverterClient.HealthSummary("ok")
    )
    def convert(
        file: Blob,
        filename: ConverterClient.Filename,
        quality: ConverterClient.HighQuality
    ): Either[Problem, ConvertedDoc] = Left(
      Problem("not used")
    )

  /** Run `f` while the maintenance lock is held, exactly as a restore holds it. */
  private def underLock[A](f: => Either[Api.Error, A]): Either[Api.Error, A] =
    var out: Either[Api.Error, A] = Left(Api.Error(Problem("never ran")))
    val _ = h.maintenance.exclusively(WriteGate.OperationName("test-restore")) { out = f; Right(()) }
    out

  private def refused(name: String)(f: => Either[Api.Error, ?]): Unit =
    val r = underLock(f)
    val e = r.swap.toOption.getOrElse(fail(s"$name was NOT refused while a restore held the lock"))
    assertEquals(e.code, Some(Backup.CodeMaintenance), s"$name: ${e.message}")

  private val png =
    AssetInput(
      Bundle.Asset.FileName("f.png"),
      Bundle.Asset.MimeText("image/png"),
      AssetInput.Base64(java.util.Base64.getEncoder.encodeToString(Array[Byte](1, 2, 3))),
      None,
      None,
      None
    )

  test("every source write is refused while a restore holds the lock"):
    refused("put_source")(
      h.library.sources.put(Citekey("zzlock_src"), Source.Put(title = com.histmersion.api.library.Title("T")))
    )
    refused("patch_source")(
      h.library.sources.patch(Citekey("zzlock_src"), Source.Patch(summary = Some(Source.Summary("x"))))
    )
    refused("import")(
      h.library.sources.importNote(NoteImport(text = NoteImport.Text("---\ncitekey: zzlock_src\n---\n")))
    )
    refused("seed")(h.seeding.seed(SeedRequest(dir = SeedRequest.Dir("/tmp"))))
    refused("reindex")(h.upkeep.reindex(None, None))
    refused("edges/resolve")(h.library.citations.resolveEdges)
    refused("delete_source")(
      h.library.sources.delete(Citekey("zzlock_src"), Source.DeleteRequest(apply = Source.DeleteRequest.Apply(true)))
    )

  test("every ARTICLE write is refused too — these had no guard at all"):
    refused("put_article")(
      h.research.articles.put(
        Slug("zzlock-art"),
        Article.Put(title = Article.Title("T"), kind = Article.Kind.Wire("concept"), body = Article.Body("b"))
      )
    )
    refused("patch_article")(
      h.research.articles.patch(Slug("zzlock-art"), Article.Patch(body = Some(Article.Body("x"))))
    )
    refused("delete_article")(h.research.articles.delete(Slug("zzlock-art")))
    refused("import_article")(
      h.research.articles.importNote(Article.Import(text = Article.Import.Text("---\nslug: zzlock-art\n---\n")))
    )

  test("the gazetteer writes are refused"):
    refused("places/import")(
      h.places.importPlaces(
        Caller.Local,
        None,
        None,
        PlacesControllers.DatasetBody("""{"type":"FeatureCollection","features":[]}""")
      )
    )
    refused("places/rebuild")(h.places.rebuild(Caller.Local))
    refused("places/names/add")(
      h.places.addName(
        Caller.Local,
        Place.Id("zzlock_place"),
        com.histmersion.api.catalogue.Place.NameAdd(com.histmersion.api.catalogue.Place.NameAdd.Name("X"))
      )
    )
    refused("places/names/remove")(
      h.places.removeName(
        Caller.Local,
        Place.Id("zzlock_place"),
        com.histmersion.api.catalogue.Place.NameRemove(com.histmersion.api.catalogue.Place.NameRemove.Name("X"))
      )
    )
    refused("places/add")(
      h.places.add(
        Caller.Local,
        com.histmersion.api.catalogue.Place.Add(
          com.histmersion.api.catalogue.Place.Add.Label("Zzlock"),
          com.histmersion.api.catalogue.Place.Add.Kind(Place.Kind.Site),
          Longitude(0),
          Latitude(0),
          com.histmersion.api.catalogue.Place.Add.Source("fixture")
        )
      )
    )

  test("the ingest surface is refused"):
    val f = java.io.File.createTempFile("zzlock", ".pdf")
    java.nio.file.Files.write(f.toPath, Array[Byte](1, 2, 3))
    try
      refused("ingest")(
        h.ingest.controllers
          .ingest(
            Ingest.Params(com.histmersion.api.library.Citekey("zzlock_src"), Some(Bundle.Asset.FileName("a.pdf"))),
            Upload.handed(HandedFile.snapshot(f))
          )
      )
      refused("reocr")(h.ingest.controllers.reocr(Citekey("zzlock_src"), Ingest.Params.Quality(false)))
      refused("put_assets")(h.media.assets.putFigures(Citekey("zzlock_src"), List(png)))
    finally { val _ = f.delete() }

  test("the ops writes are refused, while the READS still answer"):
    refused("lint/fix")(h.checks.guardedLintFix())
    refused("queue/add")(
      h.workLists.add(WorkListControllers.ListName("tasks"), Queue.QueueAdd(text = Queue.QueueItem.Text("zzlock item")))
    )
    refused("queue/patch")(
      h.workLists.patch(QueueItemId(1), Queue.QueuePatch(status = Some(Queue.QueueItem.Status("done"))))
    )
    refused("queue/delete")(h.workLists.delete(QueueItemId(1)))
    refused("queue/import")(
      h.workLists.importText(Queue.Kind.Label("tasks"), Queue.QueueImport(text = Queue.QueueItem.Text("")))
    )
    // reads are NOT blocked: a restore stops writes, it does not take the service down
    val reads = underLock(h.checks.lint(Nil))
    assert(reads.isRight, s"lint (a read) must still answer: $reads")
    assert(
      underLock(
        h.workListView.list(WorkListControllers.KindOfList("tasks"), all = WorkListView.IncludeAll(true))
      ).isRight
    )

  test("the OCR pool is paused for the duration of a restore, and resumed after"):
    assert(!h.ingest.queue.paused, "the pool starts running")
    val _ = h.maintenance.exclusively(WriteGate.OperationName("test-restore")) {
      h.ingest.queue.pause()
      try
        assert(h.ingest.queue.paused, "a restore must stop the workers writing into a dropped schema")
        Right(())
      finally h.ingest.queue.resume()
    }
    assert(!h.ingest.queue.paused, "…and it must come back, whatever the restore did")

  test("once the lock is released, the same writes go through"):
    assert(
      h.library.sources.put(Citekey("zzlock_src"), Source.Put(title = com.histmersion.api.library.Title("T"))).isRight
    )
    assert(h.places.rebuild(Caller.Local).isRight)
