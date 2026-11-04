package com.histmersion.domain.library.workflows

import com.histmersion.config.*
import com.histmersion.api.given
import com.histmersion.config.given

import com.histmersion.api.automation.Agents.ApprovalKind
import com.histmersion.domain.AgentProposals.Proposal
import CurrencyCheckRules.*
import com.histmersion.api.library.FollowupRow
import com.histmersion.api.library.Edge

/** What a currency check makes of a paper citing a source. */
class CurrencyCheckRulesSuite extends munit.FunSuite:

  test(
    "currency: a disagreeing paper in the corpus is an edge proposal (supersedes points older → newer), outside it an acquisition"
  ):
    val row = FollowupRow(
      title = Some("New"),
      year = Some(2024),
      doi = Some("10.1000/new"),
      contexts = List("we refute X"),
      proposedKind = "refutes",
      citekey = Some("new2024_x")
    )
    currencyAction("old2000_y", row) match
      case CurrencyAction.Propose(p) =>
        assertEquals(
          (p.kind, p.citekey, p.edgeKind, p.target),
          (ApprovalKind.Edge, "new2024_x", Some(Edge.Kind.Refutes), Some("old2000_y"))
        )
        assert(p.evidence.contains("we refute X"), p.evidence)
      case other => fail(s"expected a proposal, got $other")
    currencyAction("old2000_y", row.copy(proposedKind = "supersedes")) match
      case CurrencyAction.Propose(p) =>
        assertEquals((p.citekey, p.edgeKind, p.target), ("old2000_y", Some(Edge.Kind.SupersededBy), Some("new2024_x")))
      case other => fail(s"expected a proposal, got $other")
    assertEquals(currencyAction("old2000_y", row.copy(citekey = None)), CurrencyAction.Fetch(row.copy(citekey = None)))
    assertEquals(currencyAction("old2000_y", row.copy(proposedKind = "confirms")), CurrencyAction.Ignore)
    assertEquals(currencyAction("old2000_y", row.copy(proposedKind = "cites")), CurrencyAction.Ignore)
    assertEquals(currencyAction("new2024_x", row), CurrencyAction.Ignore, "a paper citing itself proposes nothing")
