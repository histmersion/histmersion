package com.histmersion.core

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import io.circe.{Decoder, Encoder, Json}
import sttp.tapir.Schema

import java.util.UUID

/** The portable bundle (`.hmx`, docs/research-merge-import.md §4): a lossless, logical,
  * identity-carrying export of one collection, built to be imported into another.
  *
  * A bundle is a gzip-compressed tar of
  * {{{
  * manifest.json
  * entities/<kind>.ndjson     one JSON object per line, sorted by uid (or by the owner's uid)
  * blobs/sha256/ab/abcdef…    OCR bodies, article-version bodies, asset bytes, stored originals
  * }}}
  * Every entity line carries its `uid`, its `origin` (the instance that created it), its `rev` (the
  * sha256 of the line's canonical JSON without `rev` and `updatedAt`, see `BundleFormat.stamp`) and
  * its label (citekey, slug, id); other entities are referenced BY UID. Calibre is not part of a
  * bundle (§3a): `calibre_id` is a per-machine pointer and is never exported.
  *
  * The types here are the FILE format. They have circe codecs (one line = one value) but no tapir
  * schemas: no endpoint takes or returns them, except the manifest and the reports at the bottom. */
object Bundle:
  val Format        = "histmersion-bundle"
  val FormatVersion = 1
  val Extension     = ".hmx"
  val ManifestPath  = "manifest.json"

  /** Where a blob lives in the tar: content-addressed, fanned out on its first byte. */
  def blobPath(sha256: String): String = s"blobs/sha256/${sha256.take(2)}/$sha256"

  /** The sha256 named by a blob path, when `path` is one. */
  def blobSha(path: String): Option[String] =
    val prefix = "blobs/sha256/"
    Option.when(path.startsWith(prefix)) {
      val rest = path.drop(prefix.length)
      rest.drop(3)
    }.filter(s => s.length == 64 && s.forall(c => c.isDigit || ('a' to 'f').contains(c)) &&
      path == blobPath(s))

/** The kinds of entity a bundle carries one line per uid of — and what a tombstone says was deleted.
  * Wire names are kebab-case (`article-version`), as the V031 trigger writes them. */
enum BundleKind:
  case Source, Finding, Article, ArticleVersion, Asset, AssetLink, Attestation, Event, Goal, QueueItem

  def wire: String = BundleKind.wireOf(this)

  /** The NDJSON file holding this kind's lines. */
  def file: String = s"entities/${BundleKind.fileStem(this)}.ndjson"

object BundleKind:
  def wireOf(k: BundleKind): String = k match
    case Source         => "source"
    case Finding        => "finding"
    case Article        => "article"
    case ArticleVersion => "article-version"
    case Asset          => "asset"
    case AssetLink      => "asset-link"
    case Attestation    => "attestation"
    case Event          => "event"
    case Goal           => "goal"
    case QueueItem      => "queue-item"

  private def fileStem(k: BundleKind): String = k match
    case Source         => "sources"
    case Finding        => "findings"
    case Article        => "articles"
    case ArticleVersion => "article-versions"
    case Asset          => "assets"
    case AssetLink      => "asset-links"
    case Attestation    => "attestations"
    case Event          => "events"
    case Goal           => "goals"
    case QueueItem      => "queue"

  def fromWire(s: String): Option[BundleKind] = values.find(_.wire == s.trim.toLowerCase)

  given WireEnumValues[BundleKind]:
    def all: Array[BundleKind]                  = BundleKind.values
    def wire(e: BundleKind): String             = wireOf(e)
    def fromWire(s: String): Option[BundleKind] = BundleKind.fromWire(s)

  given Encoder[BundleKind] = WireEnum.encoder
  given Decoder[BundleKind] = WireEnum.decoder
  given Schema[BundleKind]  = WireEnum.schema

/** The files of a bundle besides the blobs, in the order they are written. The entity files of
  * [[BundleKind]] come first; the owned sets that have no uid of their own follow. */
object BundleFiles:
  val Edges           = "entities/edges.ndjson"
  val UnresolvedEdges = "entities/unresolved-edges.ndjson"
  val Assessments     = "entities/assessments.ndjson"
  val Aliases         = "entities/aliases.ndjson"
  val Tombstones      = "entities/tombstones.ndjson"

  val all: List[String] =
    BundleKind.values.toList.map(_.file) ++ List(Edges, UnresolvedEdges, Assessments, Aliases, Tombstones)

/** A nullable `int8range` read losslessly: `None` bounds are unbounded, both are INCLUSIVE (the
  * `upper − 1` of AGENTS.md is applied on the way out and undone on the way in). */
final case class BundleRange(start: Option[Long] = None, end: Option[Long] = None) derives KindlingsCodecAsObject

final case class BundleSource(
    uid: UUID,
    origin: UUID,
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
    period: Option[BundleRange],
    summary: Option[String],
    notes: Option[String],
    provenance: Option[String],
    citedByCount: Option[Int],
    influentialCitations: Option[Int],
    addedDate: String,
    /** The sha256 of the UTF-8 OCR body, a blob of the bundle. */
    bodySha256: Option[String],
    /** `sources.sha256`: the digest of the uploaded original. */
    originalSha256: Option[String],
    /** The extension the server stored the original under — present iff the original is a blob of
      * the bundle (a source ingested elsewhere has a digest but no stored file). */
    originalExt: Option[String],
    updatedAt: String,
    rev: String = "",
    /** ISBN-13s recorded for the source (`source_identifiers`); absent when there are none, so a
      * line written before the field existed keeps its rev. A strong key of the matcher (M3). */
    isbns: Option[List[String]] = None
) derives KindlingsCodecAsObject

final case class BundleFinding(
    uid: UUID,
    origin: UUID,
    sourceUid: UUID,
    /** The label, `<citekey>#<n>`. */
    id: String,
    claim: String,
    region: Option[String],
    lon: Option[Double],
    lat: Option[Double],
    period: Option[BundleRange],
    periodName: Option[String],
    tags: List[String],
    confidence: Option[Confidence],
    significance: Option[String],
    updatedAt: String,
    rev: String = ""
) derives KindlingsCodecAsObject

/** A source → source edge (`edges`). The whole out-set of a source travels with it. */
final case class BundleEdge(srcUid: UUID, dstUid: UUID, kind: String) derives KindlingsCodecAsObject

/** An edge parked until its target arrives (`unresolved_edges`): `dst` is a label, not a uid. */
final case class BundleUnresolvedEdge(srcUid: UUID, dst: String, kind: String) derives KindlingsCodecAsObject

final case class BundleAssessment(
    sourceUid: UUID,
    checkedAt: String,
    retracted: Boolean,
    notices: Json,
    citedBy: Option[Int],
    influential: Option[Int],
    verdict: String
) derives KindlingsCodecAsObject

/** An old citekey (`source_aliases`) and the source it names now. */
final case class BundleAlias(alias: String, sourceUid: UUID, createdAt: String) derives KindlingsCodecAsObject

final case class BundleArticle(
    uid: UUID,
    origin: UUID,
    slug: String,
    title: String,
    kind: String,
    body: String,
    summary: Option[String],
    tags: List[String],
    regions: List[String],
    period: Option[BundleRange],
    updatedAt: String,
    rev: String = ""
) derives KindlingsCodecAsObject

/** One recorded previous version of an article; the body is a blob. */
final case class BundleArticleVersion(
    uid: UUID,
    origin: UUID,
    articleUid: UUID,
    bodySha256: String,
    summary: Option[String],
    /** The `updated_at` of the version that was replaced. */
    versionOf: String,
    /** When the version was recorded; it never changes, so it is also the line's `updatedAt`. */
    updatedAt: String,
    rev: String = ""
) derives KindlingsCodecAsObject

final case class BundleAsset(
    uid: UUID,
    origin: UUID,
    sourceUid: UUID,
    name: String,
    mime: String,
    sha256: String,
    width: Option[Int],
    height: Option[Int],
    kind: Option[String],
    caption: Option[String],
    license: Option[String],
    /** Hex EWKB — exact, unlike WKT at a fixed number of decimals. */
    geom: Option[String],
    period: Option[BundleRange],
    updatedAt: String,
    rev: String = ""
) derives KindlingsCodecAsObject

final case class BundleAssetLink(
    uid: UUID,
    origin: UUID,
    assetUid: UUID,
    relation: String,
    targetType: String,
    /** The target's label (a finding id, a citekey, a slug, an event or place id). */
    targetId: String,
    /** The target's uid when it is an entity that has one. */
    targetUid: Option[UUID],
    xywh: Option[String],
    notes: Option[String],
    updatedAt: String,
    rev: String = ""
) derives KindlingsCodecAsObject

final case class BundleAttestation(
    uid: UUID,
    origin: UUID,
    sourceUid: UUID,
    targetType: String,
    targetId: String,
    targetUid: Option[UUID],
    quote: Option[String],
    prefix: Option[String],
    suffix: Option[String],
    charStart: Option[Int],
    charEnd: Option[Int],
    chunkOrd: Option[Int],
    page: Option[String],
    canvas: Option[String],
    xywh: Option[String],
    ctsUrn: Option[String],
    note: Option[String],
    createdAt: String,
    updatedAt: String,
    rev: String = ""
) derives KindlingsCodecAsObject

final case class BundleEventDating(
    citekey: String, sourceUid: Option[UUID], notBefore: Option[Long], beginBy: Option[Long],
    endFrom: Option[Long], notAfter: Option[Long], method: Option[String], note: Option[String]
) derives KindlingsCodecAsObject

final case class BundleEventEdge(dstUid: UUID, kind: String, citekey: String, sourceUid: Option[UUID],
    note: Option[String]) derives KindlingsCodecAsObject

final case class BundleEventParticipant(actor: String, role: String, citekey: Option[String],
    sourceUid: Option[UUID]) derives KindlingsCodecAsObject

final case class BundleEvent(
    uid: UUID,
    origin: UUID,
    id: String,
    label: String,
    kind: String,
    notBefore: Option[Long],
    beginBy: Option[Long],
    endFrom: Option[Long],
    notAfter: Option[Long],
    historicity: String,
    geom: Option[String],
    /** A gazetteer id: places are reference data present on both sides (see [[BundleReference]]). */
    placeId: Option[String],
    parentUid: Option[UUID],
    description: Option[String],
    citekey: Option[String],
    sourceUid: Option[UUID],
    datings: List[BundleEventDating],
    /** The OUTGOING edges (`src_id` = this event). */
    edges: List[BundleEventEdge],
    participants: List[BundleEventParticipant],
    updatedAt: String,
    rev: String = ""
) derives KindlingsCodecAsObject

final case class BundleGoal(
    uid: UUID,
    origin: UUID,
    slug: String,
    title: String,
    status: String,
    notes: Option[String],
    tags: List[String],
    regions: List[String],
    period: Option[BundleRange],
    parentUid: Option[UUID],
    createdAt: String,
    updatedAt: String,
    rev: String = ""
) derives KindlingsCodecAsObject

/** A queue row. Its serial `id` is a local label and is not carried. */
final case class BundleQueueItem(
    uid: UUID,
    origin: UUID,
    list: String,
    text: String,
    topic: Option[String],
    status: String,
    reason: Option[String],
    createdAt: String,
    doneAt: Option[String],
    updatedAt: String,
    rev: String = ""
) derives KindlingsCodecAsObject

final case class BundleTombstone(uid: UUID, kind: BundleKind, origin: UUID, deletedAt: String)
    derives KindlingsCodecAsObject

// ── the manifest ──────────────────────────────────────────────────────────────────────────────────

final case class BundleInstance(id: UUID, label: Option[String] = None) derives KindlingsCodecAsObject
object BundleInstance:
  given Schema[BundleInstance] = KindlingsSchema.derived[BundleInstance].schema

/** One NDJSON file: its path, how many lines, its size and its sha256. */
final case class BundleFileInfo(path: String, lines: Int, bytes: Long, sha256: String) derives KindlingsCodecAsObject
object BundleFileInfo:
  given Schema[BundleFileInfo] = KindlingsSchema.derived[BundleFileInfo].schema

final case class BundleBlobs(count: Int, bytes: Long) derives KindlingsCodecAsObject
object BundleBlobs:
  given Schema[BundleBlobs] = KindlingsSchema.derived[BundleBlobs].schema

/** The reference data a bundle does NOT carry, fingerprinted: the gazetteer and the named periods
  * come from the same bundled seeds on every instance (`histmersion init`), so events point into them
  * by id. A differing fingerprint is reported on import, never fatal. */
final case class BundleReference(places: Int, placesDigest: String, periods: Int, periodsDigest: String)
    derives KindlingsCodecAsObject
object BundleReference:
  given Schema[BundleReference] = KindlingsSchema.derived[BundleReference].schema

/** `manifest.json`. Deterministic: there is no wall-clock export time — `asOf` is the newest change
  * the bundle carries (the value to pass as the next `--since`), and `bundleId` is derived from the
  * instance and the content, so two exports of an unchanged database are byte-identical. */
final case class BundleManifest(
    format: String,
    formatVersion: Int,
    schemaVersion: String,
    bundleId: UUID,
    instance: BundleInstance,
    since: Option[String],
    asOf: Option[String],
    files: List[BundleFileInfo],
    blobs: BundleBlobs,
    reference: BundleReference
) derives KindlingsCodecAsObject
object BundleManifest:
  given Schema[BundleManifest] = KindlingsSchema.derived[BundleManifest].schema

// ── what the endpoints answer ─────────────────────────────────────────────────────────────────────

/** `GET /api/instance` — this collection's identity. */
final case class InstanceInfo(id: UUID, label: Option[String], createdAt: String) derives KindlingsCodecAsObject
object InstanceInfo:
  given Schema[InstanceInfo] = KindlingsSchema.derived[InstanceInfo].schema

/** `PUT /api/instance` — name this collection ("work", "home"); blank clears the label. */
final case class InstanceLabel(label: Option[String]) derives KindlingsCodecAsObject
object InstanceLabel:
  given Schema[InstanceLabel] = KindlingsSchema.derived[InstanceLabel].schema

/** `POST /api/bundle/verify` — every problem found, not just the first. */
final case class BundleVerifyResult(
    ok: Boolean,
    manifest: Option[BundleManifest],
    blobsChecked: Int,
    problems: List[String]
) derives KindlingsCodecAsObject
object BundleVerifyResult:
  given Schema[BundleVerifyResult] = KindlingsSchema.derived[BundleVerifyResult].schema

/** What an import did (or, with `dryRun`, would do) to the entities of one kind. */
final case class BundleKindCounts(
    kind: BundleKind,
    inserted: Int = 0,
    updated: Int = 0,
    unchanged: Int = 0,
    keptLocal: Int = 0,
    conflicts: Int = 0,
    deleted: Int = 0,
    skippedDeleted: Int = 0,
    /** Held back for `merge apply`: part of a source that is the same work as one here (M3). */
    held: Int = 0
) derives KindlingsCodecAsObject:
  def changed: Int = inserted + updated + deleted
object BundleKindCounts:
  given Schema[BundleKindCounts] = KindlingsSchema.derived[BundleKindCounts].schema

/** `POST /api/bundle/import`. `problems` are blocking — when there are any nothing was written;
  * among them every open issue of the merge plan (`issues` has them whole, to render as `merge plan`
  * does) and every decision only `merge apply` can carry out. `conflicts` are rows changed on BOTH
  * sides since the last import that a resolution decided; `notes` are informational. */
final case class BundleImportResult(
    bundleId: UUID,
    from: BundleInstance,
    dryRun: Boolean,
    applied: Boolean,
    counts: List[BundleKindCounts],
    blobs: Int,
    problems: List[String],
    conflicts: List[String],
    notes: List[String],
    mergeId: Option[UUID] = None,
    /** The plan's issues that hold this import up (open, or decided in a way only `merge apply` can
      * carry out). */
    issues: List[MergeIssue] = Nil,
    planHash: Option[String] = None
) derives KindlingsCodecAsObject:
  def changed: Int = counts.map(_.changed).sum
object BundleImportResult:
  given Schema[BundleImportResult] = KindlingsSchema.derived[BundleImportResult].schema
