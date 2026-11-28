package com.histmersion
package domain

import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.util.json.StoredIdCodecs.given
import com.histmersion.api.library.EdgesPatch
import com.histmersion.api.library.MetadataPatch
import com.histmersion.api.given
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import com.histmersion.api.automation.Approval
import com.histmersion.api.library.Edge
import com.histmersion.api.library.Source
import com.histmersion.lint.tolerated
import com.histmersion.lint.LintRule
import io.github.iltotore.iron.constraint.any.Pure
import sttp.tapir.Schema
import com.histmersion.util.problem.Problem
import com.histmersion.util.problem.Problem.Content
import com.histmersion.util.problem.Problem.Field
import com.histmersion.util.problem.Problem.Invalid
import com.histmersion.util.problem.Problem.Raw
import com.histmersion.util.problem.Problem.Where

@tolerated(LintRule.G47Fqn, "pending G47 (no FQN noise)")
final case class Proposal(
    kind: Approval.Kind,
    citekey: library.Citekey,
    status: Option[Source.Status] = None,
    edgeKind: Option[Edge.Kind] = None,
    target: Option[library.Citekey] = None,
    evidence: Proposal.Evidence = Proposal.Evidence(""),
    quote: Option[Proposal.Quote] = None
) derives JsonCodec,
      KindlingsSchema,
      com.histmersion.api.json.Schema:
  /** One line for a person: what would change. */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def describe: Proposal.Description = kind match
    case Approval.Kind.StatusChange => Proposal.Description(s"[[$citekey]] status → ${status.fold("?")(_.wire)}")
    case Approval.Kind.Edge         =>
      Proposal.Description(
        s"edge [[$citekey]] ${edgeKind.fold("?")(Edge.Kind.wireOf)} [[${target.fold("?")(_.value)}]]"
      )
    // a plan or an article is never a source proposal (it is carried by its own approval)
    case k => Proposal.Description(s"${k.wire} (not a source change)")

  /** The one patch that applies it, on `citekey`. An edge is patched on its source. */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(LintRule.G47Fqn, "pending G47 (no FQN noise)")
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

  type Description = Description.T

  object Description extends Newtype[String, Pure]

  val EdgeKinds: List[Edge.Kind] =
    List(Edge.Kind.Challenges, Edge.Kind.Refutes, Edge.Kind.Updates, Edge.Kind.SupersededBy)

  enum Route:
    /** Applied by code now: the fill rule, or a kind configured `auto`. */
    case Apply

    /** Recorded as an open approval; nothing changes until a person approves it. */
    case Ask

    /** Nothing to do (the source already has that status). */
    case Skip(reason: Unneeded)

  enum Unneeded:
    /** The source already has the proposed status (`status`; None: it has none, and none is proposed). */
    case Already(status: Option[Source.Status])

    /** A kind of approval that changes no source (`kind`: a plan, an article). */
    case NotSourceChange(kind: Approval.Kind)

  extension (p: Proposal)
    def route(current: Option[Source.Status], statusMode: Approval.Mode, edgeMode: Approval.Mode): Route =
      p.kind match
        case Approval.Kind.StatusChange =>
          if p.status.isEmpty || p.status == current then Route.Skip(Unneeded.Already(current))
          else if current.contains(Source.Status.Unassessed) &&
            p.status.exists(s => s == Source.Status.Current || s == Source.Status.Foundational)
          then Route.Apply
          else if statusMode == Approval.Mode.Auto then Route.Apply
          else Route.Ask
        case Approval.Kind.Edge =>
          if edgeMode == Approval.Mode.Auto then Route.Apply else Route.Ask
        case k => Route.Skip(Unneeded.NotSourceChange(k))

  @tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
  final case class ProposalAnswer(
      kind: ProposalAnswer.Kind,
      citekey: ProposalAnswer.Citekey,
      status: Option[ProposalAnswer.Status] = None,
      edgeKind: Option[ProposalAnswer.EdgeKind] = None,
      target: Option[ProposalAnswer.Target] = None,
      evidence: ProposalAnswer.Evidence,
      quote: ProposalAnswer.Quote
  ) derives JsonCodec

  object ProposalAnswer:
    type Kind = Kind.T
    object Kind extends Newtype[String, Pure]
    type Citekey = Citekey.T
    object Citekey extends Newtype[String, Pure]
    export library.Source.Status.Wire as Status
    export Edge.Kind.Wire as EdgeKind
    type Target = Target.T
    object Target extends Newtype[String, Pure]
    type Evidence = Evidence.T
    object Evidence extends Newtype[String, Pure]
    type Quote = Quote.T
    object Quote extends Newtype[String, Pure]

    @tolerated(LintRule.Derives, "pending G18 (derives instead of hand-written givens)")
    @tolerated(LintRule.G48, "pending G48 (schemas from Kindlings annotations)")
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

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def check(
      a: ProposalAnswer,
      isCitekey: library.Citekey => Boolean,
      text: library.Citekey => Option[AgentVerify.SourceBody],
      minRatio: AgentVerify.MinRatio
  ): Either[Problem, Proposal] =
    val raw = a.citekey.value.trim
    val shown = if raw.isEmpty then "?" else raw
    // what the model wrote is shown back to it as it wrote it (Raw.of)
    val at = Where(s"proposal for $shown")
    val field = Field(s"proposal for $shown")
    def broken(rule: Content.Requirement): Problem = Content.Proposal(rule, at)
    def quoted(keys: List[library.Citekey]): Either[Problem, Proposal.Quote] =
      val q = Proposal.Quote(a.quote.value.trim)
      if q.value.isEmpty then Left(broken(Content.Requirement.QuoteGiven))
      else if keys.flatMap(text(_)).exists(t => AgentVerify.findQuote(q, t, minRatio).accepted) then Right(q)
      else Left(broken(Content.Requirement.QuoteInRecords(keys.toVector.map(Raw.of(_)))))
    for
      ck <- library.Citekey
        .parse(raw)
        .toOption
        .filter(isCitekey)
        .toRight(broken(Content.Requirement.KnownCitekey(Raw.of(a.citekey))))
      _ <- Either.cond(a.evidence.value.trim.nonEmpty, (), broken(Content.Requirement.EvidenceGiven))
      p <- a.kind.value.trim.toLowerCase match
        case "status" =>
          for
            st <- a.status
              .toRight(broken(Content.Requirement.StatusNamed))
              .flatMap(s => AgentVerify.status(s).left.map(_.within(field)))
            _ <- Either.cond(st != Source.Status.Unassessed, (), broken(Content.Requirement.NotUnassessed))
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
              .toRight(
                Invalid.Unknown
                  .here(
                    Problem.TypeName.named[Edge.Kind],
                    a.edgeKind.fold(Raw(""))(Raw.of(_)),
                    EdgeKinds.toVector.map(e => Raw.of(Edge.Kind.wireOf(e)))
                  )
                  .within(field)
              )
            t <- a.target
              .map(_.value.trim)
              .flatMap(library.Citekey.parse(_).toOption)
              .filter(isCitekey)
              .toRight(broken(Content.Requirement.EdgeTarget))
            _ <- Either.cond(t != ck, (), broken(Content.Requirement.NoSelfEdge))
            q <- quoted(List(ck, t))
          yield Proposal(
            Approval.Kind.Edge,
            ck,
            edgeKind = Some(k),
            target = Some(t),
            evidence = Proposal.Evidence(a.evidence.value.trim),
            quote = Some(q)
          )
        case _ =>
          Left(
            Invalid.Unknown
              .here(Problem.TypeName.of(ProposalAnswer.Kind), Raw.of(a.kind), Vector(Raw("status"), Raw("edge")))
              .within(field)
          )
    yield p
