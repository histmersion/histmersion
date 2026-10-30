package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

/** Events from CATALOGUES (#1449): the Smithsonian's Holocene eruptions, NOAA's earthquakes, eruptions and tsunamis,
  * Wikidata's battles, sieges, wars, treaties… — every event a catalogue knows, not only the ones a compiled source
  * names. A catalogue event is a RESEARCH LEAD, not evidence: it is drawn lighter than an event a source in the corpus
  * attests (docs/visualization-guide.md §1), and its card names the catalogue, its licence and its citation as its
  * provenance.
  *
  * This file is the pure half: the providers' identities and terms, the mapping of their datings onto the four-point
  * time-span, the records an import reads, how records from several catalogues and the events already stored are merged
  * into ONE event, the report of an import, and the shapes of the proposed links to the corpus and of the questions an
  * event raises.
  */

// ── providers ───────────────────────────────────────────────────────────────────────────────────

/** What an import read and did (a dry run: would do). `records` read from the catalogue; `skipped` per reason (no
  * position, below the threshold, outside the years…); `kept` records that pass; `created` new events, `merged` records
  * joined to another record of this run, `matched` records joined to an event already stored (`matchedEvents` names a
  * few); `byKind` the kept records' kinds; `parts` the catalogue requests (a class, a page) done, `pending` those left
  * for the next run.
  */
final case class EventImportReport(
    provider: String,
    dryRun: Boolean,
    records: Int = 0,
    kept: Int = 0,
    created: Int = 0,
    merged: Int = 0,
    matched: Int = 0,
    updated: Int = 0,
    byKind: List[EventCount] = Nil,
    skipped: List[EventCount] = Nil,
    matchedEvents: List[String] = Nil,
    parts: Int = 0,
    pending: Int = 0,
    requests: Int = 0,
    failed: List[String] = Nil,
    licence: String = "",
    citation: String = "",
    /** The refreshed events' corrections by hand ([[Corrections]]): kept, retired (the catalogue now says what the
      * person said), and in conflict (the catalogue changed to something else).
      */
    corrections: CorrectionCounts = CorrectionCounts()
) derives KindlingsCodecAsObject,
      KindlingsSchema
object EventImportReport:
  given Schema[EventImportReport] = summon[KindlingsSchema[EventImportReport]].schema

  def combine(a: EventImportReport, b: EventImportReport): EventImportReport =
    EventImportReport(
      b.provider,
      b.dryRun,
      a.records + b.records,
      a.kept + b.kept,
      a.created + b.created,
      a.merged + b.merged,
      a.matched + b.matched,
      a.updated + b.updated,
      EventCount.add(a.byKind, b.byKind),
      EventCount.add(a.skipped, b.skipped),
      (a.matchedEvents ++ b.matchedEvents).distinct.take(20),
      a.parts + b.parts,
      b.pending,
      a.requests + b.requests,
      a.failed ++ b.failed,
      if b.licence.nonEmpty then b.licence else a.licence,
      if b.citation.nonEmpty then b.citation else a.citation,
      a.corrections + b.corrections
    )

  /** Pure: the one line `histmersion jobs` shows for a finished import. */
  def summary(r: EventImportReport): String =
    val verb = if r.dryRun then "would create" else "created"
    List(
      Some(s"${r.provider}: ${r.records} record(s) read, ${r.kept} kept"),
      Some(
        s"$verb ${r.created} event(s), ${r.merged} merged with another record of the same thing, ${r.matched} matched to stored events"
      ),
      Option.when(r.byKind.nonEmpty)("kinds: " + r.byKind.map(c => s"${c.key} ${c.n}").mkString(", ")),
      Option.when(r.skipped.nonEmpty)("skipped: " + r.skipped.map(c => s"${c.key} ${c.n}").mkString(", ")),
      Option.when(r.pending > 0)(s"${r.pending} part(s) not read yet"),
      Option.when(r.failed.nonEmpty)(s"${r.failed.size} failed: ${r.failed.take(3).mkString("; ")}"),
      r.corrections.summary
    ).flatten.mkString(" · ")

  /** Pure: the report as Markdown (the CLI after the job, the MCP answer). */
  def markdown(r: EventImportReport): String =
    def counts(cs: List[EventCount]) = if cs.isEmpty then "none" else cs.map(c => s"${c.key} ${c.n}").mkString(" · ")
    val head = s"## Event import — ${r.provider}${if r.dryRun then " (dry run: nothing stored)" else ""}"
    val lines = List(
      s"- records read: ${r.records} (${r.parts} request part(s), ${r.requests} HTTP request(s))",
      s"- kept: ${r.kept} — ${if r.dryRun then "would create" else "created"} ${r.created} event(s), " +
        s"${r.merged} merged with another catalogue record, ${r.matched} matched to stored events" +
        (if r.updated > 0 then s", ${r.updated} refreshed" else ""),
      s"- by kind: ${counts(r.byKind)}",
      s"- skipped: ${counts(r.skipped)}"
    ) ++
      Option.when(r.matchedEvents.nonEmpty)(s"- matched: ${r.matchedEvents.mkString(", ")}").toList ++
      Option.when(r.pending > 0)(s"- not read yet: ${r.pending} part(s) (the next run takes them)").toList ++
      Option.when(r.failed.nonEmpty)(s"- failed: ${r.failed.mkString("; ")}").toList ++
      r.corrections.summary.map(c => s"- $c (`histmersion events corrections`)").toList ++
      Option.when(r.licence.nonEmpty)(s"- licence: ${r.licence}").toList ++
      Option.when(r.citation.nonEmpty)(s"- cite as: ${r.citation}").toList
    (head :: "" :: lines).mkString("\n")
