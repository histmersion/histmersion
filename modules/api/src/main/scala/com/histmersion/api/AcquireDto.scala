package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.values.SourceStatus

/** Wire DTOs of the acquisition & assessment surface (plan §5.2 stream 2.6) — the ports of `discover.py`, `refs.py`,
  * `assess.py`, `followups.py`, `fetch_oa.py`, `archive_org.py` and the Calibre tools. Circe codec and tapir Schema are
  * both derived from each case class, so the REST body, the MCP tool schema and the CLI's `--json` output are the same
  * shape by construction.
  *
  * These are DTOs only — every rule that operates on them is a pure function in [[Acquire]], and every network call
  * sits behind a trait in the server module.
  */

// ── discover (OpenAlex / Crossref / Wikipedia) ────────────────────────────────────────────────

/** One work as OpenAlex/Crossref describe it, normalised the way `discover.py normalize_work` did.
  *
  * @param citekey
  *   the citekey this work WOULD get (`Acquire.citekey`), so a caller can queue it
  * @param inDb
  *   whether a source with that DOI (or citekey) is already stored
  */
final case class WorkRef(
    doi: Option[String] = None,
    openalexId: Option[String] = None,
    arxivId: Option[String] = None,
    title: Option[String] = None,
    year: Option[Int] = None,
    authors: List[String] = Nil,
    venue: Option[String] = None,
    citedByCount: Option[Int] = None,
    isOa: Boolean = false,
    oaStatus: Option[String] = None,
    oaUrl: Option[String] = None,
    isRetracted: Boolean = false,
    workType: Option[String] = None,
    abstractText: Option[String] = None,
    referencedWorks: List[String] = Nil,
    citekey: Option[String] = None,
    inDb: Boolean = false,
    oaLocations: List[String] = Nil,
    oaLicence: Option[String] = None
) derives KindlingsCodecAsObject,
      KindlingsSchema
object WorkRef:
  given Schema[WorkRef] = summon[KindlingsSchema[WorkRef]].schema

final case class DiscoverResult(query: String, count: Int, works: List[WorkRef])
    derives KindlingsCodecAsObject,
      KindlingsSchema
object DiscoverResult:
  given Schema[DiscoverResult] = summon[KindlingsSchema[DiscoverResult]].schema

/** DOIs and arXiv ids harvested from a Wikipedia article's wikitext — the trailhead. */
final case class WikirefsResult(
    article: String,
    url: String,
    doiCount: Int,
    dois: List[String],
    arxiv: List[String],
    known: List[String] = Nil // the subset of `dois` already stored, as citekeys
) derives KindlingsCodecAsObject,
      KindlingsSchema
object WikirefsResult:
  given Schema[WikirefsResult] = summon[KindlingsSchema[WikirefsResult]].schema

// ── refs (Crossref reference list → `references` edges) ────────────────────────────────────────

final case class RefRow(
    title: Option[String] = None,
    authors: List[String] = Nil,
    year: Option[Int] = None,
    doi: Option[String] = None,
    raw: Option[String] = None,
    /** The stored source this reference resolved to, when it is already in the corpus. */
    citekey: Option[String] = None
) derives KindlingsCodecAsObject,
      KindlingsSchema
object RefRow:
  given Schema[RefRow] = summon[KindlingsSchema[RefRow]].schema

/** @param edgesAdded how many `references` edges were written (0 unless `apply`) */
final case class RefsResult(
    target: String,
    doi: Option[String],
    backend: String,
    count: Int,
    resolved: Int,
    edgesAdded: Int,
    references: List[RefRow]
) derives KindlingsCodecAsObject,
      KindlingsSchema
object RefsResult:
  given Schema[RefsResult] = summon[KindlingsSchema[RefsResult]].schema

// ── assess (Retraction Watch + Crossref update notices + citation metrics) ─────────────────────

final case class RetractionNotice(nature: Option[String], reason: Option[String], date: Option[String])
    derives KindlingsCodecAsObject,
      KindlingsSchema
object RetractionNotice:
  given Schema[RetractionNotice] = summon[KindlingsSchema[RetractionNotice]].schema

/** A Crossref `update-to` entry: a retraction, correction or expression of concern. */
final case class UpdateNotice(kind: Option[String], label: Option[String], doi: Option[String])
    derives KindlingsCodecAsObject,
      KindlingsSchema
object UpdateNotice:
  given Schema[UpdateNotice] = summon[KindlingsSchema[UpdateNotice]].schema

/** @param verdict
  *   one of [[Acquire.Verdicts]]: retracted|corrected|current|low-impact|unassessed
  * @param applied
  *   the `status` written back to the source, when `--apply` was used
  */
final case class Assessment(
    target: String,
    doi: Option[String],
    citekey: Option[String],
    year: Option[Int],
    citedByCount: Option[Int],
    influentialCitations: Option[Int],
    isRetracted: Boolean,
    retractionWatch: List[RetractionNotice],
    crossrefUpdates: List[UpdateNotice],
    flagged: Boolean,
    verdict: String,
    recommendation: String,
    applied: Option[String] = None,
    checkedAt: Option[String] = None,
    /** With `apply`: the stored status that was KEPT instead of the verdict's (it was already set). */
    kept: Option[SourceStatus] = None
) derives KindlingsCodecAsObject,
      KindlingsSchema
object Assessment:
  given Schema[Assessment] = summon[KindlingsSchema[Assessment]].schema

/** The state of the local Retraction Watch mirror.
  *
  * The refresh downloads ~30 MB and rewrites ~60 000 rows — far longer than one HTTP request may take (the same problem
  * `reindex` has), so `POST` STARTS it and returns immediately with `running = true`; poll `GET` until `running` is
  * false. `rows` is what the table holds right now.
  */
final case class RetractionsUpdate(
    rows: Int,
    source: String,
    running: Boolean = false,
    error: Option[String] = None
) derives KindlingsCodecAsObject,
      KindlingsSchema
object RetractionsUpdate:
  given Schema[RetractionsUpdate] = summon[KindlingsSchema[RetractionsUpdate]].schema

// ── followups (Semantic Scholar citation graph, OpenAlex fallback) ────────────────────────────

/** One citing paper. `proposedKind` is [[Acquire.classify]]'s reading of the citation contexts and intents —
  * `confirms|challenges|refutes|supersedes|cites` — the edge kind `--apply` would add.
  */
final case class FollowupRow(
    title: Option[String] = None,
    year: Option[Int] = None,
    doi: Option[String] = None,
    arxiv: Option[String] = None,
    citedBy: Option[Int] = None,
    isInfluential: Boolean = false,
    intents: List[String] = Nil,
    contexts: List[String] = Nil,
    proposedKind: String = "cites",
    citekey: Option[String] = None
) derives KindlingsCodecAsObject,
      KindlingsSchema
object FollowupRow:
  given Schema[FollowupRow] = summon[KindlingsSchema[FollowupRow]].schema

final case class FollowupsResult(
    target: String,
    doi: Option[String],
    backend: String,
    count: Int,
    edgesAdded: Int,
    followups: List[FollowupRow]
) derives KindlingsCodecAsObject,
      KindlingsSchema
object FollowupsResult:
  given Schema[FollowupsResult] = summon[KindlingsSchema[FollowupsResult]].schema

// ── archive.org ────────────────────────────────────────────────────────────────────────────────

final case class ArchiveFile(name: String, format: Option[String], megabytes: Double)
    derives KindlingsCodecAsObject,
      KindlingsSchema
object ArchiveFile:
  given Schema[ArchiveFile] = summon[KindlingsSchema[ArchiveFile]].schema

/** @param lendingOnly
  *   the item is access-restricted: it must be BORROWED on archive.org and is never downloaded here (no DRM
  *   circumvention — the Python tool stopped at exactly this point and so do we).
  */
final case class ArchiveItem(
    identifier: String,
    title: Option[String] = None,
    creator: List[String] = Nil,
    year: Option[Int] = None,
    publisher: Option[String] = None,
    description: Option[String] = None,
    lendingOnly: Boolean = false,
    files: List[ArchiveFile] = Nil
) derives KindlingsCodecAsObject,
      KindlingsSchema
object ArchiveItem:
  given Schema[ArchiveItem] = summon[KindlingsSchema[ArchiveItem]].schema

final case class ArchiveSearchResult(query: String, count: Int, items: List[ArchiveItem])
    derives KindlingsCodecAsObject,
      KindlingsSchema
object ArchiveSearchResult:
  given Schema[ArchiveSearchResult] = summon[KindlingsSchema[ArchiveSearchResult]].schema

// ── ingest by identifier ──────────────────────────────────────────────────────────────────────

/** One line of the ingest report: what was attempted and how it went. */
final case class IngestStep(step: String, ok: Boolean, detail: String) derives KindlingsCodecAsObject, KindlingsSchema
object IngestStep:
  given Schema[IngestStep] = summon[KindlingsSchema[IngestStep]].schema

/** Where the PDF came from. `source` is `unpaywall|core|openalex|europepmc|pmc|arxiv|zenodo|repository|archive_org`, or
  * the channel a fetcher plugin names.
  */
final case class PdfInfo(source: String, url: Option[String], bytes: Long, spoolPath: Option[String])
    derives KindlingsCodecAsObject,
      KindlingsSchema
object PdfInfo:
  given Schema[PdfInfo] = summon[KindlingsSchema[PdfInfo]].schema

/** The result of `histmersion ingest <doi|arxiv:id>`.
  *
  * @param dropIn
  *   the manual instruction to print when no PDF could be fetched legally
  */
final case class IngestReport(
    citekey: String,
    doi: Option[String],
    title: Option[String],
    created: Boolean,
    pdf: Option[PdfInfo],
    edgesAdded: Int,
    assessment: Option[Assessment],
    steps: List[IngestStep],
    dropIn: Option[String] = None
) derives KindlingsCodecAsObject,
      KindlingsSchema
object IngestReport:
  given Schema[IngestReport] = summon[KindlingsSchema[IngestReport]].schema

// ── Calibre (host-side; the server only reports which sources are missing from Calibre) ────────

/** A stored source that is NOT in Calibre yet, with the metadata `calibredb add` needs. */
final case class CalibreGap(
    citekey: String,
    title: String,
    authors: List[String],
    doi: Option[String],
    year: Option[Int],
    venue: Option[String],
    summary: Option[String],
    topics: List[String],
    provenance: Option[String],
    /** The entry this source already has in Calibre (`calibre update` rewrites it); none for a gap. */
    calibreId: Option[String] = None,
    /** An ISBN the corpus knows for the work (`source_identifiers`), for the Calibre identifiers. */
    isbn: Option[String] = None,
    /** Whether the server stores the source's original (`GET /api/sources/{ck}/original` serves it), which is where a
      * host-side `calibre push`/`sync` takes the file from. Set by the gaps listing; None where the server does not say
      * (the push metadata, an older server): the push then tries.
      */
    storedOriginal: Option[Boolean] = None
) derives KindlingsCodecAsObject,
      KindlingsSchema
object CalibreGap:
  given Schema[CalibreGap] = summon[KindlingsSchema[CalibreGap]].schema

final case class CalibreGaps(count: Int, gaps: List[CalibreGap]) derives KindlingsCodecAsObject, KindlingsSchema
object CalibreGaps:
  given Schema[CalibreGaps] = summon[KindlingsSchema[CalibreGaps]].schema

// ── request bodies (the operations that write) ────────────────────────────────────────────────

final case class RefsRequest(id: String, apply: Boolean = false) derives KindlingsCodecAsObject, KindlingsSchema
object RefsRequest:
  given Schema[RefsRequest] = summon[KindlingsSchema[RefsRequest]].schema

/** `forceStatus`: with `apply`, write the verdict's status even over one that is already set (by default only
  * `unassessed` is overwritten — and a retraction always is).
  */
final case class AssessRequest(id: String, apply: Boolean = false, forceStatus: Boolean = false)
    derives KindlingsCodecAsObject,
      KindlingsSchema
object AssessRequest:
  given Schema[AssessRequest] = summon[KindlingsSchema[AssessRequest]].schema

final case class FollowupsRequest(
    id: String,
    since: Option[Int] = None,
    limit: Option[Int] = None,
    apply: Boolean = false
) derives KindlingsCodecAsObject,
      KindlingsSchema
object FollowupsRequest:
  given Schema[FollowupsRequest] = summon[KindlingsSchema[FollowupsRequest]].schema

/** @param id
  *   a DOI, `arxiv:<id>`, or a bare arXiv id
  * @param citekey
  *   override the generated citekey
  * @param arxiv
  *   an explicit arXiv id to try when the DOI has no OA copy
  */
final case class IngestRequest(
    id: String,
    citekey: Option[String] = None,
    arxiv: Option[String] = None,
    /** Fetch and convert again although the source already has its original (a stored file or a converted body) — the
      * new conversion replaces the body. Without it such a source only has its metadata and edges refreshed; one with
      * no original is always fetched.
      */
    refetch: Boolean = false,
    /** Ask the legal chain only: no fetcher the config marks unauthorised runs, whatever `acquire.allow-unauthorised`
      * says. Otherwise a person's ingest as usual — with no legal copy the source is still created from its metadata.
      */
    legalOnly: Boolean = false
) derives KindlingsCodecAsObject,
      KindlingsSchema
object IngestRequest:
  given Schema[IngestRequest] = summon[KindlingsSchema[IngestRequest]].schema

/** Written back after a host-side `calibre push`, so the source stops counting as a gap. */
final case class CalibrePushRequest(citekey: String, calibreId: String) derives KindlingsCodecAsObject, KindlingsSchema
object CalibrePushRequest:
  given Schema[CalibrePushRequest] = summon[KindlingsSchema[CalibrePushRequest]].schema

final case class CalibrePushResult(citekey: String, calibreId: String, ok: Boolean)
    derives KindlingsCodecAsObject,
      KindlingsSchema
object CalibrePushResult:
  given Schema[CalibrePushResult] = summon[KindlingsSchema[CalibrePushResult]].schema

final case class ArchiveImportRequest(
    id: String,
    citekey: Option[String] = None,
    format: Option[String] = None
) derives KindlingsCodecAsObject,
      KindlingsSchema
object ArchiveImportRequest:
  given Schema[ArchiveImportRequest] = summon[KindlingsSchema[ArchiveImportRequest]].schema
