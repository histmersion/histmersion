package com.histmersion
package domain.library.workflows

import com.histmersion.testsupport.ComparedByValue.given

import hearth.kindlings.optics.syntax.*

import com.histmersion.config.*
import com.histmersion.api.given
import com.histmersion.config.given

import com.histmersion.api.automation.Agents.ApprovalKind
import com.histmersion.domain.AgentProposals.Proposal
import CurrencyCheckRules.*
import com.histmersion.api.library.FollowupRow
import com.histmersion.api.library.Edge
import com.histmersion.api.library.Title
import com.histmersion.api.catalogue.Year
import com.histmersion.api.library.Doi
import com.histmersion.api.library.Citekey

/** What a currency check makes of a paper citing a source. */
class CurrencyCheckRulesSuite extends munit.FunSuite:

  test(
    "currency: a disagreeing paper in the corpus is an edge proposal (supersedes points older → newer), outside it an acquisition"
  ):
    val row = FollowupRow(
      title = Some(Title("New")),
      year = Some(Year(2024)),
      doi = Some(Doi("10.1000/new")),
      contexts = List(FollowupRow.Contexts("we refute X")),
      proposedKind = FollowupRow.ProposedKind("refutes"),
      citekey = Some(Citekey("new2024_x"))
    )
    currencyAction(com.histmersion.domain.library.Citekey("old2000_y"), row) match
      case CurrencyAction.Propose(p) =>
        assertEquals(
          (p.kind, p.citekey, p.edgeKind, p.target),
          (ApprovalKind.Edge, "new2024_x", Some(Edge.Kind.Refutes), Some("old2000_y"))
        )
        assert(p.evidence.value.contains("we refute X"), p.evidence)
      case other => fail(s"expected a proposal, got $other")
    currencyAction(com.histmersion.domain.library.Citekey("old2000_y"), row.modify(_.proposedKind).setTo(FollowupRow.ProposedKind("supersedes"))) match
      case CurrencyAction.Propose(p) =>
        assertEquals((p.citekey, p.edgeKind, p.target), ("old2000_y", Some(Edge.Kind.SupersededBy), Some("new2024_x")))
      case other => fail(s"expected a proposal, got $other")
    assertEquals(currencyAction(com.histmersion.domain.library.Citekey("old2000_y"), row.modify(_.citekey).setTo(None)), CurrencyAction.Fetch(row.modify(_.citekey).setTo(None)))
    assertEquals(currencyAction(com.histmersion.domain.library.Citekey("old2000_y"), row.modify(_.proposedKind).setTo(FollowupRow.ProposedKind("confirms"))), CurrencyAction.Ignore)
    assertEquals(currencyAction(com.histmersion.domain.library.Citekey("old2000_y"), row.modify(_.proposedKind).setTo(FollowupRow.ProposedKind("cites"))), CurrencyAction.Ignore)
    assertEquals(currencyAction(com.histmersion.domain.library.Citekey("new2024_x"), row), CurrencyAction.Ignore, "a paper citing itself proposes nothing")
