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
  type BlobPathResult = BlobPathResult.T
  object BlobPathResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BlobPathSha256 = BlobPathSha256.T
  object BlobPathSha256 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BlobShaResult = BlobShaResult.T
  object BlobShaResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BlobShaPath = BlobShaPath.T
  object BlobShaPath extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  enum Kind:
    case Source, Finding, Article, ArticleVersion, Asset, AssetLink, Attestation, Event, Goal, QueueItem

    /** A place or a named period added HERE (`dataset = 'local'`); the bundled seeds every instance imports itself are
      * not carried (docs/research-merge-import.md §7.8).
      */
    case Place, Period

    def wire: com.histmersion.api.exchange.Bundle.Kind.WireResult = Kind.wireOf(this)

    /** The NDJSON file holding this kind's lines. */
    def file: com.histmersion.api.exchange.Bundle.Kind.FileResult = _root_.com.histmersion.api.exchange.Bundle.Kind.FileResult(s"entities/${Kind.fileStem(this)}.ndjson")

    /** The letter a merge issue about this kind of record starts its id with (`P-1a2b3c4d`). */
    def issueLetter: com.histmersion.api.exchange.Bundle.Kind.IssueLetterResult = Kind.letterOf(this)

  object Kind:
    type WireResult = WireResult.T
    object WireResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type FileResult = FileResult.T
    object FileResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type IssueLetterResult = IssueLetterResult.T
    object IssueLetterResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type WireOfResult = WireOfResult.T
    object WireOfResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type FileStemResult = FileStemResult.T
    object FileStemResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type LetterOfResult = LetterOfResult.T
    object LetterOfResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type FromWireS = FromWireS.T
    object FromWireS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    def wireOf(k: Kind): com.histmersion.api.exchange.Bundle.Kind.WireOfResult = k match
      case Source         => _root_.com.histmersion.api.exchange.Bundle.Kind.WireOfResult("source")
      case Finding        => _root_.com.histmersion.api.exchange.Bundle.Kind.WireOfResult("finding")
      case Article        => _root_.com.histmersion.api.exchange.Bundle.Kind.WireOfResult("article")
      case ArticleVersion => _root_.com.histmersion.api.exchange.Bundle.Kind.WireOfResult("article-version")
      case Asset          => _root_.com.histmersion.api.exchange.Bundle.Kind.WireOfResult("asset")
      case AssetLink      => _root_.com.histmersion.api.exchange.Bundle.Kind.WireOfResult("asset-link")
      case Attestation    => _root_.com.histmersion.api.exchange.Bundle.Kind.WireOfResult("attestation")
      case Event          => _root_.com.histmersion.api.exchange.Bundle.Kind.WireOfResult("event")
      case Goal           => _root_.com.histmersion.api.exchange.Bundle.Kind.WireOfResult("goal")
      case QueueItem      => _root_.com.histmersion.api.exchange.Bundle.Kind.WireOfResult("queue-item")
      case Place          => _root_.com.histmersion.api.exchange.Bundle.Kind.WireOfResult("place")
      case Period         => _root_.com.histmersion.api.exchange.Bundle.Kind.WireOfResult("period")

    private def fileStem(k: Kind): com.histmersion.api.exchange.Bundle.Kind.FileStemResult = k match
      case Source         => _root_.com.histmersion.api.exchange.Bundle.Kind.FileStemResult("sources")
      case Finding        => _root_.com.histmersion.api.exchange.Bundle.Kind.FileStemResult("findings")
      case Article        => _root_.com.histmersion.api.exchange.Bundle.Kind.FileStemResult("articles")
      case ArticleVersion => _root_.com.histmersion.api.exchange.Bundle.Kind.FileStemResult("article-versions")
      case Asset          => _root_.com.histmersion.api.exchange.Bundle.Kind.FileStemResult("assets")
      case AssetLink      => _root_.com.histmersion.api.exchange.Bundle.Kind.FileStemResult("asset-links")
      case Attestation    => _root_.com.histmersion.api.exchange.Bundle.Kind.FileStemResult("attestations")
      case Event          => _root_.com.histmersion.api.exchange.Bundle.Kind.FileStemResult("events")
      case Goal           => _root_.com.histmersion.api.exchange.Bundle.Kind.FileStemResult("goals")
      case QueueItem      => _root_.com.histmersion.api.exchange.Bundle.Kind.FileStemResult("queue")
      case Place          => _root_.com.histmersion.api.exchange.Bundle.Kind.FileStemResult("places")
      case Period         => _root_.com.histmersion.api.exchange.Bundle.Kind.FileStemResult("periods")

    private def letterOf(k: Kind): com.histmersion.api.exchange.Bundle.Kind.LetterOfResult = k match
      case Source         => _root_.com.histmersion.api.exchange.Bundle.Kind.LetterOfResult("S")
      case Finding        => _root_.com.histmersion.api.exchange.Bundle.Kind.LetterOfResult("F")
      case Article        => _root_.com.histmersion.api.exchange.Bundle.Kind.LetterOfResult("A")
      case ArticleVersion => _root_.com.histmersion.api.exchange.Bundle.Kind.LetterOfResult("V")
      case Asset          => _root_.com.histmersion.api.exchange.Bundle.Kind.LetterOfResult("X")
      case AssetLink      => _root_.com.histmersion.api.exchange.Bundle.Kind.LetterOfResult("K")
      case Attestation    => _root_.com.histmersion.api.exchange.Bundle.Kind.LetterOfResult("T")
      case Event          => _root_.com.histmersion.api.exchange.Bundle.Kind.LetterOfResult("E")
      case Goal           => _root_.com.histmersion.api.exchange.Bundle.Kind.LetterOfResult("G")
      case QueueItem      => _root_.com.histmersion.api.exchange.Bundle.Kind.LetterOfResult("Q")
      case Place          => _root_.com.histmersion.api.exchange.Bundle.Kind.LetterOfResult("P")
      case Period         => _root_.com.histmersion.api.exchange.Bundle.Kind.LetterOfResult("N")

    def fromWire(s: com.histmersion.api.exchange.Bundle.Kind.FromWireS): Option[Kind] = values.find(_.wire == (s).value.trim.toLowerCase)

    given WireEnumValues[Kind]:
      def all: Array[Kind] = Kind.values
      def wire(e: Kind): com.histmersion.api.exchange.Bundle.Kind.WireResult = wireOf(e)
      def fromWire(s: com.histmersion.api.exchange.Bundle.Kind.FromWireS): Option[Kind] = Kind.fromWire(s)
  val Format = "histmersion-bundle"
  val FormatVersion = 1
  val Extension = ".hmx"
  val ManifestPath = "manifest.json"

  def blobPath(sha256: com.histmersion.api.exchange.Bundle.BlobPathSha256): com.histmersion.api.exchange.Bundle.BlobPathResult = _root_.com.histmersion.api.exchange.Bundle.BlobPathResult(s"blobs/sha256/${(sha256).value.take(2)}/$sha256")

  def blobSha(path: com.histmersion.api.exchange.Bundle.BlobShaPath): Option[com.histmersion.api.exchange.Bundle.BlobShaResult] =
    val prefix = "blobs/sha256/"
    (Option
      .when((path).value.startsWith(prefix)) {
        val rest = (path).value.drop(prefix.length)
        rest.drop(3)
      }
      .filter(s =>
        s.length == 64 && s.forall(c => c.isDigit || ('a' to 'f').contains(c)) &&
          path == blobPath(_root_.com.histmersion.api.exchange.Bundle.BlobPathSha256(s))
      )).map(refactorValue => _root_.com.histmersion.api.exchange.Bundle.BlobShaResult(refactorValue))

  final case class Alias(alias: Alias.Value, sourceUid: UUID, createdAt: Alias.CreatedAt) derives KindlingsCodecAsObject

  object Alias:
    type Value = Value.T
    object Value extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type CreatedAt = CreatedAt.T
    object CreatedAt extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  final case class Article(
      uid: UUID,
      origin: UUID,
      slug: Article.Slug,
      title: Article.Title,
      kind: Article.Kind,
      body: Article.Body,
      summary: Option[Article.Summary],
      tags: List[Article.Tags],
      regions: List[Article.Regions],
      period: Option[Range],
      updatedAt: Article.UpdatedAt,
      rev: Article.Rev = _root_.com.histmersion.api.exchange.Bundle.Article.Rev("")
  ) derives KindlingsCodecAsObject

  object Article:
    type Slug = Slug.T
    object Slug extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Title = Title.T
    object Title extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Kind = Kind.T
    object Kind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Body = Body.T
    object Body extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Summary = Summary.T
    object Summary extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Tags = Tags.T
    object Tags extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Regions = Regions.T
    object Regions extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type UpdatedAt = UpdatedAt.T
    object UpdatedAt extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Rev = Rev.T
    object Rev extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  final case class ArticleAlias(alias: ArticleAlias.Alias, articleUid: UUID, createdAt: ArticleAlias.CreatedAt) derives KindlingsCodecAsObject

  object ArticleAlias:
    type Alias = Alias.T
    object Alias extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type CreatedAt = CreatedAt.T
    object CreatedAt extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  final case class ArticleVersion(
      uid: UUID,
      origin: UUID,
      articleUid: UUID,
      bodySha256: ArticleVersion.BodySha256,
      summary: Option[ArticleVersion.Summary],
      versionOf: ArticleVersion.VersionOf,
      updatedAt: ArticleVersion.UpdatedAt,
      rev: ArticleVersion.Rev = _root_.com.histmersion.api.exchange.Bundle.ArticleVersion.Rev("")
  ) derives KindlingsCodecAsObject

  object ArticleVersion:
    type BodySha256 = BodySha256.T
    object BodySha256 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Summary = Summary.T
    object Summary extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type VersionOf = VersionOf.T
    object VersionOf extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type UpdatedAt = UpdatedAt.T
    object UpdatedAt extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Rev = Rev.T
    object Rev extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  final case class Assessment(
      sourceUid: UUID,
      checkedAt: Assessment.CheckedAt,
      retracted: Assessment.Retracted,
      notices: Json,
      citedBy: Option[Assessment.CitedBy],
      influential: Option[Assessment.Influential],
      verdict: Assessment.Verdict
  ) derives KindlingsCodecAsObject

  object Assessment:
    type CheckedAt = CheckedAt.T
    object CheckedAt extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Retracted = Retracted.T
    object Retracted extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type CitedBy = CitedBy.T
    object CitedBy extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Influential = Influential.T
    object Influential extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Verdict = Verdict.T
    object Verdict extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  final case class Asset(
      uid: UUID,
      origin: UUID,
      sourceUid: UUID,
      name: Asset.Name,
      mime: Asset.Mime,
      sha256: com.histmersion.api.Sha256,
      width: Option[Asset.Width],
      height: Option[Asset.Height],
      kind: Option[Asset.Kind],
      caption: Option[Asset.Caption],
      license: Option[Asset.License],
      /** Hex EWKB — exact, unlike WKT at a fixed number of decimals. */
      geom: Option[Asset.Geom],
      period: Option[Range],
      updatedAt: Asset.UpdatedAt,
      rev: Asset.Rev = _root_.com.histmersion.api.exchange.Bundle.Asset.Rev("")
  ) derives KindlingsCodecAsObject

  object Asset:
    type Name = Name.T
    object Name extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Mime = Mime.T
    object Mime extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Width = Width.T
    object Width extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Height = Height.T
    object Height extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Kind = Kind.T
    object Kind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Caption = Caption.T
    object Caption extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type License = License.T
    object License extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Geom = Geom.T
    object Geom extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type UpdatedAt = UpdatedAt.T
    object UpdatedAt extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Rev = Rev.T
    object Rev extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  final case class AssetLink(
      uid: UUID,
      origin: UUID,
      assetUid: UUID,
      relation: AssetLink.Relation,
      targetType: AssetLink.TargetType,
      targetId: AssetLink.TargetId,
      targetUid: Option[UUID],
      xywh: Option[AssetLink.Xywh],
      notes: Option[AssetLink.Notes],
      updatedAt: AssetLink.UpdatedAt,
      rev: AssetLink.Rev = _root_.com.histmersion.api.exchange.Bundle.AssetLink.Rev("")
  ) derives KindlingsCodecAsObject

  object AssetLink:
    type Relation = Relation.T
    object Relation extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type TargetType = TargetType.T
    object TargetType extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type TargetId = TargetId.T
    object TargetId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Xywh = Xywh.T
    object Xywh extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Notes = Notes.T
    object Notes extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type UpdatedAt = UpdatedAt.T
    object UpdatedAt extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Rev = Rev.T
    object Rev extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  final case class Attestation(
      uid: UUID,
      origin: UUID,
      sourceUid: UUID,
      targetType: Attestation.TargetType,
      targetId: Attestation.TargetId,
      targetUid: Option[UUID],
      quote: Option[Attestation.Quote],
      prefix: Option[Attestation.Prefix],
      suffix: Option[Attestation.Suffix],
      charStart: Option[Attestation.CharStart],
      charEnd: Option[Attestation.CharEnd],
      chunkOrd: Option[Attestation.ChunkOrd],
      page: Option[Attestation.Page],
      canvas: Option[Attestation.Canvas],
      xywh: Option[Attestation.Xywh],
      ctsUrn: Option[Attestation.CtsUrn],
      note: Option[Attestation.Note],
      createdAt: Attestation.CreatedAt,
      updatedAt: Attestation.UpdatedAt,
      rev: Attestation.Rev = _root_.com.histmersion.api.exchange.Bundle.Attestation.Rev("")
  ) derives KindlingsCodecAsObject

  object Attestation:
    type TargetType = TargetType.T
    object TargetType extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type TargetId = TargetId.T
    object TargetId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Quote = Quote.T
    object Quote extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Prefix = Prefix.T
    object Prefix extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Suffix = Suffix.T
    object Suffix extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type CharStart = CharStart.T
    object CharStart extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type CharEnd = CharEnd.T
    object CharEnd extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ChunkOrd = ChunkOrd.T
    object ChunkOrd extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Page = Page.T
    object Page extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Canvas = Canvas.T
    object Canvas extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Xywh = Xywh.T
    object Xywh extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type CtsUrn = CtsUrn.T
    object CtsUrn extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Note = Note.T
    object Note extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type CreatedAt = CreatedAt.T
    object CreatedAt extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type UpdatedAt = UpdatedAt.T
    object UpdatedAt extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Rev = Rev.T
    object Rev extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  final case class Blobs(count: Blobs.Count, bytes: com.histmersion.api.ByteCount) derives KindlingsCodecAsObject, KindlingsSchema, com.histmersion.api.json.Schema

  object Blobs:
    type Count = Count.T
    object Count extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]


  final case class Correction(
      targetKind: Correction.TargetKind,
      targetId: Correction.TargetId,
      targetUid: Option[UUID],
      field: Correction.Field,
      value: Correction.Value,
      upstream: Option[Correction.Upstream],
      upstreamValue: Correction.UpstreamValue,
      upstreamNow: Option[Correction.UpstreamNow],
      status: Correction.Status,
      createdAt: Correction.CreatedAt,
      updatedAt: Correction.UpdatedAt,
      checkedAt: Option[Correction.CheckedAt]
  ) derives KindlingsCodecAsObject

  object Correction:
    type TargetKind = TargetKind.T
    object TargetKind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type TargetId = TargetId.T
    object TargetId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Field = Field.T
    object Field extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Value = Value.T
    object Value extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Upstream = Upstream.T
    object Upstream extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type UpstreamValue = UpstreamValue.T
    object UpstreamValue extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type UpstreamNow = UpstreamNow.T
    object UpstreamNow extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Status = Status.T
    object Status extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type CreatedAt = CreatedAt.T
    object CreatedAt extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type UpdatedAt = UpdatedAt.T
    object UpdatedAt extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type CheckedAt = CheckedAt.T
    object CheckedAt extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  final case class Edge(srcUid: UUID, dstUid: UUID, kind: Edge.Kind) derives KindlingsCodecAsObject

  object Edge:
    type Kind = Kind.T
    object Kind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  final case class Event(
      uid: UUID,
      origin: UUID,
      id: Event.Id,
      label: Event.Label,
      kind: Event.Kind,
      notBefore: Option[Event.NotBefore],
      beginBy: Option[Event.BeginBy],
      endFrom: Option[Event.EndFrom],
      notAfter: Option[Event.NotAfter],
      historicity: Event.Historicity,
      geom: Option[Event.Geom],
      placeId: Option[Event.PlaceId],
      parentUid: Option[UUID],
      description: Option[Event.Description],
      citekey: Option[com.histmersion.api.library.Citekey],
      sourceUid: Option[UUID],
      datings: List[EventDating],
      edges: List[EventEdge],
      participants: List[EventParticipant],
      updatedAt: Event.UpdatedAt,
      rev: Event.Rev = _root_.com.histmersion.api.exchange.Bundle.Event.Rev(""),
      /** Absent from the JSON when empty, so the rev of an event that states none stays what older bundles computed. */
      scale: Option[Event.Scale] = None,
      /** Each absent from the JSON when empty, so the rev of an event compiled from sources stays what older bundles
        * computed.
        */
      catalogue: Option[Event.Catalogue] = None,
      names: Option[List[Event.Names]] = None,
      wikidata: Option[Event.Wikidata] = None,
      central: Option[Event.Central] = None,
      plusMinus: Option[Event.PlusMinus] = None,
      datingMethod: Option[Event.DatingMethod] = None
  ) derives KindlingsCodecAsObject

  object Event:
    type Id = Id.T
    object Id extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Label = Label.T
    object Label extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Kind = Kind.T
    object Kind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type NotBefore = NotBefore.T
    object NotBefore extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type BeginBy = BeginBy.T
    object BeginBy extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type EndFrom = EndFrom.T
    object EndFrom extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type NotAfter = NotAfter.T
    object NotAfter extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Historicity = Historicity.T
    object Historicity extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Geom = Geom.T
    object Geom extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type PlaceId = PlaceId.T
    object PlaceId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Description = Description.T
    object Description extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type UpdatedAt = UpdatedAt.T
    object UpdatedAt extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Rev = Rev.T
    object Rev extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Scale = Scale.T
    object Scale extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Catalogue = Catalogue.T
    object Catalogue extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Names = Names.T
    object Names extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Wikidata = Wikidata.T
    object Wikidata extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Central = Central.T
    object Central extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type PlusMinus = PlusMinus.T
    object PlusMinus extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type DatingMethod = DatingMethod.T
    object DatingMethod extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  final case class EventAlias(alias: EventAlias.Alias, eventUid: UUID, createdAt: EventAlias.CreatedAt) derives KindlingsCodecAsObject

  object EventAlias:
    type Alias = Alias.T
    object Alias extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type CreatedAt = CreatedAt.T
    object CreatedAt extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  final case class EventCandidate(
      eventUid: UUID,
      sourceUid: UUID,
      citekey: com.histmersion.api.library.Citekey,
      score: EventCandidate.Score,
      status: EventCandidate.Status,
      findings: List[EventCandidate.Findings],
      passages: Json,
      reasons: List[EventCandidate.Reasons],
      computedAt: EventCandidate.ComputedAt
  ) derives KindlingsCodecAsObject

  object EventCandidate:
    type Score = Score.T
    object Score extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Status = Status.T
    object Status extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Findings = Findings.T
    object Findings extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Reasons = Reasons.T
    object Reasons extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ComputedAt = ComputedAt.T
    object ComputedAt extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  final case class EventCatalogue(
      eventUid: UUID,
      provider: EventCatalogue.Provider,
      providerId: EventCatalogue.ProviderId,
      url: Option[EventCatalogue.Url],
      label: EventCatalogue.Label,
      kind: EventCatalogue.Kind,
      notBefore: Option[EventCatalogue.NotBefore],
      beginBy: Option[EventCatalogue.BeginBy],
      endFrom: Option[EventCatalogue.EndFrom],
      notAfter: Option[EventCatalogue.NotAfter],
      lat: Option[com.histmersion.api.catalogue.Latitude],
      lon: Option[com.histmersion.api.catalogue.Longitude],
      vei: Option[EventCatalogue.Vei],
      magnitude: Option[EventCatalogue.Magnitude],
      deaths: Option[EventCatalogue.Deaths],
      historicity: EventCatalogue.Historicity,
      record: Json,
      retrievedAt: EventCatalogue.RetrievedAt
  ) derives KindlingsCodecAsObject

  object EventCatalogue:
    type Provider = Provider.T
    object Provider extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ProviderId = ProviderId.T
    object ProviderId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Url = Url.T
    object Url extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Label = Label.T
    object Label extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Kind = Kind.T
    object Kind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type NotBefore = NotBefore.T
    object NotBefore extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type BeginBy = BeginBy.T
    object BeginBy extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type EndFrom = EndFrom.T
    object EndFrom extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type NotAfter = NotAfter.T
    object NotAfter extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Vei = Vei.T
    object Vei extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Magnitude = Magnitude.T
    object Magnitude extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Deaths = Deaths.T
    object Deaths extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Historicity = Historicity.T
    object Historicity extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type RetrievedAt = RetrievedAt.T
    object RetrievedAt extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  final case class EventCheck(eventUid: UUID, checkedAt: EventCheck.CheckedAt) derives KindlingsCodecAsObject

  object EventCheck:
    type CheckedAt = CheckedAt.T
    object CheckedAt extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  final case class EventDating(
      citekey: com.histmersion.api.library.Citekey,
      sourceUid: Option[UUID],
      notBefore: Option[EventDating.NotBefore],
      beginBy: Option[EventDating.BeginBy],
      endFrom: Option[EventDating.EndFrom],
      notAfter: Option[EventDating.NotAfter],
      method: Option[EventDating.Method],
      note: Option[EventDating.Note]
  ) derives KindlingsCodecAsObject

  object EventDating:
    type NotBefore = NotBefore.T
    object NotBefore extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type BeginBy = BeginBy.T
    object BeginBy extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type EndFrom = EndFrom.T
    object EndFrom extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type NotAfter = NotAfter.T
    object NotAfter extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Method = Method.T
    object Method extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Note = Note.T
    object Note extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  final case class EventEdge(
      dstUid: UUID,
      kind: EventEdge.Kind,
      citekey: com.histmersion.api.library.Citekey,
      sourceUid: Option[UUID],
      note: Option[EventEdge.Note]
  ) derives KindlingsCodecAsObject

  object EventEdge:
    type Kind = Kind.T
    object Kind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Note = Note.T
    object Note extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  final case class EventParticipant(actor: EventParticipant.Actor, role: EventParticipant.Role, citekey: Option[com.histmersion.api.library.Citekey], sourceUid: Option[UUID])
      derives KindlingsCodecAsObject

  object EventParticipant:
    type Actor = Actor.T
    object Actor extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Role = Role.T
    object Role extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  object EventSets:
    type CandidateStatusResult = CandidateStatusResult.T
    object CandidateStatusResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type CandidateStatusHere = CandidateStatusHere.T
    object CandidateStatusHere extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type CandidateStatusThere = CandidateStatusThere.T
    object CandidateStatusThere extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type NewerResult = NewerResult.T
    object NewerResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type NewerHere = NewerHere.T
    object NewerHere extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type NewerThere = NewerThere.T
    object NewerThere extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    def candidateStatus(here: com.histmersion.api.exchange.Bundle.EventSets.CandidateStatusHere, there: com.histmersion.api.exchange.Bundle.EventSets.CandidateStatusThere): com.histmersion.api.exchange.Bundle.EventSets.CandidateStatusResult =
      if here == "proposed" then there else here

    /** Timestamps share one fixed-width UTC format, so text order is time order. */
    def newer(here: Option[com.histmersion.api.exchange.Bundle.EventSets.NewerHere], there: Option[com.histmersion.api.exchange.Bundle.EventSets.NewerThere]): com.histmersion.api.exchange.Bundle.EventSets.NewerResult = (here, there) match
      case (None, Some(_))    => _root_.com.histmersion.api.exchange.Bundle.EventSets.NewerResult(true)
      case (Some(h), Some(t)) => _root_.com.histmersion.api.exchange.Bundle.EventSets.NewerResult((t).value > (h).value)
      case _                  => _root_.com.histmersion.api.exchange.Bundle.EventSets.NewerResult(false)

  final case class FileInfo(path: FileInfo.Path, lines: FileInfo.Lines, bytes: com.histmersion.api.ByteCount, sha256: com.histmersion.api.Sha256)
      derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema

  object FileInfo:
    type Path = Path.T
    object Path extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Lines = Lines.T
    object Lines extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]


  object Files:
    type All = All.T
    object All extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

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

    val all: List[com.histmersion.api.exchange.Bundle.Files.All] =
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
      id: Finding.Id,
      claim: Finding.Claim,
      region: Option[Finding.Region],
      lon: Option[com.histmersion.api.catalogue.Longitude],
      lat: Option[com.histmersion.api.catalogue.Latitude],
      period: Option[Range],
      periodName: Option[Finding.PeriodName],
      tags: List[Finding.Tags],
      confidence: Option[Confidence],
      significance: Option[Finding.Significance],
      updatedAt: Finding.UpdatedAt,
      rev: Finding.Rev = _root_.com.histmersion.api.exchange.Bundle.Finding.Rev("")
  ) derives KindlingsCodecAsObject

  object Finding:
    type Id = Id.T
    object Id extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Claim = Claim.T
    object Claim extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Region = Region.T
    object Region extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type PeriodName = PeriodName.T
    object PeriodName extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Tags = Tags.T
    object Tags extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Significance = Significance.T
    object Significance extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type UpdatedAt = UpdatedAt.T
    object UpdatedAt extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Rev = Rev.T
    object Rev extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  final case class Goal(
      uid: UUID,
      origin: UUID,
      slug: Goal.Slug,
      title: Goal.Title,
      status: Goal.Status,
      notes: Option[Goal.Notes],
      tags: List[Goal.Tags],
      regions: List[Goal.Regions],
      period: Option[Range],
      parentUid: Option[UUID],
      createdAt: Goal.CreatedAt,
      updatedAt: Goal.UpdatedAt,
      rev: Goal.Rev = _root_.com.histmersion.api.exchange.Bundle.Goal.Rev(""),
      /** The four fields below are absent when empty, so a line written before they existed keeps its `rev`. */
      plan: Option[Json] = None,
      planApprovedAt: Option[Goal.PlanApprovedAt] = None,
      budgetUsd: Option[Goal.BudgetUsd] = None,
      links: Option[List[GoalLink]] = None,
      schedules: Option[List[GoalSchedule]] = None
  ) derives KindlingsCodecAsObject

  object Goal:
    type Slug = Slug.T
    object Slug extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Title = Title.T
    object Title extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Status = Status.T
    object Status extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Notes = Notes.T
    object Notes extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Tags = Tags.T
    object Tags extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Regions = Regions.T
    object Regions extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type CreatedAt = CreatedAt.T
    object CreatedAt extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type UpdatedAt = UpdatedAt.T
    object UpdatedAt extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Rev = Rev.T
    object Rev extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type PlanApprovedAt = PlanApprovedAt.T
    object PlanApprovedAt extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type BudgetUsd = BudgetUsd.T
    object BudgetUsd extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]

  final case class GoalAlias(alias: GoalAlias.Alias, goalUid: UUID, createdAt: GoalAlias.CreatedAt) derives KindlingsCodecAsObject

  object GoalAlias:
    type Alias = Alias.T
    object Alias extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type CreatedAt = CreatedAt.T
    object CreatedAt extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  final case class GoalLink(
      kind: GoalLink.Kind,
      ref: GoalLink.Ref,
      refUid: Option[UUID],
      note: Option[GoalLink.Note],
      createdAt: GoalLink.CreatedAt
  ) derives KindlingsCodecAsObject

  object GoalLink:
    type Kind = Kind.T
    object Kind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Ref = Ref.T
    object Ref extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Note = Note.T
    object Note extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type CreatedAt = CreatedAt.T
    object CreatedAt extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  final case class GoalSchedule(kind: GoalSchedule.Kind, cron: GoalSchedule.Cron, enabled: GoalSchedule.Enabled, budgetUsd: Option[GoalSchedule.BudgetUsd])
      derives KindlingsCodecAsObject

  object GoalSchedule:
    type Kind = Kind.T
    object Kind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Cron = Cron.T
    object Cron extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Enabled = Enabled.T
    object Enabled extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type BudgetUsd = BudgetUsd.T
    object BudgetUsd extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]

  final case class ImportResult(
      bundleId: UUID,
      from: Instance,
      dryRun: ImportResult.DryRun,
      applied: ImportResult.Applied,
      counts: List[KindCounts],
      blobs: ImportResult.Blobs,
      problems: List[ImportResult.Problems],
      conflicts: List[ImportResult.Conflicts],
      notes: List[ImportResult.Notes],
      mergeId: Option[UUID] = None,
      issues: List[MergeIssue] = Nil,
      planHash: Option[ImportResult.PlanHash] = None
  ) derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema:
    def changed: com.histmersion.api.exchange.Bundle.ImportResult.ChangedResult = counts.map(_.changed).sum

  object ImportResult:
    type ChangedResult = ChangedResult.T
    object ChangedResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

    type DryRun = DryRun.T
    object DryRun extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Applied = Applied.T
    object Applied extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Blobs = Blobs.T
    object Blobs extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Problems = Problems.T
    object Problems extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Conflicts = Conflicts.T
    object Conflicts extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Notes = Notes.T
    object Notes extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type PlanHash = PlanHash.T
    object PlanHash extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]


  final case class Instance(id: UUID, label: Option[Instance.Label] = None) derives KindlingsCodecAsObject, KindlingsSchema, com.histmersion.api.json.Schema

  object Instance:
    type Label = Label.T
    object Label extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]


  final case class KindCounts(
      kind: com.histmersion.api.exchange.Bundle.Kind,
      inserted: KindCounts.Inserted = _root_.com.histmersion.api.exchange.Bundle.KindCounts.Inserted(0),
      updated: KindCounts.Updated = _root_.com.histmersion.api.exchange.Bundle.KindCounts.Updated(0),
      unchanged: KindCounts.Unchanged = _root_.com.histmersion.api.exchange.Bundle.KindCounts.Unchanged(0),
      keptLocal: KindCounts.KeptLocal = _root_.com.histmersion.api.exchange.Bundle.KindCounts.KeptLocal(0),
      conflicts: KindCounts.Conflicts = _root_.com.histmersion.api.exchange.Bundle.KindCounts.Conflicts(0),
      deleted: KindCounts.Deleted = _root_.com.histmersion.api.exchange.Bundle.KindCounts.Deleted(0),
      skippedDeleted: KindCounts.SkippedDeleted = _root_.com.histmersion.api.exchange.Bundle.KindCounts.SkippedDeleted(0),
      held: KindCounts.Held = _root_.com.histmersion.api.exchange.Bundle.KindCounts.Held(0)
  ) derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema:
    def changed: com.histmersion.api.exchange.Bundle.KindCounts.ChangedResult = (inserted).value + (updated).value + deleted

  object KindCounts:
    type ChangedResult = ChangedResult.T
    object ChangedResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

    type Inserted = Inserted.T
    object Inserted extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Updated = Updated.T
    object Updated extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Unchanged = Unchanged.T
    object Unchanged extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type KeptLocal = KeptLocal.T
    object KeptLocal extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Conflicts = Conflicts.T
    object Conflicts extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Deleted = Deleted.T
    object Deleted extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type SkippedDeleted = SkippedDeleted.T
    object SkippedDeleted extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Held = Held.T
    object Held extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]


  final case class Manifest(
      format: Manifest.Format,
      formatVersion: Manifest.FormatVersion,
      schemaVersion: Manifest.SchemaVersion,
      bundleId: UUID,
      instance: Instance,
      since: Option[Manifest.Since],
      asOf: Option[Manifest.AsOf],
      files: List[FileInfo],
      blobs: Blobs,
      reference: Reference
  ) derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema

  object Manifest:
    type Format = Format.T
    object Format extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type FormatVersion = FormatVersion.T
    object FormatVersion extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type SchemaVersion = SchemaVersion.T
    object SchemaVersion extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Since = Since.T
    object Since extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type AsOf = AsOf.T
    object AsOf extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]


  final case class Period(
      uid: UUID,
      origin: UUID,
      id: Period.Id,
      label: Period.Label,
      authority: Option[Period.Authority],
      period: Option[Range],
      region: Option[Period.Region],
      parentId: Option[Period.ParentId],
      updatedAt: Period.UpdatedAt,
      rev: Period.Rev = _root_.com.histmersion.api.exchange.Bundle.Period.Rev("")
  ) derives KindlingsCodecAsObject

  object Period:
    type Id = Id.T
    object Id extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Label = Label.T
    object Label extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Authority = Authority.T
    object Authority extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Region = Region.T
    object Region extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ParentId = ParentId.T
    object ParentId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type UpdatedAt = UpdatedAt.T
    object UpdatedAt extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Rev = Rev.T
    object Rev extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  final case class Place(
      uid: UUID,
      origin: UUID,
      id: Place.Id,
      label: Place.Label,
      kind: Place.Kind,
      geom: Option[Place.Geom],
      period: Option[Range],
      source: Option[Place.Source],
      properties: Option[Json],
      names: List[PlaceName],
      relations: List[PlaceRelation],
      links: List[PlaceLink],
      types: List[PlaceType],
      updatedAt: Place.UpdatedAt,
      rev: Place.Rev = _root_.com.histmersion.api.exchange.Bundle.Place.Rev("")
  ) derives KindlingsCodecAsObject

  object Place:
    type Id = Id.T
    object Id extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Label = Label.T
    object Label extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Kind = Kind.T
    object Kind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Geom = Geom.T
    object Geom extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Source = Source.T
    object Source extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type UpdatedAt = UpdatedAt.T
    object UpdatedAt extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Rev = Rev.T
    object Rev extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  final case class PlaceAlias(alias: PlaceAlias.Alias, placeUid: UUID, createdAt: PlaceAlias.CreatedAt) derives KindlingsCodecAsObject

  object PlaceAlias:
    type Alias = Alias.T
    object Alias extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type CreatedAt = CreatedAt.T
    object CreatedAt extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  final case class PlaceLink(scheme: PlaceLink.Scheme, externalId: PlaceLink.ExternalId, relation: PlaceLink.Relation) derives KindlingsCodecAsObject

  object PlaceLink:
    type Scheme = Scheme.T
    object Scheme extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ExternalId = ExternalId.T
    object ExternalId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Relation = Relation.T
    object Relation extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  final case class PlaceName(name: PlaceName.Name, lang: Option[PlaceName.Lang], period: Option[Range]) derives KindlingsCodecAsObject

  object PlaceName:
    type Name = Name.T
    object Name extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Lang = Lang.T
    object Lang extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  final case class PlaceRelation(parentId: PlaceRelation.ParentId, kind: PlaceRelation.Kind, period: Option[Range]) derives KindlingsCodecAsObject

  object PlaceRelation:
    type ParentId = ParentId.T
    object ParentId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Kind = Kind.T
    object Kind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  final case class PlaceType(typeLabel: PlaceType.TypeLabel, period: Option[Range], citekey: Option[com.histmersion.api.library.Citekey])
      derives KindlingsCodecAsObject

  object PlaceType:
    type TypeLabel = TypeLabel.T
    object TypeLabel extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  final case class QueueItem(
      uid: UUID,
      origin: UUID,
      list: QueueItem.List,
      text: QueueItem.Text,
      topic: Option[QueueItem.Topic],
      status: QueueItem.Status,
      reason: Option[QueueItem.Reason],
      createdAt: QueueItem.CreatedAt,
      doneAt: Option[QueueItem.DoneAt],
      updatedAt: QueueItem.UpdatedAt,
      rev: QueueItem.Rev = _root_.com.histmersion.api.exchange.Bundle.QueueItem.Rev("")
  ) derives KindlingsCodecAsObject

  object QueueItem:
    type List = List.T
    object List extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Text = Text.T
    object Text extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Topic = Topic.T
    object Topic extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Status = Status.T
    object Status extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Reason = Reason.T
    object Reason extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type CreatedAt = CreatedAt.T
    object CreatedAt extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type DoneAt = DoneAt.T
    object DoneAt extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type UpdatedAt = UpdatedAt.T
    object UpdatedAt extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Rev = Rev.T
    object Rev extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** Both bounds inclusive: Postgres keeps an `int8range` as `[lower, upper)`, so `upper − 1` is applied on the way out
    * and undone on the way in.
    */
  final case class Range(start: Option[com.histmersion.api.catalogue.Year] = None, end: Option[com.histmersion.api.catalogue.Year] = None) derives KindlingsCodecAsObject

  final case class Reference(places: Reference.Places, placesDigest: Reference.PlacesDigest, periods: Reference.Periods, periodsDigest: Reference.PeriodsDigest)
      derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema

  object Reference:
    type Places = Places.T
    object Places extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type PlacesDigest = PlacesDigest.T
    object PlacesDigest extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Periods = Periods.T
    object Periods extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type PeriodsDigest = PeriodsDigest.T
    object PeriodsDigest extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]


  final case class SiteType(placeId: SiteType.PlaceId, siteType: Option[SiteType.Value], checkedAt: Option[SiteType.CheckedAt])
      derives KindlingsCodecAsObject

  object SiteType:
    type PlaceId = PlaceId.T
    object PlaceId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Value = Value.T
    object Value extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type CheckedAt = CheckedAt.T
    object CheckedAt extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  final case class Source(
      uid: UUID,
      origin: UUID,
      citekey: com.histmersion.api.library.Citekey,
      title: com.histmersion.api.library.Title,
      authors: List[com.histmersion.api.library.AuthorName],
      doi: Option[com.histmersion.api.library.Doi],
      year: Option[com.histmersion.api.catalogue.Year],
      venue: Option[com.histmersion.api.library.Venue],
      status: com.histmersion.api.library.Source.Status,
      topics: List[Source.Topics],
      regions: List[Source.Regions],
      tags: List[Source.Tags],
      period: Option[Range],
      summary: Option[Source.Summary],
      notes: Option[Source.Notes],
      provenance: Option[Source.Provenance],
      citedByCount: Option[Source.CitedByCount],
      influentialCitations: Option[Source.InfluentialCitations],
      addedDate: Source.AddedDate,
      bodySha256: Option[Source.BodySha256],
      originalSha256: Option[Source.OriginalSha256],
      originalExt: Option[Source.OriginalExt],
      updatedAt: Source.UpdatedAt,
      rev: Source.Rev = _root_.com.histmersion.api.exchange.Bundle.Source.Rev(""),
      /** Absent when there are none, so a line written before the field existed keeps its rev. */
      isbns: Option[List[Source.Isbns]] = None
  ) derives KindlingsCodecAsObject

  object Source:
    type Topics = Topics.T
    object Topics extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Regions = Regions.T
    object Regions extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Tags = Tags.T
    object Tags extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Summary = Summary.T
    object Summary extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Notes = Notes.T
    object Notes extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Provenance = Provenance.T
    object Provenance extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type CitedByCount = CitedByCount.T
    object CitedByCount extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type InfluentialCitations = InfluentialCitations.T
    object InfluentialCitations extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type AddedDate = AddedDate.T
    object AddedDate extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type BodySha256 = BodySha256.T
    object BodySha256 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type OriginalSha256 = OriginalSha256.T
    object OriginalSha256 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type OriginalExt = OriginalExt.T
    object OriginalExt extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type UpdatedAt = UpdatedAt.T
    object UpdatedAt extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Rev = Rev.T
    object Rev extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Isbns = Isbns.T
    object Isbns extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  final case class Tombstone(
      uid: UUID,
      kind: com.histmersion.api.exchange.Bundle.Kind,
      origin: UUID,
      deletedAt: Tombstone.DeletedAt
  ) derives KindlingsCodecAsObject

  object Tombstone:
    type DeletedAt = DeletedAt.T
    object DeletedAt extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  final case class UnresolvedEdge(srcUid: UUID, dst: UnresolvedEdge.Dst, kind: UnresolvedEdge.Kind) derives KindlingsCodecAsObject

  object UnresolvedEdge:
    type Dst = Dst.T
    object Dst extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Kind = Kind.T
    object Kind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  final case class Upload(sha256: com.histmersion.api.Sha256, bytes: com.histmersion.api.ByteCount, manifest: Option[Manifest], problems: List[Upload.Problems])
      derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema

  object Upload:
    type Problems = Problems.T
    object Problems extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]


  final case class VerifyResult(
      ok: VerifyResult.Ok,
      manifest: Option[Manifest],
      blobsChecked: VerifyResult.BlobsChecked,
      problems: List[VerifyResult.Problems]
  ) derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema

  object VerifyResult:
    type Ok = Ok.T
    object Ok extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type BlobsChecked = BlobsChecked.T
    object BlobsChecked extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Problems = Problems.T
    object Problems extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
