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
    id: String,
    kind: MergeIssue.Kind,
    severity: MergeIssue.Severity,
    entity: com.histmersion.api.exchange.Bundle.Kind,
    title: String,
    left: Option[IssueSide] = None,
    right: Option[IssueSide] = None,
    why: List[String] = Nil,
    options: List[MergeDecision.Kind] = Nil,
    suggested: Option[MergeDecision] = None,
    condition: Option[IssueCondition] = None,
    decision: Option[MergeDecision] = None,
    decidedBy: Option[MergeIssue.DecidedBy] = None,
    detail: Option[IssueDetail] = None
) derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema:
  def open: Boolean = severity == MergeIssue.Severity.Blocking && condition.isEmpty && decision.isEmpty
object MergeIssue:

  /** What an issue is about. The wire name is part of the issue id, so it never changes. */
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
    def wireOf(k: Kind): String = k match
      case SameWork                => "same-work"
      case CitekeyCollision        => "citekey-collision"
      case DoiDiffers              => "doi-differs"
      case YearDiffers             => "year-differs"
      case StatusDiffers           => "status-differs"
      case TitleDiffers            => "title-differs"
      case AuthorsDiffers          => "authors-differs"
      case SummaryDiffers          => "summary-differs"
      case NotesDiffers            => "notes-differs"
      case PeriodDiffers           => "period-differs"
      case BodyDiffers             => "body-differs"
      case ChangedBothSides        => "changed-both-sides"
      case DeletedThereChangedHere => "deleted-there-changed-here"
      case ArticleDiverged         => "article-diverged"
      case LabelTaken              => "label-taken"
      case AliasConflict           => "alias-conflict"
      case FindingNearDuplicate    => "finding-near-duplicate"
      case AttestationUnanchored   => "attestation-unanchored"
      case PlaceDiffers            => "place-differs"
      case NamedPeriodDiffers      => "named-period-differs"
      case GoalPlanDiffers         => "goal-plan-differs"

    given WireEnumValues[Kind]:
      def all: Array[Kind] = Kind.values
      def wire(e: Kind): String = wireOf(e)
      def fromWire(s: String): Option[Kind] = Kind.values.find(k => wireOf(k) == s.trim)

  /** How much an issue holds the merge up. */
  enum Severity:
    /** Nothing is merged until it has a decision. */
    case Blocking

    /** Has a default that is applied unless overridden. */
    case Default

    /** Reported only. */
    case Info

  object Severity:
    given WireEnumValues[Severity]:
      def all: Array[Severity] = Severity.values
      def wire(e: Severity): String = Ascii.lower(e.toString)
      def fromWire(s: String): Option[Severity] = Severity.values.find(_.toString.equalsIgnoreCase(s.trim))

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
    given WireEnumValues[DecidedBy]:
      def all: Array[DecidedBy] = DecidedBy.values
      def wire(e: DecidedBy): String = Ascii.lower(e.toString)
      def fromWire(s: String): Option[DecidedBy] = DecidedBy.values.find(_.toString.equalsIgnoreCase(s.trim))


  object Detail:

    def describe(s: Bundle.Source): List[String] =
      val who = s.authors.take(2).mkString("; ") + (if s.authors.length > 2 then " et al." else "")
      List(
        s"\"${s.title}\"",
        List(
          Some(who).filter(_.nonEmpty),
          s.year.map(_.toString),
          Some(s.doi.fold("no DOI")(d => s"DOI $d")),
          Some(s.status.wire)
        ).flatten.mkString(" · ")
      )

    def side(s: Bundle.Source): IssueSide = IssueSide(s.citekey, s.uid, describe(s))

    def rangeText(r: Option[Bundle.Range]): String = r match
      case None                           => "none"
      case Some(Bundle.Range(None, None)) => "always"
      case Some(b)                        => s"${b.start.fold("…")(_.toString)}..${b.end.fold("…")(_.toString)}"

    def preview(s: String, n: Int = 80): String =
      val one = s.replaceAll("\\s+", " ").trim
      if one.length <= n then one else one.take(n - 1) + "…"

    def span(r: Bundle.Range): IssueSpan = IssueSpan(r.start, r.end)

    def eventSpan(e: Bundle.Event): Option[IssueSpan] =
      val (a, b) = (e.notBefore.orElse(e.beginBy), e.notAfter.orElse(e.endFrom))
      Option.when(a.isDefined || b.isDefined)(IssueSpan(a, b))

    def clip(s: Option[String], n: Int = 600): Option[String] =
      s.map(_.trim).filter(_.nonEmpty).map(t => if t.length <= n then t else t.take(n - 1) + "…")

    def sourceFields(l: Bundle.Source, r: Bundle.Source): List[IssueField] =
      def range(p: Option[Bundle.Range]) = p.map(x => rangeText(Some(x)))
      def list(xs: List[String]) = Option(xs.mkString("; ")).filter(_.nonEmpty)
      List(
        IssueField("citekey", Some(l.citekey), Some(r.citekey)),
        IssueField("title", Some(l.title), Some(r.title)),
        IssueField("authors", list(l.authors), list(r.authors)),
        IssueField("year", l.year.map(_.toString), r.year.map(_.toString)),
        IssueField("DOI", l.doi, r.doi),
        IssueField("venue", l.venue, r.venue),
        IssueField("status", Some(l.status.wire), Some(r.status.wire)),
        IssueField("period", range(l.period), range(r.period)),
        IssueField("tags", list(l.tags), list(r.tags)),
        IssueField("summary", clip(l.summary), clip(r.summary)),
        IssueField("notes", clip(l.notes), clip(r.notes)),
        IssueField(
          "OCR body",
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
          IssueField("id", Some(l.id), Some(r.id)),
          IssueField("label", Some(l.label), Some(r.label)),
          IssueField("kind", Some(l.kind), Some(r.kind)),
          IssueField("dates (not before · begin by · end from · not after)", dates(l), dates(r)),
          IssueField("place", l.placeId, r.placeId),
          IssueField("historicity", Some(l.historicity), Some(r.historicity)),
          IssueField("description", clip(l.description), clip(r.description))
        ),
        place = Some(IssuePlace(l.geom, r.geom, eventSpan(l), eventSpan(r)))
      )

    def placeDetail(l: Bundle.Place, r: Bundle.Place): IssueDetail =
      IssueDetail(
        fields = List(
          IssueField("label", Some(l.label), Some(r.label)),
          IssueField("kind", Some(l.kind), Some(r.kind)),
          IssueField("span", Some(rangeText(l.period)), Some(rangeText(r.period))),
          IssueField(
            "names",
            Option(l.names.map(_.name).mkString("; ")).filter(_.nonEmpty),
            Option(r.names.map(_.name).mkString("; ")).filter(_.nonEmpty)
          )
        ),
        place = Some(IssuePlace(l.geom, r.geom, l.period.map(span), r.period.map(span)))
      )

    def planText(j: Json): String =
      j.as[Goal.Plans.Plan].toOption.fold(preview(j.noSpaces, 300)) { p =>
        List(
          Option(p.rationale).filter(_.nonEmpty).map(r => preview(r, 160)),
          Option.when(p.subGoals.nonEmpty)(s"sub-goals: ${p.subGoals.map(_.slug).mkString(", ")}"),
          Option.when(p.queries.nonEmpty)(s"queries: ${p.queries.mkString("; ")}"),
          Option.when(p.works.nonEmpty)(s"${Plural.count(p.works.size, "work")}")
        ).flatten.mkString(" · ")
      }
