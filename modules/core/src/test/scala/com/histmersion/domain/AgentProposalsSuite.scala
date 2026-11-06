package com.histmersion.domain

import com.histmersion.config.*
import com.histmersion.api.given
import com.histmersion.config.given

import com.histmersion.domain.AgentProposals.*
import com.histmersion.api.automation.Agents.{ApprovalKind, ApprovalMode}
import com.histmersion.api.library.MetadataPatch
import com.histmersion.api.library.Edge
import com.histmersion.api.library.Source

class AgentProposalsSuite extends munit.FunSuite:

  private val cks = Set(
    com.histmersion.domain.library.Citekey("knapp2016_tin"),
    com.histmersion.domain.library.Citekey("jones2021_tin")
  )
  private val text = Map(
    com.histmersion.domain.library.Citekey("knapp2016_tin") ->
      AgentVerify.SourceBody("The Uluburun ship carried ten tons of copper and one ton of tin."),
    com.histmersion.domain.library.Citekey("jones2021_tin") ->
      AgentVerify.SourceBody("We show that the tin of the Uluburun cargo came from Central Asia, not Anatolia.")
  )
  private def check(a: ProposalAnswer) = AgentProposals.check(a, cks, text.get, AgentVerify.MinRatio(0.9))

  test("a status change: a known citekey, a status in the vocabulary, a quote in its record"):
    val p = check(
      ProposalAnswer(
        com.histmersion.domain.AgentProposals.ProposalAnswer.Kind("status"),
        com.histmersion.domain.AgentProposals.ProposalAnswer.Citekey("knapp2016_tin"),
        Some(com.histmersion.domain.library.Source.Status.Wire("Contested")),
        None,
        None,
        com.histmersion.domain.AgentProposals.ProposalAnswer.Evidence("a later paper disagrees"),
        com.histmersion.domain.AgentProposals.ProposalAnswer.Quote("carried ten tons of copper and one ton of tin")
      )
    ).toOption.get
    assertEquals((p.kind, p.status), (ApprovalKind.StatusChange, Some(Source.Status.Contested)))
    assertEquals(
      p.patch,
      com.histmersion.api.library.Source.Patch(metadata = Some(MetadataPatch(status = Some(Source.Status.Contested))))
    )

  test("an edge: the quote may come from either end; references and self-edges are refused"):
    val p = check(
      ProposalAnswer(
        com.histmersion.domain.AgentProposals.ProposalAnswer.Kind("edge"),
        com.histmersion.domain.AgentProposals.ProposalAnswer.Citekey("knapp2016_tin"),
        None,
        Some(com.histmersion.api.library.Edge.Kind.Wire("superseded_by")),
        Some(com.histmersion.domain.AgentProposals.ProposalAnswer.Target("jones2021_tin")),
        com.histmersion.domain.AgentProposals.ProposalAnswer.Evidence("newer provenance study"),
        com.histmersion.domain.AgentProposals.ProposalAnswer.Quote("the tin of the Uluburun cargo came from Central Asia")
      )
    ).toOption.get
    assertEquals(
      p.patch.edges.map(_.add),
      Some(List(com.histmersion.api.library.Edge.Input(com.histmersion.api.library.Citekey("jones2021_tin"), Edge.Kind.SupersededBy)))
    )
    assert(p.describe.value.contains("superseded_by"))
    assert(
      check(
        ProposalAnswer(com.histmersion.domain.AgentProposals.ProposalAnswer.Kind("edge"), com.histmersion.domain.AgentProposals.ProposalAnswer.Citekey("knapp2016_tin"), None, Some(com.histmersion.api.library.Edge.Kind.Wire("references")), Some(com.histmersion.domain.AgentProposals.ProposalAnswer.Target("jones2021_tin")), com.histmersion.domain.AgentProposals.ProposalAnswer.Evidence("e"), com.histmersion.domain.AgentProposals.ProposalAnswer.Quote("one ton of tin"))
      ).left.exists(_.value.contains("edge kind"))
    )
    assert(
      check(
        ProposalAnswer(com.histmersion.domain.AgentProposals.ProposalAnswer.Kind("edge"), com.histmersion.domain.AgentProposals.ProposalAnswer.Citekey("knapp2016_tin"), None, Some(com.histmersion.api.library.Edge.Kind.Wire("refutes")), Some(com.histmersion.domain.AgentProposals.ProposalAnswer.Target("knapp2016_tin")), com.histmersion.domain.AgentProposals.ProposalAnswer.Evidence("e"), com.histmersion.domain.AgentProposals.ProposalAnswer.Quote("one ton of tin"))
      ).left.exists(_.value.contains("itself"))
    )

  test("refused: unknown citekey, no evidence, an invented quote, unassessed, an unknown kind"):
    assert(
      check(ProposalAnswer(com.histmersion.domain.AgentProposals.ProposalAnswer.Kind("status"), com.histmersion.domain.AgentProposals.ProposalAnswer.Citekey("ghost"), Some(com.histmersion.domain.library.Source.Status.Wire("contested")), None, None, com.histmersion.domain.AgentProposals.ProposalAnswer.Evidence("e"), com.histmersion.domain.AgentProposals.ProposalAnswer.Quote("q"))).left
        .exists(_.value.contains("not a citekey"))
    )
    assert(
      check(ProposalAnswer(com.histmersion.domain.AgentProposals.ProposalAnswer.Kind("status"), com.histmersion.domain.AgentProposals.ProposalAnswer.Citekey("knapp2016_tin"), Some(com.histmersion.domain.library.Source.Status.Wire("contested")), None, None, com.histmersion.domain.AgentProposals.ProposalAnswer.Evidence(" "), com.histmersion.domain.AgentProposals.ProposalAnswer.Quote("one ton of tin"))).left
        .exists(_.value.contains("no evidence"))
    )
    assert(
      check(
        ProposalAnswer(
          com.histmersion.domain.AgentProposals.ProposalAnswer.Kind("status"),
          com.histmersion.domain.AgentProposals.ProposalAnswer.Citekey("knapp2016_tin"),
          Some(com.histmersion.domain.library.Source.Status.Wire("debunked")),
          None,
          None,
          com.histmersion.domain.AgentProposals.ProposalAnswer.Evidence("e"),
          com.histmersion.domain.AgentProposals.ProposalAnswer.Quote("the ship was a Phoenician trader from Tyre")
        )
      ).left.exists(_.value.contains("not in the record"))
    )
    assert(
      check(ProposalAnswer(com.histmersion.domain.AgentProposals.ProposalAnswer.Kind("status"), com.histmersion.domain.AgentProposals.ProposalAnswer.Citekey("knapp2016_tin"), Some(com.histmersion.domain.library.Source.Status.Wire("unassessed")), None, None, com.histmersion.domain.AgentProposals.ProposalAnswer.Evidence("e"), com.histmersion.domain.AgentProposals.ProposalAnswer.Quote("one ton of tin"))).isLeft
    )
    assert(
      check(ProposalAnswer(com.histmersion.domain.AgentProposals.ProposalAnswer.Kind("rename"), com.histmersion.domain.AgentProposals.ProposalAnswer.Citekey("knapp2016_tin"), None, None, None, com.histmersion.domain.AgentProposals.ProposalAnswer.Evidence("e"), com.histmersion.domain.AgentProposals.ProposalAnswer.Quote("one ton of tin"))).left
        .exists(_.value.contains("neither"))
    )

  test("route: the fill rule applies, anything else waits for a person unless configured auto"):
    val current = Proposal(ApprovalKind.StatusChange, com.histmersion.domain.library.Citekey("a"), status = Some(Source.Status.Current))
    val debunk = Proposal(ApprovalKind.StatusChange, com.histmersion.domain.library.Citekey("a"), status = Some(Source.Status.Debunked))
    val edge = Proposal(ApprovalKind.Edge, com.histmersion.domain.library.Citekey("a"), edgeKind = Some(Edge.Kind.Refutes), target = Some(com.histmersion.domain.library.Citekey("b")))
    import ApprovalMode.*
    assertEquals(route(current, Some(Source.Status.Unassessed), Manual, Manual), Route.Apply)
    assertEquals(route(current, Some(Source.Status.Contested), Manual, Manual), Route.Ask)
    assertEquals(route(debunk, Some(Source.Status.Unassessed), Manual, Manual), Route.Ask)
    assertEquals(route(debunk, Some(Source.Status.Unassessed), Auto, Manual), Route.Apply)
    assert(route(debunk, Some(Source.Status.Debunked), Manual, Manual).isInstanceOf[Route.Skip])
    assertEquals(route(edge, None, Auto, Manual), Route.Ask)
    assertEquals(route(edge, None, Manual, Auto), Route.Apply)

  test("an approval mode that is not exactly auto is manual"):
    assertEquals(ApprovalModes.configured(com.histmersion.domain.automation.Approval.Mode.Wire("AUTO")), com.histmersion.domain.automation.Approval.Mode.Auto)
    assertEquals(ApprovalModes.configured(com.histmersion.domain.automation.Approval.Mode.Wire("automatic")), com.histmersion.domain.automation.Approval.Mode.Manual)
    assertEquals(ApprovalModes.configured(com.histmersion.domain.automation.Approval.Mode.Wire("")), com.histmersion.domain.automation.Approval.Mode.Manual)
