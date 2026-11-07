package com.histmersion.domain.library.sources

import com.histmersion.api.exchange.Bundle

import com.histmersion.api.given
import com.histmersion.util.data.exchange.BundleImport
import com.histmersion.util.database.{Param, Sql}
import com.histmersion.util.data.exchange.BundleEntities
import com.histmersion.util.data.exchange.BundleEntities.BlobUse
import com.histmersion.util.data.{DataOwner, TableName}
import com.histmersion.util.data.exchange.{
  BlobOut,
  BlobSink,
  BundleEntry,
  BundleFormat,
  BundleScope,
  CarriedKind,
  CarriedOut,
  ImportRank,
  ImportStage,
  RowsIn
}
import com.histmersion.util.storage.ContentStore

import com.augustnagro.magnum.DbCon
import hearth.kindlings.optics.syntax.*

/** What a bundle carries of the library: the sources (each line stamped with its rev, its body and its stored original
  * blobs), their findings, the edges they make and the ones still parked, their assessments and their old citekeys.
  *
  * The ranks are the import's order among every owner's steps: a source's rows, then what hangs off it; its body's
  * figure renames after the blobs; its chunks, finding places and parked edges in the derived step.
  */
object SourcesCarried:
  type Rank = Rank.T
  object Rank extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Stem = Stem.T
  object Stem extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  private val Library = DataOwner("library")

  private def rows(rank: com.histmersion.domain.library.sources.SourcesCarried.Rank)(write: BundleImport.Writer => Unit) = RowsIn(ImportStage.Rows, ImportRank(rank), write)

  /** The bytes of a source's body, written as a blob. */
  private def body(uid: java.util.UUID)(using DbCon): java.io.OutputStream => Unit =
    out =>
      out.write(
        Sql
          .of("SELECT convert_to(ocr_markdown, 'UTF8') FROM sources WHERE uid = ?::uuid", Param.S(uid.toString))
          .frag
          .query[Array[Byte]]
          .run()
          .head
      )

  /** The sources: the lines without their blobs are what a merge compares, the lines with them what an export writes.
    */
  def sources(referrers: com.histmersion.domain.library.sources.referrers.SourceReferrers): CarriedKind = new CarriedKind:
    val owner: DataOwner = Library
    val entry: BundleEntry = BundleEntry(Bundle.Kind.Source.file)
    override val record: Option[Bundle.Kind] = Some(Bundle.Kind.Source)
    override val table: Option[TableName] = Some(TableName("sources"))

    def out(scope: BundleScope, files: ContentStore)(using DbCon): CarriedOut =
      val rs = SourceBundleRepo.sources(scope).toList
      val bodies = rs.flatMap(r => r.bodySha.zip(r.bodyBytes).map((sha, n) => BlobOut(sha, n, body(r.uid))))
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
      val as = rs.map(SourceBundleRepo.sourceLine(_, files))
      CarriedOut(
        BundleFormat.ndjson(as.map(BundleFormat.stamp(_).line)),
        as.map(_.updatedAt).maxOption,
        bodies ++ stored
      )

    def into(scope: BundleScope, files: ContentStore)(using DbCon): BundleEntities => BundleEntities =
      val as = SourceBundleRepo.sources(scope).toList.map(SourceBundleRepo.sourceLine(_, files))
      es => es.modify(_.sources).setTo(as)

    override def blobIn(
        use: BlobUse,
        sha256: String,
        writer: BundleImport.Writer,
        files: ContentStore
    ): Option[BlobSink] = SourceBundleRepo.blobIn(use, sha256, writer, files)

    override val rowsIn: List[RowsIn] = List(
      rows(com.histmersion.domain.library.sources.SourcesCarried.Rank(10))(w => SourceBundleRepo.sourcesIn(w, referrers)),
      RowsIn(ImportStage.AfterBlobs, ImportRank(100), SourceBundleRepo.bodyRenamesIn),
      RowsIn(ImportStage.Derived, ImportRank(200), SourceBundleRepo.derivedIn)
    )

  val findings: CarriedKind = CarriedKind.records[com.histmersion.api.exchange.Bundle.Finding](
    Library,
    Bundle.Kind.Finding,
    TableName("findings"),
    List(rows(com.histmersion.domain.library.sources.SourcesCarried.Rank(23))(SourceBundleRepo.findingsIn))
  )(scope => SourceBundleRepo.findings(scope).toList)(_.updatedAt, (es, ls) => es.modify(_.findings).setTo(ls))

  /** The entry of a set records own, by its file's stem. */
  private def set(stem: com.histmersion.domain.library.sources.SourcesCarried.Stem): BundleEntry = BundleEntry(s"entities/$stem.ndjson")

  val edges: CarriedKind =
    CarriedKind
      .owned[com.histmersion.api.exchange.Bundle.Edge](Library, set(com.histmersion.domain.library.sources.SourcesCarried.Stem("edges")), List(rows(com.histmersion.domain.library.sources.SourcesCarried.Rank(20))(SourceBundleRepo.edgesIn)))(
        scope => SourceBundleRepo.edges(scope).toList
      )(_ => None, (es, ls) => es.modify(_.edges).setTo(ls))

  val unresolvedEdges: CarriedKind = CarriedKind.owned[com.histmersion.api.exchange.Bundle.UnresolvedEdge](
    Library,
    set(com.histmersion.domain.library.sources.SourcesCarried.Stem("unresolved-edges")),
    List(rows(com.histmersion.domain.library.sources.SourcesCarried.Rank(21))(SourceBundleRepo.unresolvedEdgesIn))
  )(scope => SourceBundleRepo.unresolvedEdges(scope).toList)(
    _ => None,
    (es, ls) => es.modify(_.unresolvedEdges).setTo(ls)
  )

  val assessments: CarriedKind = CarriedKind.owned[com.histmersion.api.exchange.Bundle.Assessment](
    Library,
    set(com.histmersion.domain.library.sources.SourcesCarried.Stem("assessments")),
    List(rows(com.histmersion.domain.library.sources.SourcesCarried.Rank(24))(SourceBundleRepo.assessmentsIn))
  )(scope => SourceBundleRepo.assessments(scope).toList)(
    a => Some(a.checkedAt),
    (es, ls) => es.modify(_.assessments).setTo(ls)
  )

  val aliases: CarriedKind =
    CarriedKind.owned[com.histmersion.api.exchange.Bundle.Alias](
      Library,
      set(com.histmersion.domain.library.sources.SourcesCarried.Stem("aliases")),
      List(rows(com.histmersion.domain.library.sources.SourcesCarried.Rank(22))(SourceBundleRepo.aliasesIn))
    )(scope => SourceBundleRepo.aliases(scope).toList)(
      a => Some(a.createdAt),
      (es, ls) => es.modify(_.aliases).setTo(ls)
    )

  /** Every kind of the library, in the order the registry has always listed them. */
  def all(referrers: com.histmersion.domain.library.sources.referrers.SourceReferrers): List[CarriedKind] = List(sources(referrers), findings, edges, unresolvedEdges, assessments, aliases)
