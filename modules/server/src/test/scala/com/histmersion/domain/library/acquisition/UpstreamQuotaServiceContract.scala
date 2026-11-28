package com.histmersion
package domain.library.acquisition

import com.histmersion.domain.corequota.State
import com.histmersion.domain.corequota.Backoff
import com.histmersion.domain.corequota.Window

import hearth.kindlings.optics.syntax.*

import com.histmersion.domain.CoreQuota

import java.time.{Instant, LocalDate}
import com.histmersion.util.database.TestPermits.given
import com.histmersion.lint.{tolerated, LintRule}

/** A metered upstream's budget, the same on the database and in memory: read back as it was saved but its recent call
  * slots (kept in memory only), and replaced by the next save.
  */
@tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
abstract class UpstreamQuotaServiceContract extends AcquisitionStoreSuite:

  stest("a budget is read back as it was saved but its call slots; the next save replaces it") { s =>
    assertEquals(s.quotas.load(UpstreamQuotaService.Upstream("zq")), None)
    val st = State(
      LocalDate.of(2026, 10, 26),
      State.LookupTokens(5),
      State.SearchTokens(2),
      Some(Backoff(Instant.parse("2026-10-26T19:00:00Z"), Backoff.Rationale.Throttled)),
      Some(
        Window(
          Some(Window.Limit(25)),
          Some(Window.Remaining(3)),
          Instant.parse("2026-10-26T18:50:00Z")
        )
      )
    )
    s.quotas.save(
      UpstreamQuotaService.Upstream("zq"),
      st.modify(_.recent).setTo(Vector(Instant.parse("2026-10-26T18:49:59Z")))
    )
    assertEquals(s.quotas.load(UpstreamQuotaService.Upstream("zq")), Some(st))
    val next = State(
      LocalDate.of(2026, 10, 27),
      State.LookupTokens(0),
      State.SearchTokens(0),
      None,
      None
    )
    s.quotas.save(UpstreamQuotaService.Upstream("zq"), next)
    assertEquals(s.quotas.load(UpstreamQuotaService.Upstream("zq")), Some(next))
    assertEquals(s.quotas.load(UpstreamQuotaService.Upstream("other")), None)
  }

class UpstreamQuotaServiceInMemorySuite extends UpstreamQuotaServiceContract with AcquisitionStoreSuite.InMemory
class UpstreamQuotaServiceDbSuite extends UpstreamQuotaServiceContract with AcquisitionStoreSuite.OnDatabase
