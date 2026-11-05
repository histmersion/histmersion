package com.histmersion.domain.research.articles

import com.histmersion.api.Write
import com.histmersion.domain.Caller
import com.histmersion.domain.research.ResearchState
import com.histmersion.util.database.Transactor

import hearth.kindlings.optics.syntax.*

import java.time.{Clock, Instant, ZoneOffset}
import java.time.format.DateTimeFormatter
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference
import scala.annotation.tailrec
import com.histmersion.api.catalogue.EventArticleRef
import com.histmersion.api.research.{Article, Articles, Backlink, CitedSource, CritiqueReason}
import com.histmersion.api.Api

/** Research's articles as records: one article with the links it makes and what links to it, written whole or merged
  * from a patch (its critique reasons beside it), renamed (the old slug kept, every `[[old]]` rewritten), deleted, and
  * its recorded versions. Slugs are taken as given; the controllers normalise them.
  */
trait ArticleService:

  /** The slug an old slug was renamed to, or the slug itself. */
  def canonical(slug: com.histmersion.domain.research.articles.ArticleService.CanonicalSlug): com.histmersion.domain.research.articles.ArticleService.CanonicalResult

  /** The whole article as written, under its own slug (no old slug followed). */
  def load(slug: com.histmersion.domain.research.articles.ArticleService.LoadSlug): Option[Article]

  /** The article an old slug or its own names, with both directions of its link graph. */
  def view(requested: com.histmersion.domain.research.articles.ArticleService.ViewRequested): Option[Article.View]

  /** Whether an article has this slug, as stored. */
  def exists(slug: com.histmersion.domain.research.articles.ArticleService.ExistsSlug): com.histmersion.domain.research.articles.ArticleService.ExistsResult

  /** Every slug, in byte order. */
  def slugs: List[com.histmersion.domain.research.articles.ArticleService.SlugsResult]

  /** `slug -> its last write` in epoch milliseconds, for the seed's staleness guard. */
  def updatedAtMillis: Map[com.histmersion.domain.research.articles.ArticleService.UpdatedAtMillisResultPart1, com.histmersion.domain.research.articles.ArticleService.UpdatedAtMillisResultPart2]

  /** The link view of the stored articles among `slugs`, in slug order. */
  def refs(slugs: List[com.histmersion.domain.research.articles.ArticleService.RefsSlugs]): List[Article.Ref]

  /** The articles that cite a source, by title. */
  def backlinksForSource(citekey: com.histmersion.domain.library.Citekey): List[Backlink]

  /** The articles citing any of `citekeys` (each once, with its first cited key), at most `limit`. */
  def citing(citekeys: List[com.histmersion.domain.research.articles.ArticleService.CitingCitekeys], limit: com.histmersion.domain.research.articles.ArticleService.CitingLimit): List[EventArticleRef]

  /** The sources the article cites, a dangling citekey with no title. */
  def sourcesCited(slug: com.histmersion.domain.research.articles.ArticleService.SourcesCitedSlug): List[CitedSource]

  /** The versions of an article, newest first, at most twenty. */
  def versions(slug: com.histmersion.domain.research.articles.ArticleService.VersionsSlug): List[Article.Version]

  def version(slug: com.histmersion.domain.research.articles.ArticleService.VersionSlug, id: com.histmersion.domain.research.articles.ArticleService.VersionId): Option[Article.VersionFull]

  /** The newest saved version of an article: after a write, the one holding the text it replaced. */
  def latestVersion(slug: com.histmersion.domain.research.articles.ArticleService.LatestVersionSlug): Option[com.histmersion.domain.research.articles.ArticleService.LatestVersionResult]

  /** The article's stored critique reasons, by pair. */
  def critiqueReasons(slug: com.histmersion.domain.research.articles.ArticleService.CritiqueReasonsSlug): List[CritiqueReason]

  /** Every goal's linked articles that exist: goal → slugs. */
  def articlesByGoal: Map[com.histmersion.domain.research.articles.ArticleService.ArticlesByGoalResultPart1, List[com.histmersion.domain.research.articles.ArticleService.ArticlesByGoalResultPart2]]

  /** Replace everything under `article.slug`; the new last-write token. */
  def put(article: Article, caller: Caller): com.histmersion.domain.research.articles.ArticleService.PutResult

  /** Merge `patch` into the article `slug`, its critique reasons beside it; the new last-write token. */
  def patch(slug: com.histmersion.domain.research.articles.ArticleService.PatchSlug, patch: Article.Patch, caller: Caller): Either[Api.Error, com.histmersion.domain.research.articles.ArticleService.PatchResultPart2]

  def delete(slug: com.histmersion.domain.research.articles.ArticleService.DeleteSlug, caller: Caller): com.histmersion.domain.research.articles.ArticleService.DeleteResult

  /** A new slug, the old one kept as an alias, every `[[old]]` rewritten; the articles rewritten. */
  def rename(from: com.histmersion.domain.research.articles.ArticleService.RenameFrom, to: com.histmersion.domain.research.articles.ArticleService.RenameTo, caller: Caller): Either[Api.Error, List[com.histmersion.domain.research.articles.ArticleService.RenameResultPart2]]

object ArticleService:

  type CanonicalResult = CanonicalResult.T
  object CanonicalResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CanonicalSlug = CanonicalSlug.T
  object CanonicalSlug extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LoadSlug = LoadSlug.T
  object LoadSlug extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ViewRequested = ViewRequested.T
  object ViewRequested extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ExistsResult = ExistsResult.T
  object ExistsResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ExistsSlug = ExistsSlug.T
  object ExistsSlug extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SlugsResult = SlugsResult.T
  object SlugsResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type UpdatedAtMillisResultPart1 = UpdatedAtMillisResultPart1.T
  object UpdatedAtMillisResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type UpdatedAtMillisResultPart2 = UpdatedAtMillisResultPart2.T
  object UpdatedAtMillisResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RefsSlugs = RefsSlugs.T
  object RefsSlugs extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CitingCitekeys = CitingCitekeys.T
  object CitingCitekeys extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CitingLimit = CitingLimit.T
  object CitingLimit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SourcesCitedSlug = SourcesCitedSlug.T
  object SourcesCitedSlug extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type VersionsSlug = VersionsSlug.T
  object VersionsSlug extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type VersionSlug = VersionSlug.T
  object VersionSlug extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type VersionId = VersionId.T
  object VersionId extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LatestVersionResult = LatestVersionResult.T
  object LatestVersionResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LatestVersionSlug = LatestVersionSlug.T
  object LatestVersionSlug extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CritiqueReasonsSlug = CritiqueReasonsSlug.T
  object CritiqueReasonsSlug extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ArticlesByGoalResultPart1 = ArticlesByGoalResultPart1.T
  object ArticlesByGoalResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ArticlesByGoalResultPart2 = ArticlesByGoalResultPart2.T
  object ArticlesByGoalResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PutResult = PutResult.T
  object PutResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PatchResultPart2 = PatchResultPart2.T
  object PatchResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PatchSlug = PatchSlug.T
  object PatchSlug extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DeleteResult = DeleteResult.T
  object DeleteResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DeleteSlug = DeleteSlug.T
  object DeleteSlug extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RenameResultPart2 = RenameResultPart2.T
  object RenameResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RenameFrom = RenameFrom.T
  object RenameFrom extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RenameTo = RenameTo.T
  object RenameTo extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def apply(db: Transactor): ArticleService = Implementation(db)

  final private class Implementation(db: Transactor) extends ArticleService:
    def canonical(slug: com.histmersion.domain.research.articles.ArticleService.Implementation.CanonicalSlug): com.histmersion.domain.research.articles.ArticleService.Implementation.CanonicalResult = db.read(ArticleRepo.canonical(slug))
    def load(slug: com.histmersion.domain.research.articles.ArticleService.Implementation.LoadSlug): Option[Article] = db.read(ArticleRepo.load(slug))
    def view(requested: com.histmersion.domain.research.articles.ArticleService.Implementation.ViewRequested): Option[Article.View] = db.read(ArticleRepo.view(requested))
    def exists(slug: com.histmersion.domain.research.articles.ArticleService.Implementation.ExistsSlug): com.histmersion.domain.research.articles.ArticleService.Implementation.ExistsResult = db.read(ArticleRepo.slugExists(slug))
    def slugs: List[com.histmersion.domain.research.articles.ArticleService.Implementation.SlugsResult] = db.read(ArticleRepo.slugs).toList
    def updatedAtMillis: Map[com.histmersion.domain.research.articles.ArticleService.Implementation.UpdatedAtMillisResultPart1, com.histmersion.domain.research.articles.ArticleService.Implementation.UpdatedAtMillisResultPart2] = db.read(ArticleRepo.updatedAtMillis)
    def refs(slugs: List[com.histmersion.domain.research.articles.ArticleService.Implementation.RefsSlugs]): List[Article.Ref] = db.read(ArticleRepo.refs(slugs))
    def backlinksForSource(citekey: com.histmersion.domain.library.Citekey): List[Backlink] = db.read(ArticleRepo.backlinksForSource(citekey))
    def citing(citekeys: List[com.histmersion.domain.research.articles.ArticleService.Implementation.CitingCitekeys], limit: com.histmersion.domain.research.articles.ArticleService.Implementation.CitingLimit): List[EventArticleRef] =
      if citekeys.isEmpty then Nil else db.read(ArticleRepo.citing(citekeys, limit))
    def sourcesCited(slug: com.histmersion.domain.research.articles.ArticleService.Implementation.SourcesCitedSlug): List[CitedSource] = db.read(ArticleRepo.sourcesCited(slug))
    def versions(slug: com.histmersion.domain.research.articles.ArticleService.Implementation.VersionsSlug): List[Article.Version] = db.read(ArticleRepo.listVersions(slug))
    def version(slug: com.histmersion.domain.research.articles.ArticleService.Implementation.VersionSlug, id: com.histmersion.domain.research.articles.ArticleService.Implementation.VersionId): Option[Article.VersionFull] = db.read(ArticleRepo.getVersion(slug, id))
    def latestVersion(slug: com.histmersion.domain.research.articles.ArticleService.Implementation.LatestVersionSlug): Option[com.histmersion.domain.research.articles.ArticleService.Implementation.LatestVersionResult] = db.read(ArticleRepo.latestVersion(slug))
    def critiqueReasons(slug: com.histmersion.domain.research.articles.ArticleService.Implementation.CritiqueReasonsSlug): List[CritiqueReason] = db.read(CritiqueRepo.list(slug))
    def articlesByGoal: Map[com.histmersion.domain.research.articles.ArticleService.Implementation.ArticlesByGoalResultPart1, List[com.histmersion.domain.research.articles.ArticleService.Implementation.ArticlesByGoalResultPart2]] = db.read(ArticleRepo.articlesByGoal)
    def put(article: Article, caller: Caller): com.histmersion.domain.research.articles.ArticleService.Implementation.PutResult = db.transact(ArticleRepo.put(article))
    def patch(slug: com.histmersion.domain.research.articles.ArticleService.Implementation.PatchSlug, patch: Article.Patch, caller: Caller): Either[Api.Error, com.histmersion.domain.research.articles.ArticleService.Implementation.PatchResultPart2] =
      db.transact(ArticleRepo.patch(slug, patch))
    def delete(slug: com.histmersion.domain.research.articles.ArticleService.Implementation.DeleteSlug, caller: Caller): com.histmersion.domain.research.articles.ArticleService.Implementation.DeleteResult = db.transact(ArticleRepo.delete(slug))
    def rename(from: com.histmersion.domain.research.articles.ArticleService.Implementation.RenameFrom, to: com.histmersion.domain.research.articles.ArticleService.Implementation.RenameTo, caller: Caller): Either[Api.Error, List[com.histmersion.domain.research.articles.ArticleService.Implementation.RenameResultPart2]] =
      db.transact(ArticleRepo.rename(from, to))

  object Implementation:
    type CanonicalResult = CanonicalResult.T
    object CanonicalResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type CanonicalSlug = CanonicalSlug.T
    object CanonicalSlug extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type LoadSlug = LoadSlug.T
    object LoadSlug extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ViewRequested = ViewRequested.T
    object ViewRequested extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ExistsResult = ExistsResult.T
    object ExistsResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ExistsSlug = ExistsSlug.T
    object ExistsSlug extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type SlugsResult = SlugsResult.T
    object SlugsResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type UpdatedAtMillisResultPart1 = UpdatedAtMillisResultPart1.T
    object UpdatedAtMillisResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type UpdatedAtMillisResultPart2 = UpdatedAtMillisResultPart2.T
    object UpdatedAtMillisResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type RefsSlugs = RefsSlugs.T
    object RefsSlugs extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type CitingCitekeys = CitingCitekeys.T
    object CitingCitekeys extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type CitingLimit = CitingLimit.T
    object CitingLimit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type SourcesCitedSlug = SourcesCitedSlug.T
    object SourcesCitedSlug extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type VersionsSlug = VersionsSlug.T
    object VersionsSlug extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type VersionSlug = VersionSlug.T
    object VersionSlug extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type VersionId = VersionId.T
    object VersionId extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type LatestVersionResult = LatestVersionResult.T
    object LatestVersionResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type LatestVersionSlug = LatestVersionSlug.T
    object LatestVersionSlug extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type CritiqueReasonsSlug = CritiqueReasonsSlug.T
    object CritiqueReasonsSlug extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ArticlesByGoalResultPart1 = ArticlesByGoalResultPart1.T
    object ArticlesByGoalResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ArticlesByGoalResultPart2 = ArticlesByGoalResultPart2.T
    object ArticlesByGoalResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type PutResult = PutResult.T
    object PutResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type PatchResultPart2 = PatchResultPart2.T
    object PatchResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type PatchSlug = PatchSlug.T
    object PatchSlug extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type DeleteResult = DeleteResult.T
    object DeleteResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type DeleteSlug = DeleteSlug.T
    object DeleteSlug extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type RenameResultPart2 = RenameResultPart2.T
    object RenameResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type RenameFrom = RenameFrom.T
    object RenameFrom extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type RenameTo = RenameTo.T
    object RenameTo extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** Over the articles of `state`, as a database holding only research's rows answers: no source is stored, so every
    * link is an article link, nothing cites a source and a critique reason names no citekey; no goal links an article.
    * `clock` stands in for the database's `now()`.
    */
  final class InMemory(state: AtomicReference[ResearchState], clock: Clock) extends ArticleService:

    private val Token = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'").withZone(ZoneOffset.UTC)
    private def token(at: Instant): com.histmersion.domain.research.articles.ArticleService.InMemory.TokenResult = Token.format(at)

    private def canonicalIn(st: ResearchState, slug: com.histmersion.domain.research.articles.ArticleService.InMemory.CanonicalInSlug): com.histmersion.domain.research.articles.ArticleService.InMemory.CanonicalInResult =
      if st.articles.contains(slug) then slug else st.aliases.getOrElse(slug, slug)

    private def loaded(stored: ResearchState.Stored): Article =
      stored.article.modify(_.updatedAt).setTo(Some(token(stored.updatedAt)))

    def canonical(slug: com.histmersion.domain.research.articles.ArticleService.InMemory.CanonicalSlug): com.histmersion.domain.research.articles.ArticleService.InMemory.CanonicalResult = canonicalIn(state.get, slug)
    def load(slug: com.histmersion.domain.research.articles.ArticleService.InMemory.LoadSlug): Option[Article] = state.get.articles.get(slug).map(loaded)

    def view(requested: com.histmersion.domain.research.articles.ArticleService.InMemory.ViewRequested): Option[Article.View] =
      val st = state.get
      val slug = canonicalIn(st, requested)
      st.articles.get(slug).map { stored =>
        val a = stored.article
        val row = Article.Row(
          a.slug,
          a.title,
          a.kind,
          a.body,
          a.summary,
          a.tags,
          a.regions,
          a.period.map(_.start),
          a.period.map(_.end),
          Some(token(stored.updatedAt)),
          Some(stored.uid)
        )
        val backlinks = st.articles.values.toList
          .filter(o => o.article.slug != slug && o.links.contains(slug))
          .map(o => Backlink("article", o.article.slug, Some(o.article.title)))
          .sortBy(_.id)
        Article.View(row, backlinks, Nil)
      }

    def exists(slug: com.histmersion.domain.research.articles.ArticleService.InMemory.ExistsSlug): com.histmersion.domain.research.articles.ArticleService.InMemory.ExistsResult = state.get.articles.contains(slug)
    def slugs: List[com.histmersion.domain.research.articles.ArticleService.InMemory.SlugsResult] = state.get.articles.keys.toList.sorted
    def updatedAtMillis: Map[com.histmersion.domain.research.articles.ArticleService.InMemory.UpdatedAtMillisResultPart1, com.histmersion.domain.research.articles.ArticleService.InMemory.UpdatedAtMillisResultPart2] = state.get.articles.view.mapValues(_.updatedAt.toEpochMilli).toMap

    def refs(slugs: List[com.histmersion.domain.research.articles.ArticleService.InMemory.RefsSlugs]): List[Article.Ref] =
      val st = state.get
      slugs
        .map(_.trim.toLowerCase)
        .filter(_.nonEmpty)
        .distinct
        .sorted
        .flatMap(s => st.articles.get(s).map(o => Article.Ref(s, o.article.title, o.article.summary)))

    def backlinksForSource(citekey: com.histmersion.domain.library.Citekey): List[Backlink] = Nil
    def citing(citekeys: List[com.histmersion.domain.research.articles.ArticleService.InMemory.CitingCitekeys], limit: com.histmersion.domain.research.articles.ArticleService.InMemory.CitingLimit): List[EventArticleRef] = Nil
    def sourcesCited(slug: com.histmersion.domain.research.articles.ArticleService.InMemory.SourcesCitedSlug): List[CitedSource] = Nil

    def versions(slug: com.histmersion.domain.research.articles.ArticleService.InMemory.VersionsSlug): List[Article.Version] =
      state.get.versions
        .filter(_.slug == slug)
        .sortBy(v => (-v.createdAt.toEpochMilli, -v.id))
        .take(20)
        .map(v =>
          Article.Version(
            v.id,
            v.slug,
            v.summary.map(_.take(200)),
            token(v.updatedAt),
            token(v.createdAt),
            wordCount(v.body)
          )
        )
        .toList

    /** The words the database counts: `trim` drops spaces only, and a split keeps the empty ends. */
    private def wordCount(body: com.histmersion.domain.research.articles.ArticleService.InMemory.WordCountBody): com.histmersion.domain.research.articles.ArticleService.InMemory.WordCountResult =
      body.dropWhile(_ == ' ').reverse.dropWhile(_ == ' ').reverse.split("\\s+", -1).length

    def version(slug: com.histmersion.domain.research.articles.ArticleService.InMemory.VersionSlug, id: com.histmersion.domain.research.articles.ArticleService.InMemory.VersionId): Option[Article.VersionFull] =
      state.get.versions
        .find(v => v.slug == slug && v.id == id)
        .map(v => Article.VersionFull(v.id, v.slug, v.body, v.summary, token(v.updatedAt), token(v.createdAt)))

    def latestVersion(slug: com.histmersion.domain.research.articles.ArticleService.InMemory.LatestVersionSlug): Option[com.histmersion.domain.research.articles.ArticleService.InMemory.LatestVersionResult] = state.get.versions.filter(_.slug == slug).map(_.id).maxOption

    def critiqueReasons(slug: com.histmersion.domain.research.articles.ArticleService.InMemory.CritiqueReasonsSlug): List[CritiqueReason] = state.get.critiques.getOrElse(slug, Nil)
    def articlesByGoal: Map[com.histmersion.domain.research.articles.ArticleService.InMemory.ArticlesByGoalResultPart1, List[com.histmersion.domain.research.articles.ArticleService.InMemory.ArticlesByGoalResultPart2]] = Map.empty

    /** The write path: the previous body and summary kept as a version, the row replaced, its links recomputed. */
    private def written(st: ResearchState, a: Article, now: Instant): ResearchState =
      val before = st.articles.get(a.slug)
      val versioned = before.fold(st)(b =>
        st.modify(_.versions)
          .using(
            _ :+ ResearchState.Replaced(st.nextVersion, a.slug, b.article.body, b.article.summary, b.updatedAt, now)
          )
          .modify(_.nextVersion)
          .using(_ + 1)
      )
      val links = Article.Links.targets(a.body).filter(_ != a.slug).distinct
      val stored = ResearchState.Stored(
        a.modify(_.updatedAt).setTo(None),
        before.fold(UUID.randomUUID())(_.uid),
        now,
        links
      )
      versioned.modify(_.articles).using(_.updated(a.slug, stored))

    def put(article: Article, caller: Caller): com.histmersion.domain.research.articles.ArticleService.InMemory.PutResult =
      val now = Instant.now(clock)
      state.updateAndGet(written(_, article, now))
      token(now)

    def patch(slug: com.histmersion.domain.research.articles.ArticleService.InMemory.PatchSlug, patch: Article.Patch, caller: Caller): Either[Api.Error, com.histmersion.domain.research.articles.ArticleService.InMemory.PatchResultPart2] =
      val now = Instant.now(clock)
      @tailrec def attempt(): Either[Api.Error, String] =
        val st = state.get
        val outcome = st.articles.get(slug).map(loaded) match
          case None           => Left(Articles.notFound(slug))
          case Some(existing) =>
            Articles.precondition(existing, patch) match
              case Left(msg) => Left(Api.Error(msg, Some(Write.CodePrecondition)))
              case Right(_)  =>
                Articles.applyPatch(existing, patch) match
                  case Left(msg)      => Left(Api.Error(msg, Some(Write.CodeInvalid)))
                  case Right(updated) =>
                    critiqueRefusal(patch.critiqueReasons) match
                      case Some(refused) => Left(refused)
                      case None          =>
                        val next = if patch.onlyCritiques then st else written(st, updated, now)
                        Right(next -> next.articles.get(slug).fold("")(s => token(s.updatedAt)))
        outcome match
          case Left(e)              => Left(e)
          case Right((next, token)) => if state.compareAndSet(st, next) then Right(token) else attempt()
      attempt()

    /** A reason names its two sources by citekey; with no source stored, the first reason is refused as the database
      * refuses it (its text checked first).
      */
    private def critiqueRefusal(reasons: List[CritiqueReason]): Option[Api.Error] =
      val tidy = reasons.map(CritiqueReason.tidy)
      tidy.collectFirst { case Left(m) => Api.Error(m, Some(Write.CodeInvalid)) }.orElse {
        tidy.collectFirst { case Right(r) =>
          Api.Error(s"critique reason ${r.ref.wire}: '${r.src}' is not a citekey", Some(Write.CodeInvalid))
        }
      }

    def delete(slug: com.histmersion.domain.research.articles.ArticleService.InMemory.DeleteSlug, caller: Caller): com.histmersion.domain.research.articles.ArticleService.InMemory.DeleteResult =
      val before = state.getAndUpdate { st =>
        if !st.articles.contains(slug) then st
        else
          st.modify(_.articles)
            .using(_ - slug)
            .modify(_.versions)
            .using(_.filterNot(_.slug == slug))
            .modify(_.aliases)
            .using(_.filterNot(_._2 == slug))
            .modify(_.critiques)
            .using(_ - slug)
            .modify(_.ledgers)
            .using(_ - slug)
      }
      before.articles.contains(slug)

    def rename(from: com.histmersion.domain.research.articles.ArticleService.InMemory.RenameFrom, to: com.histmersion.domain.research.articles.ArticleService.InMemory.RenameTo, caller: Caller): Either[Api.Error, List[com.histmersion.domain.research.articles.ArticleService.InMemory.RenameResultPart2]] =
      val (oldSlug, newSlug) = (from.trim, to.trim)
      val now = Instant.now(clock)
      @tailrec def attempt(): Either[Api.Error, List[String]] =
        val st = state.get
        val aliasOf = st.aliases.get(newSlug)
        val outcome =
          if !st.articles.contains(oldSlug) then Left(Articles.notFound(oldSlug))
          else if st.articles.contains(newSlug) then
            Left(Api.Error(s"'$newSlug' is already the slug of another article", Some(Write.CodeInvalid)))
          else if aliasOf.exists(_ != oldSlug) then
            Left(
              Api.Error(
                s"'$newSlug' is an old slug of '${aliasOf.get}' and still resolves to it — pick another",
                Some(Write.CodeInvalid)
              )
            )
          else Right(renamed(st, oldSlug, newSlug, now))
        outcome match
          case Left(e)              => Left(e)
          case Right((next, moved)) => if state.compareAndSet(st, next) then Right(moved) else attempt()
      attempt()

    /** The slug moved with its versions, links and reasons, the links to it following, every other article that
      * mentions it rewritten through the write path, the old slug kept as an alias.
      */
    private def renamed(
        st: ResearchState,
        oldSlug: com.histmersion.domain.research.articles.ArticleService.InMemory.RenamedOldSlug,
        newSlug: com.histmersion.domain.research.articles.ArticleService.InMemory.RenamedNewSlug,
        now: Instant
    ): (ResearchState, List[com.histmersion.domain.research.articles.ArticleService.InMemory.RenamedResultPart2]) =
      val stored = st.articles(oldSlug)
      val moved = st
        .modify(_.aliases)
        .using(_ - newSlug)
        .modify(_.articles)
        .using(as =>
          (as - oldSlug).updated(newSlug, stored.modify(_.article).using(_.modify(_.slug).setTo(newSlug))).map {
            (s, o) => s -> o.modify(_.links).using(_.map(t => if t == oldSlug then newSlug else t))
          }
        )
        .modify(_.versions)
        .using(_.map(v => if v.slug == oldSlug then v.modify(_.slug).setTo(newSlug) else v))
        .modify(_.critiques)
        .using(cs => cs.get(oldSlug).fold(cs)(rs => (cs - oldSlug).updated(newSlug, rs)))
        .modify(_.ledgers)
        .using(ls => ls.get(oldSlug).fold(ls)(l => (ls - oldSlug).updated(newSlug, l)))
      val to: String => Option[String] = t => Option.when(t == oldSlug)(newSlug)
      val needle = oldSlug.toLowerCase
      val rewritten = moved.articles.keys.toList.sorted.filter { s =>
        val a = moved.articles(s).article
        (a.body + " " + a.summary.getOrElse("") + " " + a.reasons.map((k, v) => s"$k $v").mkString(" ")).toLowerCase
          .contains(needle)
      }
      @tailrec def loop(rest: List[String], acc: ResearchState, done: List[String]): (ResearchState, List[String]) =
        rest match
          case Nil       => (acc, done.reverse)
          case s :: tail =>
            val a = loaded(acc.articles(s))
            val (body, nb) = Article.Links.retarget(a.body, to)
            val summary = a.summary.map(Article.Links.retarget(_, to))
            val reasons = a.reasons.map((ck, r) => to(ck).getOrElse(ck) -> r)
            val count = nb + summary.fold(0)(_._2) + a.reasons.keys.count(to(_).isDefined)
            if count == 0 then loop(tail, acc, done)
            else
              val edited =
                a.modify(_.body).setTo(body).modify(_.summary).setTo(summary.map(_._1)).modify(_.reasons).setTo(reasons)
              val next = written(acc, edited, now)
              loop(tail, next, s :: done)
      val (after, done) = loop(rewritten, moved, Nil)
      (after.modify(_.aliases).using(_.updated(oldSlug, newSlug)), done)

  object InMemory:
    type TokenResult = TokenResult.T
    object TokenResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type CanonicalInResult = CanonicalInResult.T
    object CanonicalInResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type CanonicalInSlug = CanonicalInSlug.T
    object CanonicalInSlug extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type CanonicalResult = CanonicalResult.T
    object CanonicalResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type CanonicalSlug = CanonicalSlug.T
    object CanonicalSlug extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type LoadSlug = LoadSlug.T
    object LoadSlug extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ViewRequested = ViewRequested.T
    object ViewRequested extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ExistsResult = ExistsResult.T
    object ExistsResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ExistsSlug = ExistsSlug.T
    object ExistsSlug extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type SlugsResult = SlugsResult.T
    object SlugsResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type UpdatedAtMillisResultPart1 = UpdatedAtMillisResultPart1.T
    object UpdatedAtMillisResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type UpdatedAtMillisResultPart2 = UpdatedAtMillisResultPart2.T
    object UpdatedAtMillisResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type RefsSlugs = RefsSlugs.T
    object RefsSlugs extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type CitingCitekeys = CitingCitekeys.T
    object CitingCitekeys extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type CitingLimit = CitingLimit.T
    object CitingLimit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type SourcesCitedSlug = SourcesCitedSlug.T
    object SourcesCitedSlug extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type VersionsSlug = VersionsSlug.T
    object VersionsSlug extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type WordCountResult = WordCountResult.T
    object WordCountResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type WordCountBody = WordCountBody.T
    object WordCountBody extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type VersionSlug = VersionSlug.T
    object VersionSlug extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type VersionId = VersionId.T
    object VersionId extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type LatestVersionResult = LatestVersionResult.T
    object LatestVersionResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type LatestVersionSlug = LatestVersionSlug.T
    object LatestVersionSlug extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type CritiqueReasonsSlug = CritiqueReasonsSlug.T
    object CritiqueReasonsSlug extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ArticlesByGoalResultPart1 = ArticlesByGoalResultPart1.T
    object ArticlesByGoalResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ArticlesByGoalResultPart2 = ArticlesByGoalResultPart2.T
    object ArticlesByGoalResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type PutResult = PutResult.T
    object PutResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type PatchResultPart2 = PatchResultPart2.T
    object PatchResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type PatchSlug = PatchSlug.T
    object PatchSlug extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type DeleteResult = DeleteResult.T
    object DeleteResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type DeleteSlug = DeleteSlug.T
    object DeleteSlug extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type RenameResultPart2 = RenameResultPart2.T
    object RenameResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type RenameFrom = RenameFrom.T
    object RenameFrom extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type RenameTo = RenameTo.T
    object RenameTo extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type RenamedResultPart2 = RenamedResultPart2.T
    object RenamedResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type RenamedOldSlug = RenamedOldSlug.T
    object RenamedOldSlug extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type RenamedNewSlug = RenamedNewSlug.T
    object RenamedNewSlug extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
