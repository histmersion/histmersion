package com.histmersion
package domain.library.acquisition

import com.histmersion.domain.acquirerecheck.LastCheck

import com.histmersion.domain.{AcquireRecheck, Actor}
import com.histmersion.domain.library.Citekey
import com.histmersion.util.problem.{Problem, Problems}

import java.time.Instant

class AcquireVerdictSuite extends munit.FunSuite:

  private val now = Instant.parse("2026-11-22T12:00:00Z")
  private val gen = LastCheck.Generation("g1")
  private val settings = AcquireVerdict.Settings(
    domain.acquirerecheck.Cadence.EveryDays(7),
    domain.acquirerecheck.Cadence.RetryHours(6),
    AcquireVerdict.Failures(5),
    AcquireVerdict.Settings.Days(14)
  )
  private val withId = AcquireVerdict.Identifier.Named

  private def answer(said: Problem.NotFound.Said): Problem.NotFound.Answer =
    Problem.NotFound.Answer(
      Problem.NotFound.Fetcher("zq"),
      said,
      Problem.Upstream.Offline(Problem.Upstream.Name("zq"), None)
    )

  private val notHeld = answer(Problem.NotFound.Said.Miss(Problem.NotFound.Absence.NotHeld))
  private val unanswered = answer(Problem.NotFound.Said.Miss(Problem.NotFound.Absence.Unanswered))

  private def attempt(
      hoursAgo: Long,
      answers: Vector[Problem.NotFound.Answer],
      outcome: AcquireAttempt.Outcome = AcquireAttempt.Outcome.NoCopy,
      oa: Option[AcquireAttempt.Oa] = None,
      generation: LastCheck.Generation = gen
  ): AcquireAttempt =
    AcquireAttempt(
      now.minusSeconds(hoursAgo * 3600L),
      AcquireAttempt.Trigger.Recheck,
      generation,
      outcome,
      answers,
      oa
    )

  private def verdict(attempts: AcquireAttempt*): AcquireVerdict =
    AcquireVerdict.of(withId, attempts.toList, gen, settings, now)

  test("an item that names no identifier is the person's, whatever was tried"):
    assertEquals(
      AcquireVerdict.of(AcquireVerdict.Identifier.Missing, List(attempt(1, Vector(notHeld))), gen, settings, now),
      AcquireVerdict.NoIdentifier
    )

  test("never checked, or checked only under another fetcher generation (or before attempts were typed): due now"):
    assertEquals(verdict(), AcquireVerdict.Unchecked(now))
    assertEquals(
      verdict(attempt(1, Vector(notHeld), generation = LastCheck.Generation("old"))),
      AcquireVerdict.Unchecked(now)
    )
    assertEquals(
      verdict(
        attempt(
          1,
          Vector.empty,
          AcquireAttempt.Outcome.Legacy(
            AcquireAttempt.Outcome.LegacyNote("checked: no legal copy"),
            AcquireAttempt.Tries(3)
          ),
          generation = LastCheck.Generation("legacy")
        )
      ),
      AcquireVerdict.Unchecked(now)
    )

  test("every fetcher looked and had none, with no open-access location known: no free legal copy, re-checked later"):
    assertEquals(
      verdict(attempt(2, Vector(notHeld, notHeld))),
      AcquireVerdict.NoFreeCopy(now.plusSeconds(7L * 86400 - 7200))
    )

  test("a bot challenge, or a host known to refuse scripts, is a download in the browser"):
    val challenged = answer(Problem.NotFound.Said.Refused(Problem.NotFound.Refusal.BotChallenge))
    assertEquals(verdict(attempt(1, Vector(notHeld, challenged))), AcquireVerdict.ManualDownload(None))
    val oa = AcquireAttempt.Oa(
      Some(AcquireAttempt.Oa.OaStatus("gold")),
      Some(AcquireAttempt.Oa.OaUrl("https://pub.example/a")),
      None,
      AcquireAttempt.Oa.Scripts.Refused
    )
    assertEquals(verdict(attempt(1, Vector(unanswered), oa = Some(oa))), AcquireVerdict.ManualDownload(oa.url))

  test("an open-access location no fetcher could take is a download in the browser too"):
    val oa = AcquireAttempt.Oa(
      Some(AcquireAttempt.Oa.OaStatus("green")),
      Some(AcquireAttempt.Oa.OaUrl("https://repo.example/a.pdf")),
      None,
      AcquireAttempt.Oa.Scripts.Allowed
    )
    assertEquals(verdict(attempt(1, Vector(notHeld), oa = Some(oa))), AcquireVerdict.ManualDownload(oa.url))

  test("a lending-only copy is borrowed"):
    val lent = answer(Problem.NotFound.Said.Refused(Problem.NotFound.Refusal.LendingOnly))
    assertEquals(verdict(attempt(1, Vector(notHeld, lent))), AcquireVerdict.Borrow)

  test("no answer: the agents retry until the give-up threshold, then it is the person's"):
    val one = verdict(attempt(1, Vector(notHeld, unanswered)))
    assertEquals(
      one,
      AcquireVerdict.Retrying(
        AcquireVerdict.Failures(1),
        now.plusSeconds(5L * 3600),
        Problems.from(Vector(unanswered.why))
      )
    )
    assertEquals(one.actor, Actor.Agents)
    val four = verdict((1 to 4).map(h => attempt(h.toLong, Vector(unanswered)))*)
    assert(four.isInstanceOf[AcquireVerdict.Retrying], four)
    val five = verdict((1 to 5).map(h => attempt(h.toLong, Vector(unanswered)))*)
    assertEquals(
      five,
      AcquireVerdict.Stuck(
        AcquireVerdict.Failures(5),
        now.minusSeconds(5L * 3600),
        Problems.from(Vector(unanswered.why))
      )
    )
    assertEquals(five.actor, Actor.Person)

  test("no answer for give-up-days since the first of the streak is the person's even below the count"):
    val old = verdict(attempt(1, Vector(unanswered)), attempt(14L * 24, Vector(unanswered)))
    assertEquals(
      old,
      AcquireVerdict.Stuck(
        AcquireVerdict.Failures(2),
        now.minusSeconds(14L * 86400),
        Problems.from(Vector(unanswered.why))
      )
    )
    val younger = verdict(attempt(1, Vector(unanswered)), attempt(14L * 24 - 1, Vector(unanswered)))
    assert(younger.isInstanceOf[AcquireVerdict.Retrying], younger)

  test("the streak ends at the last check that got an answer"):
    val v = verdict(attempt(1, Vector(unanswered)), attempt(2, Vector(notHeld)), attempt(3, Vector(unanswered)))
    assertEquals(
      v,
      AcquireVerdict.Retrying(
        AcquireVerdict.Failures(1),
        now.plusSeconds(5L * 3600),
        Problems.from(Vector(unanswered.why))
      )
    )

  test("a check where no fetcher looked (all unconfigured or reading other identifiers) counts as no answer"):
    val unset = answer(Problem.NotFound.Said.Miss(Problem.NotFound.Absence.Unconfigured))
    assert(verdict(attempt(1, Vector(unset))).isInstanceOf[AcquireVerdict.Retrying])
    assert(verdict(attempt(1, Vector.empty)).isInstanceOf[AcquireVerdict.Retrying])

  test("a failed check carries its problem; a copy found is converting, the agents' to finish"):
    val why = Problem.Upstream.Offline(Problem.Upstream.Name("converter"), None)
    assertEquals(
      verdict(attempt(1, Vector.empty, AcquireAttempt.Outcome.Failed(Problems.of(why)))),
      AcquireVerdict.Retrying(AcquireVerdict.Failures(1), now.plusSeconds(5L * 3600), Some(Problems.of(why)))
    )
    val fetched =
      verdict(attempt(1, Vector.empty, AcquireAttempt.Outcome.Fetched(AcquireAttempt.Outcome.Source("unpaywall"))))
    assertEquals(fetched, AcquireVerdict.Converting(Some(AcquireAttempt.Outcome.Source("unpaywall"))))
    assertEquals(
      verdict(attempt(1, Vector.empty, AcquireAttempt.Outcome.Closed(Citekey("zq2020")))).actor,
      Actor.Agents
    )

  test("every answer a fetcher can give reaches a verdict, and the same attempts always give the same one"):
    val all = Problem.NotFound.Absence.values.toList.map(Problem.NotFound.Said.Miss(_)) ++
      Problem.NotFound.Refusal.values.toList.map(Problem.NotFound.Said.Refused(_))
    for said <- all; other <- all do
      val attempts = List(attempt(1, Vector(answer(said), answer(other))), attempt(30, Vector(answer(other))))
      val v = AcquireVerdict.of(withId, attempts, gen, settings, now)
      assertEquals(AcquireVerdict.of(withId, attempts, gen, settings, now), v)
      assert(v != AcquireVerdict.NoIdentifier && !v.isInstanceOf[AcquireVerdict.Unchecked], clues(said, other, v))

  test("tries count the legacy checks a migrated attempt carries"):
    val legacy = attempt(
      100,
      Vector.empty,
      AcquireAttempt.Outcome.Legacy(AcquireAttempt.Outcome.LegacyNote("x"), AcquireAttempt.Tries(4)),
      generation = LastCheck.Generation("legacy")
    )
    assertEquals(AcquireAttempt.Tries.of(List(attempt(1, Vector(notHeld)), legacy)), AcquireAttempt.Tries(5))

  test("only the newest attempts are kept"):
    assertEquals(AcquireAttempt.Kept(2).keep(List(3, 2, 1)), List(3, 2))
    assertEquals(AcquireAttempt.Kept(0).keep(List(3, 2, 1)), List(3))
