package com.histmersion.domain.library.acquisition

import com.histmersion.domain.CoreQuota

import java.time.{Instant, LocalDate}

/** A metered upstream's budget, the same on the database and in memory: read back as it was saved but its recent call
  * slots (kept in memory only), and replaced by the next save.
  */
abstract class UpstreamQuotaServiceContract extends AcquisitionStoreSuite:

  stest("a budget is read back as it was saved but its call slots; the next save replaces it") { s =>
    assertEquals(s.quotas.load("zq"), None)
    val st = CoreQuota.State(
      LocalDate.of(2026, 10, 26),
      5,
      2,
      Some(CoreQuota.Block(Instant.parse("2026-10-26T19:00:00Z"), "HTTP 429")),
      Some(CoreQuota.Window(Some(25), Some(3), Instant.parse("2026-10-26T18:50:00Z")))
    )
    s.quotas.save("zq", st.copy(recent = Vector(Instant.parse("2026-10-26T18:49:59Z"))))
    assertEquals(s.quotas.load("zq"), Some(st))
    val next = CoreQuota.State(LocalDate.of(2026, 10, 27), 0, 0, None, None)
    s.quotas.save("zq", next)
    assertEquals(s.quotas.load("zq"), Some(next))
    assertEquals(s.quotas.load("other"), None)
  }

class UpstreamQuotaServiceInMemorySuite extends UpstreamQuotaServiceContract with AcquisitionStoreSuite.InMemory
class UpstreamQuotaServiceDbSuite extends UpstreamQuotaServiceContract with AcquisitionStoreSuite.OnDatabase
