package com.histmersion
package domain.library.sources

import com.histmersion.api.exchange.Bundle

import com.histmersion.api.given
import com.histmersion.util.data.exchange.BundleImport
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
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.domain.library.sources.referrers.SourceReferrers
import com.augustnagro.magnum.sql
import com.histmersion.domain.Creator
import com.histmersion.lint.{tolerated, LintRule}

/** What a bundle carries of the library: the sources (each line stamped with its rev, its body and its stored original
  * blobs), their findings, the edges they make and the ones still parked, their assessments and their old citekeys.
  *
  * The ranks are the import's order among every owner's steps: a source's rows, then what hangs off it; its body's
  * figure renames after the blobs; its chunks, finding places and parked edges in the derived step.
  */
@tolerated(LintRule.Layer, "pending G7 (separate types per layer)")
object SourcesCarried:
  type Stem = Stem.T
  object Stem extends Newtype[String, Pure]

  private val Library = DataOwner("library")

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  private def rows(rank: ImportRank)(write: BundleImport.Writer => Unit) = RowsIn(ImportStage.Rows, rank, write)

  /** The bytes of a source's body, written as a blob. */
  private def body(uid: java.util.UUID)(using DbCon): java.io.OutputStream => Unit =
    out =>
      out.write(
        sql"SELECT convert_to(ocr_markdown, 'UTF8') FROM sources WHERE uid = ${uid.toString}::uuid"
          .query[Array[Byte]]
          .run()
          .head
      )

  /** The sources: the lines without their blobs are what a merge compares, the lines with them what an export writes.
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def sources(referrers: SourceReferrers)(using Creator.New): CarriedKind = new CarriedKind:
    val owner: DataOwner = Library
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    val entry: BundleEntry = BundleEntry(Bundle.Kind.Source.file)
    override val record: Option[Bundle.Kind] = Some(Bundle.Kind.Source)
    override val table: Option[TableName] = Some(TableName("sources"))

    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
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
        CarriedOut.Payload(BundleFormat.ndjson(as.map(BundleFormat.stamp(_).line))),
        as.map(_.updatedAt.value).maxOption.map(CarriedOut.AsOf(_)),
        bodies ++ stored
      )

    def into(scope: BundleScope, files: ContentStore)(using DbCon): BundleEntities => BundleEntities =
      val as = SourceBundleRepo.sources(scope).toList.map(SourceBundleRepo.sourceLine(_, files))
      es => es.modify(_.sources).setTo(as)

    override def blobIn(
        use: BlobUse,
        sha256: Sha256,
        writer: BundleImport.Writer,
        files: ContentStore
    ): Option[BlobSink] = SourceBundleRepo.blobIn(use, sha256, writer, files)

    override val rowsIn: List[RowsIn] = List(
      rows(ImportRank(10))(w => SourceBundleRepo.sourcesIn(w, referrers)),
      RowsIn(ImportStage.AfterBlobs, ImportRank(100), SourceBundleRepo.bodyRenamesIn),
      RowsIn(ImportStage.Derived, ImportRank(200), SourceBundleRepo.derivedIn)
    )

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  val findings: CarriedKind = CarriedKind.records[Bundle.Finding](
    Library,
    Bundle.Kind.Finding,
    TableName("findings"),
    List(rows(ImportRank(23))(SourceBundleRepo.findingsIn))
  )(scope => SourceBundleRepo.findings(scope).toList)(
    f => CarriedKind.RecordsClock(f.updatedAt.value),
    (es, ls) => es.modify(_.findings).setTo(ls)
  )

  /** The entry of a set records own, by its file's stem. */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def set(stem: Stem): BundleEntry = BundleEntry(s"entities/${stem.value}.ndjson")

  val edges: CarriedKind =
    CarriedKind
      .owned[Bundle.Edge](Library, set(Stem("edges")), List(rows(ImportRank(20))(SourceBundleRepo.edgesIn)))(scope =>
        SourceBundleRepo.edges(scope).toList
      )(_ => None, (es, ls) => es.modify(_.edges).setTo(ls))

  val unresolvedEdges: CarriedKind = CarriedKind.owned[Bundle.UnresolvedEdge](
    Library,
    set(Stem("unresolved-edges")),
    List(rows(ImportRank(21))(SourceBundleRepo.unresolvedEdgesIn))
  )(scope => SourceBundleRepo.unresolvedEdges(scope).toList)(
    _ => None,
    (es, ls) => es.modify(_.unresolvedEdges).setTo(ls)
  )

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  val assessments: CarriedKind = CarriedKind.owned[Bundle.Assessment](
    Library,
    set(Stem("assessments")),
    List(rows(ImportRank(24))(SourceBundleRepo.assessmentsIn))
  )(scope => SourceBundleRepo.assessments(scope).toList)(
    a => Some(CarriedKind.OwnedClock(a.checkedAt.value)),
    (es, ls) => es.modify(_.assessments).setTo(ls)
  )

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  val aliases: CarriedKind =
    CarriedKind.owned[Bundle.Alias](
      Library,
      set(Stem("aliases")),
      List(rows(ImportRank(22))(SourceBundleRepo.aliasesIn))
    )(scope => SourceBundleRepo.aliases(scope).toList)(
      a => Some(CarriedKind.OwnedClock(a.createdAt.value)),
      (es, ls) => es.modify(_.aliases).setTo(ls)
    )

  /** Every kind of the library, in the order the registry has always listed them. */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def all(referrers: SourceReferrers)(using Creator.New): List[CarriedKind] =
    List(sources(referrers), findings, edges, unresolvedEdges, assessments, aliases)
