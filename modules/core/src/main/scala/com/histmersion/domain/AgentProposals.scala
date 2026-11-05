package com.histmersion.domain

import com.histmersion.api.library.{EdgesPatch, MetadataPatch}
import com.histmersion.api.given

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.automation.Approval
import com.histmersion.api.library.Edge
import com.histmersion.api.library.Source

/** Changes an agent run PROPOSES to the corpus rather than makes — a source's status, a typed disagreement edge — and
  * the pure rules that decide whether code applies one at once or records it for a person
  * (docs/research-embedded-agents.md §3 "Security", §5.2 "Approvals").
  */
object AgentProposals:

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
      target: Option[Proposal.Target] = None,
      evidence: Proposal.Evidence = "",
      quote: Option[Proposal.Quote] = None
  ) derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema:
    /** One line for a person: what would change. */
    def describe: String = kind match
      case Approval.Kind.StatusChange => s"[[$citekey]] status → ${status.fold("?")(_.wire)}"
      case Approval.Kind.Edge         =>
        s"edge [[$citekey]] ${edgeKind.fold("?")(Edge.Kind.wireOf)} [[${target.getOrElse("?")}]]"
      // a plan or an article is never a source proposal (it is carried by its own approval)
      case k => s"${k.wire} (not a source change)"

    /** The one patch that applies it, on `citekey`. An edge is patched on its source. */
    def patch: com.histmersion.api.library.Source.Patch = kind match
      case Approval.Kind.StatusChange =>
        com.histmersion.api.library.Source.Patch(metadata = Some(MetadataPatch(status = status)))
      case Approval.Kind.Edge =>
        com.histmersion.api.library.Source.Patch(edges = Some(EdgesPatch(add = (for k <- edgeKind; t <- target
        yield com.histmersion.api.library.Edge.Input(t, k)).toList)))
      case _ => com.histmersion.api.library.Source.Patch()

  object Proposal:
    type Target = Target.T
    object Target extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Evidence = Evidence.T
    object Evidence extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Quote = Quote.T
    object Quote extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]



  /** What becomes of a proposal. */
  enum Route:
    /** Applied by code now: the fill rule, or a kind configured `auto`. */
    case Apply

    /** Recorded as an open approval; nothing changes until a person approves it. */
    case Ask

    /** Nothing to do (the source already has that status). */
    case Skip(reason: String)

  /** A status is APPLIED without a person only when it fills an `unassessed` source with `current` or `foundational` —
    * the rule `enrich-source` has followed since P1 — or when status changes are configured `auto`. Every edge follows
    * the edge mode.
    */
  def route(p: Proposal, current: Option[Source.Status], statusMode: Approval.Mode, edgeMode: Approval.Mode): Route =
    p.kind match
      case Approval.Kind.StatusChange =>
        if p.status.isEmpty || p.status == current then Route.Skip(s"already ${current.fold("?")(_.wire)}")
        else if current.contains(Source.Status.Unassessed) &&
          p.status.exists(s => s == Source.Status.Current || s == Source.Status.Foundational)
        then Route.Apply
        else if statusMode == Approval.Mode.Auto then Route.Apply
        else Route.Ask
      case Approval.Kind.Edge =>
        if edgeMode == Approval.Mode.Auto then Route.Apply else Route.Ask
      case k => Route.Skip(s"${k.wire} is not a source change")

  // ── from a model's answer ───────────────────────────────────────────────────────────────────────

  /** A change a model proposes, as it states it. */
  final case class ProposalAnswer(
      kind: ProposalAnswer.Kind,
      citekey: com.histmersion.domain.library.Citekey,
      status: Option[ProposalAnswer.Status] = None,
      edgeKind: Option[ProposalAnswer.EdgeKind] = None,
      target: Option[ProposalAnswer.Target] = None,
      evidence: ProposalAnswer.Evidence,
      quote: ProposalAnswer.Quote
  ) derives KindlingsCodecAsObject
  object ProposalAnswer:
    type Kind = Kind.T
    object Kind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Status = Status.T
    object Status extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type EdgeKind = EdgeKind.T
    object EdgeKind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Target = Target.T
    object Target extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Evidence = Evidence.T
    object Evidence extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Quote = Quote.T
    object Quote extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

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
      isCitekey: String => Boolean,
      text: String => Option[String],
      minRatio: Double
  ): Either[String, Proposal] =
    val ck = a.citekey.trim
    val label = s"proposal for ${if ck.isEmpty then "?" else ck}"
    def quoted(keys: List[String]): Either[String, String] =
      val q = a.quote.trim
      if q.isEmpty then Left(s"$label: no quote")
      else if keys.flatMap(text).exists(t => AgentVerify.findQuote(q, t, minRatio).accepted) then Right(q)
      else Left(s"$label: the quote is not in the record of ${keys.mkString(" or ")}")
    for
      _ <- Either.cond(isCitekey(ck), (), s"$label: '$ck' is not a citekey")
      _ <- Either.cond(a.evidence.trim.nonEmpty, (), s"$label: no evidence given")
      p <- a.kind.trim.toLowerCase match
        case "status" =>
          for
            st <- a.status
              .toRight(s"$label: a status change names no status")
              .flatMap(AgentVerify.status)
              .left
              .map(m => s"$label: $m")
            _ <- Either.cond(st != Source.Status.Unassessed, (), s"$label: unassessed is not a change to propose")
            q <- quoted(List(ck))
          yield Proposal(Approval.Kind.StatusChange, ck, status = Some(st), evidence = a.evidence.trim, quote = Some(q))
        case "edge" =>
          for
            k <- a.edgeKind
              .flatMap(Edge.Kind.fromWire)
              .filter(EdgeKinds.contains)
              .toRight(s"$label: edge kind must be one of ${EdgeKinds.map(Edge.Kind.wireOf).mkString(", ")}")
            t <- a.target.map(_.trim).filter(isCitekey).toRight(s"$label: the edge target is not a citekey")
            _ <- Either.cond(t != ck, (), s"$label: an edge from a source to itself")
            q <- quoted(List(ck, t))
          yield Proposal(
            Approval.Kind.Edge,
            ck,
            edgeKind = Some(k),
            target = Some(t),
            evidence = a.evidence.trim,
            quote = Some(q)
          )
        case other => Left(s"$label: kind '$other' is neither status nor edge")
    yield p
