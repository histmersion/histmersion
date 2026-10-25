package com.histmersion.api

import io.circe.Json

/** What the merge review screen shows beside an issue: a pair of bundle lines described as the issue's
  * sides, fields, spans and places, and an approved goal plan as one comparable line. Pure. */
object MergeIssueDetail:

  def describe(s: BundleSource): List[String] =
    val who = s.authors.take(2).mkString("; ") + (if s.authors.length > 2 then " et al." else "")
    List(s"\"${s.title}\"",
      List(Some(who).filter(_.nonEmpty), s.year.map(_.toString), Some(s.doi.fold("no DOI")(d => s"DOI $d")),
        Some(s.status.wire)).flatten.mkString(" · "))

  def side(s: BundleSource): IssueSide = IssueSide(s.citekey, s.uid, describe(s))

  def rangeText(r: Option[BundleRange]): String = r match
    case None                              => "none"
    case Some(BundleRange(None, None))     => "always"
    case Some(b) => s"${b.start.fold("…")(_.toString)}..${b.end.fold("…")(_.toString)}"

  def preview(s: String, n: Int = 80): String =
    val one = s.replaceAll("\\s+", " ").trim
    if one.length <= n then one else one.take(n - 1) + "…"

  def span(r: BundleRange): IssueSpan = IssueSpan(r.start, r.end)

  def eventSpan(e: BundleEvent): Option[IssueSpan] =
    val (a, b) = (e.notBefore.orElse(e.beginBy), e.notAfter.orElse(e.endFrom))
    Option.when(a.isDefined || b.isDefined)(IssueSpan(a, b))

  def clip(s: Option[String], n: Int = 600): Option[String] =
    s.map(_.trim).filter(_.nonEmpty).map(t => if t.length <= n then t else t.take(n - 1) + "…")

  /** A source pair, field by field (the three-column table: here | result | bundle). */
  def sourceFields(l: BundleSource, r: BundleSource): List[IssueField] =
    def range(p: Option[BundleRange]) = p.map(x => rangeText(Some(x)))
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
      IssueField("OCR body", l.bodySha256.map(h => s"sha256 ${h.take(12)}…"), r.bodySha256.map(h => s"sha256 ${h.take(12)}…")))

  /** An event pair: its fields, both geometries (hex EWKB here; the database turns them into GeoJSON
    * for the screen) and spans. */
  def eventDetail(l: BundleEvent, r: BundleEvent): IssueDetail =
    def dates(e: BundleEvent) = Some(List(e.notBefore, e.beginBy, e.endFrom, e.notAfter).map(_.fold("…")(_.toString)).mkString(" · "))
    IssueDetail(
      fields = List(IssueField("id", Some(l.id), Some(r.id)), IssueField("label", Some(l.label), Some(r.label)),
        IssueField("kind", Some(l.kind), Some(r.kind)),
        IssueField("dates (not before · begin by · end from · not after)", dates(l), dates(r)),
        IssueField("place", l.placeId, r.placeId), IssueField("historicity", Some(l.historicity), Some(r.historicity)),
        IssueField("description", clip(l.description), clip(r.description))),
      place = Some(IssuePlace(l.geom, r.geom, eventSpan(l), eventSpan(r))))

  def placeDetail(l: BundlePlace, r: BundlePlace): IssueDetail =
    IssueDetail(
      fields = List(IssueField("label", Some(l.label), Some(r.label)), IssueField("kind", Some(l.kind), Some(r.kind)),
        IssueField("span", Some(rangeText(l.period)), Some(rangeText(r.period))),
        IssueField("names", Option(l.names.map(_.name).mkString("; ")).filter(_.nonEmpty),
          Option(r.names.map(_.name).mkString("; ")).filter(_.nonEmpty))),
      place = Some(IssuePlace(l.geom, r.geom, l.period.map(span), r.period.map(span))))

  /** An approved plan, as one line a person can compare. */
  def planText(j: Json): String =
    j.as[GoalPlans.Plan].toOption.fold(preview(j.noSpaces, 300)) { p =>
      List(Option(p.rationale).filter(_.nonEmpty).map(r => preview(r, 160)),
        Option.when(p.subGoals.nonEmpty)(s"sub-goals: ${p.subGoals.map(_.slug).mkString(", ")}"),
        Option.when(p.queries.nonEmpty)(s"queries: ${p.queries.mkString("; ")}"),
        Option.when(p.works.nonEmpty)(s"${Plural.count(p.works.size, "work")}")).flatten.mkString(" · ")
    }
