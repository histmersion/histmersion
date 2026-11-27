package com.histmersion
package api.exchange
import com.histmersion.util.json.RawJson.*
import hearth.kindlings.jsoniterjson.Json

import com.histmersion.api.{Ascii, WireEnumValues}
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.api.Plural
import com.histmersion.api.automation.Goal
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.catalogue.Year
import MergeIssue.Rationale
import com.histmersion.lint.{tolerated, LintRule}

@tolerated(LintRule.Direction, "pending G12 (a domain names only lower places)")
final case class MergeIssue(
    id: MergeIssue.Id,
    kind: MergeIssue.Kind,
    severity: MergeIssue.Severity,
    entity: Bundle.Kind,
    title: MergeIssue.Title,
    left: Option[IssueSide] = None,
    right: Option[IssueSide] = None,
    why: List[Rationale] = Nil,
    options: List[MergeDecision.Kind] = Nil,
    suggested: Option[MergeDecision] = None,
    condition: Option[IssueCondition] = None,
    decision: Option[MergeDecision] = None,
    decidedBy: Option[MergeIssue.DecidedBy] = None,
    detail: Option[IssueDetail] = None
) derives JsonCodec,
      KindlingsSchema,
      api.json.Schema:
  def open: Boolean = (severity == MergeIssue.Severity.Blocking && condition.isEmpty && decision.isEmpty)
object MergeIssue:

  /** What an issue is about. The wire name is part of the issue id, so it never changes. */

  type Id = Id.T
  object Id extends Newtype[String, Pure]
  type Title = Title.T
  object Title extends Newtype[String, Pure]:
    given Display[T] = Display(_.value)

  /** Why the planner raises an issue or suggests a choice: the account of its decision, not a failure. */
  type Rationale = Rationale.T
  object Rationale extends Newtype[String, Pure]

  enum Kind derives CanEqual:
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
    type Wire = Wire.T
    object Wire extends Newtype[String, Pure]

    def wireOf(k: Kind): Wire = k match
      case SameWork                => Wire("same-work")
      case CitekeyCollision        => Wire("citekey-collision")
      case DoiDiffers              => Wire("doi-differs")
      case YearDiffers             => Wire("year-differs")
      case StatusDiffers           => Wire("status-differs")
      case TitleDiffers            => Wire("title-differs")
      case AuthorsDiffers          => Wire("authors-differs")
      case SummaryDiffers          => Wire("summary-differs")
      case NotesDiffers            => Wire("notes-differs")
      case PeriodDiffers           => Wire("period-differs")
      case BodyDiffers             => Wire("body-differs")
      case ChangedBothSides        => Wire("changed-both-sides")
      case DeletedThereChangedHere => Wire("deleted-there-changed-here")
      case ArticleDiverged         => Wire("article-diverged")
      case LabelTaken              => Wire("label-taken")
      case AliasConflict           => Wire("alias-conflict")
      case FindingNearDuplicate    => Wire("finding-near-duplicate")
      case AttestationUnanchored   => Wire("attestation-unanchored")
      case PlaceDiffers            => Wire("place-differs")
      case NamedPeriodDiffers      => Wire("named-period-differs")
      case GoalPlanDiffers         => Wire("goal-plan-differs")

    given WireEnumValues[Kind]:
      def all: Array[Kind] = values
      @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
      def wire(e: Kind): WireEnumValues.Wire = WireEnumValues.Wire((wireOf(e)).value)
      @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
      def fromWire(s: WireEnumValues.Wire): Option[Kind] = values.find(k => wireOf(k) == Wire((s).value.trim))

  /** How much an issue holds the merge up. */
  enum Severity derives CanEqual:
    /** Nothing is merged until it has a decision. */
    case Blocking

    /** Has a default that is applied unless overridden. */
    case Default

    /** Reported only. */
    case Info

  object Severity:
    type Wire = Wire.T
    object Wire extends Newtype[String, Pure]

    given WireEnumValues[Severity]:
      def all: Array[Severity] = values
      @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
      def wire(e: Severity): WireEnumValues.Wire = WireEnumValues.Wire((Ascii.lower((e.toString))))
      @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
      def fromWire(s: WireEnumValues.Wire): Option[Severity] = values.find(_.toString.equalsIgnoreCase((s).value.trim))

  /** Who decided an issue. */
  enum DecidedBy derives CanEqual:
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
    type Wire = Wire.T
    object Wire extends Newtype[String, Pure]

    given WireEnumValues[DecidedBy]:
      def all: Array[DecidedBy] = values
      @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
      def wire(e: DecidedBy): WireEnumValues.Wire = WireEnumValues.Wire((Ascii.lower((e.toString))))
      @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
      def fromWire(s: WireEnumValues.Wire): Option[DecidedBy] = values.find(_.toString.equalsIgnoreCase((s).value.trim))

  object Detail:
    type ClipLength = ClipLength.T
    object ClipLength extends Newtype[Int, Pure]

    @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def describe(s: Bundle.Source): List[String] =
      val who = s.authors.take(2).mkString("; ") + (if s.authors.length > 2 then " et al." else "")
      List(
        (s"\"${s.title}\""),
        (List(
          Some(who).filter(_.nonEmpty),
          s.year.map(_.toString),
          Some(s.doi.fold("no DOI")(d => s"DOI $d")),
          Some(s.status.wire)
        ).flatten.mkString(" · "))
      )

    @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def side(s: Bundle.Source): IssueSide =
      IssueSide(IssueSide.Label(s.citekey.value), s.uid, describe(s).map(IssueSide.Lines(_)))

    def rangeText(r: Option[Bundle.Range]): String = r match
      case None                           => ("none")
      case Some(Bundle.Range(None, None)) => ("always")
      case Some(b)                        => (s"${b.start.fold("…")(_.toString)}..${b.end.fold("…")(_.toString)}")

    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def preview(s: String, n: ClipLength = ClipLength(80)): String =
      val one = (s).replaceAll("\\s+", " ").trim
      if one.length <= (n).value then (one) else (one.take((n).value - 1) + "…")

    @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
    def span(r: Bundle.Range): IssueSpan = IssueSpan(r.start, r.end)

    @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def eventSpan(e: Bundle.Event): Option[IssueSpan] =
      val a = e.notBefore.map(y => Year(y.value)).orElse(e.beginBy.map(y => Year(y.value)))
      val b = e.notAfter.map(y => Year(y.value)).orElse(e.endFrom.map(y => Year(y.value)))
      Option.when(a.isDefined || b.isDefined)(IssueSpan(a, b))

    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def clip(s: Option[String], n: ClipLength = ClipLength(600)): Option[String] =
      s.map((_).trim).filter(_.nonEmpty).map(t => if t.length <= (n).value then (t) else (t.take((n).value - 1) + "…"))

    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    private def field(name: String, l: Option[String], r: Option[String]): IssueField =
      IssueField(IssueField.Field(name), l.map(IssueField.Value(_)), r.map(IssueField.Value(_)))

    @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def sourceFields(l: Bundle.Source, r: Bundle.Source): List[IssueField] =
      def range(p: Option[Bundle.Range]) = p.map(x => rangeText(Some(x)))
      def list(xs: List[String]) = Option(xs.mkString("; ")).filter(_.nonEmpty)
      List(
        field("citekey", Some(l.citekey.value), Some(r.citekey.value)),
        field("title", Some(l.title.value), Some(r.title.value)),
        field("authors", list(l.authors.map(_.value)), list(r.authors.map(_.value))),
        field("year", l.year.map(_.value.toString), r.year.map(_.value.toString)),
        field("DOI", l.doi.map(_.value), r.doi.map(_.value)),
        field("venue", l.venue.map(_.value), r.venue.map(_.value)),
        field("status", Some(l.status.wire.value), Some(r.status.wire.value)),
        field("period", range(l.period), range(r.period)),
        field("tags", list(l.tags.map(_.value)), list(r.tags.map(_.value))),
        field("summary", clip(l.summary.map(_.value)), clip(r.summary.map(_.value))),
        field("notes", clip(l.notes.map(_.value)), clip(r.notes.map(_.value))),
        field(
          "OCR body",
          l.bodySha256.map(h => s"sha256 ${h.value.take(12)}…"),
          r.bodySha256.map(h => s"sha256 ${h.value.take(12)}…")
        )
      )

    @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def eventDetail(l: Bundle.Event, r: Bundle.Event): IssueDetail =
      def dates(e: Bundle.Event) = Some(
        List(e.notBefore, e.beginBy, e.endFrom, e.notAfter).map(_.fold("…")(_.toString)).mkString(" · ")
      )
      IssueDetail(
        fields = List(
          field("id", Some(l.id.value), Some(r.id.value)),
          field("label", Some(l.label.value), Some(r.label.value)),
          field("kind", Some(l.kind.value), Some(r.kind.value)),
          field("dates (not before · begin by · end from · not after)", dates(l), dates(r)),
          field("place", l.placeId.map(_.value), r.placeId.map(_.value)),
          field("historicity", Some(l.historicity.value), Some(r.historicity.value)),
          field("description", clip(l.description.map(_.value)), clip(r.description.map(_.value)))
        ),
        place = Some(
          IssuePlace(
            l.geom.map(g => IssuePlace.LeftGeometry(g.value)),
            r.geom.map(g => IssuePlace.RightGeometry(g.value)),
            eventSpan(l),
            eventSpan(r)
          )
        )
      )

    @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def placeDetail(l: Bundle.Place, r: Bundle.Place): IssueDetail =
      IssueDetail(
        fields = List(
          field("label", Some(l.label.value), Some(r.label.value)),
          field("kind", Some(l.kind.value), Some(r.kind.value)),
          field("span", Some(rangeText(l.period)), Some(rangeText(r.period))),
          field(
            "names",
            Option(l.names.map(_.name).mkString("; ")).filter(_.nonEmpty),
            Option(r.names.map(_.name).mkString("; ")).filter(_.nonEmpty)
          )
        ),
        place = Some(
          IssuePlace(
            l.geom.map(g => IssuePlace.LeftGeometry(g.value)),
            r.geom.map(g => IssuePlace.RightGeometry(g.value)),
            l.period.map(span),
            r.period.map(span)
          )
        )
      )

    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def planText(j: Json): String =
      j.as[Goal.Plans.Plan].toOption.fold(preview((j.noSpaces), ClipLength(300))) { p =>
        List(
          Option(p.rationale.value).filter(_.nonEmpty).map(r => preview(r, ClipLength(160))),
          Option.when(p.subGoals.nonEmpty)(s"sub-goals: ${p.subGoals.map(_.slug).mkString(", ")}"),
          Option.when(p.queries.nonEmpty)(s"queries: ${p.queries.mkString("; ")}"),
          Option.when(p.works.nonEmpty)(
            s"${Plural.count(Plural.Count((p.works.size).toLong), Plural.Noun.Singular("work"))}"
          )
        ).flatten.mkString(" · ")
      }
