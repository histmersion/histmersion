package com.histmersion
package plugin

import com.histmersion.util.problem.{External, Problem}

class FetchAnswerSuite extends munit.FunSuite:

  private val name = Problem.Upstream.Name("zq-host")
  private val url = Problem.Upstream.Url("https://zq.example/f.pdf")

  private def http(status: Problem.Upstream.Status): Problem =
    Problem.Upstream.Http.here(name, url, status, External.ReplyExcerpt(""))

  private val forbidden = http(Problem.Upstream.Status(403))
  private val unauthorized = http(Problem.Upstream.Status(401))
  private val payment = http(Problem.Upstream.Status(402))
  private val tooMany = http(Problem.Upstream.Status(429))
  private val unavailable = http(Problem.Upstream.Status(503))
  private val missing = http(Problem.Upstream.Status(404))

  private def kind(answer: Fetch): String = answer match
    case Fetch.Found(_)            => "found"
    case Fetch.Miss(absence, _)    => s"miss ${absence.productPrefix}"
    case Fetch.Refused(refusal, _) => s"refused ${refusal.productPrefix}"

  test("a failed request reads as the answer it stands for"):
    assertEquals(
      kind(Fetch.failed(Problem.Upstream.Challenged.here(name, url, Problem.Upstream.Status(403)))),
      "refused BotChallenge"
    )
    assertEquals(kind(Fetch.failed(forbidden)), "refused BotChallenge")
    assertEquals(kind(Fetch.failed(unauthorized)), "refused NoEntitlement")
    assertEquals(kind(Fetch.failed(payment)), "refused NoEntitlement")
    assertEquals(kind(Fetch.failed(tooMany)), "miss RateLimited")
    assertEquals(kind(Fetch.failed(unavailable)), "miss Unanswered")
    assertEquals(kind(Fetch.failed(missing)), "miss NotHeld")
    assertEquals(kind(Fetch.failed(Problem.Upstream.Limited(name, None))), "miss RateLimited")
    assertEquals(kind(Fetch.failed(Problem.Upstream.Offline(name, None))), "miss Unanswered")
    assertEquals(
      kind(Fetch.failed(Problem.Upstream.Unreachable.here(name, url, External.Reported.of("refused")))),
      "miss Unanswered"
    )
    assertEquals(
      kind(Fetch.failed(Problem.NotFound.Entity(Problem.TypeName("Copy"), Problem.Raw("x")))),
      "miss NotHeld"
    )

  test("firstFound without a copy keeps the most telling answer: a refusal, then no answer, else the fetcher's own"):
    assertEquals(
      kind(
        Fetch.firstFound(Iterator(missing, unavailable, forbidden, tooMany).map(Fetch.failed), Fetch.failed(missing))
      ),
      "refused BotChallenge"
    )
    assertEquals(
      kind(Fetch.firstFound(Iterator(missing, tooMany, missing).map(Fetch.failed), Fetch.failed(missing))),
      "miss RateLimited"
    )
    assertEquals(kind(Fetch.firstFound(Iterator.empty, Fetch.failed(unavailable))), "miss Unanswered")
    assertEquals(
      kind(Fetch.firstFound(Iterator(missing).map(Fetch.failed), Fetch.failed(unavailable))),
      "miss Unanswered"
    )
