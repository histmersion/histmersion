package com.histmersion.domain.library.acquisition

import com.histmersion.api.automation.Queue
import com.histmersion.api.library.Source
import com.histmersion.domain.{AcquireRecheck, Caller}

import java.time.Instant

/** The acquisition list's checks, the same on the database and in memory: a check replaced with its count kept, the
  * checks of the open items only, counted since a time, and what the corpus holds of the items' DOIs.
  */
abstract class AcquireCheckServiceContract extends AcquisitionStoreSuite:

  private val T0 = Instant.parse("2026-10-20T10:00:00Z")

  private def source(ck: String, doi: Option[String], body: Option[String]) =
    Source(
      com.histmersion.api.library.Citekey(ck),
      com.histmersion.api.library.Title(ck),
      (Nil).map(com.histmersion.api.library.AuthorName.apply),
      (doi).map(com.histmersion.api.library.Doi.apply),
      (Some(2000)).map(com.histmersion.api.catalogue.Year.apply),
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
    val open = s.queues.add(Queue.Kind.Acquire, Queue.QueueAdd("Get 10.5555/zq.a")).id
    val done = s.queues.add(Queue.Kind.Acquire, Queue.QueueAdd("Get 10.5555/zq.b")).id
    s.checks.record(open, T0, "g1", AcquireRecheck.Outcome.NoCopy, "no copy")
    s.checks.record(open, T0.plusSeconds(60), "g2", AcquireRecheck.Outcome.Fetched, "fetched")
    s.checks.record(done, T0.plusSeconds(120), "g2", AcquireRecheck.Outcome.Closed, "held")
    val _ = s.queues.patch(
      com.histmersion.domain.automation.worklists.QueueItemId(done),
      Queue.QueuePatch(status = Some(Queue.Done))
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
    val _ = s.sources.put(source("zqa_one", Some("10.5555/ZQ.A"), Some("# body")), Caller.Local)
    val _ = s.sources.put(source("zqa_two", Some("10.5555/zq.b"), None), Caller.Local)
    val _ = s.sources.put(source("zqa_zwei", Some("10.5555/zq.b"), None), Caller.Local)
    s.sources.setOriginal("zqa_zwei", "f" * 64, Caller.Local)
    assertEquals(
      s.checks.heldByDoi(List(" 10.5555/zq.a", "10.5555/zq.b", "10.5555/zq.none", "")),
      Map(
        "10.5555/zq.a" -> AcquireRecheck.Held("zqa_one", true),
        "10.5555/zq.b" -> AcquireRecheck.Held("zqa_two", false)
      )
    )
    assertEquals(s.checks.heldByDoi(Nil), Map.empty)
  }

class AcquireCheckServiceInMemorySuite extends AcquireCheckServiceContract with AcquisitionStoreSuite.InMemory
class AcquireCheckServiceDbSuite extends AcquireCheckServiceContract with AcquisitionStoreSuite.OnDatabase
