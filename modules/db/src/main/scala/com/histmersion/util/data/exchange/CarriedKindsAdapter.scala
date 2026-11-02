package com.histmersion.util.data.exchange

import com.histmersion.api.*
import com.histmersion.api.given
import com.histmersion.db.{BundleImport, BundleRepo, Param, Sql}
import com.histmersion.files.BundleEntities
import com.histmersion.files.BundleEntities.BlobUse
import com.histmersion.util.data.{DataOwner, TableName}
import com.histmersion.util.storage.ContentStore
import com.histmersion.values.BundleKind

import com.augustnagro.magnum.*
import hearth.kindlings.optics.syntax.*
import io.circe.Encoder

/** Today's carried kinds of the owners that do not register their own yet (the catalogue, the media and the library
  * do), by owner: the lines through `BundleRepo`'s reads, the rows in through the steps of `BundleImport.Writer`.
  *
  * The ranks are the import's dependency order: tombstones first (a deleted row makes room for one created in its
  * place); a source's rows, then what hangs off it; places and periods before the events that point at them; every
  * rename before the rows that carry the new label; articles, events and goals before their aliases; the
  * event-catalogue sets after every event, source and place.
  */
object CarriedKindsAdapter:

  private val Research = DataOwner("research")
  private val Automation = DataOwner("automation")
  private val Identity = DataOwner("util.identity")

  /** One kind as today's code reads and writes it. */
  final private class Adapted(
      val owner: DataOwner,
      val entry: BundleEntry,
      override val record: Option[BundleKind],
      override val table: Option[TableName],
      read: (BundleScope, ContentStore) => DbCon ?=> CarriedOut,
      lines: (BundleScope, ContentStore) => DbCon ?=> BundleEntities => BundleEntities,
      takes: BlobUse => Boolean,
      override val rowsIn: List[RowsIn]
  ) extends CarriedKind:
    def out(scope: BundleScope, files: ContentStore)(using DbCon): CarriedOut = read(scope, files)
    def into(scope: BundleScope, files: ContentStore)(using DbCon): BundleEntities => BundleEntities =
      lines(scope, files)
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

  /** A kind of record: its lines stamped with their revs; `entities` its lines without their blobs, where reading the
    * blobs costs more than the lines.
    */
  private def records[A: Encoder.AsObject](
      owner: DataOwner,
      kind: BundleKind,
      table: String,
      steps: List[RowsIn],
      takes: BlobUse => Boolean = _ => false,
      entities: Option[(BundleScope, ContentStore) => DbCon ?=> List[A]] = None
  )(lines: (BundleScope, ContentStore) => DbCon ?=> (List[A], List[BlobOut]))(
      clock: A => String,
      set: (BundleEntities, List[A]) => BundleEntities
  ): CarriedKind =
    Adapted(
      owner,
      BundleEntry(kind.file),
      Some(kind),
      Some(TableName(table)),
      (scope, files) =>
        val (as, blobs) = lines(scope, files)
        CarriedOut(BundleFormat.ndjson(as.map(BundleFormat.stamp(_).line)), as.map(clock).maxOption, blobs)
      ,
      (scope, files) =>
        val as = entities.fold(lines(scope, files)._1)(_(scope, files))
        es => set(es, as)
      ,
      takes,
      steps
    )

  /** A set a record owns: its lines as they are, with no rev. */
  private def owned[A: Encoder.AsObject](owner: DataOwner, file: String, steps: List[RowsIn])(
      lines: BundleScope => DbCon ?=> Vector[A]
  )(clock: A => Option[String], set: (BundleEntities, List[A]) => BundleEntities): CarriedKind =
    Adapted(
      owner,
      BundleEntry(file),
      None,
      None,
      (scope, _) =>
        val as = lines(scope).toList
        CarriedOut(BundleFormat.ndjson(as.map(BundleFormat.line(_))), as.flatMap(clock).maxOption)
      ,
      (scope, _) =>
        val as = lines(scope).toList
        es => set(es, as)
      ,
      _ => false,
      steps
    )

  /** The bytes of one column of one row, written as a blob. */
  private def column(select: String, uid: java.util.UUID)(using DbCon): java.io.OutputStream => Unit =
    out => out.write(Sql.of(select, Param.S(uid.toString)).frag.query[Array[Byte]].run().head)

  private val articles = records[BundleArticle](
    Research,
    kind("article"),
    "articles",
    List(rows(40)(_.articleRenames()), rows(43)(_.articles()), derived(201)(_.articleLinks()))
  )((scope, _) => (BundleRepo.articles(scope).toList, Nil))(_.updatedAt, (es, ls) => es.modify(_.articles).setTo(ls))

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
  }(_.updatedAt, (es, ls) => es.modify(_.versions).setTo(ls))

  private val goals = records[BundleGoal](
    Automation,
    kind("goal"),
    "goals",
    List(rows(42)(_.goalRenames()), rows(48)(_.goals()))
  )((scope, _) => (BundleRepo.goals(scope).toList, Nil))(_.updatedAt, (es, ls) => es.modify(_.goals).setTo(ls))

  private val queue =
    records[BundleQueueItem](Automation, kind("queue-item"), "queue_items", List(rows(50)(_.queueItems()))) {
      (scope, _) => (BundleRepo.queue(scope).toList, Nil)
    }(_.updatedAt, (es, ls) => es.modify(_.queue).setTo(ls))

  private val articleAliases =
    owned[BundleArticleAlias](Research, set("article-aliases"), List(rows(44)(_.articleAliases())))(
      BundleRepo.articleAliases(_)
    )(a => Some(a.createdAt), (es, ls) => es.modify(_.articleAliases).setTo(ls))
  private val goalAliases =
    owned[BundleGoalAlias](Automation, set("goal-aliases"), List(rows(49)(_.goalAliases())))(
      BundleRepo.goalAliases(_)
    )(a => Some(a.createdAt), (es, ls) => es.modify(_.goalAliases).setTo(ls))
  private val tombstones =
    owned[BundleTombstone](Identity, set("tombstones"), List(rows(0)(_.tombstones())))(BundleRepo.tombstones(_))(
      t => Some(t.deletedAt),
      (es, ls) => es.modify(_.tombstones).setTo(ls)
    )

  /** Every kind, by owner. */
  val all: List[CarriedKind] = List(
    goals,
    queue,
    goalAliases,
    articles,
    versions,
    articleAliases,
    tombstones
  )

  def of(owner: DataOwner): List[CarriedKind] = all.filter(_.owner == owner)
