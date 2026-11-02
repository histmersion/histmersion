package com.histmersion.domain

import com.histmersion.config.*
import com.histmersion.api.given
import com.histmersion.config.given

import com.histmersion.api.automation.Agents.RunState
import com.histmersion.domain.AutoEnrich.*

class AutoEnrichSuite extends munit.FunSuite:

  test("a fresh source is queued only with agents on AND the switch on"):
    assertEquals(decide(agentsEnabled = true, switchOn = true, 0, None, Nil), Right(()))
    assertEquals(decide(agentsEnabled = false, switchOn = true, 0, None, Nil), Left(Skip.Off))
    assertEquals(decide(agentsEnabled = true, switchOn = false, 0, None, Nil), Left(Skip.Off))
    assertEquals(note(Skip.Off), None, "nothing asked for, nothing said")

  test("findings or notes mean enriched; an abstract-as-summary or tags do not count"):
    assert(decide(true, true, 2, None, Nil).left.exists(_ == Skip.Enriched("it already has 2 findings")))
    assert(
      decide(true, true, 0, Some("Tin by sea [[x]]."), Nil).left.exists(_ == Skip.Enriched("it already has notes"))
    )
    assertEquals(decide(true, true, 0, Some("   "), Nil), Right(()))

  test("a pending or finished enrich run covers the source; a failed or cancelled one does not"):
    List(RunState.Queued, RunState.Running, RunState.Paused, RunState.WaitingApproval, RunState.Done).foreach { st =>
      assertEquals(
        decide(true, true, 0, None, List(PriorRun("r1", st))),
        Left(Skip.Enriched(s"enrich-source run r1 is ${st.wire}"))
      )
    }
    assertEquals(
      decide(true, true, 0, None, List(PriorRun("r1", RunState.Failed), PriorRun("r2", RunState.Cancelled))),
      Right(())
    )

  test("progress notes"):
    assertEquals(queued("abc"), "enrichment queued: run abc")
    assertEquals(note(Skip.Enriched("it already has notes")), Some("enrichment not queued: it already has notes"))
