package com.histmersion.domain.research.articles

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

/** What a bundle carries of research: the articles (each line stamped with its rev), their versions (each body a blob)
  * and their old slugs.
  *
  * The ranks are the import's order among every owner's steps: the renames the other side made before the rows that
  * carry the new slug; the articles before their old slugs and versions; their links in the derived step.
  */
object ArticlesCarried:
  type Rank = Rank.T
  object Rank extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Stem = Stem.T
  object Stem extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  private val Research = DataOwner("research")

  private def rows(rank: com.histmersion.domain.research.articles.ArticlesCarried.Rank)(write: BundleImport.Writer => Unit) = RowsIn(ImportStage.Rows, ImportRank(rank), write)

  val articles: CarriedKind = CarriedKind.records[com.histmersion.api.exchange.Bundle.Article](
    Research,
    Bundle.Kind.Article,
    TableName("articles"),
    List(
      rows(com.histmersion.domain.research.articles.ArticlesCarried.Rank(40))(ArticleBundleRepo.articleRenamesIn),
      rows(com.histmersion.domain.research.articles.ArticlesCarried.Rank(43))(ArticleBundleRepo.articlesIn),
      RowsIn(ImportStage.Derived, ImportRank(201), ArticleBundleRepo.articleLinksIn)
    )
  )(scope => ArticleBundleRepo.articles(scope).toList)(_.updatedAt, (es, ls) => es.modify(_.articles).setTo(ls))

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
      CarriedOut(BundleFormat.ndjson(as.map(BundleFormat.stamp(_).line)), as.map(_.updatedAt).maxOption, bodies)

    def into(scope: BundleScope, files: ContentStore)(using DbCon): BundleEntities => BundleEntities =
      val as = ArticleBundleRepo.versions(scope).toList.map(ArticleBundleRepo.versionLine)
      es => es.modify(_.versions).setTo(as)

    override def blobIn(
        use: BlobUse,
        sha256: String,
        writer: BundleImport.Writer,
        files: ContentStore
    ): Option[BlobSink] = ArticleBundleRepo.blobIn(use, writer)

    override val rowsIn: List[RowsIn] = List(rows(com.histmersion.domain.research.articles.ArticlesCarried.Rank(45))(ArticleBundleRepo.mergedVersionsIn))

  /** The entry of a set records own, by its file's stem. */
  private def set(stem: com.histmersion.domain.research.articles.ArticlesCarried.Stem): BundleEntry = BundleEntry(s"entities/$stem.ndjson")

  val aliases: CarriedKind =
    CarriedKind.owned[com.histmersion.api.exchange.Bundle.ArticleAlias](
      Research,
      set(com.histmersion.domain.research.articles.ArticlesCarried.Stem("article-aliases")),
      List(rows(com.histmersion.domain.research.articles.ArticlesCarried.Rank(44))(ArticleBundleRepo.articleAliasesIn))
    )(scope => ArticleBundleRepo.articleAliases(scope).toList)(
      a => Some(a.createdAt),
      (es, ls) => es.modify(_.articleAliases).setTo(ls)
    )

  /** Every kind of research, in the order the registry has always listed them. */
  val all: List[CarriedKind] = List(articles, versions, aliases)
