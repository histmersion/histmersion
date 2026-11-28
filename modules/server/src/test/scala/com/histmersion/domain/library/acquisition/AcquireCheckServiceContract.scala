package com.histmersion
package domain.library.acquisition

import com.histmersion.testsupport.ComparedByValue.given

import com.histmersion.Sha256
import com.histmersion.api.automation.Queue
import com.histmersion.api.library.Source
import com.histmersion.domain.{AcquireRecheck, Caller}
import com.histmersion.domain.automation.worklists.QueueItemId
import com.histmersion.util.problem.{Problem, Problems}
import io.scalaland.chimney.dsl.*

import java.time.Instant
import com.histmersion.api.library.Citekey
import com.histmersion.api.library.Doi
import com.histmersion.api.library.Title
import com.histmersion.api.catalogue.Year
import com.histmersion.util.database.TestPermits.given
import com.histmersion.util.database.TestCreators.given
import com.histmersion.lint.{tolerated, LintRule}

@tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
@tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
abstract class AcquireCheckServiceContract extends AcquisitionStoreSuite:

  private val T0 = Instant.parse("2026-10-20T10:00:00Z")
  private val Kept = AcquireAttempt.Kept(3)

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def source(ck: Citekey, doi: Option[Doi], body: Option[Source.OcrMarkdown]) =
    Source(
      ck,
      Title(ck.value),
      Nil,
      doi,
      Some(Year(2000)),
      None,
      Source.Status.Current,
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

  private val challenged = Problem.NotFound.Answer(
    Problem.NotFound.Fetcher("landing"),
    Problem.NotFound.Said.Refused(Problem.NotFound.Refusal.BotChallenge),
    Problem.Upstream.Challenged(
      Problem.Upstream.Name("landing"),
      Problem.Upstream.Url("https://landing.example/f"),
      Problem.Upstream.Status(403),
      Vector.empty
    )
  )

  private def attempt(
      seconds: Long,
      outcome: AcquireAttempt.Outcome,
      answers: Vector[Problem.NotFound.Answer] = Vector.empty
  ) =
    AcquireAttempt(
      T0.plusSeconds(seconds),
      AcquireAttempt.Trigger.Recheck,
      AcquireRecheck.LastCheck.Generation("g1"),
      outcome,
      answers,
      Some(
        AcquireAttempt.Oa(
          Some(AcquireAttempt.Oa.OaStatus("gold")),
          Some(AcquireAttempt.Oa.OaUrl("https://pub.example/a")),
          None,
          AcquireAttempt.Oa.Scripts.Refused
        )
      )
    )

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def item(s: AcquisitionStores, text: String): AcquireAttemptRow.Id =
    s.queues
      .add(
        Queue.Kind.Acquire,
        Queue.QueueAdd(Queue.QueueItem.Text(text)),
        domain.Creator.New.Manual(domain.Creator.Surface.Cli)
      )
      .id
      .transformInto[AcquireAttemptRow.Id]

  stest("every attempt is kept with each fetcher's typed answer, newest first, the newest `kept` only") { s =>
    val open = item(s, "Get 10.5555/zq.a")
    val done = item(s, "Get 10.5555/zq.b")
    val first = attempt(0, AcquireAttempt.Outcome.NoCopy, Vector(challenged))
    val failed =
      attempt(
        60,
        AcquireAttempt.Outcome.Failed(Problems.of(Problem.Upstream.Offline(Problem.Upstream.Name("zq"), None)))
      )
    val third = attempt(120, AcquireAttempt.Outcome.NoCopy)
    val fetched = attempt(180, AcquireAttempt.Outcome.Fetched(AcquireAttempt.Outcome.Source("unpaywall")))
    List(first, failed, third, fetched).foreach(s.attempts.record(open, _, Kept))
    s.attempts.record(
      done,
      attempt(240, AcquireAttempt.Outcome.Closed(domain.library.Citekey("zqa_one"))),
      Kept
    )
    val _ = s.queues.patch(QueueItemId(done.value), Queue.QueuePatch(status = Some(Queue.QueueItem.Status(Queue.Done))))
    val got = s.attempts.of(List(open, done))
    assertEquals(got(open), List(fetched, third, failed))
    assertEquals(s.attempts.of(Nil), Map.empty)
    assertEquals(s.attempts.open.keySet, Set(open))
    assertEquals(
      s.attempts.countSince(T0.plusSeconds(90)),
      Map(
        AcquireRecheck.Outcome.NoCopy.wire -> 1,
        AcquireRecheck.Outcome.Fetched.wire -> 1,
        AcquireRecheck.Outcome.Closed.wire -> 1
      )
    )
  }

  stest("a typed answer, a failure and the open-access facts read back as they were written") { s =>
    val one = item(s, "Get 10.5555/zq.c")
    val written = attempt(0, AcquireAttempt.Outcome.NoCopy, Vector(challenged))
    s.attempts.record(one, written, Kept)
    assertEquals(s.attempts.of(List(one))(one), List(written))
  }

  stest("the sources holding the items' DOIs, the first citekey, and whether it has its original") { s =>
    val _ = s.sources.put(
      source(Citekey("zqa_one"), Some(Doi("10.5555/ZQ.A")), Some(Source.OcrMarkdown("# body"))),
      Caller.Local
    )
    val _ = s.sources.put(source(Citekey("zqa_two"), Some(Doi("10.5555/zq.b")), None), Caller.Local)
    val _ = s.sources.put(source(Citekey("zqa_zwei"), Some(Doi("10.5555/zq.b")), None), Caller.Local)
    s.sources.setOriginal(
      domain.library.Citekey("zqa_zwei"),
      Sha256.applyUnsafe("f" * 64),
      Caller.Local
    )
    assertEquals(
      s.attempts.heldByDoi(
        List(
          domain.library.Doi.assume(" 10.5555/zq.a"),
          domain.library.Doi("10.5555/zq.b"),
          domain.library.Doi("10.5555/zq.none"),
          domain.library.Doi.assume("")
        )
      ),
      Map(
        "10.5555/zq.a" -> AcquireRecheck
          .Held(domain.library.Citekey("zqa_one"), AcquireRecheck.Held.HasOriginal.WithOriginal),
        "10.5555/zq.b" -> AcquireRecheck.Held(
          domain.library.Citekey("zqa_two"),
          AcquireRecheck.Held.HasOriginal.WithoutOriginal
        )
      )
    )
    assertEquals(s.attempts.heldByDoi(Nil), Map.empty)
  }

class AcquireCheckServiceInMemorySuite extends AcquireCheckServiceContract with AcquisitionStoreSuite.InMemory
class AcquireCheckServiceDbSuite extends AcquireCheckServiceContract with AcquisitionStoreSuite.OnDatabase
