package com.histmersion
package domain.personal

import com.histmersion.domain.personal.seen.SeenBoard

import java.time.Instant
import com.histmersion.util.database.TestPermits.given
import com.histmersion.lint.{LintRule, tolerated}

/** How far a person has seen each window's news, in memory and in the database. */
@tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
class SeenServiceContractSuite extends PersonalServiceContract("seen"):

  both("a seen mark only moves forward, and no time means now") { w =>
    val log = SeenBoard("zz-log")
    val seenAt = Instant.parse("2026-10-30T12:00:00.123456Z")
    val m1 = w.seen.mark(me, log, Some(seenAt))
    assertEquals(m1, MarkTime.at(seenAt))
    assertEquals(w.seen.mark(me, log, Some(Instant.parse("2000-01-01T00:00:00Z"))), m1)
    val m2 = w.seen.mark(me, log, None)
    assert(m2.value.isAfter(m1.value), s"$m2 <= $m1")
    assertEquals(w.seen.marks(me).get(log), Some(m2))
  }
