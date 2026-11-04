package com.histmersion.api.exchange
import com.histmersion.api.WireEnumValues
import com.histmersion.api.given
import com.histmersion.api.aliases.Aliases
import com.histmersion.api.library.Confidence
import com.histmersion.api.library.Source
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import io.circe.Json
import java.util.UUID
import sttp.tapir.Schema
object Bundle:

  /** The kinds of entity a bundle carries one line per uid of — and what a tombstone says was deleted. Wire names are
    * kebab-case (`article-version`), as the V031 trigger writes them.
    */
  enum Kind:
    case Source, Finding, Article, ArticleVersion, Asset, AssetLink, Attestation, Event, Goal, QueueItem

    /** A place or a named period added HERE (`dataset = 'local'`); the bundled seeds every instance imports itself are
      * not carried (docs/research-merge-import.md §7.8).
      */
    case Place, Period

    def wire: String = Kind.wireOf(this)

    /** The NDJSON file holding this kind's lines. */
    def file: String = s"entities/${Kind.fileStem(this)}.ndjson"

    /** The letter a merge issue about this kind of record starts its id with (`P-1a2b3c4d`). */
    def issueLetter: String = Kind.letterOf(this)

  object Kind:
    def wireOf(k: Kind): String = k match
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
      case Place          => "place"
      case Period         => "period"

    private def fileStem(k: Kind): String = k match
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
      case Place          => "places"
      case Period         => "periods"

    private def letterOf(k: Kind): String = k match
      case Source         => "S"
      case Finding        => "F"
      case Article        => "A"
      case ArticleVersion => "V"
      case Asset          => "X"
      case AssetLink      => "K"
      case Attestation    => "T"
      case Event          => "E"
      case Goal           => "G"
      case QueueItem      => "Q"
      case Place          => "P"
      case Period         => "N"

    def fromWire(s: String): Option[Kind] = values.find(_.wire == s.trim.toLowerCase)

    given WireEnumValues[Kind]:
      def all: Array[Kind] = Kind.values
      def wire(e: Kind): String = wireOf(e)
      def fromWire(s: String): Option[Kind] = Kind.fromWire(s)
  val Format = "histmersion-bundle"
  val FormatVersion = 1
  val Extension = ".hmx"
  val ManifestPath = "manifest.json"

  def blobPath(sha256: String): String = s"blobs/sha256/${sha256.take(2)}/$sha256"

  def blobSha(path: String): Option[String] =
    val prefix = "blobs/sha256/"
    Option
      .when(path.startsWith(prefix)) {
        val rest = path.drop(prefix.length)
        rest.drop(3)
      }
      .filter(s =>
        s.length == 64 && s.forall(c => c.isDigit || ('a' to 'f').contains(c)) &&
          path == blobPath(s)
      )

  final case class Alias(alias: String, sourceUid: UUID, createdAt: String) derives KindlingsCodecAsObject

  final case class Article(
      uid: UUID,
      origin: UUID,
      slug: String,
      title: String,
      kind: String,
      body: String,
      summary: Option[String],
      tags: List[String],
      regions: List[String],
      period: Option[Range],
      updatedAt: String,
      rev: String = ""
  ) derives KindlingsCodecAsObject

  final case class ArticleAlias(alias: String, articleUid: UUID, createdAt: String) derives KindlingsCodecAsObject

  final case class ArticleVersion(
      uid: UUID,
      origin: UUID,
      articleUid: UUID,
      bodySha256: String,
      summary: Option[String],
      versionOf: String,
      updatedAt: String,
      rev: String = ""
  ) derives KindlingsCodecAsObject

  final case class Assessment(
      sourceUid: UUID,
      checkedAt: String,
      retracted: Boolean,
      notices: Json,
      citedBy: Option[Int],
      influential: Option[Int],
      verdict: String
  ) derives KindlingsCodecAsObject

  final case class Asset(
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
      period: Option[Range],
      updatedAt: String,
      rev: String = ""
  ) derives KindlingsCodecAsObject

  final case class AssetLink(
      uid: UUID,
      origin: UUID,
      assetUid: UUID,
      relation: String,
      targetType: String,
      targetId: String,
      targetUid: Option[UUID],
      xywh: Option[String],
      notes: Option[String],
      updatedAt: String,
      rev: String = ""
  ) derives KindlingsCodecAsObject

  final case class Attestation(
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

  final case class Blobs(count: Int, bytes: Long) derives KindlingsCodecAsObject, KindlingsSchema
  object Blobs:
    given Schema[Blobs] = summon[KindlingsSchema[Blobs]].schema

  final case class Correction(
      targetKind: String,
      targetId: String,
      targetUid: Option[UUID],
      field: String,
      value: String,
      upstream: Option[String],
      upstreamValue: String,
      upstreamNow: Option[String],
      status: String,
      createdAt: String,
      updatedAt: String,
      checkedAt: Option[String]
  ) derives KindlingsCodecAsObject

  final case class Edge(srcUid: UUID, dstUid: UUID, kind: String) derives KindlingsCodecAsObject

  final case class Event(
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
      placeId: Option[String],
      parentUid: Option[UUID],
      description: Option[String],
      citekey: Option[String],
      sourceUid: Option[UUID],
      datings: List[EventDating],
      edges: List[EventEdge],
      participants: List[EventParticipant],
      updatedAt: String,
      rev: String = "",
      /** Absent from the JSON when empty, so the rev of an event that states none stays what older bundles computed. */
      scale: Option[String] = None,
      /** Each absent from the JSON when empty, so the rev of an event compiled from sources stays what older bundles
        * computed.
        */
      catalogue: Option[String] = None,
      names: Option[List[String]] = None,
      wikidata: Option[String] = None,
      central: Option[Long] = None,
      plusMinus: Option[Long] = None,
      datingMethod: Option[String] = None
  ) derives KindlingsCodecAsObject

  final case class EventAlias(alias: String, eventUid: UUID, createdAt: String) derives KindlingsCodecAsObject

  final case class EventCandidate(
      eventUid: UUID,
      sourceUid: UUID,
      citekey: String,
      score: Double,
      status: String,
      findings: List[String],
      passages: Json,
      reasons: List[String],
      computedAt: String
  ) derives KindlingsCodecAsObject

  final case class EventCatalogue(
      eventUid: UUID,
      provider: String,
      providerId: String,
      url: Option[String],
      label: String,
      kind: String,
      notBefore: Option[Long],
      beginBy: Option[Long],
      endFrom: Option[Long],
      notAfter: Option[Long],
      lat: Option[Double],
      lon: Option[Double],
      vei: Option[Int],
      magnitude: Option[Double],
      deaths: Option[Long],
      historicity: String,
      record: Json,
      retrievedAt: String
  ) derives KindlingsCodecAsObject

  final case class EventCheck(eventUid: UUID, checkedAt: String) derives KindlingsCodecAsObject

  final case class EventDating(
      citekey: String,
      sourceUid: Option[UUID],
      notBefore: Option[Long],
      beginBy: Option[Long],
      endFrom: Option[Long],
      notAfter: Option[Long],
      method: Option[String],
      note: Option[String]
  ) derives KindlingsCodecAsObject

  final case class EventEdge(
      dstUid: UUID,
      kind: String,
      citekey: String,
      sourceUid: Option[UUID],
      note: Option[String]
  ) derives KindlingsCodecAsObject

  final case class EventParticipant(actor: String, role: String, citekey: Option[String], sourceUid: Option[UUID])
      derives KindlingsCodecAsObject

  object EventSets:
    def candidateStatus(here: String, there: String): String =
      if here == "proposed" then there else here

    /** Timestamps share one fixed-width UTC format, so text order is time order. */
    def newer(here: Option[String], there: Option[String]): Boolean = (here, there) match
      case (None, Some(_))    => true
      case (Some(h), Some(t)) => t > h
      case _                  => false

  final case class FileInfo(path: String, lines: Int, bytes: Long, sha256: String)
      derives KindlingsCodecAsObject,
        KindlingsSchema
  object FileInfo:
    given Schema[FileInfo] = summon[KindlingsSchema[FileInfo]].schema

  object Files:
    val Edges = "entities/edges.ndjson"
    val UnresolvedEdges = "entities/unresolved-edges.ndjson"
    val Assessments = "entities/assessments.ndjson"
    val Aliases = "entities/aliases.ndjson"

    val ArticleAliases = "entities/article-aliases.ndjson"

    val EventAliases = "entities/event-aliases.ndjson"
    val GoalAliases = "entities/goal-aliases.ndjson"
    val PlaceAliases = "entities/place-aliases.ndjson"

    /** Absent from an older bundle, which reads as empty. */
    val EventCatalogue = "entities/event-catalogue.ndjson"
    val EventCandidates = "entities/event-candidates.ndjson"
    val EventChecks = "entities/event-checks.ndjson"
    val EventCorrections = "entities/corrections.ndjson"
    val PlaceSiteTypes = "entities/site-types.ndjson"
    val Tombstones = "entities/tombstones.ndjson"

    val all: List[String] =
      com.histmersion.api.exchange.Bundle.Kind.values.toList.map(_.file) ++
        List(
          Edges,
          UnresolvedEdges,
          Assessments,
          Aliases,
          ArticleAliases,
          EventAliases,
          GoalAliases,
          PlaceAliases,
          EventCatalogue,
          EventCandidates,
          EventChecks,
          EventCorrections,
          PlaceSiteTypes,
          Tombstones
        )

  final case class Finding(
      uid: UUID,
      origin: UUID,
      sourceUid: UUID,
      id: String,
      claim: String,
      region: Option[String],
      lon: Option[Double],
      lat: Option[Double],
      period: Option[Range],
      periodName: Option[String],
      tags: List[String],
      confidence: Option[Confidence],
      significance: Option[String],
      updatedAt: String,
      rev: String = ""
  ) derives KindlingsCodecAsObject

  final case class Goal(
      uid: UUID,
      origin: UUID,
      slug: String,
      title: String,
      status: String,
      notes: Option[String],
      tags: List[String],
      regions: List[String],
      period: Option[Range],
      parentUid: Option[UUID],
      createdAt: String,
      updatedAt: String,
      rev: String = "",
      /** The four fields below are absent when empty, so a line written before they existed keeps its `rev`. */
      plan: Option[Json] = None,
      planApprovedAt: Option[String] = None,
      budgetUsd: Option[Double] = None,
      links: Option[List[GoalLink]] = None,
      schedules: Option[List[GoalSchedule]] = None
  ) derives KindlingsCodecAsObject

  final case class GoalAlias(alias: String, goalUid: UUID, createdAt: String) derives KindlingsCodecAsObject

  final case class GoalLink(
      kind: String,
      ref: String,
      refUid: Option[UUID],
      note: Option[String],
      createdAt: String
  ) derives KindlingsCodecAsObject

  final case class GoalSchedule(kind: String, cron: String, enabled: Boolean, budgetUsd: Option[Double])
      derives KindlingsCodecAsObject

  final case class ImportResult(
      bundleId: UUID,
      from: Instance,
      dryRun: Boolean,
      applied: Boolean,
      counts: List[KindCounts],
      blobs: Int,
      problems: List[String],
      conflicts: List[String],
      notes: List[String],
      mergeId: Option[UUID] = None,
      issues: List[MergeIssue] = Nil,
      planHash: Option[String] = None
  ) derives KindlingsCodecAsObject,
        KindlingsSchema:
    def changed: Int = counts.map(_.changed).sum
  object ImportResult:
    given Schema[ImportResult] = summon[KindlingsSchema[ImportResult]].schema

  final case class Instance(id: UUID, label: Option[String] = None) derives KindlingsCodecAsObject, KindlingsSchema
  object Instance:
    given Schema[Instance] = summon[KindlingsSchema[Instance]].schema

  final case class KindCounts(
      kind: com.histmersion.api.exchange.Bundle.Kind,
      inserted: Int = 0,
      updated: Int = 0,
      unchanged: Int = 0,
      keptLocal: Int = 0,
      conflicts: Int = 0,
      deleted: Int = 0,
      skippedDeleted: Int = 0,
      held: Int = 0
  ) derives KindlingsCodecAsObject,
        KindlingsSchema:
    def changed: Int = inserted + updated + deleted
  object KindCounts:
    given Schema[KindCounts] = summon[KindlingsSchema[KindCounts]].schema

  final case class Manifest(
      format: String,
      formatVersion: Int,
      schemaVersion: String,
      bundleId: UUID,
      instance: Instance,
      since: Option[String],
      asOf: Option[String],
      files: List[FileInfo],
      blobs: Blobs,
      reference: Reference
  ) derives KindlingsCodecAsObject,
        KindlingsSchema
  object Manifest:
    given Schema[Manifest] = summon[KindlingsSchema[Manifest]].schema

  final case class Period(
      uid: UUID,
      origin: UUID,
      id: String,
      label: String,
      authority: Option[String],
      period: Option[Range],
      region: Option[String],
      parentId: Option[String],
      updatedAt: String,
      rev: String = ""
  ) derives KindlingsCodecAsObject

  final case class Place(
      uid: UUID,
      origin: UUID,
      id: String,
      label: String,
      kind: String,
      geom: Option[String],
      period: Option[Range],
      source: Option[String],
      properties: Option[Json],
      names: List[PlaceName],
      relations: List[PlaceRelation],
      links: List[PlaceLink],
      types: List[PlaceType],
      updatedAt: String,
      rev: String = ""
  ) derives KindlingsCodecAsObject

  final case class PlaceAlias(alias: String, placeUid: UUID, createdAt: String) derives KindlingsCodecAsObject

  final case class PlaceLink(scheme: String, externalId: String, relation: String) derives KindlingsCodecAsObject

  final case class PlaceName(name: String, lang: Option[String], period: Option[Range]) derives KindlingsCodecAsObject

  final case class PlaceRelation(parentId: String, kind: String, period: Option[Range]) derives KindlingsCodecAsObject

  final case class PlaceType(typeLabel: String, period: Option[Range], citekey: Option[String])
      derives KindlingsCodecAsObject

  final case class QueueItem(
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

  /** Both bounds inclusive: Postgres keeps an `int8range` as `[lower, upper)`, so `upper − 1` is applied on the way out
    * and undone on the way in.
    */
  final case class Range(start: Option[Long] = None, end: Option[Long] = None) derives KindlingsCodecAsObject

  final case class Reference(places: Int, placesDigest: String, periods: Int, periodsDigest: String)
      derives KindlingsCodecAsObject,
        KindlingsSchema
  object Reference:
    given Schema[Reference] = summon[KindlingsSchema[Reference]].schema

  final case class SiteType(placeId: String, siteType: Option[String], checkedAt: Option[String])
      derives KindlingsCodecAsObject

  final case class Source(
      uid: UUID,
      origin: UUID,
      citekey: String,
      title: String,
      authors: List[String],
      doi: Option[String],
      year: Option[Int],
      venue: Option[String],
      status: com.histmersion.api.library.Source.Status,
      topics: List[String],
      regions: List[String],
      tags: List[String],
      period: Option[Range],
      summary: Option[String],
      notes: Option[String],
      provenance: Option[String],
      citedByCount: Option[Int],
      influentialCitations: Option[Int],
      addedDate: String,
      bodySha256: Option[String],
      originalSha256: Option[String],
      originalExt: Option[String],
      updatedAt: String,
      rev: String = "",
      /** Absent when there are none, so a line written before the field existed keeps its rev. */
      isbns: Option[List[String]] = None
  ) derives KindlingsCodecAsObject

  final case class Tombstone(
      uid: UUID,
      kind: com.histmersion.api.exchange.Bundle.Kind,
      origin: UUID,
      deletedAt: String
  ) derives KindlingsCodecAsObject

  final case class UnresolvedEdge(srcUid: UUID, dst: String, kind: String) derives KindlingsCodecAsObject

  final case class Upload(sha256: String, bytes: Long, manifest: Option[Manifest], problems: List[String])
      derives KindlingsCodecAsObject,
        KindlingsSchema
  object Upload:
    given Schema[Upload] = summon[KindlingsSchema[Upload]].schema

  final case class VerifyResult(
      ok: Boolean,
      manifest: Option[Manifest],
      blobsChecked: Int,
      problems: List[String]
  ) derives KindlingsCodecAsObject,
        KindlingsSchema
  object VerifyResult:
    given Schema[VerifyResult] = summon[KindlingsSchema[VerifyResult]].schema
