package com.histmersion.db

import com.histmersion.util.database.Transactor

import com.histmersion.domain.library.sources.SourceFixtures

import com.histmersion.domain.media.assets.AssetFixtures
import com.histmersion.domain.media.links.AssetLinkFixtures
import com.histmersion.domain.media.assets.NewFigure

import com.histmersion.util.jobs.JobRepo

import com.histmersion.config.*
import com.histmersion.domain.*

import java.nio.file.{Files, Path}
import com.histmersion.values.SourceStatus
import com.histmersion.util.database.Migrate
import com.histmersion.api.jobs.JobState
import com.histmersion.api.library.{Ingest, Source}
import com.histmersion.api.media.AssetLinkPut

/** V006 round-trips against a real Postgres (HISTMERSION_TEST_DB_URL); skipped otherwise. */
class AssetsJobsSuite extends com.histmersion.db.DbSuite:
  private val testDb = com.histmersion.db.TestDb.url
  override def munitIgnore: Boolean = testDb.isEmpty

  private lazy val ds =
    val base = AppConfig.load().db
    val d = testPool(base.copy(url = testDb.get))
    Migrate.run(d); d

  private val ck = "zz_assets_source"

  private def png(colour: Byte): Array[Byte] = Array[Byte](0x89.toByte, 'P', 'N', 'G', colour)

  private def source(body: Option[String] = None) = Source(
    citekey = ck,
    title = "Assets",
    authors = Nil,
    doi = None,
    year = None,
    venue = None,
    status = SourceStatus.Unassessed,
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
        NewFigure("a.png", "image/png", png(1), Some(10), Some(20)),
        NewFigure("b.png", "image/png", png(2), None, None)
      )
    )
    assertEquals(n, 2)
    val listed = AssetFixtures.list(ds, ck)
    assertEquals(listed.map(_.name).toList, List("a.png", "b.png"))
    assertEquals(listed.head.width, Some(10))
    assertEquals(listed.head.bytes, png(1).length)
    val (mime, bytes) = AssetFixtures.read(ds, ck, "a.png").get
    assertEquals(mime, "image/png")
    assertEquals(bytes.toList, png(1).toList)
    assertEquals(AssetFixtures.read(ds, ck, "nope.png"), None)

  test("identical pictures are stored once (sha256 dedupe), different ones are not"):
    seed()
    val n = AssetFixtures.replace(
      ds,
      ck,
      List(
        NewFigure("page1-logo.png", "image/png", png(7), None, None),
        NewFigure("page2-logo.png", "image/png", png(7), None, None), // same bytes
        NewFigure("plot.png", "image/png", png(8), None, None)
      )
    )
    assertEquals(n, 2)
    assertEquals(AssetFixtures.list(ds, ck).map(_.name).toList, List("page1-logo.png", "plot.png"))

  test("replace clears the previous set; add keeps it"):
    seed()
    AssetFixtures.replace(ds, ck, List(NewFigure("old.png", "image/png", png(1), None, None)))
    AssetFixtures.add(ds, ck, List(NewFigure("new.png", "image/png", png(2), None, None)))
    assertEquals(AssetFixtures.list(ds, ck).map(_.name).toList, List("new.png", "old.png"))
    AssetFixtures.replace(ds, ck, List(NewFigure("only.png", "image/png", png(3), None, None)))
    assertEquals(AssetFixtures.list(ds, ck).map(_.name).toList, List("only.png"))

  test("a re-conversion keeps each asset's uid and links: same name in place, a moved picture renamed (#826)"):
    seed()
    connectForTest(s"DELETE FROM assets WHERE citekey = '$ck'")
    AssetFixtures.replace(
      ds,
      ck,
      List(
        NewFigure("fig-1.png", "image/png", png(1), None, None),
        NewFigure("fig-2.png", "image/png", png(2), None, None),
        NewFigure("fig-3.png", "image/png", png(3), None, None)
      )
    )
    import com.augustnagro.magnum.*
    def uid(name: String) =
      connect(ds)(sql"SELECT uid::text FROM assets WHERE citekey = $ck AND name = $name".query[String].run().headOption)
    def links = AssetLinkFixtures.list(ds, Some(ck), None, None, None).links.map(l => l.assetName -> l.targetId).sorted
    val (u1, u2) = (uid("fig-1.png"), uid("fig-2.png"))
    AssetLinkFixtures
      .create(ds, AssetLinkPut(ck, "fig-1.png", "depicts", "place", "hattusa"))
      .fold(e => fail(e.message), _ => ())
    AssetLinkFixtures
      .create(ds, AssetLinkPut(ck, "fig-2.png", "depicts", "place", "troy"))
      .fold(e => fail(e.message), _ => ())
    AssetLinkFixtures
      .create(ds, AssetLinkPut(ck, "fig-3.png", "depicts", "place", "mycenae"))
      .fold(e => fail(e.message), _ => ())
    // re-OCR: fig-1 re-rendered under its name, fig-2's picture now numbered fig-4, fig-3 gone
    AssetFixtures.replace(
      ds,
      ck,
      List(
        NewFigure("fig-1.png", "image/png", png(11), None, None),
        NewFigure("fig-4.png", "image/png", png(2), None, None)
      )
    )
    assertEquals(AssetFixtures.list(ds, ck).map(_.name).toList, List("fig-1.png", "fig-4.png"))
    assertEquals(uid("fig-1.png"), u1, "re-rendered at the same name: the same asset")
    assertEquals(AssetFixtures.read(ds, ck, "fig-1.png").map(_._2.toList), Some(png(11).toList))
    assertEquals(uid("fig-4.png"), u2, "the same picture under a new name: renamed, not replaced")
    assertEquals(links, List("fig-1.png" -> "hattusa", "fig-4.png" -> "troy"))

  test("assets die with their source (ON DELETE CASCADE)"):
    seed()
    AssetFixtures.replace(ds, ck, List(NewFigure("x.png", "image/png", png(1), None, None)))
    connectForTest(s"DELETE FROM sources WHERE citekey = '$ck'")
    assertEquals(AssetFixtures.count(ds, ck), 0)

  test("the upload digest is stored on the source and finds it again"):
    seed()
    val sha = NewFigure.sha256(png(9))
    SourceFixtures.setSha256(ds, ck, sha)
    assertEquals(SourceFixtures.sha256Of(ds, ck), Some(sha))
    assertEquals(SourceFixtures.citekeyOfSha256(ds, sha), Some(ck))
    assertEquals(SourceFixtures.citekeyOfSha256(ds, "0" * 64), None)

  test("a body write does NOT clear the stored digest (put preserves sources.sha256)"):
    seed()
    val sha = NewFigure.sha256(png(4))
    SourceFixtures.setSha256(ds, ck, sha)
    SourceFixtures.upsert(ds, source(Some("new body")), AppConfig.load().limits.chunkChars)
    assertEquals(SourceFixtures.sha256Of(ds, ck), Some(sha), "reocr depends on this pointer surviving")

  // ── jobs ───────────────────────────────────────────────────────────────────────────────────
  test("job lifecycle: queued → running → done, with progress"):
    val j = Transactor(ds).transact(JobRepo.create(Ingest.KindIngest, Some(ck), Some("abc")))
    assertEquals(j.status, JobState.Queued.wire)
    assertEquals(
      Transactor(ds).transact(JobRepo.transition(j.id, JobState.Queued, JobState.Running, Some("converting"))),
      Some(JobState.Running)
    )
    Transactor(ds).transact(JobRepo.progress(j.id, "storing 3 asset(s)"))
    assertEquals(Transactor(ds).read(JobRepo.get(j.id)).flatMap(_.progress), Some("storing 3 asset(s)"))
    assertEquals(
      Transactor(ds).transact(JobRepo.transition(j.id, JobState.Running, JobState.Done, Some("1 page(s)"))),
      Some(JobState.Done)
    )
    val done = Transactor(ds).read(JobRepo.get(j.id)).get
    assertEquals(done.status, JobState.Done.wire)
    assert(done.succeeded && done.isTerminal)

  test("a job remembers the acquisition item it ticks off; one without an item reads back none"):
    val j = Transactor(ds).transact(JobRepo.create(Ingest.KindIngest, Some(ck), None, acquireItem = Some(4242)))
    assertEquals(j.acquireItem, Some(4242))
    assertEquals(Transactor(ds).read(JobRepo.get(j.id)).flatMap(_.acquireItem), Some(4242))
    assert(Transactor(ds).read(JobRepo.list(None, 50)).exists(x => x.id == j.id && x.acquireItem.contains(4242)))
    assertEquals(
      Transactor(ds)
        .read(JobRepo.get(Transactor(ds).transact(JobRepo.create(Ingest.KindIngest, Some(ck), None)).id))
        .flatMap(_.acquireItem),
      None
    )

  test("a transition from the wrong state is refused — two workers cannot claim one job"):
    val j = Transactor(ds).transact(JobRepo.create(Ingest.KindIngest, Some(ck), None))
    assertEquals(
      Transactor(ds).transact(JobRepo.transition(j.id, JobState.Queued, JobState.Running)),
      Some(JobState.Running)
    )
    // the second worker sees the row already moved on
    assertEquals(Transactor(ds).transact(JobRepo.transition(j.id, JobState.Queued, JobState.Running)), None)
    // and an illegal transition is refused by the state machine before SQL runs
    assertEquals(Transactor(ds).transact(JobRepo.transition(j.id, JobState.Running, JobState.Queued)), None)

  test("failure records the reason"):
    val j = Transactor(ds).transact(JobRepo.create(Ingest.KindReocr, Some(ck), None))
    Transactor(ds).transact(JobRepo.transition(j.id, JobState.Queued, JobState.Running))
    Transactor(ds).transact(
      JobRepo.transition(j.id, JobState.Running, JobState.Failed, error = Some("converter unreachable"))
    )
    val f = Transactor(ds).read(JobRepo.get(j.id)).get
    assertEquals(f.status, JobState.Failed.wire)
    assertEquals(f.error, Some("converter unreachable"))
    assert(!f.succeeded)

  test("a restart fails whatever was unfinished, and leaves terminal rows alone"):
    val done = Transactor(ds).transact(JobRepo.create(Ingest.KindIngest, Some(ck), None))
    Transactor(ds).transact(JobRepo.transition(done.id, JobState.Queued, JobState.Running))
    Transactor(ds).transact(JobRepo.transition(done.id, JobState.Running, JobState.Done))
    val running = Transactor(ds).transact(JobRepo.create(Ingest.KindIngest, Some(ck), None))
    Transactor(ds).transact(JobRepo.transition(running.id, JobState.Queued, JobState.Running))
    val queued = Transactor(ds).transact(JobRepo.create(Ingest.KindIngest, Some(ck), None))

    assert(Transactor(ds).transact(JobRepo.failInterrupted()) >= 2)
    assertEquals(Transactor(ds).read(JobRepo.get(running.id)).map(_.error), Some(Some(JobState.Interrupted)))
    assertEquals(Transactor(ds).read(JobRepo.get(queued.id)).map(_.status), Some(JobState.Failed.wire))
    assertEquals(Transactor(ds).read(JobRepo.get(done.id)).map(_.status), Some(JobState.Done.wire))

  test("jobs list is newest-first and filterable by status"):
    Transactor(ds).transact(JobRepo.failInterrupted())
    val a = Transactor(ds).transact(JobRepo.create(Ingest.KindIngest, Some(ck), None))
    val all = Transactor(ds).read(JobRepo.list(None, 10))
    assertEquals(all.headOption.map(_.id), Some(a.id))
    assert(Transactor(ds).read(JobRepo.list(Some("queued"), 10)).forall(_.status == "queued"))
    assert(Transactor(ds).read(JobRepo.list(Some("done"), 10)).forall(_.status == "done"))
    assertEquals(Transactor(ds).read(JobRepo.list(Some("nonsense"), 10)), Vector.empty)
    assertEquals(Transactor(ds).read(JobRepo.get("no-such-job")), None)

  private def connectForTest(sql: String): Unit =
    val c = ds.getConnection()
    try {
      val s = c.createStatement();
      try { val _ = s.execute(sql) }
      finally s.close()
    } finally c.close()
