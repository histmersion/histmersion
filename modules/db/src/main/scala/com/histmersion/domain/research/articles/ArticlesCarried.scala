package com.histmersion
package domain.research.articles

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

/** What a bundle carries of research: the articles (each line stamped with its rev), their versions (each body a blob)
  * and their old slugs.
  *
  * The ranks are the import's order among every owner's steps: the renames the other side made before the rows that
  * carry the new slug; the articles before their old slugs and versions; their links in the derived step.
  */
object ArticlesCarried:
  type Stem = Stem.T
  object Stem extends Newtype[String, Pure]

  private val Research = DataOwner("research")

  private def rows(rank: ImportRank)(write: BundleImport.Writer => Unit) = RowsIn(ImportStage.Rows, rank, write)

  val articles: CarriedKind = CarriedKind.records[Bundle.Article](
    Research,
    Bundle.Kind.Article,
    TableName("articles"),
    List(
      rows(ImportRank(40))(ArticleBundleRepo.articleRenamesIn),
      rows(ImportRank(43))(ArticleBundleRepo.articlesIn),
      RowsIn(ImportStage.Derived, ImportRank(201), ArticleBundleRepo.articleLinksIn)
    )
  )(scope => ArticleBundleRepo.articles(scope).toList)(
    a => CarriedKind.RecordsClock(a.updatedAt.value),
    (es, ls) => es.modify(_.articles).setTo(ls)
  )

  /** The versions: the lines without their bodies are what a merge compares, the lines with them what an export writes.
    */
  val versions: CarriedKind = new CarriedKind:
    val owner: DataOwner = Research
    val entry: BundleEntry = BundleEntry(Bundle.Kind.ArticleVersion.file)
    override val record: Option[Bundle.Kind] = Some(Bundle.Kind.ArticleVersion)
    override val table: Option[TableName] = Some(TableName("article_versions"))

    def out(scope: BundleScope, files: ContentStore)(using DbCon): CarriedOut =
      val rs = ArticleBundleRepo.versions(scope).toList
      val bodies = rs.map(r => BlobOut(r.bodySha, r.bodyBytes, ArticleBundleRepo.versionBody(r.uid)))
      val as = rs.map(ArticleBundleRepo.versionLine)
      CarriedOut(
        CarriedOut.Payload(BundleFormat.ndjson(as.map(BundleFormat.stamp(_).line))),
        as.map(_.updatedAt.value).maxOption.map(CarriedOut.AsOf(_)),
        bodies
      )

    def into(scope: BundleScope, files: ContentStore)(using DbCon): BundleEntities => BundleEntities =
      val as = ArticleBundleRepo.versions(scope).toList.map(ArticleBundleRepo.versionLine)
      es => es.modify(_.versions).setTo(as)

    override def blobIn(
        use: BlobUse,
        sha256: Sha256,
        writer: BundleImport.Writer,
        files: ContentStore
    ): Option[BlobSink] = ArticleBundleRepo.blobIn(use, writer)

    override val rowsIn: List[RowsIn] = List(rows(ImportRank(45))(ArticleBundleRepo.mergedVersionsIn))

  /** The entry of a set records own, by its file's stem. */
  private def set(stem: Stem): BundleEntry = BundleEntry(s"entities/$stem.ndjson")

  val aliases: CarriedKind =
    CarriedKind.owned[Bundle.ArticleAlias](
      Research,
      set(Stem("article-aliases")),
      List(rows(ImportRank(44))(ArticleBundleRepo.articleAliasesIn))
    )(scope => ArticleBundleRepo.articleAliases(scope).toList)(
      a => Some(CarriedKind.OwnedClock(a.createdAt.value)),
      (es, ls) => es.modify(_.articleAliases).setTo(ls)
    )

  /** Every kind of research, in the order the registry has always listed them. */
  val all: List[CarriedKind] = List(articles, versions, aliases)
