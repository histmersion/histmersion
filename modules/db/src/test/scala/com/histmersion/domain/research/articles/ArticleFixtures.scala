package com.histmersion
package domain.research.articles

import com.histmersion.domain.catalogue.places.PlaceScope
import com.histmersion.domain.research.Slug
import com.histmersion.util.database.Sql

import com.augustnagro.magnum.*
import javax.sql.DataSource
import com.histmersion.api.library.Filter
import com.histmersion.api.research.{Article, Backlink, CitedSource, CritiqueReason}
import com.histmersion.api.Api
import com.histmersion.domain.library.Citekey
import com.histmersion.domain.library.search.Search
import com.histmersion.domain.library.sources.CitekeyMillis

/** The db suites' reads and writes of research's articles on a database of their own, each write in a transaction of
  * its own and each read on a connection of its own, as the service runs them.
  */
object ArticleFixtures:
  export ArticleRepo.{listWhere, renameIn, Limit, VersionId}
  export CritiqueRepo.By

  def put(ds: DataSource, a: Article): Article.UpdatedAt = transact(ds)(ArticleRepo.put(a))
  def patch(
      ds: DataSource,
      slug: Slug,
      p: Article.Patch,
      by: CritiqueRepo.By = CritiqueRepo.By("person")
  ): Either[Api.Error, Article.UpdatedAt] =
    transact(ds)(ArticleRepo.patch(slug, p, by))
  def delete(ds: DataSource, slug: Slug): Boolean = transact(ds)(ArticleRepo.delete(slug))
  def rename(ds: DataSource, oldSlug: Slug, newSlug: Slug): Either[Api.Error, List[Slug]] =
    transact(ds)(ArticleRepo.rename(oldSlug, newSlug))
  def canonical(ds: DataSource, slug: Slug): Slug = connect(ds)(ArticleRepo.canonical(slug))
  def load(ds: DataSource, slug: Slug): Option[Article] = connect(ds)(ArticleRepo.load(slug))
  def get(ds: DataSource, slug: Slug): Option[Article.Row] = connect(ds)(ArticleRepo.get(slug))
  def view(ds: DataSource, requested: Slug): Option[Article.View] = connect(ds)(ArticleRepo.view(requested))
  def backlinks(ds: DataSource, slug: Slug): List[Backlink] = connect(ds)(ArticleRepo.backlinks(slug))
  def backlinksForSource(ds: DataSource, citekey: Citekey): List[Backlink] =
    connect(ds)(ArticleRepo.backlinksForSource(citekey))
  def sourcesCited(ds: DataSource, slug: Slug): List[CitedSource] = connect(ds)(ArticleRepo.sourcesCited(slug))
  def list(
      ds: DataSource,
      filter: Filter,
      scope: PlaceScope,
      kind: Option[com.histmersion.domain.research.Article.Kind],
      text: Option[Search.Query],
      limit: ArticleRepo.Limit,
      offset: Offset
  ): Article.List = connect(ds)(ArticleRepo.list(filter, scope, kind, text, limit, offset))
  def slugs(ds: DataSource): Vector[Slug] = connect(ds)(ArticleRepo.slugs)
  def updatedAtMillis(ds: DataSource): Map[Slug, CitekeyMillis.Millis] =
    connect(ds)(ArticleRepo.updatedAtMillis)
  def listVersions(
      ds: DataSource,
      slug: Slug,
      limit: ArticleRepo.Limit = ArticleRepo.Limit(20)
  ): List[Article.Version] =
    connect(ds)(ArticleRepo.listVersions(slug, limit))
  def getVersion(ds: DataSource, slug: Slug, versionId: ArticleRepo.VersionId): Option[Article.VersionFull] =
    connect(ds)(ArticleRepo.getVersion(slug, versionId))

  /** An article's stored critique reasons, by pair. */
  def critiques(ds: DataSource, slug: Slug): List[CritiqueReason] = connect(ds)(CritiqueRepo.list(slug))

  /** The WHERE of the list, as the list builds it. */
  def where(
      filter: Filter,
      scope: PlaceScope,
      kind: Option[com.histmersion.domain.research.Article.Kind],
      text: Option[Search.Query]
  ): Sql =
    ArticleRepo.listWhere(filter, scope, kind, text)
