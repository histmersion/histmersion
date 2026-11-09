package com.histmersion
package util.data.exchange

import com.histmersion.api.problem.Text.sentence
import com.histmersion.api.exchange.Bundle
import com.histmersion.api.Sha256
import com.histmersion.api.given

import io.circe.{Decoder, Encoder}

import java.util.UUID
import com.histmersion.util.data.exchange.BundleFormat

/** The entity content of a bundle, in memory — every NDJSON file of [[BundleFiles.all]] as its line type. Small by
  * construction: the large things (OCR bodies, version bodies, asset bytes, originals) are blobs, referenced by sha256
  * and streamed separately.
  */
final case class BundleEntities(
    sources: List[Bundle.Source] = Nil,
    findings: List[Bundle.Finding] = Nil,
    edges: List[Bundle.Edge] = Nil,
    unresolvedEdges: List[Bundle.UnresolvedEdge] = Nil,
    assessments: List[Bundle.Assessment] = Nil,
    aliases: List[Bundle.Alias] = Nil,
    articles: List[Bundle.Article] = Nil,
    versions: List[Bundle.ArticleVersion] = Nil,
    assets: List[Bundle.Asset] = Nil,
    assetLinks: List[Bundle.AssetLink] = Nil,
    attestations: List[Bundle.Attestation] = Nil,
    events: List[Bundle.Event] = Nil,
    goals: List[Bundle.Goal] = Nil,
    queue: List[Bundle.QueueItem] = Nil,
    tombstones: List[Bundle.Tombstone] = Nil,
    places: List[Bundle.Place] = Nil,
    periods: List[Bundle.Period] = Nil,
    articleAliases: List[Bundle.ArticleAlias] = Nil,
    eventAliases: List[Bundle.EventAlias] = Nil,
    goalAliases: List[Bundle.GoalAlias] = Nil,
    placeAliases: List[Bundle.PlaceAlias] = Nil,
    /** The event-catalogue sets (#1647): not entities, merged by their own rules on import. */
    eventCatalogue: List[Bundle.EventCatalogue] = Nil,
    eventCandidates: List[Bundle.EventCandidate] = Nil,
    eventChecks: List[Bundle.EventCheck] = Nil,
    corrections: List[Bundle.Correction] = Nil,
    siteTypes: List[Bundle.SiteType] = Nil
):
  import BundleEntities.*

  /** Every file's NDJSON payload, in [[BundleFiles.all]] order — empty files included, so a bundle always has the same
    * set of entries.
    */
  def payloads: List[(String, Array[Byte])] =
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
      Bundle.Files.Edges -> plain(edges),
      Bundle.Files.UnresolvedEdges -> plain(unresolvedEdges),
      Bundle.Files.Assessments -> plain(assessments),
      Bundle.Files.Aliases -> plain(aliases),
      Bundle.Files.ArticleAliases -> plain(articleAliases),
      Bundle.Files.EventAliases -> plain(eventAliases),
      Bundle.Files.GoalAliases -> plain(goalAliases),
      Bundle.Files.PlaceAliases -> plain(placeAliases),
      Bundle.Files.EventCatalogue -> plain(eventCatalogue),
      Bundle.Files.EventCandidates -> plain(eventCandidates),
      Bundle.Files.EventChecks -> plain(eventChecks),
      Bundle.Files.EventCorrections -> plain(corrections),
      Bundle.Files.PlaceSiteTypes -> plain(siteTypes),
      Bundle.Files.Tombstones -> plain(tombstones)
    )

  /** uid → (kind, rev, label) of every stamped entity. */
  def revs: Map[UUID, Rev] =
    def of[A: Encoder.AsObject](k: Bundle.Kind, as: List[A])(uid: A => UUID, label: A => String) =
      as.map(a => uid(a) -> Rev(k, BundleFormat.stamp(a).rev, label(a)))
    (of(Bundle.Kind.Source, sources)(_.uid, _.citekey.value) ++
      of(Bundle.Kind.Finding, findings)(_.uid, _.id.value) ++
      of(Bundle.Kind.Article, articles)(_.uid, _.slug.value) ++
      of(Bundle.Kind.ArticleVersion, versions)(_.uid, v => v.articleUid.toString) ++
      of(Bundle.Kind.Asset, assets)(_.uid, _.name.value) ++
      of(Bundle.Kind.AssetLink, assetLinks)(_.uid, l => s"${l.relation} ${l.targetType} ${l.targetId}") ++
      of(Bundle.Kind.Attestation, attestations)(_.uid, t => s"${t.targetType} ${t.targetId}") ++
      of(Bundle.Kind.Event, events)(_.uid, _.id.value) ++
      of(Bundle.Kind.Goal, goals)(_.uid, _.slug.value) ++
      of(Bundle.Kind.QueueItem, queue)(_.uid, _.text.value.take(60)) ++
      of(Bundle.Kind.Place, places)(_.uid, _.id.value) ++
      of(Bundle.Kind.Period, periods)(_.uid, _.id.value)).toMap

  /** Every uid an entity line of this bundle names as its own. */
  def uids: Set[UUID] = revs.keySet

  /** sha256 → what it is, for every blob these lines reference. */
  def blobRefs: Map[Sha256, List[BlobUse]] =
    (sources.flatMap(s =>
      s.bodySha256.map(sha => Sha256(sha.value) -> BlobUse.Body(s.uid)).toList ++
        s.originalSha256.zip(s.originalExt).map((sha, ext) => Sha256(sha.value) -> BlobUse.Original(s.uid, ext)).toList
    ) ++
      versions.map(v => Sha256(v.bodySha256.value) -> BlobUse.VersionBody(v.uid)) ++
      assets.map(a => a.sha256 -> BlobUse.AssetBytes(a.uid)))
      .groupMap(_._1)(_._2)

  /** The newest change these lines carry — the manifest's `asOf`. Timestamps share one fixed-width UTC format, so text
    * order is time order.
    */
  def asOf: Option[Bundle.Manifest.AsOf] =
    (sources.map(_.updatedAt.value) ++ findings.map(_.updatedAt.value) ++ assessments.map(_.checkedAt.value) ++
      aliases.map(_.createdAt.value) ++ articles.map(_.updatedAt.value) ++ versions.map(_.updatedAt.value) ++
      assets.map(_.updatedAt.value) ++ assetLinks.map(_.updatedAt.value) ++ attestations.map(_.updatedAt.value) ++
      events.map(_.updatedAt.value) ++ goals.map(_.updatedAt.value) ++ queue.map(_.updatedAt.value) ++
      places.map(_.updatedAt.value) ++ periods.map(_.updatedAt.value) ++ articleAliases.map(_.createdAt.value) ++
      eventAliases.map(_.createdAt.value) ++ goalAliases.map(_.createdAt.value) ++ placeAliases.map(
        _.createdAt.value
      ) ++
      eventCatalogue.map(_.retrievedAt.value) ++ eventCandidates.map(_.computedAt.value) ++
      eventChecks.map(_.checkedAt.value) ++ corrections.map(_.updatedAt.value) ++
      siteTypes.flatMap(_.checkedAt.map(_.value)) ++
      tombstones.map(_.deletedAt.value)).maxOption.map(Bundle.Manifest.AsOf(_))

  /** Every uid these lines REFER to (as opposed to own): what must exist in the bundle or locally. `owner` is the uid
    * whose import writes the referring line (a source's edges, aliases and assessment are written with the source). An
    * OPTIONAL reference (the target of an edge, an event's parent, a goal's parent) is dropped when its target is gone
    * here; a required one makes the line impossible to write.
    */
  def references: List[Reference] =
    def req(kind: Bundle.Kind, uid: UUID, owner: UUID, from: String) =
      Reference(kind, uid, owner, from, required = true)
    def opt(kind: Bundle.Kind, uid: UUID, owner: UUID, from: String) =
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

  final case class Rev(kind: Bundle.Kind, rev: String, label: String)

  /** One uid a line refers to, the uid the referring line is written under, and which line (for the message).
    */
  final case class Reference(kind: Bundle.Kind, uid: UUID, owner: UUID, from: String, required: Boolean)

  /** What a blob is for. */
  enum BlobUse derives CanEqual:
    case Body(sourceUid: UUID)
    case Original(sourceUid: UUID, ext: Bundle.Source.OriginalExt)
    case VersionBody(versionUid: UUID)
    case AssetBytes(assetUid: UUID)

    /** The uid of the record whose line uses the blob: its import writes it only when the record is written. */
    def record: UUID = this match
      case Body(u)        => u
      case Original(u, _) => u
      case VersionBody(u) => u
      case AssetBytes(u)  => u

  /** Decode the payloads read from a bundle; every bad line is reported (`<file>:<line>: why`), the good ones kept. A
    * missing file is an empty one.
    */
  def decode(payloads: Map[String, Array[Byte]]): (BundleEntities, List[String]) =
    val problems = List.newBuilder[String]
    def read[A: Decoder](path: String, stamped: Boolean): List[A] =
      payloads.get(path).toList.flatMap(BundleFormat.splitLines).zipWithIndex.flatMap { (l, i) =>
        BundleFormat.decodeLine[A](l, stamped) match
          case Right(a) => Some(a)
          case Left(e)  => problems += s"$path:${i + 1}: ${e.sentence}"; None
      }
    val es = BundleEntities(
      sources = read[Bundle.Source](Bundle.Kind.Source.file, true),
      findings = read[Bundle.Finding](Bundle.Kind.Finding.file, true),
      edges = read[Bundle.Edge](Bundle.Files.Edges, false),
      unresolvedEdges = read[Bundle.UnresolvedEdge](
        Bundle.Files.UnresolvedEdges,
        false
      ),
      assessments = read[Bundle.Assessment](
        Bundle.Files.Assessments,
        false
      ),
      aliases = read[Bundle.Alias](Bundle.Files.Aliases, false),
      articles = read[Bundle.Article](Bundle.Kind.Article.file, true),
      versions = read[Bundle.ArticleVersion](Bundle.Kind.ArticleVersion.file, true),
      assets = read[Bundle.Asset](Bundle.Kind.Asset.file, true),
      assetLinks = read[Bundle.AssetLink](Bundle.Kind.AssetLink.file, true),
      attestations = read[Bundle.Attestation](Bundle.Kind.Attestation.file, true),
      events = read[Bundle.Event](Bundle.Kind.Event.file, true),
      goals = read[Bundle.Goal](Bundle.Kind.Goal.file, true),
      queue = read[Bundle.QueueItem](Bundle.Kind.QueueItem.file, true),
      tombstones = read[Bundle.Tombstone](
        Bundle.Files.Tombstones,
        false
      ),
      places = read[Bundle.Place](Bundle.Kind.Place.file, true),
      periods = read[Bundle.Period](Bundle.Kind.Period.file, true),
      articleAliases = read[Bundle.ArticleAlias](
        Bundle.Files.ArticleAliases,
        false
      ),
      eventAliases = read[Bundle.EventAlias](
        Bundle.Files.EventAliases,
        false
      ),
      goalAliases = read[Bundle.GoalAlias](
        Bundle.Files.GoalAliases,
        false
      ),
      placeAliases = read[Bundle.PlaceAlias](
        Bundle.Files.PlaceAliases,
        false
      ),
      eventCatalogue = read[Bundle.EventCatalogue](
        Bundle.Files.EventCatalogue,
        false
      ),
      eventCandidates = read[Bundle.EventCandidate](
        Bundle.Files.EventCandidates,
        false
      ),
      eventChecks = read[Bundle.EventCheck](
        Bundle.Files.EventChecks,
        false
      ),
      corrections = read[Bundle.Correction](
        Bundle.Files.EventCorrections,
        false
      ),
      siteTypes = read[Bundle.SiteType](
        Bundle.Files.PlaceSiteTypes,
        false
      )
    )
    (es, problems.result())
