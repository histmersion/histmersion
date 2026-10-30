package com.histmersion.files

import com.histmersion.api.*
import com.histmersion.api.given

import io.circe.{Decoder, Encoder}

import java.util.UUID
import com.histmersion.values.BundleKind

/** The entity content of a bundle, in memory — every NDJSON file of [[BundleFiles.all]] as its line type. Small by
  * construction: the large things (OCR bodies, version bodies, asset bytes, originals) are blobs, referenced by sha256
  * and streamed separately.
  */
final case class BundleEntities(
    sources: List[BundleSource] = Nil,
    findings: List[BundleFinding] = Nil,
    edges: List[BundleEdge] = Nil,
    unresolvedEdges: List[BundleUnresolvedEdge] = Nil,
    assessments: List[BundleAssessment] = Nil,
    aliases: List[BundleAlias] = Nil,
    articles: List[BundleArticle] = Nil,
    versions: List[BundleArticleVersion] = Nil,
    assets: List[BundleAsset] = Nil,
    assetLinks: List[BundleAssetLink] = Nil,
    attestations: List[BundleAttestation] = Nil,
    events: List[BundleEvent] = Nil,
    goals: List[BundleGoal] = Nil,
    queue: List[BundleQueueItem] = Nil,
    tombstones: List[BundleTombstone] = Nil,
    places: List[BundlePlace] = Nil,
    periods: List[BundlePeriod] = Nil,
    articleAliases: List[BundleArticleAlias] = Nil,
    eventAliases: List[BundleEventAlias] = Nil,
    goalAliases: List[BundleGoalAlias] = Nil,
    placeAliases: List[BundlePlaceAlias] = Nil,
    /** The event-catalogue sets (#1647): not entities, merged by their own rules on import. */
    eventCatalogue: List[BundleEventCatalogue] = Nil,
    eventCandidates: List[BundleEventCandidate] = Nil,
    eventChecks: List[BundleEventCheck] = Nil,
    corrections: List[BundleCorrection] = Nil,
    siteTypes: List[BundleSiteType] = Nil
):
  import BundleEntities.*

  /** Every file's NDJSON payload, in [[BundleFiles.all]] order — empty files included, so a bundle always has the same
    * set of entries.
    */
  def payloads: List[(String, Array[Byte])] =
    def stamped[A: Encoder.AsObject](as: List[A]) = BundleFormat.ndjson(as.map(BundleFormat.stamp(_).line))
    def plain[A: Encoder.AsObject](as: List[A]) = BundleFormat.ndjson(as.map(BundleFormat.line(_)))
    List(
      BundleKind.Source.file -> stamped(sources),
      BundleKind.Finding.file -> stamped(findings),
      BundleKind.Article.file -> stamped(articles),
      BundleKind.ArticleVersion.file -> stamped(versions),
      BundleKind.Asset.file -> stamped(assets),
      BundleKind.AssetLink.file -> stamped(assetLinks),
      BundleKind.Attestation.file -> stamped(attestations),
      BundleKind.Event.file -> stamped(events),
      BundleKind.Goal.file -> stamped(goals),
      BundleKind.QueueItem.file -> stamped(queue),
      BundleKind.Place.file -> stamped(places),
      BundleKind.Period.file -> stamped(periods),
      BundleFiles.Edges -> plain(edges),
      BundleFiles.UnresolvedEdges -> plain(unresolvedEdges),
      BundleFiles.Assessments -> plain(assessments),
      BundleFiles.Aliases -> plain(aliases),
      BundleFiles.ArticleAliases -> plain(articleAliases),
      BundleFiles.EventAliases -> plain(eventAliases),
      BundleFiles.GoalAliases -> plain(goalAliases),
      BundleFiles.PlaceAliases -> plain(placeAliases),
      BundleFiles.EventCatalogue -> plain(eventCatalogue),
      BundleFiles.EventCandidates -> plain(eventCandidates),
      BundleFiles.EventChecks -> plain(eventChecks),
      BundleFiles.Corrections -> plain(corrections),
      BundleFiles.SiteTypes -> plain(siteTypes),
      BundleFiles.Tombstones -> plain(tombstones)
    )

  /** uid → (kind, rev, label) of every stamped entity. */
  def revs: Map[UUID, Rev] =
    def of[A: Encoder.AsObject](k: BundleKind, as: List[A])(uid: A => UUID, label: A => String) =
      as.map(a => uid(a) -> Rev(k, BundleFormat.stamp(a).rev, label(a)))
    (of(BundleKind.Source, sources)(_.uid, _.citekey) ++
      of(BundleKind.Finding, findings)(_.uid, _.id) ++
      of(BundleKind.Article, articles)(_.uid, _.slug) ++
      of(BundleKind.ArticleVersion, versions)(_.uid, v => v.articleUid.toString) ++
      of(BundleKind.Asset, assets)(_.uid, _.name) ++
      of(BundleKind.AssetLink, assetLinks)(_.uid, l => s"${l.relation} ${l.targetType} ${l.targetId}") ++
      of(BundleKind.Attestation, attestations)(_.uid, t => s"${t.targetType} ${t.targetId}") ++
      of(BundleKind.Event, events)(_.uid, _.id) ++
      of(BundleKind.Goal, goals)(_.uid, _.slug) ++
      of(BundleKind.QueueItem, queue)(_.uid, _.text.take(60)) ++
      of(BundleKind.Place, places)(_.uid, _.id) ++
      of(BundleKind.Period, periods)(_.uid, _.id)).toMap

  /** Every uid an entity line of this bundle names as its own. */
  def uids: Set[UUID] = revs.keySet

  /** sha256 → what it is, for every blob these lines reference. */
  def blobRefs: Map[String, List[BlobUse]] =
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
  def asOf: Option[String] =
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
    def req(kind: BundleKind, uid: UUID, owner: UUID, from: String) = Reference(kind, uid, owner, from, required = true)
    def opt(kind: BundleKind, uid: UUID, owner: UUID, from: String) =
      Reference(kind, uid, owner, from, required = false)
    findings.map(f => req(BundleKind.Source, f.sourceUid, f.uid, s"finding ${f.id}")) ++
      edges.map(e => opt(BundleKind.Source, e.dstUid, e.srcUid, s"edge ${e.kind}")) ++
      versions.map(v => req(BundleKind.Article, v.articleUid, v.uid, "article version")) ++
      assets.map(a => req(BundleKind.Source, a.sourceUid, a.uid, s"asset ${a.name}")) ++
      assetLinks.map(l => req(BundleKind.Asset, l.assetUid, l.uid, s"asset link to ${l.targetType} ${l.targetId}")) ++
      attestations.map(t =>
        req(BundleKind.Source, t.sourceUid, t.uid, s"attestation of ${t.targetType} ${t.targetId}")
      ) ++
      events.flatMap(e =>
        e.parentUid.map(opt(BundleKind.Event, _, e.uid, s"event ${e.id} parent")).toList ++
          e.edges.map(g => opt(BundleKind.Event, g.dstUid, e.uid, s"event ${e.id} edge ${g.kind}"))
      ) ++
      goals.flatMap(g => g.parentUid.map(opt(BundleKind.Goal, _, g.uid, s"goal ${g.slug} parent")).toList) ++
      // the owned sets: their source must at least be in the bundle or here
      edges.map(e => req(BundleKind.Source, e.srcUid, e.srcUid, s"edge ${e.kind}")) ++
      unresolvedEdges.map(e => req(BundleKind.Source, e.srcUid, e.srcUid, s"edge ${e.kind} to ${e.dst}")) ++
      assessments.map(a => opt(BundleKind.Source, a.sourceUid, a.sourceUid, "assessment")) ++
      aliases.map(a => opt(BundleKind.Source, a.sourceUid, a.sourceUid, s"alias ${a.alias}")) ++
      articleAliases.map(a => opt(BundleKind.Article, a.articleUid, a.articleUid, s"old slug ${a.alias}")) ++
      eventAliases.map(a => opt(BundleKind.Event, a.eventUid, a.eventUid, s"old event id ${a.alias}")) ++
      goalAliases.map(a => opt(BundleKind.Goal, a.goalUid, a.goalUid, s"old goal slug ${a.alias}")) ++
      placeAliases.map(a => opt(BundleKind.Place, a.placeUid, a.placeUid, s"old place id ${a.alias}"))

  /** References no line of this bundle satisfies — fine in an incremental bundle as long as the receiving side has
    * them, a problem in a full one.
    */
  def dangling: List[Reference] =
    val own = uids
    references.filterNot(r => own(r.uid))

object BundleEntities:

  final case class Rev(kind: BundleKind, rev: String, label: String)

  /** One uid a line refers to, the uid the referring line is written under, and which line (for the message).
    */
  final case class Reference(kind: BundleKind, uid: UUID, owner: UUID, from: String, required: Boolean)

  /** What a blob is for. */
  enum BlobUse:
    case Body(sourceUid: UUID)
    case Original(sourceUid: UUID, ext: String)
    case VersionBody(versionUid: UUID)
    case AssetBytes(assetUid: UUID)

  /** Decode the payloads read from a bundle; every bad line is reported (`<file>:<line>: why`), the good ones kept. A
    * missing file is an empty one.
    */
  def decode(payloads: Map[String, Array[Byte]]): (BundleEntities, List[String]) =
    val problems = List.newBuilder[String]
    def read[A: Decoder](path: String, stamped: Boolean): List[A] =
      payloads.get(path).toList.flatMap(BundleFormat.splitLines).zipWithIndex.flatMap { (l, i) =>
        BundleFormat.decodeLine[A](l, stamped) match
          case Right(a) => Some(a)
          case Left(e)  => problems += s"$path:${i + 1}: $e"; None
      }
    val es = BundleEntities(
      sources = read[BundleSource](BundleKind.Source.file, true),
      findings = read[BundleFinding](BundleKind.Finding.file, true),
      edges = read[BundleEdge](BundleFiles.Edges, false),
      unresolvedEdges = read[BundleUnresolvedEdge](BundleFiles.UnresolvedEdges, false),
      assessments = read[BundleAssessment](BundleFiles.Assessments, false),
      aliases = read[BundleAlias](BundleFiles.Aliases, false),
      articles = read[BundleArticle](BundleKind.Article.file, true),
      versions = read[BundleArticleVersion](BundleKind.ArticleVersion.file, true),
      assets = read[BundleAsset](BundleKind.Asset.file, true),
      assetLinks = read[BundleAssetLink](BundleKind.AssetLink.file, true),
      attestations = read[BundleAttestation](BundleKind.Attestation.file, true),
      events = read[BundleEvent](BundleKind.Event.file, true),
      goals = read[BundleGoal](BundleKind.Goal.file, true),
      queue = read[BundleQueueItem](BundleKind.QueueItem.file, true),
      tombstones = read[BundleTombstone](BundleFiles.Tombstones, false),
      places = read[BundlePlace](BundleKind.Place.file, true),
      periods = read[BundlePeriod](BundleKind.Period.file, true),
      articleAliases = read[BundleArticleAlias](BundleFiles.ArticleAliases, false),
      eventAliases = read[BundleEventAlias](BundleFiles.EventAliases, false),
      goalAliases = read[BundleGoalAlias](BundleFiles.GoalAliases, false),
      placeAliases = read[BundlePlaceAlias](BundleFiles.PlaceAliases, false),
      eventCatalogue = read[BundleEventCatalogue](BundleFiles.EventCatalogue, false),
      eventCandidates = read[BundleEventCandidate](BundleFiles.EventCandidates, false),
      eventChecks = read[BundleEventCheck](BundleFiles.EventChecks, false),
      corrections = read[BundleCorrection](BundleFiles.Corrections, false),
      siteTypes = read[BundleSiteType](BundleFiles.SiteTypes, false)
    )
    (es, problems.result())
