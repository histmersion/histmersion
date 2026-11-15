package com.histmersion
package domain.library.acquisition

import hearth.kindlings.optics.syntax.*

import com.histmersion.domain.CoreQuota

import java.time.{Instant, LocalDate}

/** A metered upstream's budget, the same on the database and in memory: read back as it was saved but its recent call
  * slots (kept in memory only), and replaced by the next save.
  */
abstract class UpstreamQuotaServiceContract extends AcquisitionStoreSuite:

  stest("a budget is read back as it was saved but its call slots; the next save replaces it") { s =>
    assertEquals(s.quotas.load(UpstreamQuotaService.Upstream("zq")), None)
    val st = CoreQuota.State(
      LocalDate.of(2026, 10, 26),
      CoreQuota.State.LookupTokens(5),
      CoreQuota.State.SearchTokens(2),
      Some(CoreQuota.Block(Instant.parse("2026-10-26T19:00:00Z"), CoreQuota.Block.Rationale.Throttled)),
      Some(
        CoreQuota.Window(
          Some(CoreQuota.Window.Limit(25)),
          Some(CoreQuota.Window.Remaining(3)),
          Instant.parse("2026-10-26T18:50:00Z")
        )
      )
    )
    s.quotas.save(
      UpstreamQuotaService.Upstream("zq"),
      st.modify(_.recent).setTo(Vector(Instant.parse("2026-10-26T18:49:59Z")))
    )
    assertEquals(s.quotas.load(UpstreamQuotaService.Upstream("zq")), Some(st))
    val next = CoreQuota.State(
      LocalDate.of(2026, 10, 27),
      CoreQuota.State.LookupTokens(0),
      CoreQuota.State.SearchTokens(0),
      None,
      None
    )
    s.quotas.save(UpstreamQuotaService.Upstream("zq"), next)
    assertEquals(s.quotas.load(UpstreamQuotaService.Upstream("zq")), Some(next))
    assertEquals(s.quotas.load(UpstreamQuotaService.Upstream("other")), None)
  }

class UpstreamQuotaServiceInMemorySuite extends UpstreamQuotaServiceContract with AcquisitionStoreSuite.InMemory
class UpstreamQuotaServiceDbSuite extends UpstreamQuotaServiceContract with AcquisitionStoreSuite.OnDatabase
