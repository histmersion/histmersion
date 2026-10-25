package com.histmersion.api

import io.circe.{Encoder, Decoder}
import sttp.tapir.{Schema, Validator}
import com.histmersion.values.{Confidence, SourceStatus, WireEnumValues}

/** Deep time as signed integer years (BCE negative). A point in time is `start == end`;
  * a range is `start < end`. Stored in Postgres as `int8range`. */
final case class Period(start: Long, end: Long):
  def isPoint: Boolean = start == end

/** WGS84 lon/lat, matching PostGIS `geometry(Point, 4326)`. */
final case class GeoPoint(lon: Double, lat: Double)

/** A geo-located, time-scoped claim mined from a source. */
final case class Finding(
    id: String,
    citekey: String,
    claim: String,
    region: Option[String],
    geo: Option[GeoPoint],
    period: Option[Period],
    periodName: Option[String],
    tags: List[String],
    confidence: Option[Confidence],
    significance: Option[String] = None
)

/** One paper/book. `ocrMarkdown` (the full converted body) is the store-of-record content that the
  * server serialises back to the LLM as Markdown; the original binary stays in Calibre. */
final case class Source(
    citekey: String,
    title: String,
    authors: List[String],
    doi: Option[String],
    year: Option[Int],
    venue: Option[String],
    status: SourceStatus,
    topics: List[String],
    regions: List[String],
    tags: List[String],
    period: Option[Period],
    summary: Option[String],
    citedByCount: Option[Int],
    influentialCitations: Option[Int],
    provenance: Option[String],
    calibreId: Option[String],
    ocrMarkdown: Option[String] = None,
    findings: List[Finding] = Nil,
    edges: List[Edge] = Nil,
    /** Free Markdown the agent compiles ABOUT this source (the body of `wiki/sources/<ck>.md`). */
    notes: Option[String] = None,
    /** Opaque last-write token (`updated_at`), set by the store. `patch_source`'s `ifUpdatedAt`
      * precondition compares against it; `None` when the value did not come from the database. */
    updatedAt: Option[String] = None
)

/** A directed, typed reference between sources: `src` --kind--> `dst`.
  * kind ∈ {references, superseded_by, refutes, challenges, updates, supersedes}. */
final case class Edge(src: String, dst: String, kind: String)

// ── wire enum helper ──────────────────────────────────────────────────────────────────────────────
// All domain enums serialise as their LOWERCASE wire string: `Foundational` ↔ `"foundational"`.
// One reusable helper wires circe Encoder/Decoder + tapir Schema.string with Validator.enumeration
// so every enum shares the same pattern and every new value is a compile error, not a runtime one.

/** Reusable derivation for enums whose wire representation is the lowercase name. */
object WireEnum:
  def encoder[E <: reflect.Enum](using values: WireEnumValues[E]): Encoder[E] =
    Encoder.encodeString.contramap(values.wire)
  def decoder[E <: reflect.Enum](using values: WireEnumValues[E]): Decoder[E] =
    Decoder.decodeString.emap(s => values.fromWire(s).toRight(s"unknown value: $s"))
  def schema[E](using values: WireEnumValues[E]): Schema[E] =
    Schema.string[E].validate(Validator.enumeration(values.all.toList))

// ── article kind ──────────────────────────────────────────────────────────────────────────────────

/** The kinds the compiled wiki uses. Not a constraint — the kind field is free text so a new
  * section needs no migration — but the vocabulary the tools advertise and the CLI validates. */
enum ArticleKind:
  case Concept, Myth, Culture, Region, Timeline, Report, Index

object ArticleKind:
  def fromString(s: String): Option[ArticleKind] =
    values.find(_.toString.equalsIgnoreCase(s))

  given WireEnumValues[ArticleKind]:
    def all: Array[ArticleKind] = ArticleKind.values
    def wire(e: ArticleKind): String = e.toString.toLowerCase
    def fromWire(s: String): Option[ArticleKind] =
      ArticleKind.values.find(_.toString.equalsIgnoreCase(s))

  given Encoder[ArticleKind] = WireEnum.encoder
  given Decoder[ArticleKind] = WireEnum.decoder
  given Schema[ArticleKind]  = WireEnum.schema

// ── label display ─────────────────────────────────────────────────────────────────────────────────

/** Display helper for user-facing labels: the ONE place that turns internal `snake_case` into
  * readable `space separated` form. Used by BOTH server Markdown and web — replaces the scattered
  * `_.replace("_", " ")` calls. */
object Label:
  /** `"bronze_age_collapse"` → `"bronze age collapse"`. */
  def display(s: String): String = s.replace("_", " ")

// ── reference types ───────────────────────────────────────────────────────────────────────────────

/** A lightweight reference to a source, carrying just enough to render a citation link.
  * `idLabel` is the DOI when present, else the citekey — the owner's rule: "keep our own internal
  * naming system BUT present DOI in UI when it is available, falling back on our item". */
final case class SourceRef(
    citekey: String,
    doi: Option[String],
    authors: List[String],
    year: Option[Int],
    title: String,
    status: SourceStatus
):
  def idLabel: String = doi.getOrElse(citekey)

  /** `"Lastname & Lastname 2015"` or `"Lastname et al. 2015"`. */
  def shortCite: String =
    val auth = SourceRef.shortAuthors(authors)
    val yr   = year.map(_.toString).getOrElse("n.d.")
    if auth.isEmpty then yr else s"$auth $yr"

object SourceRef:
  /** Extract a surname from an author string, handling both `"Firstname Lastname"` and
    * `"Lastname, Firstname"` forms. */
  def surname(full: String): String =
    val t = full.trim
    if t.contains(",") then t.takeWhile(_ != ',').trim
    else t.split("\\s+").lastOption.getOrElse(t)

  /** `"Lastname & Lastname"` or `"Lastname et al."` from a properly typed author list. */
  def shortAuthors(authors: List[String]): String =
    val names = authors.map(_.trim).filter(_.nonEmpty)
    names match
      case Nil          => ""
      case a :: Nil     => surname(a)
      case a :: b :: Nil => surname(a) + " & " + surname(b)
      case a :: _       => surname(a) + " et al."

/** What the far end of a source's edge is. Since V026 `edges` connects stored sources only, so a
  * `Source` target always resolves; an edge parked in `unresolved_edges` names either a compiled
  * article (`Article`, linked into the wiki) or a work not in the corpus yet (`Unresolved`). */
enum NeighborTarget:
  case Source, Article, Unresolved

object NeighborTarget:
  given WireEnumValues[NeighborTarget]:
    def all: Array[NeighborTarget] = NeighborTarget.values
    def wire(e: NeighborTarget): String = e.toString.toLowerCase
    def fromWire(s: String): Option[NeighborTarget] =
      NeighborTarget.values.find(_.toString.equalsIgnoreCase(s))

  given Encoder[NeighborTarget] = WireEnum.encoder
  given Decoder[NeighborTarget] = WireEnum.decoder
  given Schema[NeighborTarget]  = WireEnum.schema

/** A lightweight reference to an article, carrying just enough to render a link. */
final case class ArticleRef(slug: String, title: String, summary: Option[String] = None)
