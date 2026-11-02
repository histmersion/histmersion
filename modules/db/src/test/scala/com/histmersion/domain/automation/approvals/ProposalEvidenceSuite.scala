package com.histmersion.domain.automation.approvals

import com.histmersion.util.database.Jsonb
import com.histmersion.domain.AgentProposals.Proposal
import io.circe.syntax.*
import com.histmersion.domain.automation.Approval
import com.histmersion.domain.library.Edge
import com.histmersion.domain.library.Source

/** What the approvals' lists read of a source proposal's payload is what the proposal says: its evidence and quote. */
class ProposalEvidenceSuite extends munit.FunSuite:

  private val proposals = List(
    Proposal(
      Approval.Kind.StatusChange,
      "a2000_x",
      status = Some(Source.Status.Contested),
      evidence = "why",
      quote = Some("q")
    ),
    Proposal(
      Approval.Kind.Edge,
      "a2000_x",
      edgeKind = Some(Edge.Kind.Refutes),
      target = Some("b2001_y"),
      evidence = "e"
    ),
    Proposal(Approval.Kind.Edge, "a2000_x", edgeKind = Some(Edge.Kind.Challenges), target = Some("b2001_y"))
  )

  test("every proposal's payload reads as its evidence and quote"):
    proposals.foreach { p =>
      val read = Jsonb.read[ProposalEvidence](p.asJson.noSpaces)
      assertEquals(read.flatMap(_.evidence).getOrElse(""), p.evidence)
      assertEquals(read.flatMap(_.quote), p.quote)
    }

  test("a payload that is no JSON reads as nothing"):
    assertEquals(Jsonb.read[ProposalEvidence]("not json"), None)
