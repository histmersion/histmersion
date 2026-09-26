package com.histmersion.core

import hearth.kindlings.circederivation.{Configuration, KindlingsCodecAsObject}
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

/** Honour Scala default values when DECODING. Without this, Kindlings requires every non-Option field
  * in the JSON, so `{"tags":{"add":["sea-peoples"]}}` would be rejected for a missing `remove` — and
  * the whole point of the patch DTOs is that what you omit is what you leave alone. Top-level in
  * `com.histmersion.core`, so every DTO in this package decodes its defaults the same way. */
given Configuration = Configuration.default.withDefaults

/** The WRITE side of the contract (plan §1.9a): DTOs for `put_source` / `patch_source` plus the pure
  * merge that turns a patch into a new [[Source]]. No I/O here — the db module persists the result,
  * so every merge rule is unit-testable without Postgres.
  *
  * Two operations, deliberately asymmetric:
  *   - `put_source` REPLACES everything for a citekey (create / deliberate re-import; what `seed` does),
  *   - `patch_source` touches ONLY the parts present in the request (enrichment; what agents do).
  *
  * Every field is optional with a `None`/`Nil` default so an omitted JSON key decodes to "absent"
  * and therefore means "leave alone".
  */

/** A finding as supplied by a writer: no id — the store allocates a stable `<citekey>#<n>`. */
final case class FindingInput(
    claim: String,
    region: Option[String] = None,
    lon: Option[Double] = None,
    lat: Option[Double] = None,
    periodStart: Option[Long] = None,
    periodEnd: Option[Long] = None,
    periodName: Option[String] = None,
    tags: List[String] = Nil,
    confidence: Option[String] = None,
    significance: Option[String] = None
) derives KindlingsCodecAsObject
object FindingInput:
  given Schema[FindingInput] = KindlingsSchema.derived[FindingInput].schema

  /** Round-trip helper: an existing finding back into writer form (drops the id). */
  def of(f: Finding): FindingInput = FindingInput(
    claim = f.claim, region = f.region, lon = f.geo.map(_.lon), lat = f.geo.map(_.lat),
    periodStart = f.period.map(_.start), periodEnd = f.period.map(_.end),
    periodName = f.periodName, tags = f.tags, confidence = f.confidence.map(_.toString.toLowerCase),
    significance = f.significance)

/** Full replace of one citekey. `body` is the paper's converted text (`ocr_markdown`);
  * `notes` is the agent's own article about it. */
final case class SourcePut(
    title: String,
    authors: List[String] = Nil,
    doi: Option[String] = None,
    year: Option[Int] = None,
    venue: Option[String] = None,
    status: Option[SourceStatus] = None,
    topics: List[String] = Nil,
    regions: List[String] = Nil,
    tags: List[String] = Nil,
    periodStart: Option[Long] = None,
    periodEnd: Option[Long] = None,
    summary: Option[String] = None,
    body: Option[String] = None,
    notes: Option[String] = None,
    citedByCount: Option[Int] = None,
    influentialCitations: Option[Int] = None,
    provenance: Option[String] = None,
    calibreId: Option[String] = None,
    findings: List[FindingInput] = Nil,
    edges: List[Edge] = Nil
) derives KindlingsCodecAsObject
object SourcePut:
  given Schema[SourcePut] = KindlingsSchema.derived[SourcePut].schema

  /** Round-trip helper: an existing source back into writer form. */
  def of(s: Source): SourcePut = SourcePut(
    title = s.title, authors = s.authors, doi = s.doi, year = s.year, venue = s.venue,
    status = Some(s.status), topics = s.topics, regions = s.regions, tags = s.tags,
    periodStart = s.period.map(_.start), periodEnd = s.period.map(_.end), summary = s.summary,
    body = s.ocrMarkdown, notes = s.notes, citedByCount = s.citedByCount,
    influentialCitations = s.influentialCitations, provenance = s.provenance, calibreId = s.calibreId,
    findings = s.findings.map(FindingInput.of), edges = s.edges)

/** Scalar metadata. A present field replaces; an absent one is left alone (values cannot be cleared
  * to NULL through a patch — that is what `put_source` is for). */
final case class MetadataPatch(
    title: Option[String] = None,
    authors: Option[List[String]] = None,
    year: Option[Int] = None,
    venue: Option[String] = None,
    doi: Option[String] = None,
    status: Option[SourceStatus] = None,
    provenance: Option[String] = None
) derives KindlingsCodecAsObject
object MetadataPatch:
  given Schema[MetadataPatch] = KindlingsSchema.derived[MetadataPatch].schema

/** Set-like edit of a string list: `replace` first (when given), then `remove`, then `add`. */
final case class ListPatch(
    add: List[String] = Nil,
    remove: List[String] = Nil,
    replace: Option[List[String]] = None
) derives KindlingsCodecAsObject
object ListPatch:
  given Schema[ListPatch] = KindlingsSchema.derived[ListPatch].schema

/** `remove` carries finding IDS (`<citekey>#<n>`); `add`/`replace` carry finding VALUES. */
final case class FindingsPatch(
    add: List[FindingInput] = Nil,
    remove: List[String] = Nil,
    replace: Option[List[FindingInput]] = None
) derives KindlingsCodecAsObject
object FindingsPatch:
  given Schema[FindingsPatch] = KindlingsSchema.derived[FindingsPatch].schema

final case class EdgesPatch(add: List[Edge] = Nil, remove: List[Edge] = Nil)
    derives KindlingsCodecAsObject
object EdgesPatch:
  given Schema[EdgesPatch] = KindlingsSchema.derived[EdgesPatch].schema

/** Deep time as signed years, BCE negative (3300 BCE -> -3300). */
final case class PeriodInput(start: Long, end: Long) derives KindlingsCodecAsObject
object PeriodInput:
  given Schema[PeriodInput] = KindlingsSchema.derived[PeriodInput].schema

/** Selective enrichment. Every top-level part is independently optional; a present part replaces only
  * itself, an absent part is untouched. `ifUpdatedAt` is an optimistic-concurrency precondition:
  * supply the `updatedAt` you last read and the write is rejected (HTTP 412) if the row moved on. */
final case class SourcePatch(
    metadata: Option[MetadataPatch] = None,
    summary: Option[String] = None,
    body: Option[String] = None,
    notes: Option[String] = None,
    topics: Option[List[String]] = None,
    regions: Option[List[String]] = None,
    period: Option[PeriodInput] = None,
    tags: Option[ListPatch] = None,
    findings: Option[FindingsPatch] = None,
    edges: Option[EdgesPatch] = None,
    ifUpdatedAt: Option[String] = None
) derives KindlingsCodecAsObject
object SourcePatch:
  given Schema[SourcePatch] = KindlingsSchema.derived[SourcePatch].schema

/** `POST /api/sources/import`: a note's raw text, parsed server-side with [[Frontmatter.parseNote]] and
  * applied as a `put_source`. Lets the CLI seed from a directory the SERVER cannot see (the host's
  * `wiki/sources`), while the seed guard still runs server-side.
  *
  * @param text    the whole note: YAML frontmatter + Markdown body (the body becomes `notes`)
  * @param citekey fallback only, used when the frontmatter itself has no `citekey`
  * @param body    the converted paper text (`md_path`), read by the client — the server has no host FS
  * @param mtime   the file's last-modified time in epoch millis; enables the "newer in DB" skip
  * @param force   apply even when the stored row is newer than `mtime`
  * @param assets  the note's figures (`md/<citekey>/figures/…`, plan §1.8), base64-encoded; the
  *                body's `figures/<name>` links are normalised to `assets/<name>` on the way in.
  *                Keep the batch small — `POST /api/sources/{ck}/assets` exists for larger sets.
  */
final case class NoteImport(
    text: String,
    citekey: Option[String] = None,
    body: Option[String] = None,
    mtime: Option[Long] = None,
    force: Boolean = false,
    assets: List[AssetInput] = Nil
) derives KindlingsCodecAsObject
object NoteImport:
  given Schema[NoteImport] = KindlingsSchema.derived[NoteImport].schema

/** Result of unifying short edge targets to full citekeys after a client-driven seed. */
final case class EdgesResolved(resolved: Int) derives KindlingsCodecAsObject
object EdgesResolved:
  given Schema[EdgesResolved] = KindlingsSchema.derived[EdgesResolved].schema

/** Pure merge rules for the write operations. */
object Write:

  /** A text field given as blank means "none": trimmed, and empty becomes None (NULL), so an empty
    * DOI can never be stored and then rendered or matched as if it were one. */
  private[core] def blankToNone(v: Option[String]): Option[String] = v.map(_.trim).filter(_.nonEmpty)

  /** [[ApiError.code]] values the endpoints map to HTTP statuses. */
  val CodeNotFound     = "not_found"
  val CodePrecondition = "precondition_failed"
  val CodeInvalid      = "invalid"

  def notFound(citekey: String): ApiError = ApiError(s"No source '$citekey'", Some(CodeNotFound))

  /** Stable finding id. */
  def findingId(citekey: String, n: Int): String = s"$citekey#$n"

  // -- put ------------------------------------------------------------------------------------
  /** Build the complete stored [[Source]] for a full replace. Finding ids are (re)assigned from 0. */
  def applyPut(citekey: String, p: SourcePut): Either[String, Source] =
    for
      period <- periodOf(p.periodStart, p.periodEnd, "source")
      fs     <- numbered(citekey, p.findings)
    yield Source(
      citekey = citekey,
      title = p.title,
      authors = p.authors,
      doi = blankToNone(p.doi),
      year = p.year,
      venue = blankToNone(p.venue),
      status = p.status.getOrElse(SourceStatus.Unassessed),
      topics = p.topics,
      regions = p.regions,
      tags = p.tags,
      period = period,
      summary = p.summary,
      citedByCount = p.citedByCount,
      influentialCitations = p.influentialCitations,
      provenance = p.provenance,
      calibreId = p.calibreId,
      ocrMarkdown = p.body,
      findings = fs,
      edges = p.edges.map(e => e.copy(src = citekey)).distinct,
      notes = p.notes,
      updatedAt = None
    )

  // -- patch ----------------------------------------------------------------------------------
  /** The optimistic-concurrency check, separate so the endpoint can answer 412 specifically. */
  def precondition(existing: Source, patch: SourcePatch): Either[String, Unit] =
    patch.ifUpdatedAt match
      case None => Right(())
      case Some(want) =>
        val actual = existing.updatedAt.getOrElse("")
        if want == actual then Right(())
        else
          Left(s"'${existing.citekey}' has moved on: you sent ifUpdatedAt='$want' but the stored " +
            s"updatedAt is '$actual'. Re-read the source and re-apply your patch.")

  /** Merge a patch into the existing source. Absent parts are untouched; present parts replace only
    * themselves. Pure — the caller persists the result and stamps a new `updatedAt`. */
  def applyPatch(existing: Source, patch: SourcePatch): Either[String, Source] =
    for
      _      <- precondition(existing, patch)
      period <- patch.period.fold[Either[String, Option[Period]]](Right(existing.period))(p =>
                  periodOf(Some(p.start), Some(p.end), "source"))
      fs     <- patch.findings.fold[Either[String, List[Finding]]](Right(existing.findings))(fp =>
                  applyFindings(existing.citekey, existing.findings, fp))
      meta = patch.metadata.getOrElse(MetadataPatch())
      newEdges = patch.edges.fold(existing.edges)(applyEdges(existing.citekey, existing.edges, _))
      newStatus = meta.status.getOrElse(existing.status)
      _ <- supersededEdgeCheck(newStatus, newEdges)
    yield existing.copy(
      title = meta.title.getOrElse(existing.title),
      authors = meta.authors.getOrElse(existing.authors),
      year = meta.year.orElse(existing.year),
      // an explicit "" CLEARS the field: stored as NULL, never as an empty string
      venue = meta.venue.fold(existing.venue)(v => blankToNone(Some(v))),
      doi = meta.doi.fold(existing.doi)(d => blankToNone(Some(d))),
      status = newStatus,
      summary = patch.summary.orElse(existing.summary),
      ocrMarkdown = patch.body.orElse(existing.ocrMarkdown),
      notes = patch.notes.orElse(existing.notes),
      topics = patch.topics.getOrElse(existing.topics),
      regions = patch.regions.getOrElse(existing.regions),
      tags = patch.tags.fold(existing.tags)(applyList(existing.tags, _)),
      period = period,
      findings = fs,
      edges = newEdges,
      provenance = meta.provenance.orElse(existing.provenance)
    )

  /** `replace` (when given) is the new baseline; then `remove`, then `add`. Order-preserving, deduped. */
  def applyList(current: List[String], p: ListPatch): List[String] =
    val base    = p.replace.getOrElse(current)
    val removed = p.remove.toSet
    (base.filterNot(removed) ++ p.add.filterNot(removed)).distinct

  def applyEdges(citekey: String, current: List[Edge], p: EdgesPatch): List[Edge] =
    val norm    = (e: Edge) => e.copy(src = citekey)
    val removed = p.remove.map(norm).toSet
    (current.map(norm).filterNot(removed) ++ p.add.map(norm).filterNot(removed)).distinct

  /** `replace` renumbers from 0; `add` takes the next FREE `n` so ids stay stable across patches. */
  def applyFindings(citekey: String, current: List[Finding], p: FindingsPatch): Either[String, List[Finding]] =
    for
      base <- p.replace.fold[Either[String, List[Finding]]](Right(current))(numbered(citekey, _))
      kept  = base.filterNot(f => p.remove.contains(f.id))
      added <- allocate(citekey, kept, p.add)
    yield kept ++ added

  // -- helpers --------------------------------------------------------------------------------
  /** Ids 0..n-1, as [[Frontmatter]] does when it reads a note. */
  private def numbered(citekey: String, inputs: List[FindingInput]): Either[String, List[Finding]] =
    traverse(inputs.zipWithIndex)((in, i) => toFinding(citekey, findingId(citekey, i), in))

  /** Ids are the smallest `n` not already taken, allocated left to right. */
  private def allocate(
      citekey: String,
      existing: List[Finding],
      adds: List[FindingInput]
  ): Either[String, List[Finding]] =
    val (_, out) = adds.foldLeft((existing.map(_.id).toSet, List.empty[Either[String, Finding]])) {
      case ((used, acc), in) =>
        val id = Iterator.from(0).map(findingId(citekey, _)).find(n => !used.contains(n)).get
        (used + id, acc :+ toFinding(citekey, id, in))
    }
    traverse(out)(identity)

  private def toFinding(citekey: String, id: String, in: FindingInput): Either[String, Finding] =
    periodOf(in.periodStart, in.periodEnd, s"finding '${in.claim.take(40)}'").map { p =>
      val geo = (in.lon, in.lat) match
        case (Some(x), Some(y)) => Some(GeoPoint(x, y))
        case _                  => None
      Finding(id, citekey, in.claim, in.region, geo, p, in.periodName, in.tags,
        in.confidence.flatMap(Confidence.fromString), in.significance)
    }

  /** A period needs BOTH bounds and `start <= end` (Postgres rejects an inverted int8range). */
  private def periodOf(start: Option[Long], end: Option[Long], what: String): Either[String, Option[Period]] =
    def sane(v: Long) = v >= Filter.Earliest && v <= Filter.Latest
    (start, end) match
      case (Some(a), _) if !sane(a) => Left(outOfRange(what, "period_start", a))
      case (_, Some(b)) if !sane(b) => Left(outOfRange(what, "period_end", b))
      case (Some(a), Some(b)) if a <= b => Right(Some(Period(a, b)))
      case (Some(a), Some(b))           => Left(s"$what: period_start ($a) must not be after period_end ($b)")
      case (None, None)                 => Right(None)
      case _                            => Left(s"$what: give both period_start and period_end, or neither")

  /** A period bound outside `[Filter.Earliest, Filter.Latest]` is refused on the way IN rather than
    * stored: those are the sentinels every half-open filter range uses, so a stored period sitting
    * on one of them makes "the caller gave no bound" and "the caller gave this bound" the same
    * value, and the aggregation window then reads back as unbounded. */
  private def outOfRange(what: String, field: String, v: Long): String =
    s"$what: $field ($v) is outside the representable range " +
      s"[${Filter.Earliest}, ${Filter.Latest}] — deep time is signed YEARS, BCE negative"

  /** Setting status=superseded requires at least one `superseded_by` edge, either already existing
    * or added in the same patch. */
  private def supersededEdgeCheck(status: SourceStatus, edges: List[Edge]): Either[String, Unit] =
    if status == SourceStatus.Superseded && !edges.exists(_.kind == "superseded_by") then
      Left("status=superseded requires a superseded_by edge — add one with edges.add " +
        "(e.g. {\"edges\":{\"add\":[{\"src\":\"this\",\"dst\":\"newer_source\",\"kind\":\"superseded_by\"}]}})")
    else Right(())

  private def traverse[A, B](as: List[A])(f: A => Either[String, B]): Either[String, List[B]] =
    as.foldLeft[Either[String, List[B]]](Right(Nil))((acc, a) => acc.flatMap(bs => f(a).map(bs :+ _)))
