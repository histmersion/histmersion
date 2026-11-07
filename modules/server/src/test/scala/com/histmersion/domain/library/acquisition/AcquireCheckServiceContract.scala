package com.histmersion.domain.library.acquisition

import com.histmersion.testsupport.ComparedByValue.given

import com.histmersion.Sha256
import com.histmersion.api.automation.Queue
import com.histmersion.api.library.Source
import com.histmersion.domain.{AcquireRecheck, Caller}
import com.histmersion.domain.automation.worklists.QueueItemId

import java.time.Instant

/** The acquisition list's checks, the same on the database and in memory: a check replaced with its count kept, the
  * checks of the open items only, counted since a time, and what the corpus holds of the items' DOIs.
  */
abstract class AcquireCheckServiceContract extends AcquisitionStoreSuite:

  private val T0 = Instant.parse("2026-10-20T10:00:00Z")

  private def source(ck: com.histmersion.api.library.Citekey, doi: Option[com.histmersion.api.library.Doi], body: Option[Source.OcrMarkdown]) =
    Source(
      ck,
      com.histmersion.api.library.Title(ck.value),
      Nil,
      doi,
      Some(com.histmersion.api.catalogue.Year(2000)),
      None,
      com.histmersion.api.library.Source.Status.Current,
      Nil,
      Nil,
      Nil,
      None,
      None,
      None,
      None,
      None,
      None
    )
      .copy(ocrMarkdown = body)

  stest("a check is replaced with its count kept; the open items' checks; counted since a time") { s =>
    val open = AcquireCheckRow.Item(s.queues.add(Queue.Kind.Acquire, Queue.QueueAdd(Queue.QueueItem.Text("Get 10.5555/zq.a"))).id.value)
    val done = AcquireCheckRow.Item(s.queues.add(Queue.Kind.Acquire, Queue.QueueAdd(Queue.QueueItem.Text("Get 10.5555/zq.b"))).id.value)
    s.checks.record(open, T0, com.histmersion.domain.AcquireRecheck.LastCheck.Generation("g1"), AcquireRecheck.Outcome.NoCopy, com.histmersion.domain.library.acquisition.AcquireCheckRow.Reason("no copy"))
    s.checks.record(open, T0.plusSeconds(60), com.histmersion.domain.AcquireRecheck.LastCheck.Generation("g2"), AcquireRecheck.Outcome.Fetched, com.histmersion.domain.library.acquisition.AcquireCheckRow.Reason("fetched"))
    s.checks.record(done, T0.plusSeconds(120), com.histmersion.domain.AcquireRecheck.LastCheck.Generation("g2"), AcquireRecheck.Outcome.Closed, com.histmersion.domain.library.acquisition.AcquireCheckRow.Reason("held"))
    val _ = s.queues.patch(
      QueueItemId(done.value),
      Queue.QueuePatch(status = Some(Queue.QueueItem.Status(Queue.Done)))
    )
    val got = s.checks.of(List(open, done))
    assertEquals(got(open).checks, 2)
    assertEquals(got(open).generation, "g2")
    assertEquals(got(open).outcome, AcquireRecheck.Outcome.Fetched.wire)
    assertEquals(got(open).at, T0.plusSeconds(60))
    assertEquals(s.checks.of(Nil), Map.empty)
    assertEquals(s.checks.open.keySet, Set(open))
    assertEquals(
      s.checks.countSince(T0.plusSeconds(30)),
      Map(AcquireRecheck.Outcome.Fetched.wire -> 1, AcquireRecheck.Outcome.Closed.wire -> 1)
    )
  }

  stest("the sources holding the items' DOIs, the first citekey, and whether it has its original") { s =>
    val _ = s.sources.put(source(com.histmersion.api.library.Citekey("zqa_one"), Some(com.histmersion.api.library.Doi("10.5555/ZQ.A")), Some(Source.OcrMarkdown("# body"))), Caller.Local)
    val _ = s.sources.put(source(com.histmersion.api.library.Citekey("zqa_two"), Some(com.histmersion.api.library.Doi("10.5555/zq.b")), None), Caller.Local)
    val _ = s.sources.put(source(com.histmersion.api.library.Citekey("zqa_zwei"), Some(com.histmersion.api.library.Doi("10.5555/zq.b")), None), Caller.Local)
    s.sources.setOriginal(com.histmersion.domain.library.Citekey("zqa_zwei"), Sha256.applyUnsafe("f" * 64), Caller.Local)
    assertEquals(
      s.checks.heldByDoi(List(com.histmersion.domain.library.Doi(" 10.5555/zq.a"), com.histmersion.domain.library.Doi("10.5555/zq.b"), com.histmersion.domain.library.Doi("10.5555/zq.none"), com.histmersion.domain.library.Doi(""))),
      Map(
        "10.5555/zq.a" -> AcquireRecheck.Held(com.histmersion.domain.library.Citekey("zqa_one"), com.histmersion.domain.AcquireRecheck.Held.HasOriginal(true)),
        "10.5555/zq.b" -> AcquireRecheck.Held(com.histmersion.domain.library.Citekey("zqa_two"), com.histmersion.domain.AcquireRecheck.Held.HasOriginal(false))
      )
    )
    assertEquals(s.checks.heldByDoi(Nil), Map.empty)
  }

class AcquireCheckServiceInMemorySuite extends AcquireCheckServiceContract with AcquisitionStoreSuite.InMemory
class AcquireCheckServiceDbSuite extends AcquireCheckServiceContract with AcquisitionStoreSuite.OnDatabase
