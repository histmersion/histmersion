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
  def canonical(slug: com.histmersion.domain.research.Slug): com.histmersion.domain.research.Slug

  /** The whole article as written, under its own slug (no old slug followed). */
  def load(slug: com.histmersion.domain.research.Slug): Option[Article]

  /** The article an old slug or its own names, with both directions of its link graph. */
  def view(requested: com.histmersion.domain.research.articles.ArticleService.ViewMode): Option[Article.View]

  /** Whether an article has this slug, as stored. */
  def exists(slug: com.histmersion.domain.research.Slug): Boolean

  /** Every slug, in byte order. */
  def slugs: List[com.histmersion.domain.research.articles.ArticleService.SlugList]

  /** `slug -> its last write` in epoch milliseconds, for the seed's staleness guard. */
  def updatedAtMillis: Map[com.histmersion.domain.research.articles.ArticleService.UpdatedAtText, com.histmersion.domain.research.articles.ArticleService.UpdatedAtMillis]

  /** The link view of the stored articles among `slugs`, in slug order. */
  def refs(slugs: List[com.histmersion.domain.research.articles.ArticleService.SlugList]): List[Article.Ref]

  /** The articles that cite a source, by title. */
  def backlinksForSource(citekey: com.histmersion.domain.library.Citekey): List[Backlink]

  /** The articles citing any of `citekeys` (each once, with its first cited key), at most `limit`. */
  def citing(citekeys: List[com.histmersion.domain.research.articles.ArticleService.CitekeyList], limit: com.histmersion.domain.research.articles.ArticleService.CitingLimit): List[EventArticleRef]

  /** The sources the article cites, a dangling citekey with no title. */
  def sourcesCited(slug: com.histmersion.domain.research.Slug): List[CitedSource]

  /** The versions of an article, newest first, at most twenty. */
  def versions(slug: com.histmersion.domain.research.Slug): List[Article.Version]

  def version(slug: com.histmersion.domain.research.Slug, id: com.histmersion.domain.research.articles.ArticleService.VersionId): Option[Article.VersionFull]

  /** The newest saved version of an article: after a write, the one holding the text it replaced. */
  def latestVersion(slug: com.histmersion.domain.research.Slug): Option[com.histmersion.domain.research.articles.ArticleService.LatestVersionResult]

  /** The article's stored critique reasons, by pair. */
  def critiqueReasons(slug: com.histmersion.domain.research.Slug): List[CritiqueReason]

  /** Every goal's linked articles that exist: goal → slugs. */
  def articlesByGoal: Map[com.histmersion.domain.research.Slug, List[com.histmersion.domain.research.articles.ArticleService.ArticleSummary]]

  /** Replace everything under `article.slug`; the new last-write token. */
  def put(article: Article, caller: Caller): com.histmersion.domain.research.articles.ArticleService.PutText

  /** Merge `patch` into the article `slug`, its critique reasons beside it; the new last-write token. */
  def patch(slug: com.histmersion.domain.research.Slug, patch: Article.Patch, caller: Caller): Either[Api.Error, com.histmersion.domain.research.articles.ArticleService.PatchText]

  def delete(slug: com.histmersion.domain.research.Slug, caller: Caller): Boolean

  /** A new slug, the old one kept as an alias, every `[[old]]` rewritten; the articles rewritten. */
  def rename(from: com.histmersion.domain.research.Slug, to: com.histmersion.domain.research.Slug, caller: Caller): Either[Api.Error, List[com.histmersion.domain.research.articles.ArticleService.RenameText]]

object ArticleService:
  type ArticleSummary = ArticleSummary.T
  object ArticleSummary extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Body = Body.T
  object Body extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CitekeyList = CitekeyList.T
  object CitekeyList extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PatchText = PatchText.T
  object PatchText extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PutText = PutText.T
  object PutText extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RenameText = RenameText.T
  object RenameText extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SlugList = SlugList.T
  object SlugList extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Token = Token.T
  object Token extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type UpdatedAtMillis = UpdatedAtMillis.T
  object UpdatedAtMillis extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type UpdatedAtText = UpdatedAtText.T
  object UpdatedAtText extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ViewMode = ViewMode.T
  object ViewMode extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type WordCount = WordCount.T
  object WordCount extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type CitingLimit = CitingLimit.T
  object CitingLimit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type VersionId = VersionId.T
  object VersionId extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LatestVersionResult = LatestVersionResult.T
  object LatestVersionResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def apply(db: Transactor): ArticleService = Implementation(db)

  final private class Implementation(db: Transactor) extends ArticleService:
    def canonical(slug: com.histmersion.domain.research.Slug): com.histmersion.domain.research.Slug = db.read(ArticleRepo.canonical(slug))
    def load(slug: com.histmersion.domain.research.Slug): Option[Article] = db.read(ArticleRepo.load(slug))
    def view(requested: com.histmersion.domain.research.articles.ArticleService.ViewMode): Option[Article.View] = db.read(ArticleRepo.view(requested))
    def exists(slug: com.histmersion.domain.research.Slug): Boolean = db.read(ArticleRepo.slugExists(slug))
    def slugs: List[com.histmersion.domain.research.articles.ArticleService.SlugList] = db.read(ArticleRepo.slugs).toList
    def updatedAtMillis: Map[com.histmersion.domain.research.articles.ArticleService.UpdatedAtText, com.histmersion.domain.research.articles.ArticleService.UpdatedAtMillis] = db.read(ArticleRepo.updatedAtMillis)
    def refs(slugs: List[com.histmersion.domain.research.articles.ArticleService.SlugList]): List[Article.Ref] = db.read(ArticleRepo.refs(slugs))
    def backlinksForSource(citekey: com.histmersion.domain.library.Citekey): List[Backlink] = db.read(ArticleRepo.backlinksForSource(citekey))
    def citing(citekeys: List[com.histmersion.domain.research.articles.ArticleService.CitekeyList], limit: com.histmersion.domain.research.articles.ArticleService.Implementation.CitingLimit): List[EventArticleRef] =
      if citekeys.isEmpty then Nil else db.read(ArticleRepo.citing(citekeys, limit))
    def sourcesCited(slug: com.histmersion.domain.research.Slug): List[CitedSource] = db.read(ArticleRepo.sourcesCited(slug))
    def versions(slug: com.histmersion.domain.research.Slug): List[Article.Version] = db.read(ArticleRepo.listVersions(slug))
    def version(slug: com.histmersion.domain.research.Slug, id: com.histmersion.domain.research.articles.ArticleService.Implementation.VersionId): Option[Article.VersionFull] = db.read(ArticleRepo.getVersion(slug, id))
    def latestVersion(slug: com.histmersion.domain.research.Slug): Option[com.histmersion.domain.research.articles.ArticleService.Implementation.LatestVersionResult] = db.read(ArticleRepo.latestVersion(slug))
    def critiqueReasons(slug: com.histmersion.domain.research.Slug): List[CritiqueReason] = db.read(CritiqueRepo.list(slug))
    def articlesByGoal: Map[com.histmersion.domain.research.Slug, List[com.histmersion.domain.research.articles.ArticleService.ArticleSummary]] = db.read(ArticleRepo.articlesByGoal)
    def put(article: Article, caller: Caller): com.histmersion.domain.research.articles.ArticleService.PutText = db.transact(ArticleRepo.put(article))
    def patch(slug: com.histmersion.domain.research.Slug, patch: Article.Patch, caller: Caller): Either[Api.Error, com.histmersion.domain.research.articles.ArticleService.PatchText] =
      db.transact(ArticleRepo.patch(slug, patch))
    def delete(slug: com.histmersion.domain.research.Slug, caller: Caller): Boolean = db.transact(ArticleRepo.delete(slug))
    def rename(from: com.histmersion.domain.research.Slug, to: com.histmersion.domain.research.Slug, caller: Caller): Either[Api.Error, List[com.histmersion.domain.research.articles.ArticleService.RenameText]] =
      db.transact(ArticleRepo.rename(from, to))

  object Implementation:
    type CitingLimit = CitingLimit.T
    object CitingLimit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type VersionId = VersionId.T
    object VersionId extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type LatestVersionResult = LatestVersionResult.T
    object LatestVersionResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** Over the articles of `state`, as a database holding only research's rows answers: no source is stored, so every
    * link is an article link, nothing cites a source and a critique reason names no citekey; no goal links an article.
    * `clock` stands in for the database's `now()`.
    */
  final class InMemory(state: AtomicReference[ResearchState], clock: Clock) extends ArticleService:

    private val Token = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'").withZone(ZoneOffset.UTC)
    private def token(at: Instant): com.histmersion.domain.research.articles.ArticleService.Token = Token.format(at)

    private def canonicalIn(st: ResearchState, slug: com.histmersion.domain.research.Slug): com.histmersion.domain.research.Slug =
      if st.articles.contains(slug) then slug else st.aliases.getOrElse(slug, slug)

    private def loaded(stored: ResearchState.Stored): Article =
      stored.article.modify(_.updatedAt).setTo(Some(token(stored.updatedAt)))

    def canonical(slug: com.histmersion.domain.research.Slug): com.histmersion.domain.research.Slug = canonicalIn(state.get, slug)
    def load(slug: com.histmersion.domain.research.Slug): Option[Article] = state.get.articles.get(slug).map(loaded)

    def view(requested: com.histmersion.domain.research.articles.ArticleService.ViewMode): Option[Article.View] =
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
          .map(o => Backlink(com.histmersion.api.research.Backlink.Kind("article"), o.article.slug, Some(o.article.title)))
          .sortBy(_.id)
        Article.View(row, backlinks, Nil)
      }

    def exists(slug: com.histmersion.domain.research.Slug): Boolean = state.get.articles.contains(slug)
    def slugs: List[com.histmersion.domain.research.articles.ArticleService.SlugList] = state.get.articles.keys.toList.sorted
    def updatedAtMillis: Map[com.histmersion.domain.research.articles.ArticleService.UpdatedAtText, com.histmersion.domain.research.articles.ArticleService.UpdatedAtMillis] = state.get.articles.view.mapValues(_.updatedAt.toEpochMilli).toMap

    def refs(slugs: List[com.histmersion.domain.research.articles.ArticleService.SlugList]): List[Article.Ref] =
      val st = state.get
      slugs
        .map(_.trim.toLowerCase)
        .filter(_.nonEmpty)
        .distinct
        .sorted
        .flatMap(s => st.articles.get(s).map(o => Article.Ref(s, o.article.title, o.article.summary)))

    def backlinksForSource(citekey: com.histmersion.domain.library.Citekey): List[Backlink] = Nil
    def citing(citekeys: List[com.histmersion.domain.research.articles.ArticleService.CitekeyList], limit: com.histmersion.domain.research.articles.ArticleService.InMemory.CitingLimit): List[EventArticleRef] = Nil
    def sourcesCited(slug: com.histmersion.domain.research.Slug): List[CitedSource] = Nil

    def versions(slug: com.histmersion.domain.research.Slug): List[Article.Version] =
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
    private def wordCount(body: com.histmersion.domain.research.articles.ArticleService.Body): com.histmersion.domain.research.articles.ArticleService.WordCount =
      body.dropWhile(_ == ' ').reverse.dropWhile(_ == ' ').reverse.split("\\s+", -1).length

    def version(slug: com.histmersion.domain.research.Slug, id: com.histmersion.domain.research.articles.ArticleService.InMemory.VersionId): Option[Article.VersionFull] =
      state.get.versions
        .find(v => v.slug == slug && v.id == id)
        .map(v => Article.VersionFull(v.id, v.slug, v.body, v.summary, token(v.updatedAt), token(v.createdAt)))

    def latestVersion(slug: com.histmersion.domain.research.Slug): Option[com.histmersion.domain.research.articles.ArticleService.InMemory.LatestVersionResult] = state.get.versions.filter(_.slug == slug).map(_.id).maxOption

    def critiqueReasons(slug: com.histmersion.domain.research.Slug): List[CritiqueReason] = state.get.critiques.getOrElse(slug, Nil)
    def articlesByGoal: Map[com.histmersion.domain.research.Slug, List[com.histmersion.domain.research.articles.ArticleService.ArticleSummary]] = Map.empty

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

    def put(article: Article, caller: Caller): com.histmersion.domain.research.articles.ArticleService.PutText =
      val now = Instant.now(clock)
      state.updateAndGet(written(_, article, now))
      token(now)

    def patch(slug: com.histmersion.domain.research.Slug, patch: Article.Patch, caller: Caller): Either[Api.Error, com.histmersion.domain.research.articles.ArticleService.PatchText] =
      val now = Instant.now(clock)
      @tailrec def attempt(): Either[Api.Error, com.histmersion.domain.research.articles.ArticleService.PatchText] =
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

    def delete(slug: com.histmersion.domain.research.Slug, caller: Caller): Boolean =
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

    def rename(from: com.histmersion.domain.research.Slug, to: com.histmersion.domain.research.Slug, caller: Caller): Either[Api.Error, List[com.histmersion.domain.research.articles.ArticleService.RenameText]] =
      val (oldSlug, newSlug) = (from.trim, to.trim)
      val now = Instant.now(clock)
      @tailrec def attempt(): Either[Api.Error, List[com.histmersion.domain.research.articles.ArticleService.RenameText]] =
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
        oldSlug: com.histmersion.domain.research.Slug,
        newSlug: com.histmersion.domain.research.Slug,
        now: Instant
    ): (ResearchState, List[com.histmersion.domain.research.articles.ArticleService.RenameText]) =
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
      val to: com.histmersion.domain.research.Slug => Option[com.histmersion.domain.research.Slug] = t => Option.when(t == oldSlug)(newSlug)
      val needle = oldSlug.toLowerCase
      val rewritten = moved.articles.keys.toList.sorted.filter { s =>
        val a = moved.articles(s).article
        (a.body + " " + a.summary.getOrElse("") + " " + a.reasons.map((k, v) => s"$k $v").mkString(" ")).toLowerCase
          .contains(needle)
      }
      @tailrec def loop(rest: List[com.histmersion.domain.research.articles.ArticleService.SlugList], acc: ResearchState, done: List[com.histmersion.domain.research.articles.ArticleService.SlugList]): (ResearchState, List[com.histmersion.domain.research.articles.ArticleService.RenameText]) =
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

    type CitingLimit = CitingLimit.T
    object CitingLimit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type VersionId = VersionId.T
    object VersionId extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type LatestVersionResult = LatestVersionResult.T
    object LatestVersionResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
