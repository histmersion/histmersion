package com.histmersion.db

import com.histmersion.config.AppConfig
import com.histmersion.domain.CoreQuota

import com.augustnagro.magnum.*
import java.time.{Instant, LocalDate}

/** A metered upstream's budget survives the database unchanged (HISTMERSION_TEST_DB_URL; skipped otherwise): the day,
  * both counts, the block and the last window — and a later save replaces it.
  */
class UpstreamQuotaRepoSuite extends com.histmersion.db.DbSuite:
  private val testDb = com.histmersion.db.TestDb.url
  override def munitIgnore: Boolean = testDb.isEmpty

  private lazy val ds =
    val d = testPool(AppConfig.load().db.copy(url = testDb.get))
    Migrate.run(d)
    d

  private val Up = "zzquota-test"

  override def afterSuite(): Unit =
    if testDb.isDefined then
      val _ = transact(ds)(sql"DELETE FROM upstream_quota WHERE upstream = $Up".update.run())

  test("a budget is read back as it was saved; the next save replaces it"):
    assertEquals(UpstreamQuotaRepo.load(ds, Up), None)
    val s = CoreQuota.State(
      LocalDate.of(2026, 10, 26),
      12,
      34,
      Some(CoreQuota.Block(Instant.parse("2026-10-26T18:52:11Z"), "HTTP 429")),
      Some(CoreQuota.Window(Some(25), Some(0), Instant.parse("2026-10-26T18:50:00Z")))
    )
    UpstreamQuotaRepo.save(ds, Up, s.copy(recent = Vector(Instant.parse("2026-10-26T18:49:59Z"))))
    // the recent call slots are not kept
    assertEquals(UpstreamQuotaRepo.load(ds, Up), Some(s))
    val next = CoreQuota.State.fresh(LocalDate.of(2026, 10, 27)).copy(lookupTokens = 1)
    UpstreamQuotaRepo.save(ds, Up, next)
    assertEquals(UpstreamQuotaRepo.load(ds, Up), Some(next))
