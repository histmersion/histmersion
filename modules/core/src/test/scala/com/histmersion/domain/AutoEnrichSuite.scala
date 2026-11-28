package com.histmersion
package domain

import com.histmersion.testsupport.ComparedByValue.given

import com.histmersion.config.*
import com.histmersion.api.given
import com.histmersion.config.given

import com.histmersion.domain.automation.Run.State as RunState
import com.histmersion.domain.automation.Run
import com.histmersion.lint.{tolerated, LintRule}

@tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
@tolerated(LintRule.UnnamedBoolean, "pending ADR 0027 (a Boolean argument is passed by name)")
class AutoEnrichSuite extends munit.FunSuite:

  test("a fresh source is queued only with agents on AND the switch on"):
    assertEquals(PriorRun.decide(agentsEnabled = true, switchOn = true, PriorRun.FindingCount(0), None, Nil), Right(()))
    assertEquals(
      PriorRun.decide(agentsEnabled = false, switchOn = true, PriorRun.FindingCount(0), None, Nil),
      Left(PriorRun.Skip.Off)
    )
    assertEquals(
      PriorRun.decide(agentsEnabled = true, switchOn = false, PriorRun.FindingCount(0), None, Nil),
      Left(PriorRun.Skip.Off)
    )
    assertEquals(PriorRun.note(PriorRun.Skip.Off), None, "nothing asked for, nothing said")

  test("findings or notes mean enriched; an abstract-as-summary or tags do not count"):
    assert(
      PriorRun
        .decide(true, true, PriorRun.FindingCount(2), None, Nil)
        .left
        .exists(_ == PriorRun.Skip.Enriched(PriorRun.Rationale.HasFindings(PriorRun.FindingCount(2))))
    )
    assert(
      PriorRun
        .decide(true, true, PriorRun.FindingCount(0), Some(PriorRun.Notes("Tin by sea [[x]].")), Nil)
        .left
        .exists(_ == PriorRun.Skip.Enriched(PriorRun.Rationale.HasNotes))
    )
    assertEquals(PriorRun.decide(true, true, PriorRun.FindingCount(0), Some(PriorRun.Notes("   ")), Nil), Right(()))

  test("a pending or finished enrich run covers the source; a failed or cancelled one does not"):
    List(Run.State.Queued, Run.State.Running, Run.State.Paused, Run.State.WaitingApproval, Run.State.Done).foreach { st =>
      assertEquals(
        PriorRun.decide(true, true, PriorRun.FindingCount(0), None, List(PriorRun(PriorRun.Id("r1"), st))),
        Left(PriorRun.Skip.Enriched(PriorRun.Rationale.RunLive(PriorRun.Id("r1"), st)))
      )
    }
    assertEquals(
      PriorRun.decide(
        true,
        true,
        PriorRun.FindingCount(0),
        None,
        List(PriorRun(PriorRun.Id("r1"), Run.State.Failed), PriorRun(PriorRun.Id("r2"), Run.State.Cancelled))
      ),
      Right(())
    )

  test("progress notes"):
    assertEquals(
      PriorRun.queued(Run.Id("90015098-3cd2-4fb0-d696-3f7d28e17f72")),
      "enrichment queued: run 90015098-3cd2-4fb0-d696-3f7d28e17f72"
    )
    assertEquals(
      PriorRun.note(PriorRun.Skip.Enriched(PriorRun.Rationale.HasNotes)),
      Some("enrichment not queued: it already has notes")
    )
    assertEquals(PriorRun.Rationale.HasFindings(PriorRun.FindingCount(2)).said, "it already has 2 findings")
    assertEquals(
      PriorRun.Rationale.RunLive(PriorRun.Id("r1"), Run.State.Done).said,
      s"enrich-source run r1 is ${Run.State.Done.wire}"
    )
