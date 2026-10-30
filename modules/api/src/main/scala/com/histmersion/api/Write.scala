package com.histmersion.api

import scala.annotation.tailrec
import hearth.kindlings.circederivation.{Configuration, KindlingsCodecAsObject}
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import io.circe.{Codec, Decoder, DecodingFailure, Encoder, Json}
import sttp.tapir.Schema
import com.histmersion.values.{Confidence, EdgeKind, SourceStatus, WireEnumValues}

/** Honour Scala default values when DECODING. Without this, Kindlings requires every non-Option field in the JSON, so
  * `{"tags":{"add":["sea-peoples"]}}` would be rejected for a missing `remove` — and the whole point of the patch DTOs
  * is that what you omit is what you leave alone. Top-level in `com.histmersion.api`, so every DTO in this package
  * decodes its defaults the same way; the domain and config packages import it (`import com.histmersion.api.given`) for
  * the codecs they derive.
  */
given Configuration = Configuration.default.withDefaults

/** The WRITE side of the contract (plan §1.9a): DTOs for `put_source` / `patch_source` plus the pure merge that turns a
  * patch into a new [[Source]]. No I/O here — the db module persists the result, so every merge rule is unit-testable
  * without Postgres.
  *
  * Two operations, deliberately asymmetric:
  *   - `put_source` REPLACES everything for a citekey (create / deliberate re-import; what `seed` does),
  *   - `patch_source` touches ONLY the parts present in the request (enrichment; what agents do).
  *
  * Every field is optional with a `None`/`Nil` default so an omitted JSON key decodes to "absent" and therefore means
  * "leave alone".
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
)
object FindingInput:
  given Schema[FindingInput] = KindlingsSchema
    .derived[FindingInput]
    .schema
    .modify(_.confidence)(_.description("high | medium | low | contested — another word is refused"))

  /** Strict (#813): an unknown key is refused, not dropped — see [[StrictJson]]. */
  given Codec.AsObject[FindingInput] = StrictJson.codec(KindlingsCodecAsObject.derived[FindingInput])

  /** Round-trip helper: an existing finding back into writer form (drops the id). */
  def of(f: Finding): FindingInput = FindingInput(
    claim = f.claim,
    region = f.region,
    lon = f.geo.map(_.lon),
    lat = f.geo.map(_.lat),
    periodStart = f.period.map(_.start),
    periodEnd = f.period.map(_.end),
    periodName = f.periodName,
    tags = f.tags,
    confidence = f.confidence.map(_.toString.toLowerCase),
    significance = f.significance
  )

/** Full replace of one citekey. `body` is the paper's converted text (`ocr_markdown`); `notes` is the agent's own
  * article about it. `edges` follow the same rule as a patch's (#766): every edge is stored FROM this source, so `src`
  * may be omitted; one naming another source is refused.
  */
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
    edges: List[EdgeInput] = Nil
) derives KindlingsSchema
object SourcePut:
  given Schema[SourcePut] = summon[KindlingsSchema[SourcePut]].schema

  /** Strict (#813): an unknown key is refused, not dropped — see [[StrictJson]]. */
  given Codec.AsObject[SourcePut] = StrictJson.codec(KindlingsCodecAsObject.derived[SourcePut])

  /** Round-trip helper: an existing source back into writer form. */
  def of(s: Source): SourcePut = SourcePut(
    title = s.title,
    authors = s.authors,
    doi = s.doi,
    year = s.year,
    venue = s.venue,
    status = Some(s.status),
    topics = s.topics,
    regions = s.regions,
    tags = s.tags,
    periodStart = s.period.map(_.start),
    periodEnd = s.period.map(_.end),
    summary = s.summary,
    body = s.ocrMarkdown,
    notes = s.notes,
    citedByCount = s.citedByCount,
    influentialCitations = s.influentialCitations,
    provenance = s.provenance,
    calibreId = s.calibreId,
    findings = s.findings.map(FindingInput.of),
    edges = s.edges.flatMap(e => EdgeKind.fromWire(e.kind).map(EdgeInput(e.dst, _)))
  )

/** The metadata fields a patch may CLEAR (store as NULL). Title, authors and status always have a value, so they are
  * not here. The wire spelling is the JSON key of the field in [[MetadataPatch]].
  */
enum MetadataField:
  case Year, Venue, Doi, Provenance, CalibreId

object MetadataField:
  def wireOf(f: MetadataField): String = f match
    case Year       => "year"
    case Venue      => "venue"
    case Doi        => "doi"
    case Provenance => "provenance"
    case CalibreId  => "calibreId"

  def fromWire(s: String): Option[MetadataField] = values.find(wireOf(_) == s)

  given WireEnumValues[MetadataField]:
    def all: Array[MetadataField] = MetadataField.values
    def wire(e: MetadataField): String = wireOf(e)
    def fromWire(s: String): Option[MetadataField] = MetadataField.fromWire(s)

  given Encoder[MetadataField] = WireEnum.encoder
  given Decoder[MetadataField] = WireEnum.decoder
  given Schema[MetadataField] = WireEnum.schema

/** Scalar metadata. A present field replaces; an absent one is left alone.
  *
  * Clearing (#745): `clear` names the fields to store as NULL. An explicit JSON `null` on a clearable field means the
  * same thing — `{"doi":null}` decodes to `clear = [doi]` — so "absent" and "null" are no longer the same value. A
  * `null` on a field that cannot be cleared (title, authors, status) is refused instead of being silently ignored. `""`
  * on `venue`/`doi` still clears, as before.
  *
  * `calibreId` (#721) re-links the source to another Calibre entry (`histmersion calibre link` checks the entry exists
  * first); it must be the entry's numeric id.
  */
final case class MetadataPatch(
    title: Option[String] = None,
    authors: Option[List[String]] = None,
    year: Option[Int] = None,
    venue: Option[String] = None,
    doi: Option[String] = None,
    status: Option[SourceStatus] = None,
    provenance: Option[String] = None,
    calibreId: Option[String] = None,
    clear: List[MetadataField] = Nil
) derives KindlingsSchema
object MetadataPatch:
  given Schema[MetadataPatch] = summon[KindlingsSchema[MetadataPatch]].schema

  private val derived: Codec.AsObject[MetadataPatch] = KindlingsCodecAsObject.derived[MetadataPatch]

  /** The derived codec, wrapped so that absent and `null` differ (see the class doc). The decoder moves every `null`
    * key into `clear` (or refuses it); the encoder drops `None` fields instead of writing them as `null`, so a patch
    * the CLI or the web UI sends means what its value says. An unknown key is refused before any of that (#813,
    * [[StrictJson]]).
    */
  given Codec.AsObject[MetadataPatch] = Codec.AsObject.from(
    StrictJson.decoder(Decoder.instance { c =>
      c.value.asObject match
        case None    => derived(c)
        case Some(o) =>
          val nulls = o.toList.collect { case (k, v) if v.isNull => k }
          val (refused, cleared) = nulls.partitionMap(k => MetadataField.fromWire(k).toRight(k))
          if refused.nonEmpty then
            Left(
              DecodingFailure(
                s"metadata.${refused.mkString(", metadata.")} cannot be cleared — null is accepted only on " +
                  s"${MetadataField.values.map(MetadataField.wireOf).mkString(", ")}; omit a field to leave it alone",
                c.history
              )
            )
          else
            derived
              .decodeJson(Json.fromJsonObject(o.filter((_, v) => !v.isNull)))
              .map(m => m.copy(clear = (m.clear ++ cleared).distinct))
    }),
    Encoder.AsObject.instance(m => derived.encodeObject(m).filter((_, v) => !v.isNull))
  )

/** Set-like edit of a string list: `replace` first (when given), then `remove`, then `add`. */
final case class ListPatch(
    add: List[String] = Nil,
    remove: List[String] = Nil,
    replace: Option[List[String]] = None
) derives KindlingsSchema
object ListPatch:
  given Schema[ListPatch] = summon[KindlingsSchema[ListPatch]].schema

  /** Strict (#813): an unknown key is refused, not dropped — see [[StrictJson]]. */
  given Codec.AsObject[ListPatch] = StrictJson.codec(KindlingsCodecAsObject.derived[ListPatch])

/** `remove` carries finding IDS (`<citekey>#<n>`); `add`/`replace` carry finding VALUES. */
final case class FindingsPatch(
    add: List[FindingInput] = Nil,
    remove: List[String] = Nil,
    replace: Option[List[FindingInput]] = None
) derives KindlingsSchema
object FindingsPatch:
  given Schema[FindingsPatch] = summon[KindlingsSchema[FindingsPatch]].schema

  /** Strict (#813): an unknown key is refused, not dropped — see [[StrictJson]]. */
  given Codec.AsObject[FindingsPatch] = StrictJson.codec(KindlingsCodecAsObject.derived[FindingsPatch])

/** One edge in a patch. It is stored on the PATCHED source, so `src` may be omitted; when given it must name the
  * patched citekey (#698: a different `src` used to be silently replaced, turning a critic → target edge into a
  * self-edge). To record `a --kind--> b`, patch `a`.
  */
final case class EdgeInput(dst: String, kind: EdgeKind, src: Option[String] = None) derives KindlingsSchema
object EdgeInput:
  given Schema[EdgeInput] = summon[KindlingsSchema[EdgeInput]].schema

  /** Strict (#813): an unknown key is refused, not dropped — see [[StrictJson]]. */
  given Codec.AsObject[EdgeInput] = StrictJson.codec(KindlingsCodecAsObject.derived[EdgeInput])

final case class EdgesPatch(add: List[EdgeInput] = Nil, remove: List[EdgeInput] = Nil) derives KindlingsSchema
object EdgesPatch:
  given Schema[EdgesPatch] = summon[KindlingsSchema[EdgesPatch]].schema

  /** Strict (#813): an unknown key is refused, not dropped — see [[StrictJson]]. */
  given Codec.AsObject[EdgesPatch] = StrictJson.codec(KindlingsCodecAsObject.derived[EdgesPatch])

/** Deep time as signed years, BCE negative (3300 BCE -> -3300). */
final case class PeriodInput(start: Long, end: Long) derives KindlingsSchema
object PeriodInput:
  given Schema[PeriodInput] = summon[KindlingsSchema[PeriodInput]].schema

  /** Strict (#813): an unknown key is refused, not dropped — see [[StrictJson]]. */
  given Codec.AsObject[PeriodInput] = StrictJson.codec(KindlingsCodecAsObject.derived[PeriodInput])

/** Selective enrichment. Every top-level part is independently optional; a present part replaces only itself, an absent
  * part is untouched. `ifUpdatedAt` is an optimistic-concurrency precondition: supply the `updatedAt` you last read and
  * the write is rejected (HTTP 412) if the row moved on.
  */
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
) derives KindlingsSchema
object SourcePatch:
  given Schema[SourcePatch] = summon[KindlingsSchema[SourcePatch]].schema

  /** Strict (#813): an unknown key is refused, not dropped — see [[StrictJson]]. */
  given Codec.AsObject[SourcePatch] = StrictJson.codec(KindlingsCodecAsObject.derived[SourcePatch])

/** `POST /api/sources/import`: a note's raw text, parsed server-side with [[Frontmatter.parseNote]] and applied as a
  * `put_source`. Lets the CLI seed from a directory the SERVER cannot see (the host's `wiki/sources`), while the seed
  * guard still runs server-side.
  *
  * @param text
  *   the whole note: YAML frontmatter + Markdown body (the body becomes `notes`)
  * @param citekey
  *   fallback only, used when the frontmatter itself has no `citekey`
  * @param body
  *   the converted paper text (`md_path`), read by the client — the server has no host FS
  * @param mtime
  *   the file's last-modified time in epoch millis; enables the "newer in DB" skip
  * @param force
  *   apply even when the stored row is newer than `mtime`
  * @param assets
  *   the note's figures (`md/<citekey>/figures/…`, plan §1.8), base64-encoded; the body's `figures/<name>` links are
  *   normalised to `assets/<name>` on the way in. Keep the batch small — `POST /api/sources/{ck}/assets` exists for
  *   larger sets.
  */
final case class NoteImport(
    text: String,
    citekey: Option[String] = None,
    body: Option[String] = None,
    mtime: Option[Long] = None,
    force: Boolean = false,
    assets: List[AssetInput] = Nil
) derives KindlingsCodecAsObject,
      KindlingsSchema
object NoteImport:
  given Schema[NoteImport] = summon[KindlingsSchema[NoteImport]].schema

/** Result of unifying short edge targets to full citekeys after a client-driven seed. */
final case class EdgesResolved(resolved: Int) derives KindlingsCodecAsObject, KindlingsSchema
object EdgesResolved:
  given Schema[EdgesResolved] = summon[KindlingsSchema[EdgesResolved]].schema

/** Pure merge rules for the write operations. */
object Write:

  /** A text field given as blank means "none": trimmed, and empty becomes None (NULL), so an empty DOI can never be
    * stored and then rendered or matched as if it were one.
    */
  private[api] def blankToNone(v: Option[String]): Option[String] = v.map(_.trim).filter(_.nonEmpty)

  /** [[ApiError.code]] values the endpoints map to HTTP statuses. */
  val CodeNotFound = "not_found"
  val CodePrecondition = "precondition_failed"
  val CodeInvalid = "invalid"

  def notFound(citekey: String): ApiError = ApiError(s"No source '$citekey'", Some(CodeNotFound))

  /** Stable finding id. */
  def findingId(citekey: String, n: Int): String = s"$citekey#$n"

  // -- put ------------------------------------------------------------------------------------
  /** Build the complete stored [[Source]] for a full replace. Finding ids are (re)assigned from 0. Edges follow the
    * patch rule (#766): an omitted `src` is this source, and so is one of its OLD citekeys (`aliases`, V027); any other
    * `src` is refused rather than rewritten, and so is a self-edge.
    */
  def applyPut(citekey: String, p: SourcePut, aliases: Set[String] = Set.empty): Either[String, Source] =
    for
      period <- periodOf(p.periodStart, p.periodEnd, "source")
      fs <- numbered(citekey, p.findings)
      edges <- traverse(p.edges)(edgeOf(citekey, aliases, "edges", "source being put", _))
      _ <- edges
        .find(e => e.dst == citekey || aliases.contains(e.dst))
        .fold(Right(()))(e =>
          Left(s"edges: '${e.dst}' --${e.kind}--> itself is a self-edge; dst must be another source")
        )
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
      edges = edges.distinct,
      notes = p.notes,
      updatedAt = None
    )

  // -- patch ----------------------------------------------------------------------------------
  /** The optimistic-concurrency check, separate so the endpoint can answer 412 specifically. */
  def precondition(existing: Source, patch: SourcePatch): Either[String, Unit] =
    patch.ifUpdatedAt match
      case None       => Right(())
      case Some(want) =>
        val actual = existing.updatedAt.getOrElse("")
        if want == actual then Right(())
        else
          Left(
            s"'${existing.citekey}' has moved on: you sent ifUpdatedAt='$want' but the stored " +
              s"updatedAt is '$actual'. Re-read the source and re-apply your patch."
          )

  /** Merge a patch into the existing source. Absent parts are untouched; present parts replace only themselves. Pure —
    * the caller persists the result and stamps a new `updatedAt`.
    */
  def applyPatch(existing: Source, patch: SourcePatch): Either[String, Source] =
    for
      _ <- precondition(existing, patch)
      period <- patch.period.fold[Either[String, Option[Period]]](Right(existing.period))(p =>
        periodOf(Some(p.start), Some(p.end), "source")
      )
      fs <- patch.findings.fold[Either[String, List[Finding]]](Right(existing.findings))(fp =>
        applyFindings(existing.citekey, existing.findings, fp)
      )
      meta = patch.metadata.getOrElse(MetadataPatch())
      _ <- metadataCheck(meta)
      newEdges <- patch.edges.fold[Either[String, List[Edge]]](Right(existing.edges))(
        applyEdges(existing.citekey, existing.edges, _)
      )
      newStatus = meta.status.getOrElse(existing.status)
      _ <- supersededEdgeCheck(newStatus, newEdges)
    yield
      def cleared(f: MetadataField) = meta.clear.contains(f)
      existing.copy(
        title = meta.title.getOrElse(existing.title),
        authors = meta.authors.getOrElse(existing.authors),
        year = if cleared(MetadataField.Year) then None else meta.year.orElse(existing.year),
        // an explicit "" CLEARS the field: stored as NULL, never as an empty string
        venue = if cleared(MetadataField.Venue) then None
        else meta.venue.fold(existing.venue)(v => blankToNone(Some(v))),
        doi = if cleared(MetadataField.Doi) then None
        else meta.doi.fold(existing.doi)(d => blankToNone(Some(d))),
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
        provenance = if cleared(MetadataField.Provenance) then None
        else meta.provenance.orElse(existing.provenance),
        calibreId = if cleared(MetadataField.CalibreId) then None
        else meta.calibreId.map(_.trim).orElse(existing.calibreId)
      )

  /** A patch sent to an OLD citekey (renamed, V027) may name that old key as an edge's `src`; it is the same source, so
    * it is accepted as the canonical one rather than refused.
    */
  def srcAliased(p: SourcePatch, requested: String, canonical: String): SourcePatch =
    if requested == canonical then p
    else
      def fix(e: EdgeInput) = if e.src.map(_.trim).contains(requested) then e.copy(src = None) else e
      p.copy(edges = p.edges.map(ep => EdgesPatch(ep.add.map(fix), ep.remove.map(fix))))

  /** `replace` (when given) is the new baseline; then `remove`, then `add`. Order-preserving, deduped. */
  def applyList(current: List[String], p: ListPatch): List[String] =
    val base = p.replace.getOrElse(current)
    val removed = p.remove.toSet
    (base.filterNot(removed) ++ p.add.filterNot(removed)).distinct

  /** `remove` first, then `add`, deduped; every edge is rooted at `citekey`. An edge whose `src` names another source,
    * or whose `dst` is the source itself, is refused rather than rewritten.
    */
  def applyEdges(citekey: String, current: List[Edge], p: EdgesPatch): Either[String, List[Edge]] =
    for
      removed <- traverse(p.remove)(edgeOf(citekey, Set.empty, "edges.remove", "patched source", _))
      added <- traverse(p.add)(edgeOf(citekey, Set.empty, "edges.add", "patched source", _))
      _ <- added
        .find(_.dst == citekey)
        .fold(Right(()))(e =>
          Left(s"edges.add: '${e.dst}' --${e.kind}--> itself is a self-edge; dst must be another source")
        )
    yield
      val gone = removed.toSet
      (current.map(_.copy(src = citekey)).filterNot(gone) ++ added.filterNot(gone)).distinct

  /** One edge rooted at `citekey`. `src` may be omitted, or name `citekey` or one of its `aliases`; anything else is
    * refused. `part` and `role` only word the refusal.
    */
  private def edgeOf(
      citekey: String,
      aliases: Set[String],
      part: String,
      role: String,
      in: EdgeInput
  ): Either[String, Edge] =
    in.src.map(_.trim).filter(_.nonEmpty) match
      case Some(other) if other != citekey && !aliases.contains(other) =>
        Left(
          s"$part: src '$other' is not the $role '$citekey'. An edge is stored on its " +
            s"source: patch '$other' to record '$other' --${EdgeKind.wireOf(in.kind)}--> '${in.dst}' " +
            s"(src may be omitted; it defaults to the $role)"
        )
      case _ => Right(Edge(citekey, in.dst.trim, EdgeKind.wireOf(in.kind)))

  /** A field cannot be both set and cleared, and a Calibre id is the entry's number. */
  private def metadataCheck(m: MetadataPatch): Either[String, Unit] =
    val both = m.clear.filter {
      case MetadataField.Year       => m.year.isDefined
      case MetadataField.Venue      => m.venue.isDefined
      case MetadataField.Doi        => m.doi.isDefined
      case MetadataField.Provenance => m.provenance.isDefined
      case MetadataField.CalibreId  => m.calibreId.isDefined
    }
    if both.nonEmpty then
      Left(s"metadata: ${both.map(MetadataField.wireOf).mkString(", ")} is both set and cleared in one patch")
    else
      m.calibreId.map(_.trim) match
        case Some(id) if !id.matches("[0-9]+") =>
          Left(s"metadata.calibreId: '$id' is not a Calibre id (the entry's number, e.g. 1805)")
        case _ => Right(())

  /** `replace` renumbers from 0; `add` takes the next FREE `n` so ids stay stable across patches. */
  def applyFindings(citekey: String, current: List[Finding], p: FindingsPatch): Either[String, List[Finding]] =
    for
      base <- p.replace.fold[Either[String, List[Finding]]](Right(current))(numbered(citekey, _))
      kept = base.filterNot(f => p.remove.contains(f.id))
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
    @tailrec def loop(
        rest: List[FindingInput],
        used: Set[String],
        acc: List[Either[String, Finding]]
    ): List[Either[String, Finding]] =
      rest match
        case Nil        => acc.reverse
        case in :: tail =>
          val id = Iterator.from(0).map(findingId(citekey, _)).find(n => !used.contains(n)).get
          loop(tail, used + id, toFinding(citekey, id, in) :: acc)
    traverse(loop(adds, existing.map(_.id).toSet, Nil))(identity)

  private def toFinding(citekey: String, id: String, in: FindingInput): Either[String, Finding] =
    val what = s"finding '${in.claim.take(40)}'"
    for
      p <- periodOf(in.periodStart, in.periodEnd, what)
      conf <- confidenceOf(in.confidence, what)
    yield
      val geo = (in.lon, in.lat) match
        case (Some(x), Some(y)) => Some(GeoPoint(x, y))
        case _                  => None
      Finding(id, citekey, in.claim, in.region, geo, p, in.periodName, in.tags, conf, in.significance)

  /** A stated confidence must be one of the vocabulary: an unknown word is refused, never dropped (or, as the store
    * once read it, taken as `high`). Blank = not stated.
    */
  private def confidenceOf(raw: Option[String], what: String): Either[String, Option[Confidence]] =
    raw.map(_.trim).filter(_.nonEmpty) match
      case None    => Right(None)
      case Some(c) =>
        Confidence
          .fromString(c)
          .map(Some(_))
          .toRight(s"$what: confidence '$c' is not one of ${Confidence.values.map(_.wire).mkString("|")}")

  /** A period needs BOTH bounds and `start <= end` (Postgres rejects an inverted int8range). */
  private def periodOf(start: Option[Long], end: Option[Long], what: String): Either[String, Option[Period]] =
    def sane(v: Long) = v >= Filter.Earliest && v <= Filter.Latest
    (start, end) match
      case (Some(a), _) if !sane(a)     => Left(outOfRange(what, "period_start", a))
      case (_, Some(b)) if !sane(b)     => Left(outOfRange(what, "period_end", b))
      case (Some(a), Some(b)) if a <= b => Right(Some(Period(a, b)))
      case (Some(a), Some(b))           => Left(s"$what: period_start ($a) must not be after period_end ($b)")
      case (None, None)                 => Right(None)
      case _                            => Left(s"$what: give both period_start and period_end, or neither")

  /** A period bound outside `[Filter.Earliest, Filter.Latest]` is refused on the way IN rather than stored: those are
    * the sentinels every half-open filter range uses, so a stored period sitting on one of them makes "the caller gave
    * no bound" and "the caller gave this bound" the same value, and the aggregation window then reads back as
    * unbounded.
    */
  private def outOfRange(what: String, field: String, v: Long): String =
    s"$what: $field ($v) is outside the representable range " +
      s"[${Filter.Earliest}, ${Filter.Latest}] — deep time is signed YEARS, BCE negative"

  /** Setting status=superseded requires at least one `superseded_by` edge, either already existing or added in the same
    * patch.
    */
  private def supersededEdgeCheck(status: SourceStatus, edges: List[Edge]): Either[String, Unit] =
    if status == SourceStatus.Superseded && !edges.exists(_.kind == "superseded_by") then
      Left(
        "status=superseded requires a superseded_by edge — add one with edges.add " +
          "(e.g. {\"edges\":{\"add\":[{\"dst\":\"newer_source\",\"kind\":\"superseded_by\"}]}}, " +
          "or the CLI flag --add-edge superseded_by:newer_source)"
      )
    else Right(())

  private def traverse[A, B](as: List[A])(f: A => Either[String, B]): Either[String, List[B]] =
    as.foldLeft[Either[String, List[B]]](Right(Nil))((acc, a) => acc.flatMap(bs => f(a).map(bs :+ _)))
