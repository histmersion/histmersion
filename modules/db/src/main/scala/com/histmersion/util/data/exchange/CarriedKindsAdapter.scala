package com.histmersion.util.data.exchange

import com.histmersion.api.*
import com.histmersion.api.given
import com.histmersion.db.{BundleImport, BundleRepo, Param, Sql}
import com.histmersion.files.BundleEntities.BlobUse
import com.histmersion.util.data.{DataOwner, TableName}
import com.histmersion.util.storage.ContentStore
import com.histmersion.values.BundleKind

import com.augustnagro.magnum.*
import io.circe.Encoder

/** Today's carried kinds, registered by owner until each owner registers its own: the lines through `BundleRepo`'s
  * reads, the rows in through the steps of `BundleImport.Writer`.
  *
  * The ranks are the import's dependency order: tombstones first (a deleted row makes room for one created in its
  * place); a source's rows, then what hangs off it; places and periods before the events that point at them; every
  * rename before the rows that carry the new label; articles, events and goals before their aliases; the
  * event-catalogue sets after every event, source and place.
  */
object CarriedKindsAdapter:

  private val Library = DataOwner("library")
  private val Research = DataOwner("research")
  private val Catalogue = DataOwner("catalogue")
  private val Media = DataOwner("media")
  private val Automation = DataOwner("automation")
  private val Identity = DataOwner("util.identity")

  /** One kind as today's code reads and writes it. */
  final private class Adapted(
      val owner: DataOwner,
      val entry: BundleEntry,
      override val record: Option[BundleKind],
      override val table: Option[TableName],
      read: (BundleScope, ContentStore) => DbCon ?=> CarriedOut,
      takes: BlobUse => Boolean,
      override val rowsIn: List[RowsIn]
  ) extends CarriedKind:
    def out(scope: BundleScope, files: ContentStore)(using DbCon): CarriedOut = read(scope, files)
    override def blobIn(
        use: BlobUse,
        sha256: String,
        writer: BundleImport.Writer,
        files: ContentStore
    ): Option[BlobSink] =
      Option.when(takes(use))(writer.blobSink(use, sha256, files))

  private def rows(rank: Int)(write: BundleImport.Writer => Unit) = RowsIn(ImportStage.Rows, ImportRank(rank), write)
  private def afterBlobs(rank: Int)(write: BundleImport.Writer => Unit) =
    RowsIn(ImportStage.AfterBlobs, ImportRank(rank), write)
  private def derived(rank: Int)(write: BundleImport.Writer => Unit) =
    RowsIn(ImportStage.Derived, ImportRank(rank), write)

  /** A kind of record, by its wire name. */
  private def kind(wire: String): BundleKind =
    BundleKind.fromWire(wire).getOrElse(throw IllegalArgumentException(s"no kind of record '$wire'"))

  /** The entry of a set records own, by its file's stem. */
  private def set(stem: String): String = s"entities/$stem.ndjson"

  /** A kind of record: its lines stamped with their revs. */
  private def records[A: Encoder.AsObject](
      owner: DataOwner,
      kind: BundleKind,
      table: String,
      steps: List[RowsIn],
      takes: BlobUse => Boolean = _ => false
  )(lines: (BundleScope, ContentStore) => DbCon ?=> (List[A], List[BlobOut]))(clock: A => String): CarriedKind =
    Adapted(
      owner,
      BundleEntry(kind.file),
      Some(kind),
      Some(TableName(table)),
      (scope, files) =>
        val (as, blobs) = lines(scope, files)
        CarriedOut(BundleFormat.ndjson(as.map(BundleFormat.stamp(_).line)), as.map(clock).maxOption, blobs)
      ,
      takes,
      steps
    )

  /** A set a record owns: its lines as they are, with no rev. */
  private def owned[A: Encoder.AsObject](owner: DataOwner, file: String, steps: List[RowsIn])(
      lines: BundleScope => DbCon ?=> Vector[A]
  )(clock: A => Option[String]): CarriedKind =
    Adapted(
      owner,
      BundleEntry(file),
      None,
      None,
      (scope, _) =>
        val as = lines(scope).toList
        CarriedOut(BundleFormat.ndjson(as.map(BundleFormat.line(_))), as.flatMap(clock).maxOption)
      ,
      _ => false,
      steps
    )

  /** The bytes of one column of one row, written as a blob. */
  private def column(select: String, uid: java.util.UUID)(using DbCon): java.io.OutputStream => Unit =
    out => out.write(Sql.of(select, Param.S(uid.toString)).frag.query[Array[Byte]].run().head)

  private val sources = records[BundleSource](
    Library,
    kind("source"),
    "sources",
    List(rows(10)(_.sources()), afterBlobs(100)(_.bodyRenames()), derived(200)(_.sourcesDerived())),
    {
      case _: BlobUse.Body | _: BlobUse.Original => true
      case _                                     => false
    }
  ) { (scope, files) =>
    val rs = BundleRepo.sources(scope).toList
    val bodies = rs.flatMap(r =>
      r.bodySha
        .zip(r.bodyBytes)
        .map((sha, n) =>
          BlobOut(sha, n, column("SELECT convert_to(ocr_markdown, 'UTF8') FROM sources WHERE uid = ?::uuid", r.uid))
        )
    )
    val stored = rs.flatMap(r =>
      r.originalSha.flatMap(sha =>
        files.find(sha).map { (file, _) =>
          BlobOut(
            sha,
            file.size,
            out =>
              val in = file.open()
              try { val _ = in.transferTo(out) }
              finally in.close()
          )
        }
      )
    )
    (rs.map(BundleRepo.sourceLine(_, files)), bodies ++ stored)
  }(_.updatedAt)

  private val findings = records[BundleFinding](Library, kind("finding"), "findings", List(rows(23)(_.findings()))) {
    (scope, _) => (BundleRepo.findings(scope).toList, Nil)
  }(_.updatedAt)

  private val articles = records[BundleArticle](
    Research,
    kind("article"),
    "articles",
    List(rows(40)(_.articleRenames()), rows(43)(_.articles()), derived(201)(_.articleLinks()))
  )((scope, _) => (BundleRepo.articles(scope).toList, Nil))(_.updatedAt)

  private val versions = records[BundleArticleVersion](
    Research,
    kind("article-version"),
    "article_versions",
    List(rows(45)(_.mergedVersions())),
    {
      case _: BlobUse.VersionBody => true
      case _                      => false
    }
  ) { (scope, _) =>
    val rs = BundleRepo.versions(scope).toList
    val bodies = rs.map(r =>
      BlobOut(
        r.bodySha,
        r.bodyBytes,
        column("SELECT convert_to(body, 'UTF8') FROM article_versions WHERE uid = ?::uuid", r.uid)
      )
    )
    (rs.map(BundleRepo.versionLine), bodies)
  }(_.updatedAt)

  private val assets = records[BundleAsset](
    Media,
    kind("asset"),
    "assets",
    List(rows(51)(_.assetMetadata())),
    {
      case _: BlobUse.AssetBytes => true
      case _                     => false
    }
  ) { (scope, _) =>
    val rs = BundleRepo.assets(scope).toList
    val bytes = rs.map(r => BlobOut(r.sha, r.bytes, column("SELECT bytes FROM assets WHERE uid = ?::uuid", r.uid)))
    (rs.map(BundleRepo.assetLine), bytes)
  }(_.updatedAt)

  private val assetLinks =
    records[BundleAssetLink](Media, kind("asset-link"), "asset_links", List(afterBlobs(102)(_.assetLinks()))) {
      (scope, _) => (BundleRepo.assetLinks(scope).toList, Nil)
    }(_.updatedAt)

  private val attestations = records[BundleAttestation](
    Catalogue,
    kind("attestation"),
    "attestations",
    List(afterBlobs(101)(_.attestationFixes()), afterBlobs(103)(_.attestations()))
  )((scope, _) => (BundleRepo.attestations(scope).toList, Nil))(_.updatedAt)

  private val events = records[BundleEvent](
    Catalogue,
    kind("event"),
    "events",
    List(rows(41)(_.eventRenames()), rows(46)(_.events()))
  )((scope, _) => (BundleRepo.events(scope).toList, Nil))(_.updatedAt)

  private val goals = records[BundleGoal](
    Automation,
    kind("goal"),
    "goals",
    List(rows(42)(_.goalRenames()), rows(48)(_.goals()))
  )((scope, _) => (BundleRepo.goals(scope).toList, Nil))(_.updatedAt)

  private val queue =
    records[BundleQueueItem](Automation, kind("queue-item"), "queue_items", List(rows(50)(_.queueItems()))) {
      (scope, _) => (BundleRepo.queue(scope).toList, Nil)
    }(_.updatedAt)

  private val places = records[BundlePlace](
    Catalogue,
    kind("place"),
    "places",
    List(rows(30)(_.placeRenames()), rows(31)(_.places()))
  )((scope, _) => (BundleRepo.places(scope).toList, Nil))(_.updatedAt)

  private val periods = records[BundlePeriod](Catalogue, kind("period"), "periods", List(rows(32)(_.periods()))) {
    (scope, _) => (BundleRepo.periods(scope).toList, Nil)
  }(_.updatedAt)

  private val edges =
    owned[BundleEdge](Library, set("edges"), List(rows(20)(_.edges())))(BundleRepo.edges(_))(_ => None)
  private val unresolvedEdges =
    owned[BundleUnresolvedEdge](Library, set("unresolved-edges"), List(rows(21)(_.unresolvedEdges())))(
      BundleRepo.unresolvedEdges(_)
    )(_ => None)
  private val assessments =
    owned[BundleAssessment](Library, set("assessments"), List(rows(24)(_.assessments())))(
      BundleRepo.assessments(_)
    )(a => Some(a.checkedAt))
  private val aliases =
    owned[BundleAlias](Library, set("aliases"), List(rows(22)(_.aliases())))(BundleRepo.aliases(_))(a =>
      Some(a.createdAt)
    )
  private val articleAliases =
    owned[BundleArticleAlias](Research, set("article-aliases"), List(rows(44)(_.articleAliases())))(
      BundleRepo.articleAliases(_)
    )(a => Some(a.createdAt))
  private val eventAliases =
    owned[BundleEventAlias](Catalogue, set("event-aliases"), List(rows(47)(_.eventAliases())))(
      BundleRepo.eventAliases(_)
    )(a => Some(a.createdAt))
  private val goalAliases =
    owned[BundleGoalAlias](Automation, set("goal-aliases"), List(rows(49)(_.goalAliases())))(
      BundleRepo.goalAliases(_)
    )(a => Some(a.createdAt))
  private val placeAliases =
    owned[BundlePlaceAlias](Catalogue, set("place-aliases"), List(rows(33)(_.placeAliases())))(
      BundleRepo.placeAliases(_)
    )(a => Some(a.createdAt))
  private val eventCatalogue =
    owned[BundleEventCatalogue](Catalogue, set("event-catalogue"), List(rows(60)(_.eventCatalogue())))(
      BundleRepo.eventCatalogue(_)
    )(c => Some(c.retrievedAt))
  private val eventCandidates =
    owned[BundleEventCandidate](Catalogue, set("event-candidates"), List(rows(61)(_.eventCandidates())))(
      BundleRepo.eventCandidates(_)
    )(c => Some(c.computedAt))
  private val eventChecks =
    owned[BundleEventCheck](Catalogue, set("event-checks"), List(rows(62)(_.eventChecks())))(
      BundleRepo.eventChecks(_)
    )(c => Some(c.checkedAt))
  private val corrections =
    owned[BundleCorrection](Catalogue, set("corrections"), List(rows(63)(_.corrections())))(
      BundleRepo.corrections(_)
    )(c => Some(c.updatedAt))
  private val siteTypes =
    owned[BundleSiteType](Catalogue, set("site-types"), List(rows(64)(_.siteTypes())))(BundleRepo.siteTypes(_))(
      _.checkedAt
    )
  private val tombstones =
    owned[BundleTombstone](Identity, set("tombstones"), List(rows(0)(_.tombstones())))(BundleRepo.tombstones(_))(t =>
      Some(t.deletedAt)
    )

  /** Every kind, by owner. */
  val all: List[CarriedKind] = List(
    goals,
    queue,
    goalAliases,
    places,
    periods,
    events,
    attestations,
    placeAliases,
    eventAliases,
    eventCatalogue,
    eventCandidates,
    eventChecks,
    corrections,
    siteTypes,
    assets,
    assetLinks,
    sources,
    findings,
    edges,
    unresolvedEdges,
    assessments,
    aliases,
    articles,
    versions,
    articleAliases,
    tombstones
  )

  def of(owner: DataOwner): List[CarriedKind] = all.filter(_.owner == owner)
