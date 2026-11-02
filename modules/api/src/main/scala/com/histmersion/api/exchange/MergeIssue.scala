package com.histmersion.api.exchange
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

import com.histmersion.util.data.exchange.DecisionKind
import com.histmersion.api.given
import com.histmersion.util.data.exchange.Issue
import com.histmersion.api.Plural
import com.histmersion.api.automation.Goal
import io.circe.Json

final case class MergeIssue(
    id: String,
    kind: Issue.Kind,
    severity: Issue.Severity,
    entity: com.histmersion.util.data.exchange.Bundle.Kind,
    title: String,
    left: Option[IssueSide] = None,
    right: Option[IssueSide] = None,
    why: List[String] = Nil,
    options: List[DecisionKind] = Nil,
    suggested: Option[MergeDecision] = None,
    condition: Option[IssueCondition] = None,
    decision: Option[MergeDecision] = None,
    decidedBy: Option[Issue.DecidedBy] = None,
    detail: Option[IssueDetail] = None
) derives KindlingsCodecAsObject,
      KindlingsSchema:
  def open: Boolean = severity == Issue.Severity.Blocking && condition.isEmpty && decision.isEmpty
object MergeIssue:
  given Schema[MergeIssue] = summon[KindlingsSchema[MergeIssue]].schema

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
