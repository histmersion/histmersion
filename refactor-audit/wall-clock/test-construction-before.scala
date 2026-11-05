package com.histmersion.domain.automation.agents

import java.time.{Clock, Instant, ZoneOffset}
import com.histmersion.api.automation.AgentRole

class RunTokensClockSuite extends munit.FunSuite:
  private val epoch = Instant.parse("2001-01-01T00:00:00Z")
  private val agent = AgentDef(
    "probe", "clock test", AgentRole.Probe, Nil, Nil, Nil,
    WebAccess.None, Writes.None, 1, None, Set.empty, None, ""
  )

  test("default issuance and authentication use the supplied fixed clock"):
    val tokens = RunTokens()(using Clock.fixed(epoch, ZoneOffset.UTC))
    val grant = tokens.issue(agent, None, 2000L)
    assertEquals(grant.expiresAt, epoch.toEpochMilli + 2000L)
    assertEquals(tokens.check(grant.token), Some(grant))
    assertEquals(tokens.bearer(s"Bearer ${grant.token}"), Some(grant))
    assertEquals(tokens.check(grant.token, epoch.toEpochMilli + 2000L), None)

  test("explicit time overrides the injected clock and expiration revokes the token"):
    val tokens = RunTokens()(using Clock.fixed(epoch, ZoneOffset.UTC))
    val now = epoch.toEpochMilli + 5000L
    val grant = tokens.issue(agent, None, 1000L, now)
    assertEquals(grant.expiresAt, now + 1000L)
    assertEquals(tokens.bearer(s"Bearer ${grant.token}", now + 999L), Some(grant))
    assertEquals(tokens.bearer(s"Bearer ${grant.token}", now + 1000L), None)
    assertEquals(tokens.check(grant.token, now), None)

  test("independent token stores retain independent clocks"):
    val early = RunTokens()(using Clock.fixed(epoch, ZoneOffset.UTC))
    val late = RunTokens()(using Clock.fixed(epoch.plusSeconds(60L), ZoneOffset.UTC))
    val first = early.issue(agent, None, 1000L)
    val second = late.issue(agent, None, 1000L)
    assertEquals(second.expiresAt - first.expiresAt, 60000L)
    assertEquals(early.check(first.token), Some(first))
    assertEquals(late.check(second.token), Some(second))

  test("token storage cannot be constructed without a supplied clock"):
    assert(!scala.compiletime.testing.typeChecks("com.histmersion.domain.automation.agents.RunTokens()"))
