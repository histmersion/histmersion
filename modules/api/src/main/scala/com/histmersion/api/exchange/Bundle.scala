package com.histmersion
package api.exchange
import hearth.kindlings.jsoniterjson.Json

import com.histmersion.util.problem.Problem

import com.histmersion.api.WireEnumValues
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.api.aliases.Aliases
import com.histmersion.api.library.Confidence
import com.histmersion.api.library.Source
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import java.util.UUID
import sttp.tapir.Schema
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.Sha256
import com.histmersion.api.ByteCount
import com.histmersion.api.library.Citekey
import com.histmersion.api.automation.Queue
import com.histmersion.api.catalogue.Latitude
import com.histmersion.api.catalogue.Longitude
import com.histmersion.api.catalogue.Year
import com.histmersion.api.library.AuthorName
import com.histmersion.api.library.Doi
import com.histmersion.lint.{tolerated, LintRule}
import com.histmersion.api.Wording
@tolerated(LintRule.Direction, "pending G12 (a domain names only lower places)")
object Bundle:

  /** Two uids compare with `==` and `!=`, also under `-language:strictEquality`: a uid names one entity across
    * instances, and a merge matches the entities of two bundles by it. The one such instance, imported by name
    * (`Bundle.uidEquality`) where uids are compared.
    */
  given uidEquality: CanEqual[UUID, UUID] = CanEqual.derived

  type ChangedCount = ChangedCount.T
  object ChangedCount extends Newtype[Int, Pure]
  type Name = Name.T
  object Name extends Newtype[String, Pure]
  type UpdatedAt = UpdatedAt.T
  object UpdatedAt extends Newtype[String, Pure]

  /** The kinds of entity a bundle carries one line per uid of — and what a tombstone says was deleted. Wire names are
    * kebab-case (`article-version`), as the V031 trigger writes them.
    */

  enum Kind derives CanEqual:
    case Source, Finding, Article, ArticleVersion, Asset, AssetLink, Attestation, Event, Goal, QueueItem

    /** A place or a named period added HERE (`dataset = 'local'`); the bundled seeds every instance imports itself are
      * not carried (docs/research-merge-import.md §7.8).
      */
    case Place, Period

    def wire: Kind.Wire = Kind.wireOf(this)

    /** The NDJSON file holding this kind's lines. */
    def file: String = (s"entities/${Kind.fileStem(this)}.ndjson")

    /** The letter a merge issue about this kind of record starts its id with (`P-1a2b3c4d`). */
    def issueLetter: String = Kind.letterOf(this)

    /** The kind as a problem names the type of its record: a reader reads it as the kind's own name (`source`). */
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def typeName: Problem.TypeName = Problem.TypeName(s"Bundle.Kind.$productPrefix")

  object Kind:
    type Wire = Wire.T
    object Wire extends Newtype[String, Pure]:
      given Display[T] = Display(wire => Wording.humanize(Wording.Id(wire.value)))

    def wireOf(k: Kind): Wire = k match
      case Source         => Wire("source")
      case Finding        => Wire("finding")
      case Article        => Wire("article")
      case ArticleVersion => Wire("article-version")
      case Asset          => Wire("asset")
      case AssetLink      => Wire("asset-link")
      case Attestation    => Wire("attestation")
      case Event          => Wire("event")
      case Goal           => Wire("goal")
      case QueueItem      => Wire("queue-item")
      case Place          => Wire("place")
      case Period         => Wire("period")

    private def fileStem(k: Kind): String = k match
      case Source         => ("sources")
      case Finding        => ("findings")
      case Article        => ("articles")
      case ArticleVersion => ("article-versions")
      case Asset          => ("assets")
      case AssetLink      => ("asset-links")
      case Attestation    => ("attestations")
      case Event          => ("events")
      case Goal           => ("goals")
      case QueueItem      => ("queue")
      case Place          => ("places")
      case Period         => ("periods")

    private def letterOf(k: Kind): String = k match
      case Source         => ("S")
      case Finding        => ("F")
      case Article        => ("A")
      case ArticleVersion => ("V")
      case Asset          => ("X")
      case AssetLink      => ("K")
      case Attestation    => ("T")
      case Event          => ("E")
      case Goal           => ("G")
      case QueueItem      => ("Q")
      case Place          => ("P")
      case Period         => ("N")

    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def fromWire(s: Wire): Option[Kind] = values.find(_.wire == Wire((s).value.trim.toLowerCase))

    given WireEnumValues[Kind]:
      def all: Array[Kind] = values
      @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
      def wire(e: Kind): WireEnumValues.Wire = WireEnumValues.Wire((wireOf(e)).value)
      @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
      @tolerated(LintRule.G47Self, "pending G47 (no FQN noise)")
      def fromWire(s: WireEnumValues.Wire): Option[Kind] = Kind.fromWire(Wire((s).value))
  val Format = "histmersion-bundle"
  val FormatVersion = 2
  val Extension = ".hmx"
  val ManifestPath = "manifest.json"

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def blobPath(sha256: Sha256): String = (s"blobs/sha256/${(sha256).value.take(2)}/$sha256")

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def blobSha(path: String): Option[Sha256] =
    val prefix = "blobs/sha256/"
    (Option
      .when((path).startsWith(prefix)) {
        val rest = (path).drop(prefix.length)
        rest.drop(3)
      }
      .filter(s =>
        s.length == 64 && s.forall(c => c.isDigit || ('a' to 'f').contains(c)) &&
          path == blobPath(Sha256(s))
      ))
      .map(refactorValue => Sha256(refactorValue))

  final case class Alias(alias: Alias.Value, sourceUid: UUID, createdAt: Alias.CreatedAt) derives JsonCodec

  object Alias:
    export com.histmersion.api.library.Citekey as Value
    type CreatedAt = CreatedAt.T
    object CreatedAt extends Newtype[String, Pure]

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
      rev: Article.Rev = Article.Rev(""),
      creator: Option[Json] = None
  ) derives JsonCodec

  object Article:
    export api.research.ReviewGoal.Slug
    type Title = Title.T
    object Title extends Newtype[String, Pure]
    export api.research.Article.Kind.Wire as Kind
    export api.research.Article.{Body, SummaryText as Summary}
    type Tags = Tags.T
    object Tags extends Newtype[String, Pure]
    type Regions = Regions.T
    object Regions extends Newtype[String, Pure]
    type UpdatedAt = UpdatedAt.T
    object UpdatedAt extends Newtype[String, Pure]
    type Rev = Rev.T
    object Rev extends Newtype[String, Pure]

  @tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
  final case class ArticleAlias(alias: ArticleAlias.Alias, articleUid: UUID, createdAt: ArticleAlias.CreatedAt)
      derives JsonCodec

  object ArticleAlias:
    export api.research.ReviewGoal.Slug as Alias
    type CreatedAt = CreatedAt.T
    object CreatedAt extends Newtype[String, Pure]

  @tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
  final case class ArticleVersion(
      uid: UUID,
      origin: UUID,
      articleUid: UUID,
      bodySha256: ArticleVersion.BodySha256,
      summary: Option[ArticleVersion.Summary],
      versionOf: ArticleVersion.VersionOf,
      updatedAt: ArticleVersion.UpdatedAt,
      rev: ArticleVersion.Rev = ArticleVersion.Rev(""),
      creator: Option[Json] = None
  ) derives JsonCodec

  object ArticleVersion:
    type BodySha256 = BodySha256.T
    object BodySha256 extends Newtype[String, Pure]
    export api.research.Article.SummaryText as Summary
    type VersionOf = VersionOf.T
    @tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
    object VersionOf extends Newtype[String, Pure]
    type UpdatedAt = UpdatedAt.T
    object UpdatedAt extends Newtype[String, Pure]
    type Rev = Rev.T
    object Rev extends Newtype[String, Pure]

  final case class Assessment(
      sourceUid: UUID,
      checkedAt: Assessment.CheckedAt,
      retracted: Assessment.Retracted,
      notices: Json,
      citedBy: Option[Assessment.CitedBy],
      influential: Option[Assessment.Influential],
      verdict: Assessment.Verdict
  ) derives JsonCodec

  object Assessment:
    type CheckedAt = CheckedAt.T
    object CheckedAt extends Newtype[String, Pure]
    type Retracted = Retracted.T
    object Retracted extends Newtype[Boolean, Pure]
    export api.library.Acquire.CitedByCount as CitedBy
    type Influential = Influential.T
    object Influential extends Newtype[Int, Pure]
    type Verdict = Verdict.T
    object Verdict extends Newtype[String, Pure]

  final case class Asset(
      uid: UUID,
      origin: UUID,
      sourceUid: UUID,
      name: Asset.Name,
      mime: Asset.Mime,
      sha256: Sha256,
      width: Option[Asset.Width],
      height: Option[Asset.Height],
      kind: Option[Asset.Kind],
      caption: Option[Asset.Caption],
      license: Option[Asset.License],
      /** Hex EWKB — exact, unlike WKT at a fixed number of decimals. */
      geom: Option[Asset.Geom],
      period: Option[Range],
      updatedAt: Asset.UpdatedAt,
      rev: Asset.Rev = Asset.Rev(""),
      creator: Option[Json] = None
  ) derives JsonCodec

  object Asset:
    type Extension = Extension.T
    object Extension extends Newtype[String, Pure]
    type FileName = FileName.T
    object FileName extends Newtype[String, Pure]:
      given Display[T] = Display(_.value)
    type MimeText = MimeText.T
    object MimeText extends Newtype[String, Pure]
    type Uid = Uid.T
    object Uid extends Newtype[String, Pure]
    type Variant = Variant.T
    object Variant extends Newtype[String, Pure]
    type Name = Name.T
    object Name extends Newtype[String, Pure]
    @tolerated(LintRule.G46, "pending G46 (re-export, don't alias)")
    type Mime = MimeText
    val Mime: MimeText.type = MimeText
    export api.media.ImageSize.{Height, Width}
    export api.media.AssetKind.Wire as Kind
    type Caption = Caption.T
    object Caption extends Newtype[String, Pure]
    type License = License.T
    object License extends Newtype[String, Pure]
    type Geom = Geom.T
    object Geom extends Newtype[String, Pure]
    type UpdatedAt = UpdatedAt.T
    object UpdatedAt extends Newtype[String, Pure]
    type Rev = Rev.T
    object Rev extends Newtype[String, Pure]

  @tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
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
      rev: AssetLink.Rev = AssetLink.Rev(""),
      creator: Option[Json] = None
  ) derives JsonCodec:

    /** What the link points at: the target's uid when the link has one, else its id. */
    def target: AssetLink.Target = AssetLink.Target.of(targetUid, targetId)

  object AssetLink:
    export api.media.AssetRelation.Wire as Relation

    enum Target derives CanEqual:
      case ByUid(uid: UUID)
      case ById(id: TargetId)

    object Target:
      def of(uid: Option[UUID], id: TargetId): Target = uid.fold[Target](ById(id))(ByUid(_))

    type TargetType = TargetType.T
    @tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
    object TargetType extends Newtype[String, Pure]
    type TargetId = TargetId.T
    @tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
    object TargetId extends Newtype[String, Pure]
    type Xywh = Xywh.T
    object Xywh extends Newtype[String, Pure]
    type Notes = Notes.T
    object Notes extends Newtype[String, Pure]
    type UpdatedAt = UpdatedAt.T
    object UpdatedAt extends Newtype[String, Pure]
    type Rev = Rev.T
    object Rev extends Newtype[String, Pure]

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
      rev: Attestation.Rev = Attestation.Rev(""),
      creator: Option[Json] = None
  ) derives JsonCodec

  object Attestation:
    type TargetType = TargetType.T
    object TargetType extends Newtype[String, Pure]
    type TargetId = TargetId.T
    object TargetId extends Newtype[String, Pure]
    type Quote = Quote.T
    object Quote extends Newtype[String, Pure]
    type Prefix = Prefix.T
    object Prefix extends Newtype[String, Pure]
    type Suffix = Suffix.T
    object Suffix extends Newtype[String, Pure]
    type CharStart = CharStart.T
    object CharStart extends Newtype[Int, Pure]
    type CharEnd = CharEnd.T
    object CharEnd extends Newtype[Int, Pure]
    type ChunkOrd = ChunkOrd.T
    object ChunkOrd extends Newtype[Int, Pure]
    type Page = Page.T
    object Page extends Newtype[String, Pure]
    type Canvas = Canvas.T
    object Canvas extends Newtype[String, Pure]
    type Xywh = Xywh.T
    object Xywh extends Newtype[String, Pure]
    type CtsUrn = CtsUrn.T
    object CtsUrn extends Newtype[String, Pure]
    type Note = Note.T
    object Note extends Newtype[String, Pure]
    type CreatedAt = CreatedAt.T
    object CreatedAt extends Newtype[String, Pure]
    type UpdatedAt = UpdatedAt.T
    object UpdatedAt extends Newtype[String, Pure]
    type Rev = Rev.T
    object Rev extends Newtype[String, Pure]

  final case class Blobs(count: Blobs.Count, bytes: ByteCount) derives JsonCodec, KindlingsSchema, api.json.Schema

  object Blobs:
    type Count = Count.T
    object Count extends Newtype[Int, Pure]

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
  ) derives JsonCodec

  object Correction:
    type TargetKind = TargetKind.T
    @tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
    object TargetKind extends Newtype[String, Pure]
    type TargetId = TargetId.T
    object TargetId extends Newtype[String, Pure]
    type Field = Field.T
    object Field extends Newtype[String, Pure]
    type Value = Value.T
    object Value extends Newtype[String, Pure]
    type Upstream = Upstream.T
    object Upstream extends Newtype[String, Pure]
    type UpstreamValue = UpstreamValue.T
    @tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
    object UpstreamValue extends Newtype[String, Pure]
    type UpstreamNow = UpstreamNow.T
    @tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
    object UpstreamNow extends Newtype[String, Pure]
    export api.catalogue.EventCorrection.Status
    type CreatedAt = CreatedAt.T
    object CreatedAt extends Newtype[String, Pure]
    type UpdatedAt = UpdatedAt.T
    object UpdatedAt extends Newtype[String, Pure]
    type CheckedAt = CheckedAt.T
    object CheckedAt extends Newtype[String, Pure]

  final case class Edge(srcUid: UUID, dstUid: UUID, kind: Edge.Kind) derives JsonCodec, CanEqual

  object Edge:
    export api.library.Edge.Kind.Wire as Kind

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
      citekey: Option[Citekey],
      sourceUid: Option[UUID],
      datings: List[EventDating],
      edges: List[EventEdge],
      participants: List[EventParticipant],
      updatedAt: Event.UpdatedAt,
      rev: Event.Rev = Event.Rev(""),
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
      datingMethod: Option[Event.DatingMethod] = None,
      creator: Option[Json] = None
  ) derives JsonCodec

  object Event:
    export api.catalogue.Event.Id
    type Label = Label.T
    object Label extends Newtype[String, Pure]
    type Kind = Kind.T
    object Kind extends Newtype[String, Pure]
    type NotBefore = NotBefore.T
    object NotBefore extends Newtype[Long, Pure]
    type BeginBy = BeginBy.T
    object BeginBy extends Newtype[Long, Pure]
    type EndFrom = EndFrom.T
    object EndFrom extends Newtype[Long, Pure]
    type NotAfter = NotAfter.T
    object NotAfter extends Newtype[Long, Pure]
    export api.catalogue.Historicity.Wire as Historicity
    type Geom = Geom.T
    object Geom extends Newtype[String, Pure]
    export api.catalogue.Place.Id as PlaceId
    type Description = Description.T
    object Description extends Newtype[String, Pure]
    type UpdatedAt = UpdatedAt.T
    object UpdatedAt extends Newtype[String, Pure]
    type Rev = Rev.T
    object Rev extends Newtype[String, Pure]
    type Scale = Scale.T
    object Scale extends Newtype[String, Pure]
    type Catalogue = Catalogue.T
    object Catalogue extends Newtype[String, Pure]
    type Names = Names.T
    object Names extends Newtype[String, Pure]
    type Wikidata = Wikidata.T
    object Wikidata extends Newtype[String, Pure]
    type Central = Central.T
    object Central extends Newtype[Long, Pure]
    type PlusMinus = PlusMinus.T
    object PlusMinus extends Newtype[Long, Pure]
    type DatingMethod = DatingMethod.T
    object DatingMethod extends Newtype[String, Pure]

  @tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
  final case class EventAlias(alias: EventAlias.Alias, eventUid: UUID, createdAt: EventAlias.CreatedAt)
      derives JsonCodec

  object EventAlias:
    export api.catalogue.Event.Id as Alias
    type CreatedAt = CreatedAt.T
    object CreatedAt extends Newtype[String, Pure]

  @tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
  final case class EventCandidate(
      eventUid: UUID,
      sourceUid: UUID,
      citekey: Citekey,
      score: EventCandidate.Score,
      status: EventCandidate.Status,
      findings: List[EventCandidate.Findings],
      passages: Json,
      reasons: List[EventCandidate.Reasons],
      computedAt: EventCandidate.ComputedAt
  ) derives JsonCodec

  object EventCandidate:
    type Score = Score.T
    object Score extends Newtype[Double, Pure]
    type Status = Status.T
    object Status extends Newtype[String, Pure]
    type Findings = Findings.T
    object Findings extends Newtype[String, Pure]
    type Reasons = Reasons.T
    object Reasons extends Newtype[String, Pure]
    type ComputedAt = ComputedAt.T
    object ComputedAt extends Newtype[String, Pure]

  @tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
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
      lat: Option[Latitude],
      lon: Option[Longitude],
      vei: Option[EventCatalogue.Vei],
      magnitude: Option[EventCatalogue.Magnitude],
      deaths: Option[EventCatalogue.Deaths],
      historicity: EventCatalogue.Historicity,
      record: Json,
      retrievedAt: EventCatalogue.RetrievedAt
  ) derives JsonCodec

  object EventCatalogue:
    type Provider = Provider.T
    object Provider extends Newtype[String, Pure]
    type ProviderId = ProviderId.T
    @tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
    object ProviderId extends Newtype[String, Pure]
    type Url = Url.T
    object Url extends Newtype[String, Pure]
    type Label = Label.T
    object Label extends Newtype[String, Pure]
    type Kind = Kind.T
    object Kind extends Newtype[String, Pure]
    type NotBefore = NotBefore.T
    object NotBefore extends Newtype[Long, Pure]
    type BeginBy = BeginBy.T
    object BeginBy extends Newtype[Long, Pure]
    type EndFrom = EndFrom.T
    object EndFrom extends Newtype[Long, Pure]
    type NotAfter = NotAfter.T
    object NotAfter extends Newtype[Long, Pure]
    type Vei = Vei.T
    object Vei extends Newtype[Int, Pure]
    type Magnitude = Magnitude.T
    object Magnitude extends Newtype[Double, Pure]
    type Deaths = Deaths.T
    object Deaths extends Newtype[Long, Pure]
    export api.catalogue.Historicity.Wire as Historicity
    type RetrievedAt = RetrievedAt.T
    object RetrievedAt extends Newtype[String, Pure]

  @tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
  final case class EventCheck(eventUid: UUID, checkedAt: EventCheck.CheckedAt) derives JsonCodec

  object EventCheck:
    type CheckedAt = CheckedAt.T
    object CheckedAt extends Newtype[String, Pure]

  @tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
  final case class EventDating(
      citekey: Citekey,
      sourceUid: Option[UUID],
      notBefore: Option[EventDating.NotBefore],
      beginBy: Option[EventDating.BeginBy],
      endFrom: Option[EventDating.EndFrom],
      notAfter: Option[EventDating.NotAfter],
      method: Option[EventDating.Method],
      note: Option[EventDating.Note]
  ) derives JsonCodec

  object EventDating:
    type NotBefore = NotBefore.T
    object NotBefore extends Newtype[Long, Pure]
    type BeginBy = BeginBy.T
    object BeginBy extends Newtype[Long, Pure]
    type EndFrom = EndFrom.T
    object EndFrom extends Newtype[Long, Pure]
    type NotAfter = NotAfter.T
    object NotAfter extends Newtype[Long, Pure]
    type Method = Method.T
    object Method extends Newtype[String, Pure]
    type Note = Note.T
    object Note extends Newtype[String, Pure]

  @tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
  final case class EventEdge(
      dstUid: UUID,
      kind: EventEdge.Kind,
      citekey: Citekey,
      sourceUid: Option[UUID],
      note: Option[EventEdge.Note]
  ) derives JsonCodec

  object EventEdge:
    type Kind = Kind.T
    object Kind extends Newtype[String, Pure]
    type Note = Note.T
    object Note extends Newtype[String, Pure]

  @tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
  final case class EventParticipant(
      actor: EventParticipant.Actor,
      role: EventParticipant.Role,
      citekey: Option[Citekey],
      sourceUid: Option[UUID]
  ) derives JsonCodec

  object EventParticipant:
    type Actor = Actor.T
    object Actor extends Newtype[String, Pure]
    type Role = Role.T
    object Role extends Newtype[String, Pure]

  @tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
  object EventSets:

    @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
    def candidateStatus(here: EventCandidate.Status, there: EventCandidate.Status): EventCandidate.Status =
      if here == EventCandidate.Status("proposed") then there
      else here

    /** Timestamps share one fixed-width UTC format, so text order is time order. */
    def newer(here: Option[UpdatedAt], there: Option[UpdatedAt]): Boolean = (here, there) match
      case (None, Some(_))    => (true)
      case (Some(h), Some(t)) => ((t) > (h))
      case _                  => (false)

  final case class FileInfo(path: FileInfo.Path, lines: FileInfo.Lines, bytes: ByteCount, sha256: Sha256)
      derives JsonCodec,
        KindlingsSchema,
        api.json.Schema

  object FileInfo:
    type Path = Path.T
    object Path extends Newtype[String, Pure]
    type Lines = Lines.T
    object Lines extends Newtype[Int, Pure]

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
      Kind.values.toList.map(_.file) ++
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
      lon: Option[Longitude],
      lat: Option[Latitude],
      period: Option[Range],
      periodName: Option[Finding.PeriodName],
      tags: List[Finding.Tags],
      confidence: Option[Confidence],
      significance: Option[Finding.Significance],
      updatedAt: Finding.UpdatedAt,
      rev: Finding.Rev = Finding.Rev(""),
      creator: Option[Json] = None
  ) derives JsonCodec

  object Finding:
    export api.library.Finding.Id
    type Claim = Claim.T
    object Claim extends Newtype[String, Pure]
    type Region = Region.T
    object Region extends Newtype[String, Pure]
    type PeriodName = PeriodName.T
    @tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
    object PeriodName extends Newtype[String, Pure]
    type Tags = Tags.T
    object Tags extends Newtype[String, Pure]
    type Significance = Significance.T
    object Significance extends Newtype[String, Pure]
    type UpdatedAt = UpdatedAt.T
    object UpdatedAt extends Newtype[String, Pure]
    type Rev = Rev.T
    object Rev extends Newtype[String, Pure]

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
      rev: Goal.Rev = Goal.Rev(""),
      /** The four fields below are absent when empty, so a line written before they existed keeps its `rev`. */
      plan: Option[Json] = None,
      planApprovedAt: Option[Goal.PlanApprovedAt] = None,
      budgetUsd: Option[Goal.BudgetUsd] = None,
      links: Option[List[GoalLink]] = None,
      schedules: Option[List[GoalSchedule]] = None,
      creator: Option[Json] = None
  ) derives JsonCodec

  object Goal:
    export api.research.ReviewGoal.Slug
    type Title = Title.T
    object Title extends Newtype[String, Pure]
    export api.automation.Goal.StatusValue as Status
    type Notes = Notes.T
    object Notes extends Newtype[String, Pure]
    type Tags = Tags.T
    object Tags extends Newtype[String, Pure]
    type Regions = Regions.T
    object Regions extends Newtype[String, Pure]
    type CreatedAt = CreatedAt.T
    object CreatedAt extends Newtype[String, Pure]
    type UpdatedAt = UpdatedAt.T
    object UpdatedAt extends Newtype[String, Pure]
    type Rev = Rev.T
    object Rev extends Newtype[String, Pure]
    type PlanApprovedAt = PlanApprovedAt.T
    object PlanApprovedAt extends Newtype[String, Pure]
    type BudgetUsd = BudgetUsd.T
    object BudgetUsd extends Newtype[Double, Pure]

  @tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
  final case class GoalAlias(alias: GoalAlias.Alias, goalUid: UUID, createdAt: GoalAlias.CreatedAt) derives JsonCodec

  object GoalAlias:
    export api.research.ReviewGoal.Slug as Alias
    type CreatedAt = CreatedAt.T
    object CreatedAt extends Newtype[String, Pure]

  @tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
  final case class GoalLink(
      kind: GoalLink.Kind,
      ref: GoalLink.Ref,
      refUid: Option[UUID],
      note: Option[GoalLink.Note],
      createdAt: GoalLink.CreatedAt
  ) derives JsonCodec,
        CanEqual:

    /** What the link points at: the goal's or article's uid when the link has one, else its label. */
    def target: GoalLink.Target = GoalLink.Target.of(refUid, ref)

  object GoalLink:
    export api.automation.Goal.LinkKind.Wire as Kind

    enum Target derives CanEqual:
      case ByUid(uid: UUID)
      case ByRef(ref: Ref)

    object Target:
      def of(uid: Option[UUID], ref: Ref): Target = uid.fold[Target](ByRef(ref))(ByUid(_))

    type Ref = Ref.T
    object Ref extends Newtype[String, Pure]
    type Note = Note.T
    object Note extends Newtype[String, Pure]
    type CreatedAt = CreatedAt.T
    object CreatedAt extends Newtype[String, Pure]

  @tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
  final case class GoalSchedule(
      kind: GoalSchedule.Kind,
      cron: GoalSchedule.Cron,
      enabled: GoalSchedule.Enabled,
      budgetUsd: Option[GoalSchedule.BudgetUsd]
  ) derives JsonCodec,
        CanEqual

  object GoalSchedule:
    export api.automation.ScheduleKind.Wire as Kind
    type Cron = Cron.T
    object Cron extends Newtype[String, Pure]
    type Enabled = Enabled.T
    object Enabled extends Newtype[Boolean, Pure]
    type BudgetUsd = BudgetUsd.T
    object BudgetUsd extends Newtype[Double, Pure]

  final case class ImportResult(
      bundleId: UUID,
      from: Instance,
      dryRun: ImportResult.DryRun,
      applied: ImportResult.Applied,
      counts: List[KindCounts],
      blobs: ImportResult.Blobs,
      problems: List[Problem],
      conflicts: List[ImportResult.Conflicts],
      notes: List[ImportResult.Notes],
      mergeId: Option[UUID] = None,
      issues: List[MergeIssue] = Nil,
      planHash: Option[ImportResult.PlanHash] = None
  ) derives JsonCodec,
        KindlingsSchema,
        api.json.Schema:
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def changed: ChangedCount = ChangedCount(counts.map(c => (c.changed.value: Int)).sum)

  object ImportResult:

    type DryRun = DryRun.T
    object DryRun extends Newtype[Boolean, Pure]
    type Applied = Applied.T
    object Applied extends Newtype[Boolean, Pure]
    type Blobs = Blobs.T
    object Blobs extends Newtype[Int, Pure]
    type Conflicts = Conflicts.T
    object Conflicts extends Newtype[String, Pure]
    type Notes = Notes.T
    object Notes extends Newtype[String, Pure]
    export MergePlan.PlanHash

  final case class Instance(id: UUID, label: Option[Instance.Label] = None)
      derives JsonCodec,
        KindlingsSchema,
        api.json.Schema

  object Instance:
    type Label = Label.T
    object Label extends Newtype[String, Pure]

  @tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
  final case class KindCounts(
      kind: Kind,
      inserted: KindCounts.Inserted = KindCounts.Inserted(0),
      updated: KindCounts.Updated = KindCounts.Updated(0),
      unchanged: KindCounts.Unchanged = KindCounts.Unchanged(0),
      keptLocal: KindCounts.KeptLocal = KindCounts.KeptLocal(0),
      conflicts: KindCounts.Conflicts = KindCounts.Conflicts(0),
      deleted: KindCounts.Deleted = KindCounts.Deleted(0),
      skippedDeleted: KindCounts.SkippedDeleted = KindCounts.SkippedDeleted(0),
      held: KindCounts.Held = KindCounts.Held(0)
  ) derives JsonCodec,
        KindlingsSchema,
        api.json.Schema:
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def changed: ChangedCount = ChangedCount((inserted).value + (updated).value + (deleted).value)

  object KindCounts:

    type Inserted = Inserted.T
    object Inserted extends Newtype[Int, Pure]
    type Updated = Updated.T
    object Updated extends Newtype[Int, Pure]
    type Unchanged = Unchanged.T
    object Unchanged extends Newtype[Int, Pure]
    type KeptLocal = KeptLocal.T
    object KeptLocal extends Newtype[Int, Pure]
    type Conflicts = Conflicts.T
    object Conflicts extends Newtype[Int, Pure]
    type Deleted = Deleted.T
    object Deleted extends Newtype[Int, Pure]
    type SkippedDeleted = SkippedDeleted.T
    object SkippedDeleted extends Newtype[Int, Pure]
    type Held = Held.T
    object Held extends Newtype[Int, Pure]

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
  ) derives JsonCodec,
        KindlingsSchema,
        api.json.Schema

  object Manifest:
    type Format = Format.T
    object Format extends Newtype[String, Pure]
    type FormatVersion = FormatVersion.T
    @tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
    object FormatVersion extends Newtype[Int, Pure]
    type SchemaVersion = SchemaVersion.T
    @tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
    object SchemaVersion extends Newtype[String, Pure]
    type Since = Since.T
    object Since extends Newtype[String, Pure]
    type AsOf = AsOf.T
    object AsOf extends Newtype[String, Pure]

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
      rev: Period.Rev = Period.Rev(""),
      creator: Option[Json] = None
  ) derives JsonCodec

  object Period:
    type Id = Id.T
    object Id extends Newtype[String, Pure]
    type Label = Label.T
    object Label extends Newtype[String, Pure]
    type Authority = Authority.T
    object Authority extends Newtype[String, Pure]
    type Region = Region.T
    object Region extends Newtype[String, Pure]
    type ParentId = ParentId.T
    object ParentId extends Newtype[String, Pure]
    type UpdatedAt = UpdatedAt.T
    object UpdatedAt extends Newtype[String, Pure]
    type Rev = Rev.T
    object Rev extends Newtype[String, Pure]

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
      rev: Place.Rev = Place.Rev(""),
      creator: Option[Json] = None
  ) derives JsonCodec

  object Place:
    export api.catalogue.Place.Id
    type Label = Label.T
    object Label extends Newtype[String, Pure]
    export api.catalogue.Place.Kind.Wire as Kind
    type Geom = Geom.T
    object Geom extends Newtype[String, Pure]
    type Source = Source.T
    object Source extends Newtype[String, Pure]
    type UpdatedAt = UpdatedAt.T
    object UpdatedAt extends Newtype[String, Pure]
    type Rev = Rev.T
    object Rev extends Newtype[String, Pure]

  @tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
  final case class PlaceAlias(alias: PlaceAlias.Alias, placeUid: UUID, createdAt: PlaceAlias.CreatedAt)
      derives JsonCodec

  object PlaceAlias:
    export api.catalogue.Place.Id as Alias
    type CreatedAt = CreatedAt.T
    object CreatedAt extends Newtype[String, Pure]

  @tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
  final case class PlaceLink(scheme: PlaceLink.Scheme, externalId: PlaceLink.ExternalId, relation: PlaceLink.Relation)
      derives JsonCodec

  object PlaceLink:
    export api.catalogue.LinkScheme.Wire as Scheme
    type ExternalId = ExternalId.T
    object ExternalId extends Newtype[String, Pure]
    type Relation = Relation.T
    object Relation extends Newtype[String, Pure]

  @tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
  final case class PlaceName(name: PlaceName.Name, lang: Option[PlaceName.Lang], period: Option[Range])
      derives JsonCodec

  object PlaceName:
    type Name = Name.T
    object Name extends Newtype[String, Pure]
    type Lang = Lang.T
    object Lang extends Newtype[String, Pure]

  @tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
  final case class PlaceRelation(parentId: PlaceRelation.ParentId, kind: PlaceRelation.Kind, period: Option[Range])
      derives JsonCodec

  object PlaceRelation:
    export api.catalogue.Place.Id as ParentId
    type Kind = Kind.T
    object Kind extends Newtype[String, Pure]

  @tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
  final case class PlaceType(typeLabel: PlaceType.TypeLabel, period: Option[Range], citekey: Option[Citekey])
      derives JsonCodec

  object PlaceType:
    type TypeLabel = TypeLabel.T
    object TypeLabel extends Newtype[String, Pure]

  @tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
  final case class QueueItem(
      uid: UUID,
      origin: UUID,
      list: QueueItem.List,
      text: QueueItem.Text,
      topic: Option[QueueItem.Topic],
      status: QueueItem.Status,
      reason: Option[Queue.Rationale],
      createdAt: QueueItem.CreatedAt,
      doneAt: Option[QueueItem.DoneAt],
      updatedAt: QueueItem.UpdatedAt,
      rev: QueueItem.Rev = QueueItem.Rev(""),
      creator: Option[Json] = None
  ) derives JsonCodec

  object QueueItem:
    export Queue.Wire as List
    type Text = Text.T
    object Text extends Newtype[String, Pure]
    type Topic = Topic.T
    object Topic extends Newtype[String, Pure]
    type Status = Status.T
    object Status extends Newtype[String, Pure]
    type CreatedAt = CreatedAt.T
    object CreatedAt extends Newtype[String, Pure]
    type DoneAt = DoneAt.T
    object DoneAt extends Newtype[String, Pure]
    type UpdatedAt = UpdatedAt.T
    object UpdatedAt extends Newtype[String, Pure]
    type Rev = Rev.T
    object Rev extends Newtype[String, Pure]

  /** Both bounds inclusive: Postgres keeps an `int8range` as `[lower, upper)`, so `upper − 1` is applied on the way out
    * and undone on the way in.
    */
  final case class Range(start: Option[Year] = None, end: Option[Year] = None) derives JsonCodec, CanEqual

  final case class Reference(
      places: Reference.Places,
      placesDigest: Reference.PlacesDigest,
      periods: Reference.Periods,
      periodsDigest: Reference.PeriodsDigest
  ) derives JsonCodec,
        KindlingsSchema,
        api.json.Schema,
        CanEqual

  object Reference:
    type Places = Places.T
    object Places extends Newtype[Int, Pure]
    type PlacesDigest = PlacesDigest.T
    @tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
    object PlacesDigest extends Newtype[String, Pure]
    type Periods = Periods.T
    object Periods extends Newtype[Int, Pure]
    type PeriodsDigest = PeriodsDigest.T
    @tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
    object PeriodsDigest extends Newtype[String, Pure]

  final case class SiteType(
      placeId: SiteType.PlaceId,
      siteType: Option[SiteType.Value],
      checkedAt: Option[SiteType.CheckedAt]
  ) derives JsonCodec

  object SiteType:
    export api.catalogue.Place.Id as PlaceId
    type Value = Value.T
    object Value extends Newtype[String, Pure]
    type CheckedAt = CheckedAt.T
    object CheckedAt extends Newtype[String, Pure]

  final case class Source(
      uid: UUID,
      origin: UUID,
      citekey: Citekey,
      title: api.library.Title,
      authors: List[AuthorName],
      doi: Option[Doi],
      year: Option[Year],
      venue: Option[api.library.Source.Venue],
      status: api.library.Source.Status,
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
      rev: Source.Rev = Source.Rev(""),
      /** Absent when there are none, so a line written before the field existed keeps its rev. */
      isbns: Option[List[Source.Isbns]] = None,
      creator: Option[Json] = None
  ) derives JsonCodec

  object Source:
    type Topics = Topics.T
    object Topics extends Newtype[String, Pure]
    type Regions = Regions.T
    object Regions extends Newtype[String, Pure]
    type Tags = Tags.T
    object Tags extends Newtype[String, Pure]
    type Summary = Summary.T
    object Summary extends Newtype[String, Pure]
    type Notes = Notes.T
    object Notes extends Newtype[String, Pure]
    type Provenance = Provenance.T
    object Provenance extends Newtype[String, Pure]
    export api.library.Acquire.CitedByCount
    type InfluentialCitations = InfluentialCitations.T
    object InfluentialCitations extends Newtype[Int, Pure]
    type AddedDate = AddedDate.T
    object AddedDate extends Newtype[String, Pure]
    type BodySha256 = BodySha256.T
    object BodySha256 extends Newtype[String, Pure]
    type OriginalSha256 = OriginalSha256.T
    object OriginalSha256 extends Newtype[String, Pure]
    type OriginalExt = OriginalExt.T
    object OriginalExt extends Newtype[String, Pure]
    type UpdatedAt = UpdatedAt.T
    object UpdatedAt extends Newtype[String, Pure]
    type Rev = Rev.T
    object Rev extends Newtype[String, Pure]
    type Isbns = Isbns.T
    object Isbns extends Newtype[String, Pure]

  final case class Tombstone(
      uid: UUID,
      kind: Kind,
      origin: UUID,
      deletedAt: Tombstone.DeletedAt
  ) derives JsonCodec

  object Tombstone:
    type DeletedAt = DeletedAt.T
    object DeletedAt extends Newtype[String, Pure]

  final case class UnresolvedEdge(srcUid: UUID, dst: UnresolvedEdge.Dst, kind: UnresolvedEdge.Kind)
      derives JsonCodec,
        CanEqual

  object UnresolvedEdge:
    type Dst = Dst.T
    object Dst extends Newtype[String, Pure]
    export api.library.Edge.Kind.Wire as Kind

  final case class Upload(sha256: Sha256, bytes: ByteCount, manifest: Option[Manifest], problems: List[Problem])
      derives JsonCodec,
        KindlingsSchema,
        api.json.Schema

  final case class VerifyResult(
      ok: VerifyResult.Ok,
      manifest: Option[Manifest],
      blobsChecked: VerifyResult.BlobsChecked,
      problems: List[Problem]
  ) derives JsonCodec,
        KindlingsSchema,
        api.json.Schema

  object VerifyResult:
    type Ok = Ok.T
    object Ok extends Newtype[Boolean, Pure]
    type BlobsChecked = BlobsChecked.T
    object BlobsChecked extends Newtype[Int, Pure]
