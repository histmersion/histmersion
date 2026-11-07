package com.histmersion.domain.automation.approvals

import com.histmersion.util.database.Jsonb
import com.histmersion.domain.AgentProposals.Proposal
import io.circe.syntax.*
import com.histmersion.api.automation.Approval
import com.histmersion.api.library.Edge
import com.histmersion.api.library.Source

/** What the approvals' lists read of a source proposal's payload is what the proposal says: its evidence and quote. */
class ProposalEvidenceSuite extends munit.FunSuite:

  private val proposals = List(
    Proposal(
      Approval.Kind.StatusChange,
      com.histmersion.domain.library.Citekey("a2000_x"),
      status = Some(Source.Status.Contested),
      evidence = com.histmersion.domain.AgentProposals.Proposal.Evidence("why"),
      quote = Some(com.histmersion.domain.AgentProposals.Proposal.Quote("q"))
    ),
    Proposal(
      Approval.Kind.Edge,
      com.histmersion.domain.library.Citekey("a2000_x"),
      edgeKind = Some(Edge.Kind.Refutes),
      target = Some(com.histmersion.domain.library.Citekey("b2001_y")),
      evidence = com.histmersion.domain.AgentProposals.Proposal.Evidence("e")
    ),
    Proposal(Approval.Kind.Edge, com.histmersion.domain.library.Citekey("a2000_x"), edgeKind = Some(Edge.Kind.Challenges), target = Some(com.histmersion.domain.library.Citekey("b2001_y")))
  )

  test("every proposal's payload reads as its evidence and quote"):
    proposals.foreach { p =>
      val read = Jsonb.read[ProposalEvidence](p.asJson.noSpaces)
      assertEquals(read.flatMap(_.evidence).getOrElse(""), p.evidence)
      assertEquals(read.flatMap(_.quote), p.quote)
    }

  test("a payload that is no JSON reads as nothing"):
    assertEquals(Jsonb.read[ProposalEvidence](com.histmersion.util.database.Jsonb.JsonText("not json")), None)
