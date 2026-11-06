package com.histmersion.domain

import com.histmersion.testsupport.ComparedByValue.given

import com.histmersion.config.*
import com.histmersion.api.given
import com.histmersion.config.given

import com.histmersion.domain.automation.Run.State as RunState
import com.histmersion.domain.AutoEnrich.*

class AutoEnrichSuite extends munit.FunSuite:

  test("a fresh source is queued only with agents on AND the switch on"):
    assertEquals(decide(agentsEnabled = true, switchOn = true, com.histmersion.domain.AutoEnrich.FindingCount(0), None, Nil), Right(()))
    assertEquals(decide(agentsEnabled = false, switchOn = true, com.histmersion.domain.AutoEnrich.FindingCount(0), None, Nil), Left(Skip.Off))
    assertEquals(decide(agentsEnabled = true, switchOn = false, com.histmersion.domain.AutoEnrich.FindingCount(0), None, Nil), Left(Skip.Off))
    assertEquals(note(Skip.Off), None, "nothing asked for, nothing said")

  test("findings or notes mean enriched; an abstract-as-summary or tags do not count"):
    assert(decide(true, true, com.histmersion.domain.AutoEnrich.FindingCount(2), None, Nil).left.exists(_ == Skip.Enriched(com.histmersion.domain.AutoEnrich.Skip.Enriched.Why("it already has 2 findings"))))
    assert(
      decide(true, true, com.histmersion.domain.AutoEnrich.FindingCount(0), Some(com.histmersion.domain.AutoEnrich.Notes("Tin by sea [[x]].")), Nil).left.exists(_ == Skip.Enriched(com.histmersion.domain.AutoEnrich.Skip.Enriched.Why("it already has notes")))
    )
    assertEquals(decide(true, true, com.histmersion.domain.AutoEnrich.FindingCount(0), Some(com.histmersion.domain.AutoEnrich.Notes("   ")), Nil), Right(()))

  test("a pending or finished enrich run covers the source; a failed or cancelled one does not"):
    List(com.histmersion.domain.automation.Run.State.Queued, com.histmersion.domain.automation.Run.State.Running, com.histmersion.domain.automation.Run.State.Paused, com.histmersion.domain.automation.Run.State.WaitingApproval, com.histmersion.domain.automation.Run.State.Done).foreach { st =>
      assertEquals(
        decide(true, true, com.histmersion.domain.AutoEnrich.FindingCount(0), None, List(PriorRun(com.histmersion.domain.AutoEnrich.PriorRun.Id("r1"), st))),
        Left(Skip.Enriched(com.histmersion.domain.AutoEnrich.Skip.Enriched.Why(s"enrich-source run r1 is ${st.wire}")))
      )
    }
    assertEquals(
      decide(true, true, com.histmersion.domain.AutoEnrich.FindingCount(0), None, List(PriorRun(com.histmersion.domain.AutoEnrich.PriorRun.Id("r1"), com.histmersion.domain.automation.Run.State.Failed), PriorRun(com.histmersion.domain.AutoEnrich.PriorRun.Id("r2"), com.histmersion.domain.automation.Run.State.Cancelled))),
      Right(())
    )

  test("progress notes"):
    assertEquals(queued(com.histmersion.domain.automation.Run.Id("90015098-3cd2-4fb0-d696-3f7d28e17f72")), "enrichment queued: run 90015098-3cd2-4fb0-d696-3f7d28e17f72")
    assertEquals(note(Skip.Enriched(com.histmersion.domain.AutoEnrich.Skip.Enriched.Why("it already has notes"))), Some("enrichment not queued: it already has notes"))
