package com.histmersion
package api.catalogue
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.jobs.JobDetail

final case class EventImportReport(
    provider: EventImportReport.Provider,
    dryRun: EventImportReport.DryRun,
    records: EventImportReport.Records = EventImportReport.Records(0),
    kept: EventImportReport.Kept = EventImportReport.Kept(0),
    created: EventImportReport.Created = EventImportReport.Created(0),
    merged: EventImportReport.Merged = EventImportReport.Merged(0),
    matched: EventImportReport.Matched = EventImportReport.Matched(0),
    updated: EventImportReport.Updated = EventImportReport.Updated(0),
    byKind: List[EventCount] = Nil,
    skipped: List[EventCount] = Nil,
    matchedEvents: List[EventImportReport.MatchedEvents] = Nil,
    parts: EventImportReport.Parts = EventImportReport.Parts(0),
    pending: EventImportReport.Pending = EventImportReport.Pending(0),
    requests: EventImportReport.Requests = EventImportReport.Requests(0),
    failed: List[EventImportReport.Failed] = Nil,
    licence: EventImportReport.Licence = EventImportReport.Licence(""),
    citation: EventImportReport.Citation = EventImportReport.Citation(""),
    corrections: Correction.Counts = Correction.Counts()
) derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema
object EventImportReport:
  type Markdown = Markdown.T
  object Markdown extends Newtype[String, Pure]


  /** An events import job's report, in its detail. */
  type MarkdownResult = Markdown
  val MarkdownResult: Markdown.type = Markdown

  type Provider = Provider.T
  object Provider extends Newtype[String, Pure]
  type DryRun = DryRun.T
  object DryRun extends Newtype[Boolean, Pure]
  type Records = Records.T
  object Records extends Newtype[Int, Pure]
  type Kept = Kept.T
  object Kept extends Newtype[Int, Pure]
  type Created = Created.T
  object Created extends Newtype[Int, Pure]
  type Merged = Merged.T
  object Merged extends Newtype[Int, Pure]
  type Matched = Matched.T
  object Matched extends Newtype[Int, Pure]
  type Updated = Updated.T
  object Updated extends Newtype[Int, Pure]
  type MatchedEvents = MatchedEvents.T
  object MatchedEvents extends Newtype[String, Pure]
  type Parts = Parts.T
  object Parts extends Newtype[Int, Pure]
  type Pending = Pending.T
  object Pending extends Newtype[Int, Pure]
  type Requests = Requests.T
  object Requests extends Newtype[Int, Pure]
  type Failed = Failed.T
  object Failed extends Newtype[String, Pure]
  type Licence = Licence.T
  object Licence extends Newtype[String, Pure]
  type Citation = Citation.T
  object Citation extends Newtype[String, Pure]

  val Detail: JobDetail.Field[Option[EventImportReport]] =
    new com.histmersion.api.jobs.JobDetail.Field(JobDetail.Field.Name("eventImport"), None)

  def combine(a: EventImportReport, b: EventImportReport): EventImportReport =
    EventImportReport(
      b.provider,
      b.dryRun,
      Records((a.records).value + (b.records).value),
      Kept((a.kept).value + (b.kept).value),
      Created((a.created).value + (b.created).value),
      Merged((a.merged).value + (b.merged).value),
      Matched((a.matched).value + (b.matched).value),
      Updated((a.updated).value + (b.updated).value),
      EventCount.add(a.byKind, b.byKind),
      EventCount.add(a.skipped, b.skipped),
      (a.matchedEvents ++ b.matchedEvents).distinct.take(20),
      Parts((a.parts).value + (b.parts).value),
      b.pending,
      Requests((a.requests).value + (b.requests).value),
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

  def markdown(r: EventImportReport): Markdown =
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
    Markdown((head :: "" :: lines).mkString("\n"))
