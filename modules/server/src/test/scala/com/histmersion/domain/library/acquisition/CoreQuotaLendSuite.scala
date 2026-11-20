package com.histmersion
package domain.library.acquisition

import com.histmersion.util.problem.Text.sentence
import com.histmersion.plugin.Header

import com.histmersion.testsupport.ComparedByValue.given

import com.histmersion.config.AppConfig
import com.histmersion.domain.CoreQuota
import com.histmersion.domain.library.acquisition.CoreBudget
import com.histmersion.plugin.Plugin

import java.time.Instant
import java.util.concurrent.atomic.AtomicReference
import com.histmersion.plugin.UpstreamQuota
import com.histmersion.util.database.TestPermits.given

/** The server's CORE budget as the plugin SPI lends it (`ctx.quota("core")`): the same budget the host's own CORE calls
  * spend, so a plugin's lookups and the new-source search's searches share one day's tokens and one block.
  */
class CoreQuotaLendSuite extends munit.FunSuite:

  private val t0 = Instant.parse("2026-10-26T18:50:00Z")
  private val limits = CoreBudget.limits(AppConfig.load().acquire.core)

  private def budget(now: AtomicReference[Instant]): CoreBudget = CoreBudget(() => now.get, _ => ())

  test("a plugin's lookup and the host's search spend the ONE budget"):
    val b = budget(AtomicReference(t0))
    val lent = CoreQuotaLend(b, limits)
    assertEquals(lent.take(UpstreamQuota.TakePurpose("lookup")), Right(()))
    assertEquals(b.take(limits, CoreQuota.Purpose.Search), Right(()))
    assertEquals(lent.take(UpstreamQuota.TakePurpose("search")), Right(()))
    assertEquals((b.state.lookupTokens, b.state.searchTokens), (1, 2))

  test("an answer that asks to wait blocks every use until its time, and the plugin can say until when"):
    val now = AtomicReference(t0)
    val b = budget(now)
    val lent = CoreQuotaLend(b, limits)
    assertEquals(lent.waitingUntil, None)
    assert(lent.take(UpstreamQuota.TakePurpose("lookup")).isRight)
    lent.observe(
      UpstreamQuota.ObserveStatus(429),
      Map(
        Header.Name("x-ratelimit-retry-after") -> Header.Value(
          "2026-10-26T18:52:11+0000"
        )
      )
    )
    assertEquals(lent.waitingUntil, Some("2026-10-26T18:52:11Z"))
    assert(
      lent
        .take(UpstreamQuota.TakePurpose("lookup"))
        .left
        .exists(_.sentence.contains("CORE asked to wait until 2026-10-26T18:52:11Z (HTTP 429)"))
    )
    assert(b.unavailable(limits, CoreQuota.Purpose.Search).isDefined, "the host's search is blocked too")
    now.set(t0.plusSeconds(300))
    assertEquals(lent.take(UpstreamQuota.TakePurpose("lookup")), Right(()))

  test("a purpose CORE's budget does not know is refused, and nothing is counted"):
    val b = budget(AtomicReference(t0))
    assert(
      CoreQuotaLend(b, limits)
        .take(UpstreamQuota.TakePurpose("scroll"))
        .left
        .exists(_.sentence.contains("lookup, search"))
    )
    assertEquals(b.state.spent, 0)
