package com.histmersion
package db
import com.histmersion.util.problem.{Legacy, Problem}
import com.histmersion.util.problem.Text.{sentence, sentences}

import com.histmersion.testsupport.ComparedByValue.given

import hearth.kindlings.optics.syntax.*

import com.histmersion.util.database.Transactor

import com.histmersion.domain.library.sources.SourceFixtures

import com.histmersion.domain.media.assets.AssetFixtures
import com.histmersion.domain.media.assets.AssetInfoRow
import com.histmersion.domain.media.links.AssetLinkFixtures
import com.histmersion.domain.media.assets.NewFigure

import com.histmersion.util.jobs.JobRepo

import com.histmersion.config.*
import com.histmersion.domain.*

import java.nio.file.{Files, Path}
import com.histmersion.util.database.Migrate
import com.histmersion.api.jobs.JobState
import com.histmersion.api.library.{Ingest, Source}
import com.histmersion.api.media.AssetLink
import com.histmersion.util.database.given
import com.histmersion.domain.library.Citekey
import com.histmersion.util.jobs.Job
import com.histmersion.api.exchange.Bundle
import com.histmersion.api.media.AssetRelation
import com.histmersion.api.media.MediaTarget
import com.histmersion.util.jobs.JobRow

/** V006 round-trips against a real Postgres (HISTMERSION_TEST_DB_URL); skipped otherwise. */
class AssetsJobsSuite extends DbSuite:
  private val testDb = TestDb.url
  override def munitIgnore: Boolean = testDb.isEmpty

  private lazy val ds =
    val base = AppConfig.load().db
    val d = testPool(base.modify(_.url).setTo(DbConfig.Url(testDb.get)))
    Migrate.run(d); d

  private val ck = Citekey("zz_assets_source")

  private def png(colour: Byte): NewFigure.Bytes = NewFigure.Bytes(Array[Byte](0x89.toByte, 'P', 'N', 'G', colour))

  private val ingestKind = Job.Kind.applyUnsafe(Ingest.KindIngest)
  private val reocrKind = Job.Kind.applyUnsafe(Ingest.KindReocr)

  private def source(body: Option[Source.OcrMarkdown] = None) = Source(
    citekey = com.histmersion.api.library.Citekey(ck.value),
    title = com.histmersion.api.library.Title("Assets"),
    authors = (Nil).map(com.histmersion.api.library.AuthorName.apply),
    doi = None,
    year = None,
    venue = None,
    status = Source.Status.Unassessed,
    topics = Nil,
    regions = Nil,
    tags = Nil,
    period = None,
    summary = None,
    citedByCount = None,
    influentialCitations = None,
    provenance = None,
    calibreId = None,
    ocrMarkdown = body
  )

  private def seed(): Unit = SourceFixtures.upsert(ds, source(), AppConfig.load().limits.chunkChars)

  test("assets round-trip: write, list, read back the exact bytes"):
    seed()
    val n = AssetFixtures.replace(
      ds,
      ck,
      List(
        NewFigure(
          NewFigure.Name("a.png"),
          NewFigure.Mime("image/png"),
          png(1),
          Some(NewFigure.Width(10)),
          Some(NewFigure.Height(20))
        ),
        NewFigure(NewFigure.Name("b.png"), NewFigure.Mime("image/png"), png(2), None, None)
      )
    )
    assertEquals(n, 2)
    val listed = AssetFixtures.list(ds, ck)
    assertEquals(listed.map(_.name).toList, List("a.png", "b.png"))
    assertEquals(listed.head.width, Some(10))
    assertEquals(listed.head.bytes, png(1).value.length.toLong)
    val (mime, bytes) = AssetFixtures.read(ds, ck, AssetInfoRow.Name("a.png")).get
    assertEquals(mime, "image/png")
    assertEquals(bytes.value.toList, png(1).value.toList)
    assertEquals(AssetFixtures.read(ds, ck, AssetInfoRow.Name("nope.png")), None)

  test("identical pictures are stored once (sha256 dedupe), different ones are not"):
    seed()
    val n = AssetFixtures.replace(
      ds,
      ck,
      List(
        NewFigure(NewFigure.Name("page1-logo.png"), NewFigure.Mime("image/png"), png(7), None, None),
        NewFigure(NewFigure.Name("page2-logo.png"), NewFigure.Mime("image/png"), png(7), None, None), // same bytes
        NewFigure(NewFigure.Name("plot.png"), NewFigure.Mime("image/png"), png(8), None, None)
      )
    )
    assertEquals(n, 2)
    assertEquals(AssetFixtures.list(ds, ck).map(_.name).toList, List("page1-logo.png", "plot.png"))

  test("replace clears the previous set; add keeps it"):
    seed()
    AssetFixtures.replace(
      ds,
      ck,
      List(NewFigure(NewFigure.Name("old.png"), NewFigure.Mime("image/png"), png(1), None, None))
    )
    AssetFixtures.add(
      ds,
      ck,
      List(NewFigure(NewFigure.Name("new.png"), NewFigure.Mime("image/png"), png(2), None, None))
    )
    assertEquals(AssetFixtures.list(ds, ck).map(_.name).toList, List("new.png", "old.png"))
    AssetFixtures.replace(
      ds,
      ck,
      List(NewFigure(NewFigure.Name("only.png"), NewFigure.Mime("image/png"), png(3), None, None))
    )
    assertEquals(AssetFixtures.list(ds, ck).map(_.name).toList, List("only.png"))

  test("a re-conversion keeps each asset's uid and links: same name in place, a moved picture renamed (#826)"):
    seed()
    connectForTest(s"DELETE FROM assets WHERE citekey = '$ck'")
    AssetFixtures.replace(
      ds,
      ck,
      List(
        NewFigure(NewFigure.Name("fig-1.png"), NewFigure.Mime("image/png"), png(1), None, None),
        NewFigure(NewFigure.Name("fig-2.png"), NewFigure.Mime("image/png"), png(2), None, None),
        NewFigure(NewFigure.Name("fig-3.png"), NewFigure.Mime("image/png"), png(3), None, None)
      )
    )
    import com.augustnagro.magnum.*
    import com.histmersion.util.database.NewtypeColumns.given
    def uid(name: String) =
      connect(ds)(
        sql"SELECT uid::text FROM assets WHERE citekey = $ck AND name = $name".query[String].run().headOption
      )
    def links = AssetLinkFixtures
      .list(ds, Some(ck), None, None, None)
      .links
      .map(l => l.assetName -> l.targetId)
      .sortBy((a, t) => (a.value, t.value))
    val (u1, u2) = (uid("fig-1.png"), uid("fig-2.png"))
    AssetLinkFixtures
      .create(
        ds,
        AssetLink.Put(
          com.histmersion.api.library.Citekey(ck.value),
          Bundle.Asset.FileName("fig-1.png"),
          AssetRelation.Wire("depicts"),
          MediaTarget.TargetType("place"),
          MediaTarget.TargetId("hattusa")
        )
      )
      .fold(e => fail(e.problems.sentences.mkString("; ")), _ => ())
    AssetLinkFixtures
      .create(
        ds,
        AssetLink.Put(
          com.histmersion.api.library.Citekey(ck.value),
          Bundle.Asset.FileName("fig-2.png"),
          AssetRelation.Wire("depicts"),
          MediaTarget.TargetType("place"),
          MediaTarget.TargetId("troy")
        )
      )
      .fold(e => fail(e.problems.sentences.mkString("; ")), _ => ())
    AssetLinkFixtures
      .create(
        ds,
        AssetLink.Put(
          com.histmersion.api.library.Citekey(ck.value),
          Bundle.Asset.FileName("fig-3.png"),
          AssetRelation.Wire("depicts"),
          MediaTarget.TargetType("place"),
          MediaTarget.TargetId("mycenae")
        )
      )
      .fold(e => fail(e.problems.sentences.mkString("; ")), _ => ())
    // re-OCR: fig-1 re-rendered under its name, fig-2's picture now numbered fig-4, fig-3 gone
    AssetFixtures.replace(
      ds,
      ck,
      List(
        NewFigure(NewFigure.Name("fig-1.png"), NewFigure.Mime("image/png"), png(11), None, None),
        NewFigure(NewFigure.Name("fig-4.png"), NewFigure.Mime("image/png"), png(2), None, None)
      )
    )
    assertEquals(AssetFixtures.list(ds, ck).map(_.name).toList, List("fig-1.png", "fig-4.png"))
    assertEquals(uid("fig-1.png"), u1, "re-rendered at the same name: the same asset")
    assertEquals(
      AssetFixtures.read(ds, ck, AssetInfoRow.Name("fig-1.png")).map(_._2.value.toList),
      Some(png(11).value.toList)
    )
    assertEquals(uid("fig-4.png"), u2, "the same picture under a new name: renamed, not replaced")
    assertEquals(links, List("fig-1.png" -> "hattusa", "fig-4.png" -> "troy"))

  test("assets die with their source (ON DELETE CASCADE)"):
    seed()
    AssetFixtures.replace(
      ds,
      ck,
      List(NewFigure(NewFigure.Name("x.png"), NewFigure.Mime("image/png"), png(1), None, None))
    )
    connectForTest(s"DELETE FROM sources WHERE citekey = '$ck'")
    assertEquals(AssetFixtures.count(ds, ck), 0)

  test("the upload digest is stored on the source and finds it again"):
    seed()
    val sha = NewFigure.sha256(png(9))
    SourceFixtures.setSha256(ds, ck, sha)
    assertEquals(SourceFixtures.sha256Of(ds, ck), Some(sha))
    assertEquals(SourceFixtures.citekeyOfSha256(ds, sha), Some(ck))
    assertEquals(SourceFixtures.citekeyOfSha256(ds, Sha256.assume("0" * 64)), None)

  test("a body write does NOT clear the stored digest (put preserves sources.sha256)"):
    seed()
    val sha = NewFigure.sha256(png(4))
    SourceFixtures.setSha256(ds, ck, sha)
    SourceFixtures.upsert(ds, source(Some(Source.OcrMarkdown("new body"))), AppConfig.load().limits.chunkChars)
    assertEquals(SourceFixtures.sha256Of(ds, ck), Some(sha), "reocr depends on this pointer surviving")

  // ── jobs ───────────────────────────────────────────────────────────────────────────────────
  test("job lifecycle: queued → running → done, with progress"):
    val j = Transactor(ds).transact(JobRepo.create(ingestKind, Some(ck), Some(Sha256.assume("abc"))))
    assertEquals(j.status, JobState.Queued.wire)
    assertEquals(
      Transactor(ds).transact(
        JobRepo
          .transition(Job.Id.assume(j.id.value), JobState.Queued, JobState.Running, Some(JobRow.Progress("converting")))
      ),
      Some(JobState.Running)
    )
    Transactor(ds).transact(JobRepo.progress(Job.Id.assume(j.id.value), JobRow.Progress("storing 3 asset(s)")))
    assertEquals(
      Transactor(ds).read(JobRepo.get(Job.Id.assume(j.id.value))).flatMap(_.progress),
      Some("storing 3 asset(s)")
    )
    assertEquals(
      Transactor(ds).transact(
        JobRepo
          .transition(Job.Id.assume(j.id.value), JobState.Running, JobState.Done, Some(JobRow.Progress("1 page(s)")))
      ),
      Some(JobState.Done)
    )
    val done = Transactor(ds).read(JobRepo.get(Job.Id.assume(j.id.value))).get
    assertEquals(done.status, JobState.Done.wire)
    assert(done.succeeded && done.isTerminal)

  test("a job remembers the acquisition item it ticks off; one without an item reads back none"):
    val j =
      Transactor(ds).transact(JobRepo.create(ingestKind, Some(ck), None, acquireItem = Some(JobRow.AcquireItem(4242))))
    assertEquals(j.acquireItem, Some(4242))
    assertEquals(Transactor(ds).read(JobRepo.get(Job.Id.assume(j.id.value))).flatMap(_.acquireItem), Some(4242))
    assert(
      Transactor(ds).read(JobRepo.list(None, PageSize(50))).exists(x => x.id == j.id && x.acquireItem.contains(4242))
    )
    assertEquals(
      Transactor(ds)
        .read(JobRepo.get(Job.Id.assume(Transactor(ds).transact(JobRepo.create(ingestKind, Some(ck), None)).id.value)))
        .flatMap(_.acquireItem),
      None
    )

  test("a transition from the wrong state is refused — two workers cannot claim one job"):
    val j = Transactor(ds).transact(JobRepo.create(ingestKind, Some(ck), None))
    val id = Job.Id.assume(j.id.value)
    assertEquals(
      Transactor(ds).transact(JobRepo.transition(id, JobState.Queued, JobState.Running)),
      Some(JobState.Running)
    )
    // the second worker sees the row already moved on
    assertEquals(Transactor(ds).transact(JobRepo.transition(id, JobState.Queued, JobState.Running)), None)
    // and an illegal transition is refused by the state machine before SQL runs
    assertEquals(Transactor(ds).transact(JobRepo.transition(id, JobState.Running, JobState.Queued)), None)

  test("failure records the reason"):
    val j = Transactor(ds).transact(JobRepo.create(reocrKind, Some(ck), None))
    val id = Job.Id.assume(j.id.value)
    Transactor(ds).transact(JobRepo.transition(id, JobState.Queued, JobState.Running))
    Transactor(ds).transact(
      JobRepo.transition(
        id,
        JobState.Running,
        JobState.Failed,
        error = Some(Problem.Unclassified(Legacy.Text("converter unreachable")).toNonEmpty)
      )
    )
    val f = Transactor(ds).read(JobRepo.get(Job.Id.assume(j.id.value))).get
    assertEquals(f.status, JobState.Failed.wire)
    assertEquals(f.problems.map(_.sentences), Some(Vector("converter unreachable")))
    assert(!f.succeeded)

  test("a restart fails whatever was unfinished, and leaves terminal rows alone"):
    val done = Transactor(ds).transact(JobRepo.create(ingestKind, Some(ck), None))
    Transactor(ds).transact(JobRepo.transition(Job.Id.assume(done.id.value), JobState.Queued, JobState.Running))
    Transactor(ds).transact(JobRepo.transition(Job.Id.assume(done.id.value), JobState.Running, JobState.Done))
    val running = Transactor(ds).transact(JobRepo.create(ingestKind, Some(ck), None))
    Transactor(ds).transact(JobRepo.transition(Job.Id.assume(running.id.value), JobState.Queued, JobState.Running))
    val queued = Transactor(ds).transact(JobRepo.create(ingestKind, Some(ck), None))

    assert(Transactor(ds).transact(JobRepo.failInterrupted()).value >= 2)
    assertEquals(
      Transactor(ds).read(JobRepo.get(Job.Id.assume(running.id.value))).map(_.problems),
      Some(Some(Problem.Run.Interrupted.toNonEmpty))
    )
    assertEquals(
      Transactor(ds).read(JobRepo.get(Job.Id.assume(queued.id.value))).map(_.status),
      Some(JobState.Failed.wire)
    )
    assertEquals(Transactor(ds).read(JobRepo.get(Job.Id.assume(done.id.value))).map(_.status), Some(JobState.Done.wire))

  test("jobs list is newest-first and filterable by status"):
    Transactor(ds).transact(JobRepo.failInterrupted())
    val a = Transactor(ds).transact(JobRepo.create(ingestKind, Some(ck), None))
    val all = Transactor(ds).read(JobRepo.list(None, PageSize(10)))
    assertEquals(all.headOption.map(_.id), Some(a.id))
    assert(
      Transactor(ds).read(JobRepo.list(Some(JobState.Wire("queued")), PageSize(10))).forall(_.status.value == "queued")
    )
    assert(
      Transactor(ds).read(JobRepo.list(Some(JobState.Wire("done")), PageSize(10))).forall(_.status.value == "done")
    )
    assertEquals(Transactor(ds).read(JobRepo.list(Some(JobState.Wire("nonsense")), PageSize(10))), Vector.empty)
    assertEquals(Transactor(ds).read(JobRepo.get(Job.Id("00000000-0000-0000-0000-000000000000"))), None)

  private def connectForTest(sql: String): Unit =
    val c = ds.getConnection()
    try {
      val s = c.createStatement();
      try { val _ = s.execute(sql) }
      finally s.close()
    } finally c.close()
