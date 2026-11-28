package com.histmersion
package domain

import com.histmersion.util.problem.Text.sentence
import com.histmersion.config.*
import com.histmersion.api.given
import com.histmersion.config.given

import com.histmersion.api.automation.Agents.{ApprovalKind, ApprovalMode}
import com.histmersion.api.library.MetadataPatch
import com.histmersion.api.library.Edge
import com.histmersion.api.library.Source
import com.histmersion.domain.library.Citekey
import com.histmersion.domain.automation.Approval
import com.histmersion.lint.{tolerated, LintRule}

@tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
class AgentProposalsSuite extends munit.FunSuite:

  private val cks = Set(
    Citekey("knapp2016_tin"),
    Citekey("jones2021_tin")
  )
  private val text = Map(
    Citekey("knapp2016_tin") ->
      AgentVerify.SourceBody("The Uluburun ship carried ten tons of copper and one ton of tin."),
    Citekey("jones2021_tin") ->
      AgentVerify.SourceBody("We show that the tin of the Uluburun cargo came from Central Asia, not Anatolia.")
  )
  private def check(a: Proposal.ProposalAnswer) = Proposal.check(a, cks, text.get, AgentVerify.MinRatio(0.9))

  test("a status change: a known citekey, a status in the vocabulary, a quote in its record"):
    val p = check(
      Proposal.ProposalAnswer(
        Proposal.ProposalAnswer.Kind("status"),
        Proposal.ProposalAnswer.Citekey("knapp2016_tin"),
        Some(library.Source.Status.Wire("Contested")),
        None,
        None,
        Proposal.ProposalAnswer.Evidence("a later paper disagrees"),
        Proposal.ProposalAnswer.Quote("carried ten tons of copper and one ton of tin")
      )
    ).toOption.get
    assertEquals((p.kind, p.status), (ApprovalKind.StatusChange, Some(Source.Status.Contested)))
    assertEquals(
      p.patch,
      Source.Patch(metadata = Some(MetadataPatch(status = Some(Source.Status.Contested))))
    )

  test("an edge: the quote may come from either end; references and self-edges are refused"):
    val p = check(
      Proposal.ProposalAnswer(
        Proposal.ProposalAnswer.Kind("edge"),
        Proposal.ProposalAnswer.Citekey("knapp2016_tin"),
        None,
        Some(Edge.Kind.Wire("superseded_by")),
        Some(Proposal.ProposalAnswer.Target("jones2021_tin")),
        Proposal.ProposalAnswer.Evidence("newer provenance study"),
        Proposal.ProposalAnswer.Quote("the tin of the Uluburun cargo came from Central Asia")
      )
    ).toOption.get
    assertEquals(
      p.patch.edges.map(_.add),
      Some(List(Edge.Input(api.library.Citekey("jones2021_tin"), Edge.Kind.SupersededBy)))
    )
    assert(p.describe.value.contains("superseded_by"))
    assert(
      check(
        Proposal.ProposalAnswer(
          Proposal.ProposalAnswer.Kind("edge"),
          Proposal.ProposalAnswer.Citekey("knapp2016_tin"),
          None,
          Some(Edge.Kind.Wire("references")),
          Some(Proposal.ProposalAnswer.Target("jones2021_tin")),
          Proposal.ProposalAnswer.Evidence("e"),
          Proposal.ProposalAnswer.Quote("one ton of tin")
        )
      ).left.exists(_.sentence.contains("edge kind"))
    )
    assert(
      check(
        Proposal.ProposalAnswer(
          Proposal.ProposalAnswer.Kind("edge"),
          Proposal.ProposalAnswer.Citekey("knapp2016_tin"),
          None,
          Some(Edge.Kind.Wire("refutes")),
          Some(Proposal.ProposalAnswer.Target("knapp2016_tin")),
          Proposal.ProposalAnswer.Evidence("e"),
          Proposal.ProposalAnswer.Quote("one ton of tin")
        )
      ).left.exists(_.sentence.contains("itself"))
    )

  test("refused: unknown citekey, no evidence, an invented quote, unassessed, an unknown kind"):
    assert(
      check(
        Proposal.ProposalAnswer(
          Proposal.ProposalAnswer.Kind("status"),
          Proposal.ProposalAnswer.Citekey("ghost"),
          Some(library.Source.Status.Wire("contested")),
          None,
          None,
          Proposal.ProposalAnswer.Evidence("e"),
          Proposal.ProposalAnswer.Quote("q")
        )
      ).left
        .exists(_.sentence.contains("not a citekey"))
    )
    assert(
      check(
        Proposal.ProposalAnswer(
          Proposal.ProposalAnswer.Kind("status"),
          Proposal.ProposalAnswer.Citekey("knapp2016_tin"),
          Some(library.Source.Status.Wire("contested")),
          None,
          None,
          Proposal.ProposalAnswer.Evidence(" "),
          Proposal.ProposalAnswer.Quote("one ton of tin")
        )
      ).left
        .exists(_.sentence.contains("no evidence"))
    )
    assert(
      check(
        Proposal.ProposalAnswer(
          Proposal.ProposalAnswer.Kind("status"),
          Proposal.ProposalAnswer.Citekey("knapp2016_tin"),
          Some(library.Source.Status.Wire("debunked")),
          None,
          None,
          Proposal.ProposalAnswer.Evidence("e"),
          Proposal.ProposalAnswer.Quote("the ship was a Phoenician trader from Tyre")
        )
      ).left.exists(_.sentence.contains("not in the record"))
    )
    assert(
      check(
        Proposal.ProposalAnswer(
          Proposal.ProposalAnswer.Kind("status"),
          Proposal.ProposalAnswer.Citekey("knapp2016_tin"),
          Some(library.Source.Status.Wire("unassessed")),
          None,
          None,
          Proposal.ProposalAnswer.Evidence("e"),
          Proposal.ProposalAnswer.Quote("one ton of tin")
        )
      ).isLeft
    )
    assert(
      check(
        Proposal.ProposalAnswer(
          Proposal.ProposalAnswer.Kind("rename"),
          Proposal.ProposalAnswer.Citekey("knapp2016_tin"),
          None,
          None,
          None,
          Proposal.ProposalAnswer.Evidence("e"),
          Proposal.ProposalAnswer.Quote("one ton of tin")
        )
      ).left
        .exists(_.sentence.contains("unknown proposal answer kind 'rename' (one of: status, edge)"))
    )

  test("route: the fill rule applies, anything else waits for a person unless configured auto"):
    val current = Proposal(ApprovalKind.StatusChange, Citekey.assume("a"), status = Some(Source.Status.Current))
    val debunk = Proposal(ApprovalKind.StatusChange, Citekey.assume("a"), status = Some(Source.Status.Debunked))
    val edge = Proposal(
      ApprovalKind.Edge,
      Citekey.assume("a"),
      edgeKind = Some(Edge.Kind.Refutes),
      target = Some(Citekey.assume("b"))
    )
    import ApprovalMode.*
    assertEquals(current.route(Some(Source.Status.Unassessed), Manual, Manual), Proposal.Route.Apply)
    assertEquals(current.route(Some(Source.Status.Contested), Manual, Manual), Proposal.Route.Ask)
    assertEquals(debunk.route(Some(Source.Status.Unassessed), Manual, Manual), Proposal.Route.Ask)
    assertEquals(debunk.route(Some(Source.Status.Unassessed), Auto, Manual), Proposal.Route.Apply)
    assert(debunk.route(Some(Source.Status.Debunked), Manual, Manual).isInstanceOf[Proposal.Route.Skip])
    assertEquals(edge.route(None, Auto, Manual), Proposal.Route.Ask)
    assertEquals(edge.route(None, Manual, Auto), Proposal.Route.Apply)

  test("an approval mode that is not exactly auto is manual"):
    assertEquals(ApprovalModes.configured(Approval.Mode.Wire("AUTO")), Approval.Mode.Auto)
    assertEquals(ApprovalModes.configured(Approval.Mode.Wire("automatic")), Approval.Mode.Manual)
    assertEquals(ApprovalModes.configured(Approval.Mode.Wire("")), Approval.Mode.Manual)
