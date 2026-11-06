package com.histmersion.api.catalogue
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given

final case class EventImportReport(
    provider: EventImportReport.Provider,
    dryRun: EventImportReport.DryRun,
    records: EventImportReport.Records = _root_.com.histmersion.api.catalogue.EventImportReport.Records(0),
    kept: EventImportReport.Kept = _root_.com.histmersion.api.catalogue.EventImportReport.Kept(0),
    created: EventImportReport.Created = _root_.com.histmersion.api.catalogue.EventImportReport.Created(0),
    merged: EventImportReport.Merged = _root_.com.histmersion.api.catalogue.EventImportReport.Merged(0),
    matched: EventImportReport.Matched = _root_.com.histmersion.api.catalogue.EventImportReport.Matched(0),
    updated: EventImportReport.Updated = _root_.com.histmersion.api.catalogue.EventImportReport.Updated(0),
    byKind: List[EventCount] = Nil,
    skipped: List[EventCount] = Nil,
    matchedEvents: List[EventImportReport.MatchedEvents] = Nil,
    parts: EventImportReport.Parts = _root_.com.histmersion.api.catalogue.EventImportReport.Parts(0),
    pending: EventImportReport.Pending = _root_.com.histmersion.api.catalogue.EventImportReport.Pending(0),
    requests: EventImportReport.Requests = _root_.com.histmersion.api.catalogue.EventImportReport.Requests(0),
    failed: List[EventImportReport.Failed] = Nil,
    licence: EventImportReport.Licence = _root_.com.histmersion.api.catalogue.EventImportReport.Licence(""),
    citation: EventImportReport.Citation = _root_.com.histmersion.api.catalogue.EventImportReport.Citation(""),
    corrections: Correction.Counts = Correction.Counts()
) derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema
object EventImportReport:


  /** An events import job's report, in its detail. */
  type MarkdownResult = MarkdownResult.T
  object MarkdownResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type Provider = Provider.T
  object Provider extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DryRun = DryRun.T
  object DryRun extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Records = Records.T
  object Records extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Kept = Kept.T
  object Kept extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Created = Created.T
  object Created extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Merged = Merged.T
  object Merged extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Matched = Matched.T
  object Matched extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Updated = Updated.T
  object Updated extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MatchedEvents = MatchedEvents.T
  object MatchedEvents extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Parts = Parts.T
  object Parts extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Pending = Pending.T
  object Pending extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Requests = Requests.T
  object Requests extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Failed = Failed.T
  object Failed extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Licence = Licence.T
  object Licence extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Citation = Citation.T
  object Citation extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  val Detail: com.histmersion.api.jobs.JobDetail.Field[Option[EventImportReport]] =
    com.histmersion.api.jobs.JobDetail.Field(_root_.com.histmersion.api.jobs.JobDetail.Field.Name("eventImport"), None)

  def combine(a: EventImportReport, b: EventImportReport): EventImportReport =
    EventImportReport(
      b.provider,
      b.dryRun,
      _root_.com.histmersion.api.catalogue.EventImportReport.Records((a.records).value + (b.records).value),
      _root_.com.histmersion.api.catalogue.EventImportReport.Kept((a.kept).value + (b.kept).value),
      _root_.com.histmersion.api.catalogue.EventImportReport.Created((a.created).value + (b.created).value),
      _root_.com.histmersion.api.catalogue.EventImportReport.Merged((a.merged).value + (b.merged).value),
      _root_.com.histmersion.api.catalogue.EventImportReport.Matched((a.matched).value + (b.matched).value),
      _root_.com.histmersion.api.catalogue.EventImportReport.Updated((a.updated).value + (b.updated).value),
      EventCount.add(a.byKind, b.byKind),
      EventCount.add(a.skipped, b.skipped),
      (a.matchedEvents ++ b.matchedEvents).distinct.take(20),
      _root_.com.histmersion.api.catalogue.EventImportReport.Parts((a.parts).value + (b.parts).value),
      b.pending,
      _root_.com.histmersion.api.catalogue.EventImportReport.Requests((a.requests).value + (b.requests).value),
      a.failed ++ b.failed,
      if (b.licence).value.nonEmpty then b.licence else a.licence,
      if (b.citation).value.nonEmpty then b.citation else a.citation,
      a.corrections + b.corrections
    )

  def summary(r: EventImportReport): String =
    val verb = if (r.dryRun).value then "would create" else "created"
    (List(
      Some(s"${r.provider}: ${r.records} record(s) read, ${r.kept} kept"),
      Some(
        s"$verb ${r.created} event(s), ${r.merged} merged with another record of the same thing, ${r.matched} matched to stored events"
      ),
      Option.when(r.byKind.nonEmpty)("kinds: " + r.byKind.map(c => s"${c.key} ${c.n}").mkString(", ")),
      Option.when(r.skipped.nonEmpty)("skipped: " + r.skipped.map(c => s"${c.key} ${c.n}").mkString(", ")),
      Option.when((r.pending).value > 0)(s"${r.pending} part(s) not read yet"),
      Option.when(r.failed.nonEmpty)(s"${r.failed.size} failed: ${r.failed.take(3).mkString("; ")}"),
      r.corrections.summary
    ).flatten.mkString(" · "))

  def markdown(r: EventImportReport): com.histmersion.api.catalogue.EventImportReport.MarkdownResult =
    def counts(cs: List[EventCount]) = if cs.isEmpty then "none" else cs.map(c => s"${c.key} ${c.n}").mkString(" · ")
    val head = s"## Event import — ${r.provider}${if (r.dryRun).value then " (dry run: nothing stored)" else ""}"
    val lines = List(
      s"- records read: ${r.records} (${r.parts} request part(s), ${r.requests} HTTP request(s))",
      s"- kept: ${r.kept} — ${if (r.dryRun).value then "would create" else "created"} ${r.created} event(s), " +
        s"${r.merged} merged with another catalogue record, ${r.matched} matched to stored events" +
        (if (r.updated).value > 0 then s", ${r.updated} refreshed" else ""),
      s"- by kind: ${counts(r.byKind)}",
      s"- skipped: ${counts(r.skipped)}"
    ) ++
      Option.when(r.matchedEvents.nonEmpty)(s"- matched: ${r.matchedEvents.mkString(", ")}").toList ++
      Option.when((r.pending).value > 0)(s"- not read yet: ${r.pending} part(s) (the next run takes them)").toList ++
      Option.when(r.failed.nonEmpty)(s"- failed: ${r.failed.mkString("; ")}").toList ++
      r.corrections.summary.map(c => s"- $c (`histmersion events corrections`)").toList ++
      Option.when((r.licence).value.nonEmpty)(s"- licence: ${r.licence}").toList ++
      Option.when((r.citation).value.nonEmpty)(s"- cite as: ${r.citation}").toList
    _root_.com.histmersion.api.catalogue.EventImportReport.MarkdownResult((head :: "" :: lines).mkString("\n"))
