package com.histmersion.domain.automation.approvals

import com.histmersion.util.database.Jsonb
import com.histmersion.domain.AgentProposals.Proposal
import com.histmersion.values.{ApprovalKind, EdgeKind, SourceStatus}
import io.circe.syntax.*

/** What the approvals' lists read of a source proposal's payload is what the proposal says: its evidence and quote. */
class ProposalEvidenceSuite extends munit.FunSuite:

  private val proposals = List(
    Proposal(
      ApprovalKind.StatusChange,
      "a2000_x",
      status = Some(SourceStatus.Contested),
      evidence = "why",
      quote = Some("q")
    ),
    Proposal(ApprovalKind.Edge, "a2000_x", edgeKind = Some(EdgeKind.Refutes), target = Some("b2001_y"), evidence = "e"),
    Proposal(ApprovalKind.Edge, "a2000_x", edgeKind = Some(EdgeKind.Challenges), target = Some("b2001_y"))
  )

  test("every proposal's payload reads as its evidence and quote"):
    proposals.foreach { p =>
      val read = Jsonb.read[ProposalEvidence](p.asJson.noSpaces)
      assertEquals(read.flatMap(_.evidence).getOrElse(""), p.evidence)
      assertEquals(read.flatMap(_.quote), p.quote)
    }

  test("a payload that is no JSON reads as nothing"):
    assertEquals(Jsonb.read[ProposalEvidence]("not json"), None)
