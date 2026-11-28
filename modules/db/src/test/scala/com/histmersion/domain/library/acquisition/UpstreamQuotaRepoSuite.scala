package com.histmersion
package domain.library.acquisition

import com.histmersion.domain.library.acquisition.State
import com.histmersion.domain.library.acquisition.Backoff
import com.histmersion.domain.library.acquisition.Window

import com.histmersion.testsupport.ComparedByValue.given

import hearth.kindlings.optics.syntax.*

import com.histmersion.db.TestDb
import com.histmersion.util.database.Migrate

import com.histmersion.config.AppConfig
import com.histmersion.domain.CoreQuota

import com.augustnagro.magnum.*
import java.time.{Instant, LocalDate}
import com.histmersion.db.DbSuite
import com.histmersion.config.DbConfig
import com.histmersion.domain.automation.ProviderKind
import com.histmersion.lint.{tolerated, LintRule}

/** A metered upstream's budget survives the database unchanged (HISTMERSION_TEST_DB_URL; skipped otherwise): the day,
  * both counts, the block and the last window — and a later save replaces it.
  */
class UpstreamQuotaRepoSuite extends DbSuite:
  private val testDb = TestDb.url
  override def munitIgnore: Boolean = testDb.isEmpty

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private lazy val ds =
    val d = testPool(AppConfig.load().db.copy(url = DbConfig.Url(testDb.get)))
    Migrate.run(d)
    d

  private val Up = "zzquota-test"
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private val UpKind = ProviderKind.Wire(Up)

  override def afterSuite(): Unit =
    if testDb.isDefined then
      val _ = transact(ds)(sql"DELETE FROM upstream_quota WHERE upstream = $Up".update.run())

  test("a budget is read back as it was saved; the next save replaces it"):
    assertEquals(connect(ds)(UpstreamQuotaRepo.load(UpKind)), None)
    val s = State(
      LocalDate.of(2026, 10, 26),
      State.LookupTokens(12),
      State.SearchTokens(34),
      Some(Backoff(Instant.parse("2026-10-26T18:52:11Z"), Backoff.Rationale.Throttled)),
      Some(
        Window(
          Some(Window.Limit(25)),
          Some(Window.Remaining(0)),
          Instant.parse("2026-10-26T18:50:00Z")
        )
      )
    )
    transact(ds)(
      UpstreamQuotaRepo.save(UpKind, s.modify(_.recent).setTo(Vector(Instant.parse("2026-10-26T18:49:59Z"))))
    )
    // the recent call slots are not kept
    assertEquals(connect(ds)(UpstreamQuotaRepo.load(UpKind)), Some(s))
    val next = State.fresh(LocalDate.of(2026, 10, 27)).copy(lookupTokens = State.LookupTokens(1))
    transact(ds)(UpstreamQuotaRepo.save(UpKind, next))
    assertEquals(connect(ds)(UpstreamQuotaRepo.load(UpKind)), Some(next))
