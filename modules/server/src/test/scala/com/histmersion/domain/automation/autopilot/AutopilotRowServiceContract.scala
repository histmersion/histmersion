package com.histmersion.domain.automation.autopilot

import com.histmersion.domain.automation.{EngineStores, EngineStoreSuite}

import java.time.Instant

/** The contract the autopilot's row keeps: made off on first use; switched only when it changes; its times, strategy,
  * digest day and drain as written.
  */
abstract class AutopilotRowServiceContract extends EngineStoreSuite:
  private val t = Instant.parse("2026-10-20T08:30:00Z")
  private def a(s: EngineStores) = s.autopilot

  stest("made off on first use; a switch changes it once; turning it on forgets the last ensure") { s =>
    val r = a(s).row()
    assertEquals((r.enabled, r.changedBy, r.paused, r.lastEnsureAt), (false, "", Nil, None))
    assert(a(s).set(enabled = true, "p", Nil))
    assert(!a(s).set(enabled = true, "q", Nil), "already on")
    assertEquals(a(s).row().changedBy, "p")
    a(s).ensured(t)
    assertEquals(a(s).row().lastEnsureAt, Some("2026-10-20T08:30:00Z"))
    assert(a(s).set(enabled = false, "q", List("seed:x", "watch:x")))
    assertEquals(
      (a(s).row().enabled, a(s).row().paused, a(s).row().lastEnsureAt),
      (false, List("seed:x", "watch:x"), None)
    )
    a(s).unpaused("seed:x")
    assertEquals(a(s).row().paused, List("watch:x"))
    a(s).ensured(t)
    a(s).ensureSoon()
    assertEquals(a(s).row().lastEnsureAt, None)
    a(s).backedUp(t)
    a(s).linted(t.plusSeconds(60))
    assertEquals(
      (a(s).row().lastBackupAt, a(s).row().lastLintAt),
      (Some("2026-10-20T08:30:00Z"), Some("2026-10-20T08:31:00Z"))
    )
  }

  stest("a person's strategy is kept with who chose it; cleared, the config's is back") { s =>
    assertEquals(a(s).strategy(), None)
    a(s).setStrategy(Some("type-balanced"), "p")
    val Some((name, by, at)) = a(s).strategy(): @unchecked
    assertEquals((name, by), ("type-balanced", "p"))
    assert(at.matches("\\d{4}-\\d\\d-\\d\\dT\\d\\d:\\d\\d:\\d\\dZ"), at)
    a(s).setStrategy(None, "q")
    assertEquals(a(s).strategy(), None)
  }

  stest("the digest's day as written") { s =>
    assertEquals(a(s).lastDigest(), None)
    a(s).digested(t)
    assertEquals(a(s).lastDigest(), Some("2026-10-20T08:30:00Z"))
  }

  stest("a drain keeps its start and who started it; drained is recorded only while draining; lifted, all goes") { s =>
    a(s).drained(Some(t))
    assertEquals(a(s).drain(), None)
    assert(!a(s).setDrain(on = false, "p"), "not draining")
    assert(a(s).setDrain(on = true, "p"))
    val Some((since, by, at)) = a(s).drain(): @unchecked
    assertEquals((by, at), ("p", None))
    assert(!a(s).setDrain(on = true, "q"), "already draining")
    a(s).drained(Some(t))
    assertEquals(a(s).drain(), Some((since, "p", Some("2026-10-20T08:30:00Z"))))
    a(s).drained(None)
    assertEquals(a(s).drain().flatMap(_._3), None)
    assert(a(s).setDrain(on = false, "q"))
    assertEquals(a(s).drain(), None)
  }
