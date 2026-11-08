package com.histmersion
package domain

import com.histmersion.testsupport.ComparedByValue.given

import com.histmersion.config.*
import com.histmersion.api.given
import com.histmersion.config.given

import com.histmersion.domain.automation.Run.State as RunState
import com.histmersion.domain.AutoEnrich.*
import com.histmersion.domain.automation.Run

class AutoEnrichSuite extends munit.FunSuite:

  test("a fresh source is queued only with agents on AND the switch on"):
    assertEquals(decide(agentsEnabled = true, switchOn = true, FindingCount(0), None, Nil), Right(()))
    assertEquals(decide(agentsEnabled = false, switchOn = true, FindingCount(0), None, Nil), Left(Skip.Off))
    assertEquals(decide(agentsEnabled = true, switchOn = false, FindingCount(0), None, Nil), Left(Skip.Off))
    assertEquals(note(Skip.Off), None, "nothing asked for, nothing said")

  test("findings or notes mean enriched; an abstract-as-summary or tags do not count"):
    assert(
      decide(true, true, FindingCount(2), None, Nil).left
        .exists(_ == Skip.Enriched(Skip.Enriched.Why("it already has 2 findings")))
    )
    assert(
      decide(true, true, FindingCount(0), Some(Notes("Tin by sea [[x]].")), Nil).left
        .exists(_ == Skip.Enriched(Skip.Enriched.Why("it already has notes")))
    )
    assertEquals(decide(true, true, FindingCount(0), Some(Notes("   ")), Nil), Right(()))

  test("a pending or finished enrich run covers the source; a failed or cancelled one does not"):
    List(Run.State.Queued, Run.State.Running, Run.State.Paused, Run.State.WaitingApproval, Run.State.Done).foreach { st =>
      assertEquals(
        decide(true, true, FindingCount(0), None, List(PriorRun(PriorRun.Id("r1"), st))),
        Left(Skip.Enriched(Skip.Enriched.Why(s"enrich-source run r1 is ${st.wire}")))
      )
    }
    assertEquals(
      decide(
        true,
        true,
        FindingCount(0),
        None,
        List(PriorRun(PriorRun.Id("r1"), Run.State.Failed), PriorRun(PriorRun.Id("r2"), Run.State.Cancelled))
      ),
      Right(())
    )

  test("progress notes"):
    assertEquals(
      queued(Run.Id("90015098-3cd2-4fb0-d696-3f7d28e17f72")),
      "enrichment queued: run 90015098-3cd2-4fb0-d696-3f7d28e17f72"
    )
    assertEquals(
      note(Skip.Enriched(Skip.Enriched.Why("it already has notes"))),
      Some("enrichment not queued: it already has notes")
    )
