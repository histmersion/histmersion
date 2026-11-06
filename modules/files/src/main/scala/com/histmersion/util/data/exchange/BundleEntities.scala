package com.histmersion.util.data.exchange

import com.histmersion.api.exchange.Bundle
import com.histmersion.api.given

import io.circe.{Decoder, Encoder}

import java.util.UUID
import com.histmersion.util.data.exchange.BundleFormat

/** The entity content of a bundle, in memory — every NDJSON file of [[BundleFiles.all]] as its line type. Small by
  * construction: the large things (OCR bodies, version bodies, asset bytes, originals) are blobs, referenced by sha256
  * and streamed separately.
  */
final case class BundleEntities(
    sources: List[com.histmersion.api.exchange.Bundle.Source] = Nil,
    findings: List[com.histmersion.api.exchange.Bundle.Finding] = Nil,
    edges: List[com.histmersion.api.exchange.Bundle.Edge] = Nil,
    unresolvedEdges: List[com.histmersion.api.exchange.Bundle.UnresolvedEdge] = Nil,
    assessments: List[com.histmersion.api.exchange.Bundle.Assessment] = Nil,
    aliases: List[com.histmersion.api.exchange.Bundle.Alias] = Nil,
    articles: List[com.histmersion.api.exchange.Bundle.Article] = Nil,
    versions: List[com.histmersion.api.exchange.Bundle.ArticleVersion] = Nil,
    assets: List[com.histmersion.api.exchange.Bundle.Asset] = Nil,
    assetLinks: List[com.histmersion.api.exchange.Bundle.AssetLink] = Nil,
    attestations: List[com.histmersion.api.exchange.Bundle.Attestation] = Nil,
    events: List[com.histmersion.api.exchange.Bundle.Event] = Nil,
    goals: List[com.histmersion.api.exchange.Bundle.Goal] = Nil,
    queue: List[com.histmersion.api.exchange.Bundle.QueueItem] = Nil,
    tombstones: List[com.histmersion.api.exchange.Bundle.Tombstone] = Nil,
    places: List[com.histmersion.api.exchange.Bundle.Place] = Nil,
    periods: List[com.histmersion.api.exchange.Bundle.Period] = Nil,
    articleAliases: List[com.histmersion.api.exchange.Bundle.ArticleAlias] = Nil,
    eventAliases: List[com.histmersion.api.exchange.Bundle.EventAlias] = Nil,
    goalAliases: List[com.histmersion.api.exchange.Bundle.GoalAlias] = Nil,
    placeAliases: List[com.histmersion.api.exchange.Bundle.PlaceAlias] = Nil,
    /** The event-catalogue sets (#1647): not entities, merged by their own rules on import. */
    eventCatalogue: List[com.histmersion.api.exchange.Bundle.EventCatalogue] = Nil,
    eventCandidates: List[com.histmersion.api.exchange.Bundle.EventCandidate] = Nil,
    eventChecks: List[com.histmersion.api.exchange.Bundle.EventCheck] = Nil,
    corrections: List[com.histmersion.api.exchange.Bundle.Correction] = Nil,
    siteTypes: List[com.histmersion.api.exchange.Bundle.SiteType] = Nil
):
  import BundleEntities.*

  /** Every file's NDJSON payload, in [[BundleFiles.all]] order — empty files included, so a bundle always has the same
    * set of entries.
    */
  def payloads: List[(com.histmersion.util.data.exchange.BundleEntities.BlobPath, com.histmersion.util.data.exchange.BundleEntities.Payload)] =
    def stamped[A: Encoder.AsObject](as: List[A]) = BundleFormat.ndjson(as.map(BundleFormat.stamp(_).line))
    def plain[A: Encoder.AsObject](as: List[A]) = BundleFormat.ndjson(as.map(BundleFormat.line(_)))
    List(
      Bundle.Kind.Source.file -> stamped(sources),
      Bundle.Kind.Finding.file -> stamped(findings),
      Bundle.Kind.Article.file -> stamped(articles),
      Bundle.Kind.ArticleVersion.file -> stamped(versions),
      Bundle.Kind.Asset.file -> stamped(assets),
      Bundle.Kind.AssetLink.file -> stamped(assetLinks),
      Bundle.Kind.Attestation.file -> stamped(attestations),
      Bundle.Kind.Event.file -> stamped(events),
      Bundle.Kind.Goal.file -> stamped(goals),
      Bundle.Kind.QueueItem.file -> stamped(queue),
      Bundle.Kind.Place.file -> stamped(places),
      Bundle.Kind.Period.file -> stamped(periods),
      com.histmersion.api.exchange.Bundle.Files.Edges -> plain(edges),
      com.histmersion.api.exchange.Bundle.Files.UnresolvedEdges -> plain(unresolvedEdges),
      com.histmersion.api.exchange.Bundle.Files.Assessments -> plain(assessments),
      com.histmersion.api.exchange.Bundle.Files.Aliases -> plain(aliases),
      com.histmersion.api.exchange.Bundle.Files.ArticleAliases -> plain(articleAliases),
      com.histmersion.api.exchange.Bundle.Files.EventAliases -> plain(eventAliases),
      com.histmersion.api.exchange.Bundle.Files.GoalAliases -> plain(goalAliases),
      com.histmersion.api.exchange.Bundle.Files.PlaceAliases -> plain(placeAliases),
      com.histmersion.api.exchange.Bundle.Files.EventCatalogue -> plain(eventCatalogue),
      com.histmersion.api.exchange.Bundle.Files.EventCandidates -> plain(eventCandidates),
      com.histmersion.api.exchange.Bundle.Files.EventChecks -> plain(eventChecks),
      com.histmersion.api.exchange.Bundle.Files.EventCorrections -> plain(corrections),
      com.histmersion.api.exchange.Bundle.Files.PlaceSiteTypes -> plain(siteTypes),
      com.histmersion.api.exchange.Bundle.Files.Tombstones -> plain(tombstones)
    )

  /** uid → (kind, rev, label) of every stamped entity. */
  def revs: Map[UUID, Rev] =
    def of[A: Encoder.AsObject](k: Bundle.Kind, as: List[A])(uid: A => UUID, label: A => com.histmersion.util.data.exchange.BundleEntities.Rev.Label) =
      as.map(a => uid(a) -> Rev(k, BundleFormat.stamp(a).rev, label(a)))
    (of(Bundle.Kind.Source, sources)(_.uid, _.citekey) ++
      of(Bundle.Kind.Finding, findings)(_.uid, _.id) ++
      of(Bundle.Kind.Article, articles)(_.uid, _.slug) ++
      of(Bundle.Kind.ArticleVersion, versions)(_.uid, v => v.articleUid.toString) ++
      of(Bundle.Kind.Asset, assets)(_.uid, _.name) ++
      of(Bundle.Kind.AssetLink, assetLinks)(_.uid, l => s"${l.relation} ${l.targetType} ${l.targetId}") ++
      of(Bundle.Kind.Attestation, attestations)(_.uid, t => s"${t.targetType} ${t.targetId}") ++
      of(Bundle.Kind.Event, events)(_.uid, _.id) ++
      of(Bundle.Kind.Goal, goals)(_.uid, _.slug) ++
      of(Bundle.Kind.QueueItem, queue)(_.uid, _.text.take(60)) ++
      of(Bundle.Kind.Place, places)(_.uid, _.id) ++
      of(Bundle.Kind.Period, periods)(_.uid, _.id)).toMap

  /** Every uid an entity line of this bundle names as its own. */
  def uids: Set[UUID] = revs.keySet

  /** sha256 → what it is, for every blob these lines reference. */
  def blobRefs: Map[com.histmersion.util.data.exchange.BundleEntities.BlobPath, List[BlobUse]] =
    (sources.flatMap(s =>
      s.bodySha256.map(_ -> BlobUse.Body(s.uid)).toList ++
        s.originalSha256.zip(s.originalExt).map((sha, ext) => sha -> BlobUse.Original(s.uid, ext)).toList
    ) ++
      versions.map(v => v.bodySha256 -> BlobUse.VersionBody(v.uid)) ++
      assets.map(a => a.sha256 -> BlobUse.AssetBytes(a.uid)))
      .groupMap(_._1)(_._2)

  /** The newest change these lines carry — the manifest's `asOf`. Timestamps share one fixed-width UTC format, so text
    * order is time order.
    */
  def asOf: Option[com.histmersion.util.data.exchange.BundleEntities.AsOf] =
    (sources.map(_.updatedAt) ++ findings.map(_.updatedAt) ++ assessments.map(_.checkedAt) ++
      aliases.map(_.createdAt) ++ articles.map(_.updatedAt) ++ versions.map(_.updatedAt) ++
      assets.map(_.updatedAt) ++ assetLinks.map(_.updatedAt) ++ attestations.map(_.updatedAt) ++
      events.map(_.updatedAt) ++ goals.map(_.updatedAt) ++ queue.map(_.updatedAt) ++
      places.map(_.updatedAt) ++ periods.map(_.updatedAt) ++ articleAliases.map(_.createdAt) ++
      eventAliases.map(_.createdAt) ++ goalAliases.map(_.createdAt) ++ placeAliases.map(_.createdAt) ++
      eventCatalogue.map(_.retrievedAt) ++ eventCandidates.map(_.computedAt) ++ eventChecks.map(_.checkedAt) ++
      corrections.map(_.updatedAt) ++ siteTypes.flatMap(_.checkedAt) ++
      tombstones.map(_.deletedAt)).maxOption

  /** Every uid these lines REFER to (as opposed to own): what must exist in the bundle or locally. `owner` is the uid
    * whose import writes the referring line (a source's edges, aliases and assessment are written with the source). An
    * OPTIONAL reference (the target of an edge, an event's parent, a goal's parent) is dropped when its target is gone
    * here; a required one makes the line impossible to write.
    */
  def references: List[Reference] =
    def req(kind: Bundle.Kind, uid: UUID, owner: UUID, from: com.histmersion.util.data.exchange.BundleEntities.Reference.From) =
      Reference(kind, uid, owner, from, required = true)
    def opt(kind: Bundle.Kind, uid: UUID, owner: UUID, from: com.histmersion.util.data.exchange.BundleEntities.Reference.From) =
      Reference(kind, uid, owner, from, required = false)
    findings.map(f => req(Bundle.Kind.Source, f.sourceUid, f.uid, s"finding ${f.id}")) ++
      edges.map(e => opt(Bundle.Kind.Source, e.dstUid, e.srcUid, s"edge ${e.kind}")) ++
      versions.map(v => req(Bundle.Kind.Article, v.articleUid, v.uid, "article version")) ++
      assets.map(a => req(Bundle.Kind.Source, a.sourceUid, a.uid, s"asset ${a.name}")) ++
      assetLinks.map(l => req(Bundle.Kind.Asset, l.assetUid, l.uid, s"asset link to ${l.targetType} ${l.targetId}")) ++
      attestations.map(t =>
        req(Bundle.Kind.Source, t.sourceUid, t.uid, s"attestation of ${t.targetType} ${t.targetId}")
      ) ++
      events.flatMap(e =>
        e.parentUid.map(opt(Bundle.Kind.Event, _, e.uid, s"event ${e.id} parent")).toList ++
          e.edges.map(g => opt(Bundle.Kind.Event, g.dstUid, e.uid, s"event ${e.id} edge ${g.kind}"))
      ) ++
      goals.flatMap(g => g.parentUid.map(opt(Bundle.Kind.Goal, _, g.uid, s"goal ${g.slug} parent")).toList) ++
      // the owned sets: their source must at least be in the bundle or here
      edges.map(e => req(Bundle.Kind.Source, e.srcUid, e.srcUid, s"edge ${e.kind}")) ++
      unresolvedEdges.map(e => req(Bundle.Kind.Source, e.srcUid, e.srcUid, s"edge ${e.kind} to ${e.dst}")) ++
      assessments.map(a => opt(Bundle.Kind.Source, a.sourceUid, a.sourceUid, "assessment")) ++
      aliases.map(a => opt(Bundle.Kind.Source, a.sourceUid, a.sourceUid, s"alias ${a.alias}")) ++
      articleAliases.map(a => opt(Bundle.Kind.Article, a.articleUid, a.articleUid, s"old slug ${a.alias}")) ++
      eventAliases.map(a => opt(Bundle.Kind.Event, a.eventUid, a.eventUid, s"old event id ${a.alias}")) ++
      goalAliases.map(a => opt(Bundle.Kind.Goal, a.goalUid, a.goalUid, s"old goal slug ${a.alias}")) ++
      placeAliases.map(a => opt(Bundle.Kind.Place, a.placeUid, a.placeUid, s"old place id ${a.alias}"))

  /** References no line of this bundle satisfies — fine in an incremental bundle as long as the receiving side has
    * them, a problem in a full one.
    */
  def dangling: List[Reference] =
    val own = uids
    references.filterNot(r => own(r.uid))

object BundleEntities:
  type AsOf = AsOf.T
  object AsOf extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BlobPath = BlobPath.T
  object BlobPath extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Payload = Payload.T
  object Payload extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Array[_root_.scala.Byte], _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Problem = Problem.T
  object Problem extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Stamped = Stamped.T
  object Stamped extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]




  final case class Rev(kind: Bundle.Kind, rev: Rev.Value, label: Rev.Label)

  object Rev:
    type Value = Value.T
    object Value extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Label = Label.T
    object Label extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** One uid a line refers to, the uid the referring line is written under, and which line (for the message).
    */
  final case class Reference(kind: Bundle.Kind, uid: UUID, owner: UUID, from: Reference.From, required: Reference.Required)

  object Reference:
    type From = From.T
    object From extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Required = Required.T
    object Required extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** What a blob is for. */
  enum BlobUse:
    case Body(sourceUid: UUID)
    case Original(sourceUid: UUID, ext: BlobUse.Original.Ext)
    case VersionBody(versionUid: UUID)
    case AssetBytes(assetUid: UUID)

    /** The uid of the record whose line uses the blob: its import writes it only when the record is written. */
    def record: UUID = this match
      case Body(u)        => u
      case Original(u, _) => u
      case VersionBody(u) => u
      case AssetBytes(u)  => u

  object BlobUse:
    object Original:
      type Ext = Ext.T
      object Ext extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** Decode the payloads read from a bundle; every bad line is reported (`<file>:<line>: why`), the good ones kept. A
    * missing file is an empty one.
    */
  def decode(payloads: Map[com.histmersion.util.data.exchange.BundleEntities.BlobPath, com.histmersion.util.data.exchange.BundleEntities.Payload]): (BundleEntities, List[com.histmersion.util.data.exchange.BundleEntities.Problem]) =
    val problems = List.newBuilder[String]
    def read[A: Decoder](path: com.histmersion.util.data.exchange.Bundle.Kind.EntryPath, stamped: com.histmersion.util.data.exchange.BundleEntities.Stamped): List[A] =
      payloads.get(path).toList.flatMap(BundleFormat.splitLines).zipWithIndex.flatMap { (l, i) =>
        BundleFormat.decodeLine[A](l, stamped) match
          case Right(a) => Some(a)
          case Left(e)  => problems += s"$path:${i + 1}: $e"; None
      }
    val es = BundleEntities(
      sources = read[com.histmersion.api.exchange.Bundle.Source](Bundle.Kind.Source.file, true),
      findings = read[com.histmersion.api.exchange.Bundle.Finding](Bundle.Kind.Finding.file, true),
      edges = read[com.histmersion.api.exchange.Bundle.Edge](com.histmersion.api.exchange.Bundle.Files.Edges, false),
      unresolvedEdges = read[com.histmersion.api.exchange.Bundle.UnresolvedEdge](
        com.histmersion.api.exchange.Bundle.Files.UnresolvedEdges,
        false
      ),
      assessments = read[com.histmersion.api.exchange.Bundle.Assessment](
        com.histmersion.api.exchange.Bundle.Files.Assessments,
        false
      ),
      aliases =
        read[com.histmersion.api.exchange.Bundle.Alias](com.histmersion.api.exchange.Bundle.Files.Aliases, false),
      articles = read[com.histmersion.api.exchange.Bundle.Article](Bundle.Kind.Article.file, true),
      versions = read[com.histmersion.api.exchange.Bundle.ArticleVersion](Bundle.Kind.ArticleVersion.file, true),
      assets = read[com.histmersion.api.exchange.Bundle.Asset](Bundle.Kind.Asset.file, true),
      assetLinks = read[com.histmersion.api.exchange.Bundle.AssetLink](Bundle.Kind.AssetLink.file, true),
      attestations = read[com.histmersion.api.exchange.Bundle.Attestation](Bundle.Kind.Attestation.file, true),
      events = read[com.histmersion.api.exchange.Bundle.Event](Bundle.Kind.Event.file, true),
      goals = read[com.histmersion.api.exchange.Bundle.Goal](Bundle.Kind.Goal.file, true),
      queue = read[com.histmersion.api.exchange.Bundle.QueueItem](Bundle.Kind.QueueItem.file, true),
      tombstones = read[com.histmersion.api.exchange.Bundle.Tombstone](
        com.histmersion.api.exchange.Bundle.Files.Tombstones,
        false
      ),
      places = read[com.histmersion.api.exchange.Bundle.Place](Bundle.Kind.Place.file, true),
      periods = read[com.histmersion.api.exchange.Bundle.Period](Bundle.Kind.Period.file, true),
      articleAliases = read[com.histmersion.api.exchange.Bundle.ArticleAlias](
        com.histmersion.api.exchange.Bundle.Files.ArticleAliases,
        false
      ),
      eventAliases = read[com.histmersion.api.exchange.Bundle.EventAlias](
        com.histmersion.api.exchange.Bundle.Files.EventAliases,
        false
      ),
      goalAliases = read[com.histmersion.api.exchange.Bundle.GoalAlias](
        com.histmersion.api.exchange.Bundle.Files.GoalAliases,
        false
      ),
      placeAliases = read[com.histmersion.api.exchange.Bundle.PlaceAlias](
        com.histmersion.api.exchange.Bundle.Files.PlaceAliases,
        false
      ),
      eventCatalogue = read[com.histmersion.api.exchange.Bundle.EventCatalogue](
        com.histmersion.api.exchange.Bundle.Files.EventCatalogue,
        false
      ),
      eventCandidates = read[com.histmersion.api.exchange.Bundle.EventCandidate](
        com.histmersion.api.exchange.Bundle.Files.EventCandidates,
        false
      ),
      eventChecks = read[com.histmersion.api.exchange.Bundle.EventCheck](
        com.histmersion.api.exchange.Bundle.Files.EventChecks,
        false
      ),
      corrections = read[com.histmersion.api.exchange.Bundle.Correction](
        com.histmersion.api.exchange.Bundle.Files.EventCorrections,
        false
      ),
      siteTypes = read[com.histmersion.api.exchange.Bundle.SiteType](
        com.histmersion.api.exchange.Bundle.Files.PlaceSiteTypes,
        false
      )
    )
    (es, problems.result())
