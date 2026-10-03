package com.histmersion.core

import com.histmersion.core.AgentProposals.*
import com.histmersion.core.Agents.{ApprovalKind, ApprovalMode}

class AgentProposalsSuite extends munit.FunSuite:

  private val cks    = Set("knapp2016_tin", "jones2021_tin")
  private val text   = Map(
    "knapp2016_tin" -> "The Uluburun ship carried ten tons of copper and one ton of tin.",
    "jones2021_tin" -> "We show that the tin of the Uluburun cargo came from Central Asia, not Anatolia.")
  private def check(a: ProposalAnswer) = AgentProposals.check(a, cks, text.get, 0.9)

  test("a status change: a known citekey, a status in the vocabulary, a quote in its record"):
    val p = check(ProposalAnswer("status", "knapp2016_tin", Some("Contested"), None, None, "a later paper disagrees",
      "carried ten tons of copper and one ton of tin")).toOption.get
    assertEquals((p.kind, p.status), (ApprovalKind.StatusChange, Some(SourceStatus.Contested)))
    assertEquals(p.patch, SourcePatch(metadata = Some(MetadataPatch(status = Some(SourceStatus.Contested)))))

  test("an edge: the quote may come from either end; references and self-edges are refused"):
    val p = check(ProposalAnswer("edge", "knapp2016_tin", None, Some("superseded_by"), Some("jones2021_tin"),
      "newer provenance study", "the tin of the Uluburun cargo came from Central Asia")).toOption.get
    assertEquals(p.patch.edges.map(_.add), Some(List(EdgeInput("jones2021_tin", EdgeKind.SupersededBy))))
    assert(p.describe.contains("superseded_by"))
    assert(check(ProposalAnswer("edge", "knapp2016_tin", None, Some("references"), Some("jones2021_tin"), "e", "one ton of tin"))
      .left.exists(_.contains("edge kind")))
    assert(check(ProposalAnswer("edge", "knapp2016_tin", None, Some("refutes"), Some("knapp2016_tin"), "e", "one ton of tin"))
      .left.exists(_.contains("itself")))

  test("refused: unknown citekey, no evidence, an invented quote, unassessed, an unknown kind"):
    assert(check(ProposalAnswer("status", "ghost", Some("contested"), None, None, "e", "q")).left.exists(_.contains("not a citekey")))
    assert(check(ProposalAnswer("status", "knapp2016_tin", Some("contested"), None, None, " ", "one ton of tin")).left.exists(_.contains("no evidence")))
    assert(check(ProposalAnswer("status", "knapp2016_tin", Some("debunked"), None, None, "e",
      "the ship was a Phoenician trader from Tyre")).left.exists(_.contains("not in the record")))
    assert(check(ProposalAnswer("status", "knapp2016_tin", Some("unassessed"), None, None, "e", "one ton of tin")).isLeft)
    assert(check(ProposalAnswer("rename", "knapp2016_tin", None, None, None, "e", "one ton of tin")).left.exists(_.contains("neither")))

  test("route: the fill rule applies, anything else waits for a person unless configured auto"):
    val current = Proposal(ApprovalKind.StatusChange, "a", status = Some(SourceStatus.Current))
    val debunk  = Proposal(ApprovalKind.StatusChange, "a", status = Some(SourceStatus.Debunked))
    val edge    = Proposal(ApprovalKind.Edge, "a", edgeKind = Some(EdgeKind.Refutes), target = Some("b"))
    import ApprovalMode.*
    assertEquals(route(current, Some(SourceStatus.Unassessed), Manual, Manual), Route.Apply)
    assertEquals(route(current, Some(SourceStatus.Contested), Manual, Manual), Route.Ask)
    assertEquals(route(debunk, Some(SourceStatus.Unassessed), Manual, Manual), Route.Ask)
    assertEquals(route(debunk, Some(SourceStatus.Unassessed), Auto, Manual), Route.Apply)
    assert(route(debunk, Some(SourceStatus.Debunked), Manual, Manual).isInstanceOf[Route.Skip])
    assertEquals(route(edge, None, Auto, Manual), Route.Ask)
    assertEquals(route(edge, None, Manual, Auto), Route.Apply)

  test("an approval mode that is not exactly auto is manual"):
    assertEquals(AgentApprovalsConfig.mode("AUTO"), ApprovalMode.Auto)
    assertEquals(AgentApprovalsConfig.mode("automatic"), ApprovalMode.Manual)
    assertEquals(AgentApprovalsConfig.mode(""), ApprovalMode.Manual)
