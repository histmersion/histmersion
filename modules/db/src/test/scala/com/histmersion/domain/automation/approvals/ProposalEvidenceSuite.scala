package com.histmersion
package domain.automation.approvals
import com.histmersion.util.json.RawJson.*

import com.histmersion.testsupport.ComparedByValue.given

import com.histmersion.util.database.Jsonb
import com.histmersion.domain.AgentProposals.Proposal
import com.histmersion.api.automation.Approval
import com.histmersion.api.library.Edge
import com.histmersion.api.library.Source
import com.histmersion.domain.library.Citekey

/** What the approvals' lists read of a source proposal's payload is what the proposal says: its evidence and quote. */
class ProposalEvidenceSuite extends munit.FunSuite:

  private val proposals = List(
    Proposal(
      Approval.Kind.StatusChange,
      Citekey("a2000_x"),
      status = Some(Source.Status.Contested),
      evidence = Proposal.Evidence("why"),
      quote = Some(Proposal.Quote("q"))
    ),
    Proposal(
      Approval.Kind.Edge,
      Citekey("a2000_x"),
      edgeKind = Some(Edge.Kind.Refutes),
      target = Some(Citekey("b2001_y")),
      evidence = Proposal.Evidence("e")
    ),
    Proposal(
      Approval.Kind.Edge,
      Citekey("a2000_x"),
      edgeKind = Some(Edge.Kind.Challenges),
      target = Some(Citekey("b2001_y"))
    )
  )

  test("every proposal's payload reads as its evidence and quote"):
    proposals.foreach { p =>
      val read = Jsonb.read[ProposalEvidence](Jsonb.JsonText(p.asJson.noSpaces))
      assertEquals(read.flatMap(_.evidence).fold("")(_.value), p.evidence.value)
      assertEquals(read.flatMap(_.quote).map(_.value), p.quote.map(_.value))
    }

  test("a payload that is no JSON reads as nothing"):
    assertEquals(Jsonb.read[ProposalEvidence](Jsonb.JsonText("not json")), None)
