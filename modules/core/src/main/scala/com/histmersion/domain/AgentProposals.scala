package com.histmersion
package domain

import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.util.json.StoredIdCodecs.given
import com.histmersion.api.library.{EdgesPatch, MetadataPatch}
import com.histmersion.api.given

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.automation.Approval
import com.histmersion.api.library.Edge
import com.histmersion.api.library.Source
import io.github.iltotore.iron.constraint.any.Pure

/** Changes an agent run PROPOSES to the corpus rather than makes — a source's status, a typed disagreement edge — and
  * the pure rules that decide whether code applies one at once or records it for a person
  * (docs/research-embedded-agents.md §3 "Security", §5.2 "Approvals").
  */
object AgentProposals:
  type Description = Description.T
  object Description extends Newtype[String, Pure]

  /** The edge kinds an agent may propose: the disagreement kinds the writing guide counts. A plain `references` edge is
    * code's business (`refs`), never a model's.
    */

  val EdgeKinds: List[Edge.Kind] =
    List(Edge.Kind.Challenges, Edge.Kind.Refutes, Edge.Kind.Updates, Edge.Kind.SupersededBy)

  /** A verified proposal. For a status change, `status` is the new status of `citekey`; for an edge, `edgeKind` runs
    * from `citekey` to `target` (`superseded_by` from the older source to the newer). `quote` is the passage the
    * proposal rests on, already found in the record by code.
    */
  final case class Proposal(
      kind: Approval.Kind,
      citekey: com.histmersion.domain.library.Citekey,
      status: Option[Source.Status] = None,
      edgeKind: Option[Edge.Kind] = None,
      target: Option[com.histmersion.domain.library.Citekey] = None,
      evidence: Proposal.Evidence = Proposal.Evidence(""),
      quote: Option[Proposal.Quote] = None
  ) derives KindlingsCodecAsObject,
        KindlingsSchema,
        com.histmersion.api.json.Schema:
    /** One line for a person: what would change. */
    def describe: Description = kind match
      case Approval.Kind.StatusChange => Description(s"[[$citekey]] status → ${status.fold("?")(_.wire)}")
      case Approval.Kind.Edge         =>
        Description(s"edge [[$citekey]] ${edgeKind.fold("?")(Edge.Kind.wireOf)} [[${target.fold("?")(_.value)}]]")
      // a plan or an article is never a source proposal (it is carried by its own approval)
      case k => Description(s"${k.wire} (not a source change)")

    /** The one patch that applies it, on `citekey`. An edge is patched on its source. */
    def patch: Source.Patch = kind match
      case Approval.Kind.StatusChange =>
        Source.Patch(metadata = Some(MetadataPatch(status = status)))
      case Approval.Kind.Edge =>
        Source.Patch(edges = Some(EdgesPatch(add = (for k <- edgeKind; t <- target
        yield Edge.Input(com.histmersion.api.library.Citekey(t.value), k)).toList)))
      case _ => Source.Patch()

  object Proposal:

    type Evidence = Evidence.T
    object Evidence extends Newtype[String, Pure]
    type Quote = Quote.T
    object Quote extends Newtype[String, Pure]

  /** What becomes of a proposal. */
  enum Route derives CanEqual:
    /** Applied by code now: the fill rule, or a kind configured `auto`. */
    case Apply

    /** Recorded as an open approval; nothing changes until a person approves it. */
    case Ask

    /** Nothing to do (the source already has that status). */
    case Skip(reason: Problem)


  /** A status is APPLIED without a person only when it fills an `unassessed` source with `current` or `foundational` —
    * the rule `enrich-source` has followed since P1 — or when status changes are configured `auto`. Every edge follows
    * the edge mode.
    */
  def route(p: Proposal, current: Option[Source.Status], statusMode: Approval.Mode, edgeMode: Approval.Mode): Route =
    p.kind match
      case Approval.Kind.StatusChange =>
        if p.status.isEmpty || p.status == current then
          Route.Skip(Problem(s"already ${current.fold("?")(_.wire)}"))
        else if current.contains(Source.Status.Unassessed) &&
          p.status.exists(s => s == Source.Status.Current || s == Source.Status.Foundational)
        then Route.Apply
        else if statusMode == Approval.Mode.Auto then Route.Apply
        else Route.Ask
      case Approval.Kind.Edge =>
        if edgeMode == Approval.Mode.Auto then Route.Apply else Route.Ask
      case k => Route.Skip(Problem(s"${k.wire} is not a source change"))

  // ── from a model's answer ───────────────────────────────────────────────────────────────────────

  /** A change a model proposes, as it states it. */
  final case class ProposalAnswer(
      kind: ProposalAnswer.Kind,
      citekey: ProposalAnswer.Citekey,
      status: Option[ProposalAnswer.Status] = None,
      edgeKind: Option[ProposalAnswer.EdgeKind] = None,
      target: Option[ProposalAnswer.Target] = None,
      evidence: ProposalAnswer.Evidence,
      quote: ProposalAnswer.Quote
  ) derives KindlingsCodecAsObject
  object ProposalAnswer:
    type Kind = Kind.T
    object Kind extends Newtype[String, Pure]
    type Citekey = Citekey.T
    object Citekey extends Newtype[String, Pure]
    type Status = com.histmersion.domain.library.Source.Status.Wire
    val Status: com.histmersion.domain.library.Source.Status.Wire.type =
      com.histmersion.domain.library.Source.Status.Wire
    type EdgeKind = Edge.Kind.Wire
    val EdgeKind: Edge.Kind.Wire.type = Edge.Kind.Wire
    type Target = Target.T
    object Target extends Newtype[String, Pure]
    type Evidence = Evidence.T
    object Evidence extends Newtype[String, Pure]
    type Quote = Quote.T
    object Quote extends Newtype[String, Pure]

    given Schema[ProposalAnswer] = KindlingsSchema
      .derived[ProposalAnswer]
      .schema
      .modify(_.kind)(
        _.description("status (change citekey's status) | edge (a disagreement edge from citekey to target)")
      )
      .modify(_.citekey)(_.description("The source the change is ON (an edge starts here)"))
      .modify(_.status)(
        _.description("For kind=status: foundational | current | contested | superseded | debunked | retracted")
      )
      .modify(_.edgeKind)(
        _.description(
          "For kind=edge: challenges | refutes | updates | superseded_by (from the older source to the newer)"
        )
      )
      .modify(_.target)(_.description("For kind=edge: the citekey the edge points to"))
      .modify(_.evidence)(_.description("Why, in one or two sentences"))
      .modify(_.quote)(_.description("The sentence of citekey's (or target's) record that shows it, copied EXACTLY"))

  /** Verify one proposed change: the kind and vocabularies, that both citekeys exist, and that the quote is really in
    * the record of `citekey` or `target` (`text` gives a source's summary, findings and body). A proposal that cannot
    * be quoted is not a proposal.
    */
  def check(
      a: ProposalAnswer,
      isCitekey: com.histmersion.domain.library.Citekey => Boolean,
      text: com.histmersion.domain.library.Citekey => Option[AgentVerify.SourceBody],
      minRatio: AgentVerify.MinRatio
  ): Either[Problem, Proposal] =
    val raw = a.citekey.value.trim
    val label = s"proposal for ${if raw.isEmpty then "?" else raw}"
    def quoted(keys: List[com.histmersion.domain.library.Citekey]): Either[Problem, Proposal.Quote] =
      val q = Proposal.Quote(a.quote.value.trim)
      if q.value.isEmpty then Left(Problem(s"$label: no quote"))
      else if keys.flatMap(text(_)).exists(t => AgentVerify.findQuote(q, t, minRatio).accepted) then Right(q)
      else Left(Problem(s"$label: the quote is not in the record of ${keys.mkString(" or ")}"))
    for
      ck <- com.histmersion.domain.library.Citekey
        .parse(raw)
        .toOption
        .filter(isCitekey)
        .toRight(Problem(s"$label: '$raw' is not a citekey"))
      _ <- Either.cond(a.evidence.value.trim.nonEmpty, (), Problem(s"$label: no evidence given"))
      p <- a.kind.value.trim.toLowerCase match
        case "status" =>
          for
            st <- a.status
              .toRight(Problem(s"$label: a status change names no status"))
              .flatMap(AgentVerify.status)
              .left
              .map(m => Problem(s"$label: $m"))
            _ <- Either.cond(
              st != Source.Status.Unassessed,
              (),
              Problem(s"$label: unassessed is not a change to propose")
            )
            q <- quoted(List(ck))
          yield Proposal(
            Approval.Kind.StatusChange,
            ck,
            status = Some(st),
            evidence = Proposal.Evidence(a.evidence.value.trim),
            quote = Some(q)
          )
        case "edge" =>
          for
            k <- a.edgeKind
              .flatMap(Edge.Kind.fromWire)
              .filter(EdgeKinds.contains)
              .toRight(Problem(s"$label: edge kind must be one of ${EdgeKinds.map(Edge.Kind.wireOf).mkString(", ")}"))
            t <- a.target
              .map(_.value.trim)
              .flatMap(com.histmersion.domain.library.Citekey.parse(_).toOption)
              .filter(isCitekey)
              .toRight(Problem(s"$label: the edge target is not a citekey"))
            _ <- Either.cond(t != ck, (), Problem(s"$label: an edge from a source to itself"))
            q <- quoted(List(ck, t))
          yield Proposal(
            Approval.Kind.Edge,
            ck,
            edgeKind = Some(k),
            target = Some(t),
            evidence = Proposal.Evidence(a.evidence.value.trim),
            quote = Some(q)
          )
        case other => Left(Problem(s"$label: kind '$other' is neither status nor edge"))
    yield p
