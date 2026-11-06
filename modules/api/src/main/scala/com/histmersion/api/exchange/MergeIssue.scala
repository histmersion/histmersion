package com.histmersion.api.exchange
import com.histmersion.api.{Ascii, WireEnumValues}
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

import com.histmersion.api.given
import com.histmersion.api.Plural
import com.histmersion.api.automation.Goal
import io.circe.Json

final case class MergeIssue(
    id: MergeIssue.Id,
    kind: MergeIssue.Kind,
    severity: MergeIssue.Severity,
    entity: com.histmersion.api.exchange.Bundle.Kind,
    title: MergeIssue.Title,
    left: Option[IssueSide] = None,
    right: Option[IssueSide] = None,
    why: List[MergeIssue.Why] = Nil,
    options: List[MergeDecision.Kind] = Nil,
    suggested: Option[MergeDecision] = None,
    condition: Option[IssueCondition] = None,
    decision: Option[MergeDecision] = None,
    decidedBy: Option[MergeIssue.DecidedBy] = None,
    detail: Option[IssueDetail] = None
) derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema:
  def open: com.histmersion.api.exchange.MergeIssue.OpenResult = _root_.com.histmersion.api.exchange.MergeIssue.OpenResult(severity == MergeIssue.Severity.Blocking && condition.isEmpty && decision.isEmpty)
object MergeIssue:

  /** What an issue is about. The wire name is part of the issue id, so it never changes. */
  type OpenResult = OpenResult.T
  object OpenResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type Id = Id.T
  object Id extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Title = Title.T
  object Title extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Why = Why.T
  object Why extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  enum Kind:
    /** Two sources may be one work (a score between the thresholds, or a hard flag such as two different DOIs or years
      * far apart): merge them (`map:<citekey>`) or keep both.
      */
    case SameWork

    /** One citekey (or an old one, an alias) names two different works. */
    case CitekeyCollision

    /** Two sources that are being merged disagree on a field. */
    case DoiDiffers, YearDiffers, StatusDiffers, TitleDiffers, AuthorsDiffers, SummaryDiffers, NotesDiffers,
      PeriodDiffers

    /** The OCR bodies of two sources that are being merged differ (a default: keep this side's). */
    case BodyDiffers

    /** A record changed on both sides since the last import (the ledger is the common ancestor). */
    case ChangedBothSides

    /** Deleted over there, changed here since the last import. */
    case DeletedThereChangedHere

    /** Two different articles under one slug (created on both sides: no common base). */
    case ArticleDiverged

    /** The bundle's record has a label (event id, goal slug, finding id, asset name) that a different record has here.
      */
    case LabelTaken

    /** An old citekey of the bundle names a different source here. */
    case AliasConflict

    /** Two findings of a merged pair say nearly the same thing (a default: keep both). */
    case FindingNearDuplicate

    /** An attestation's quote is not found in the OCR body the merge keeps. */
    case AttestationUnanchored

    /** A place added on both sides under one id, with a different geometry or time span. */
    case PlaceDiffers

    /** A named period added on both sides under one id, with a different span or label. */
    case NamedPeriodDiffers

    /** A goal that is one on both sides has two different approved plans, neither newer. */
    case GoalPlanDiffers

  object Kind:
    type WireOfResult = WireOfResult.T
    object WireOfResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type WireResult = WireResult.T
    object WireResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type FromWireS = FromWireS.T
    object FromWireS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    def wireOf(k: Kind): com.histmersion.api.exchange.MergeIssue.Kind.WireOfResult = k match
      case SameWork                => _root_.com.histmersion.api.exchange.MergeIssue.Kind.WireOfResult("same-work")
      case CitekeyCollision        => _root_.com.histmersion.api.exchange.MergeIssue.Kind.WireOfResult("citekey-collision")
      case DoiDiffers              => _root_.com.histmersion.api.exchange.MergeIssue.Kind.WireOfResult("doi-differs")
      case YearDiffers             => _root_.com.histmersion.api.exchange.MergeIssue.Kind.WireOfResult("year-differs")
      case StatusDiffers           => _root_.com.histmersion.api.exchange.MergeIssue.Kind.WireOfResult("status-differs")
      case TitleDiffers            => _root_.com.histmersion.api.exchange.MergeIssue.Kind.WireOfResult("title-differs")
      case AuthorsDiffers          => _root_.com.histmersion.api.exchange.MergeIssue.Kind.WireOfResult("authors-differs")
      case SummaryDiffers          => _root_.com.histmersion.api.exchange.MergeIssue.Kind.WireOfResult("summary-differs")
      case NotesDiffers            => _root_.com.histmersion.api.exchange.MergeIssue.Kind.WireOfResult("notes-differs")
      case PeriodDiffers           => _root_.com.histmersion.api.exchange.MergeIssue.Kind.WireOfResult("period-differs")
      case BodyDiffers             => _root_.com.histmersion.api.exchange.MergeIssue.Kind.WireOfResult("body-differs")
      case ChangedBothSides        => _root_.com.histmersion.api.exchange.MergeIssue.Kind.WireOfResult("changed-both-sides")
      case DeletedThereChangedHere => _root_.com.histmersion.api.exchange.MergeIssue.Kind.WireOfResult("deleted-there-changed-here")
      case ArticleDiverged         => _root_.com.histmersion.api.exchange.MergeIssue.Kind.WireOfResult("article-diverged")
      case LabelTaken              => _root_.com.histmersion.api.exchange.MergeIssue.Kind.WireOfResult("label-taken")
      case AliasConflict           => _root_.com.histmersion.api.exchange.MergeIssue.Kind.WireOfResult("alias-conflict")
      case FindingNearDuplicate    => _root_.com.histmersion.api.exchange.MergeIssue.Kind.WireOfResult("finding-near-duplicate")
      case AttestationUnanchored   => _root_.com.histmersion.api.exchange.MergeIssue.Kind.WireOfResult("attestation-unanchored")
      case PlaceDiffers            => _root_.com.histmersion.api.exchange.MergeIssue.Kind.WireOfResult("place-differs")
      case NamedPeriodDiffers      => _root_.com.histmersion.api.exchange.MergeIssue.Kind.WireOfResult("named-period-differs")
      case GoalPlanDiffers         => _root_.com.histmersion.api.exchange.MergeIssue.Kind.WireOfResult("goal-plan-differs")

    given WireEnumValues[Kind]:
      def all: Array[Kind] = Kind.values
      def wire(e: Kind): _root_.com.histmersion.api.WireEnumValues.WireResult = _root_.com.histmersion.api.WireEnumValues.WireResult((wireOf(e)).value)
      def fromWire(s: _root_.com.histmersion.api.WireEnumValues.FromWireS): _root_.scala.Option[Kind] = Kind.values.find(k => wireOf(k) == _root_.com.histmersion.api.exchange.MergeIssue.Kind.WireOfResult((s).value.trim))

  /** How much an issue holds the merge up. */
  enum Severity:
    /** Nothing is merged until it has a decision. */
    case Blocking

    /** Has a default that is applied unless overridden. */
    case Default

    /** Reported only. */
    case Info

  object Severity:
    type WireResult = WireResult.T
    object WireResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type FromWireS = FromWireS.T
    object FromWireS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    given WireEnumValues[Severity]:
      def all: Array[Severity] = Severity.values
      def wire(e: Severity): _root_.com.histmersion.api.WireEnumValues.WireResult = _root_.com.histmersion.api.WireEnumValues.WireResult((Ascii.lower(_root_.com.histmersion.api.Ascii.LowerS(e.toString))).value)
      def fromWire(s: _root_.com.histmersion.api.WireEnumValues.FromWireS): _root_.scala.Option[Severity] = Severity.values.find(_.toString.equalsIgnoreCase((s).value.trim))

  /** Who decided an issue. */
  enum DecidedBy:
    /** A resolution (the file or `--resolve`). */
    case Resolution

    /** A policy (`--editions`). */
    case Policy

    /** The issue's default, nobody overrode it. */
    case Default

    /** The decision an earlier `merge apply` carried out for this very issue (the same records, unchanged): remembered,
      * as git's rerere does, so a decision is never asked twice.
      */
    case Remembered

  object DecidedBy:
    type WireResult = WireResult.T
    object WireResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type FromWireS = FromWireS.T
    object FromWireS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    given WireEnumValues[DecidedBy]:
      def all: Array[DecidedBy] = DecidedBy.values
      def wire(e: DecidedBy): _root_.com.histmersion.api.WireEnumValues.WireResult = _root_.com.histmersion.api.WireEnumValues.WireResult((Ascii.lower(_root_.com.histmersion.api.Ascii.LowerS(e.toString))).value)
      def fromWire(s: _root_.com.histmersion.api.WireEnumValues.FromWireS): _root_.scala.Option[DecidedBy] = DecidedBy.values.find(_.toString.equalsIgnoreCase((s).value.trim))


  object Detail:

    type SourceFieldsListXs = SourceFieldsListXs.T
    object SourceFieldsListXs extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    type DescribeResult = DescribeResult.T
    object DescribeResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type RangeTextResult = RangeTextResult.T
    object RangeTextResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type PreviewResult = PreviewResult.T
    object PreviewResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type PreviewS = PreviewS.T
    object PreviewS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type PreviewN = PreviewN.T
    object PreviewN extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ClipResult = ClipResult.T
    object ClipResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ClipS = ClipS.T
    object ClipS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ClipN = ClipN.T
    object ClipN extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type PlanTextResult = PlanTextResult.T
    object PlanTextResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    def describe(s: Bundle.Source): List[com.histmersion.api.exchange.MergeIssue.Detail.DescribeResult] =
      val who = s.authors.take(2).mkString("; ") + (if s.authors.length > 2 then " et al." else "")
      List(
        _root_.com.histmersion.api.exchange.MergeIssue.Detail.DescribeResult(s"\"${s.title}\""),
        _root_.com.histmersion.api.exchange.MergeIssue.Detail.DescribeResult(List(
          Some(who).filter(_.nonEmpty),
          s.year.map(_.toString),
          Some(s.doi.fold("no DOI")(d => s"DOI $d")),
          Some(s.status.wire)
        ).flatten.mkString(" · "))
      )

    def side(s: Bundle.Source): IssueSide = IssueSide(s.citekey, s.uid, describe(s))

    def rangeText(r: Option[Bundle.Range]): com.histmersion.api.exchange.MergeIssue.Detail.RangeTextResult = r match
      case None                           => _root_.com.histmersion.api.exchange.MergeIssue.Detail.RangeTextResult("none")
      case Some(Bundle.Range(None, None)) => _root_.com.histmersion.api.exchange.MergeIssue.Detail.RangeTextResult("always")
      case Some(b)                        => _root_.com.histmersion.api.exchange.MergeIssue.Detail.RangeTextResult(s"${b.start.fold("…")(_.toString)}..${b.end.fold("…")(_.toString)}")

    def preview(s: com.histmersion.api.exchange.MergeIssue.Detail.PreviewS, n: com.histmersion.api.exchange.MergeIssue.Detail.PreviewN = _root_.com.histmersion.api.exchange.MergeIssue.Detail.PreviewN(80)): com.histmersion.api.exchange.MergeIssue.Detail.PreviewResult =
      val one = (s).value.replaceAll("\\s+", " ").trim
      if one.length <= (n).value then _root_.com.histmersion.api.exchange.MergeIssue.Detail.PreviewResult(one) else _root_.com.histmersion.api.exchange.MergeIssue.Detail.PreviewResult(one.take((n).value - 1) + "…")

    def span(r: Bundle.Range): IssueSpan = IssueSpan(r.start, r.end)

    def eventSpan(e: Bundle.Event): Option[IssueSpan] =
      val (a, b) = (e.notBefore.orElse(e.beginBy), e.notAfter.orElse(e.endFrom))
      Option.when(a.isDefined || b.isDefined)(IssueSpan(a, b))

    def clip(s: Option[com.histmersion.api.exchange.MergeIssue.Detail.ClipS], n: com.histmersion.api.exchange.MergeIssue.Detail.ClipN = _root_.com.histmersion.api.exchange.MergeIssue.Detail.ClipN(600)): Option[com.histmersion.api.exchange.MergeIssue.Detail.ClipResult] =
      s.map((_).value.trim).filter(_.nonEmpty).map(t => if t.length <= (n).value then _root_.com.histmersion.api.exchange.MergeIssue.Detail.ClipResult(t) else _root_.com.histmersion.api.exchange.MergeIssue.Detail.ClipResult(t.take((n).value - 1) + "…"))

    def sourceFields(l: Bundle.Source, r: Bundle.Source): List[IssueField] =
      def range(p: Option[Bundle.Range]) = p.map(x => rangeText(Some(x)))
      def list(xs: List[com.histmersion.api.exchange.MergeIssue.Detail.SourceFieldsListXs]) = Option(xs.mkString("; ")).filter(_.nonEmpty)
      List(
        IssueField(_root_.com.histmersion.api.exchange.IssueField.Field("citekey"), Some(l.citekey), Some(r.citekey)),
        IssueField(_root_.com.histmersion.api.exchange.IssueField.Field("title"), Some(l.title), Some(r.title)),
        IssueField(_root_.com.histmersion.api.exchange.IssueField.Field("authors"), list(l.authors), list(r.authors)),
        IssueField(_root_.com.histmersion.api.exchange.IssueField.Field("year"), l.year.map(_root_.com.histmersion.api.exchange.IssueField.Left(_.toString)), r.year.map(_root_.com.histmersion.api.exchange.IssueField.Right(_.toString))),
        IssueField(_root_.com.histmersion.api.exchange.IssueField.Field("DOI"), l.doi, r.doi),
        IssueField(_root_.com.histmersion.api.exchange.IssueField.Field("venue"), l.venue, r.venue),
        IssueField(_root_.com.histmersion.api.exchange.IssueField.Field("status"), Some(l.status.wire), Some(r.status.wire)),
        IssueField(_root_.com.histmersion.api.exchange.IssueField.Field("period"), range(l.period), range(r.period)),
        IssueField(_root_.com.histmersion.api.exchange.IssueField.Field("tags"), list(l.tags), list(r.tags)),
        IssueField(_root_.com.histmersion.api.exchange.IssueField.Field("summary"), clip(l.summary), clip(r.summary)),
        IssueField(_root_.com.histmersion.api.exchange.IssueField.Field("notes"), clip(l.notes), clip(r.notes)),
        IssueField(
          _root_.com.histmersion.api.exchange.IssueField.Field("OCR body"),
          l.bodySha256.map(h => s"sha256 ${h.take(12)}…"),
          r.bodySha256.map(h => s"sha256 ${h.take(12)}…")
        )
      )

    def eventDetail(l: Bundle.Event, r: Bundle.Event): IssueDetail =
      def dates(e: Bundle.Event) = Some(
        List(e.notBefore, e.beginBy, e.endFrom, e.notAfter).map(_.fold("…")(_.toString)).mkString(" · ")
      )
      IssueDetail(
        fields = List(
          IssueField(_root_.com.histmersion.api.exchange.IssueField.Field("id"), Some(l.id), Some(r.id)),
          IssueField(_root_.com.histmersion.api.exchange.IssueField.Field("label"), Some(l.label), Some(r.label)),
          IssueField(_root_.com.histmersion.api.exchange.IssueField.Field("kind"), Some(l.kind), Some(r.kind)),
          IssueField(_root_.com.histmersion.api.exchange.IssueField.Field("dates (not before · begin by · end from · not after)"), (dates(l)).map(refactorValue43 => _root_.com.histmersion.api.exchange.IssueField.Left(refactorValue43)), (dates(r)).map(refactorValue44 => _root_.com.histmersion.api.exchange.IssueField.Right(refactorValue44))),
          IssueField(_root_.com.histmersion.api.exchange.IssueField.Field("place"), l.placeId, r.placeId),
          IssueField(_root_.com.histmersion.api.exchange.IssueField.Field("historicity"), Some(l.historicity), Some(r.historicity)),
          IssueField(_root_.com.histmersion.api.exchange.IssueField.Field("description"), clip(l.description), clip(r.description))
        ),
        place = Some(IssuePlace(l.geom, r.geom, eventSpan(l), eventSpan(r)))
      )

    def placeDetail(l: Bundle.Place, r: Bundle.Place): IssueDetail =
      IssueDetail(
        fields = List(
          IssueField(_root_.com.histmersion.api.exchange.IssueField.Field("label"), Some(l.label), Some(r.label)),
          IssueField(_root_.com.histmersion.api.exchange.IssueField.Field("kind"), Some(l.kind), Some(r.kind)),
          IssueField(_root_.com.histmersion.api.exchange.IssueField.Field("span"), Some(rangeText(l.period)), Some(rangeText(r.period))),
          IssueField(
            _root_.com.histmersion.api.exchange.IssueField.Field("names"),
            (Option(l.names.map(_.name).mkString("; ")).filter(_.nonEmpty)).map(refactorValue49 => _root_.com.histmersion.api.exchange.IssueField.Left(refactorValue49)),
            (Option(r.names.map(_.name).mkString("; ")).filter(_.nonEmpty)).map(refactorValue50 => _root_.com.histmersion.api.exchange.IssueField.Right(refactorValue50))
          )
        ),
        place = Some(IssuePlace(l.geom, r.geom, l.period.map(span), r.period.map(span)))
      )

    def planText(j: Json): com.histmersion.api.exchange.MergeIssue.Detail.PlanTextResult =
      j.as[Goal.Plans.Plan].toOption.fold(preview(_root_.com.histmersion.api.exchange.MergeIssue.Detail.PreviewS(j.noSpaces), _root_.com.histmersion.api.exchange.MergeIssue.Detail.PreviewN(300))) { p =>
        List(
          Option(p.rationale).filter(_.nonEmpty).map(r => preview(r, _root_.com.histmersion.api.exchange.MergeIssue.Detail.PreviewN(160))),
          Option.when(p.subGoals.nonEmpty)(s"sub-goals: ${p.subGoals.map(_.slug).mkString(", ")}"),
          Option.when(p.queries.nonEmpty)(s"queries: ${p.queries.mkString("; ")}"),
          Option.when(p.works.nonEmpty)(s"${Plural.count(_root_.com.histmersion.api.Plural.NounN((p.works.size).toLong), _root_.com.histmersion.api.Plural.NounSingular("work"))}")
        ).flatten.mkString(" · ")
      }
