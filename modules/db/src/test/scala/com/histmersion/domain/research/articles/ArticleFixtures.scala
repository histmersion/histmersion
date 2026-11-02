package com.histmersion.domain.research.articles

import com.histmersion.domain.catalogue.places.PlaceScope
import com.histmersion.util.database.Sql

import com.augustnagro.magnum.*
import javax.sql.DataSource
import com.histmersion.api.library.Filter
import com.histmersion.api.research.{Article, Backlink, CitedSource, CritiqueReason}
import com.histmersion.api.Api

/** The db suites' reads and writes of research's articles on a database of their own, each write in a transaction of
  * its own and each read on a connection of its own, as the service runs them.
  */
object ArticleFixtures:
  export ArticleRepo.{listWhere, renameIn}

  def put(ds: DataSource, a: Article): String = transact(ds)(ArticleRepo.put(a))
  def patch(ds: DataSource, slug: String, p: Article.Patch, by: String = "person"): Either[Api.Error, String] =
    transact(ds)(ArticleRepo.patch(slug, p, by))
  def delete(ds: DataSource, slug: String): Boolean = transact(ds)(ArticleRepo.delete(slug))
  def rename(ds: DataSource, oldSlug: String, newSlug: String): Either[Api.Error, List[String]] =
    transact(ds)(ArticleRepo.rename(oldSlug, newSlug))
  def canonical(ds: DataSource, slug: String): String = connect(ds)(ArticleRepo.canonical(slug))
  def load(ds: DataSource, slug: String): Option[Article] = connect(ds)(ArticleRepo.load(slug))
  def get(ds: DataSource, slug: String): Option[Article.Row] = connect(ds)(ArticleRepo.get(slug))
  def view(ds: DataSource, requested: String): Option[Article.View] = connect(ds)(ArticleRepo.view(requested))
  def backlinks(ds: DataSource, slug: String): List[Backlink] = connect(ds)(ArticleRepo.backlinks(slug))
  def backlinksForSource(ds: DataSource, citekey: String): List[Backlink] =
    connect(ds)(ArticleRepo.backlinksForSource(citekey))
  def sourcesCited(ds: DataSource, slug: String): List[CitedSource] = connect(ds)(ArticleRepo.sourcesCited(slug))
  def list(
      ds: DataSource,
      filter: Filter,
      scope: PlaceScope,
      kind: Option[String],
      text: Option[String],
      limit: Int,
      offset: Int
  ): Article.List = connect(ds)(ArticleRepo.list(filter, scope, kind, text, limit, offset))
  def slugs(ds: DataSource): Vector[String] = connect(ds)(ArticleRepo.slugs)
  def updatedAtMillis(ds: DataSource): Map[String, Long] = connect(ds)(ArticleRepo.updatedAtMillis)
  def listVersions(ds: DataSource, slug: String, limit: Int = 20): List[Article.Version] =
    connect(ds)(ArticleRepo.listVersions(slug, limit))
  def getVersion(ds: DataSource, slug: String, versionId: Long): Option[Article.VersionFull] =
    connect(ds)(ArticleRepo.getVersion(slug, versionId))

  /** An article's stored critique reasons, by pair. */
  def critiques(ds: DataSource, slug: String): List[CritiqueReason] = connect(ds)(CritiqueRepo.list(slug))

  /** The WHERE of the list, as the list builds it. */
  def where(filter: Filter, scope: PlaceScope, kind: Option[String], text: Option[String]): Sql =
    ArticleRepo.listWhere(filter, scope, kind, text)
