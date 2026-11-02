package com.histmersion.domain.research.articles

import com.histmersion.api.{ApiError, Write}
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
import com.histmersion.api.research.{
  Article,
  ArticleLinks,
  ArticlePatch,
  ArticleRef,
  ArticleRow,
  Articles,
  ArticleVersion,
  ArticleVersionFull,
  ArticleView,
  Backlink,
  CitedSource,
  CritiqueReason
}

/** Research's articles as records: one article with the links it makes and what links to it, written whole or merged
  * from a patch (its critique reasons beside it), renamed (the old slug kept, every `[[old]]` rewritten), deleted, and
  * its recorded versions. Slugs are taken as given; the controllers normalise them.
  */
trait ArticleService:

  /** The slug an old slug was renamed to, or the slug itself. */
  def canonical(slug: String): String

  /** The whole article as written, under its own slug (no old slug followed). */
  def load(slug: String): Option[Article]

  /** The article an old slug or its own names, with both directions of its link graph. */
  def view(requested: String): Option[ArticleView]

  /** Whether an article has this slug, as stored. */
  def exists(slug: String): Boolean

  /** Every slug, in byte order. */
  def slugs: List[String]

  /** `slug -> its last write` in epoch milliseconds, for the seed's staleness guard. */
  def updatedAtMillis: Map[String, Long]

  /** The link view of the stored articles among `slugs`, in slug order. */
  def refs(slugs: List[String]): List[ArticleRef]

  /** The articles that cite a source, by title. */
  def backlinksForSource(citekey: String): List[Backlink]

  /** The articles citing any of `citekeys` (each once, with its first cited key), at most `limit`. */
  def citing(citekeys: List[String], limit: Int): List[EventArticleRef]

  /** The sources the article cites, a dangling citekey with no title. */
  def sourcesCited(slug: String): List[CitedSource]

  /** The versions of an article, newest first, at most twenty. */
  def versions(slug: String): List[ArticleVersion]

  def version(slug: String, id: Long): Option[ArticleVersionFull]

  /** The newest saved version of an article: after a write, the one holding the text it replaced. */
  def latestVersion(slug: String): Option[Long]

  /** The article's stored critique reasons, by pair. */
  def critiqueReasons(slug: String): List[CritiqueReason]

  /** Every goal's linked articles that exist: goal → slugs. */
  def articlesByGoal: Map[String, List[String]]

  /** Replace everything under `article.slug`; the new last-write token. */
  def put(article: Article, caller: Caller): String

  /** Merge `patch` into the article `slug`, its critique reasons beside it; the new last-write token. */
  def patch(slug: String, patch: ArticlePatch, caller: Caller): Either[ApiError, String]

  def delete(slug: String, caller: Caller): Boolean

  /** A new slug, the old one kept as an alias, every `[[old]]` rewritten; the articles rewritten. */
  def rename(from: String, to: String, caller: Caller): Either[ApiError, List[String]]

object ArticleService:

  def apply(db: Transactor): ArticleService = Implementation(db)

  final private class Implementation(db: Transactor) extends ArticleService:
    def canonical(slug: String): String = db.read(ArticleRepo.canonical(slug))
    def load(slug: String): Option[Article] = db.read(ArticleRepo.load(slug))
    def view(requested: String): Option[ArticleView] = db.read(ArticleRepo.view(requested))
    def exists(slug: String): Boolean = db.read(ArticleRepo.slugExists(slug))
    def slugs: List[String] = db.read(ArticleRepo.slugs).toList
    def updatedAtMillis: Map[String, Long] = db.read(ArticleRepo.updatedAtMillis)
    def refs(slugs: List[String]): List[ArticleRef] = db.read(ArticleRepo.refs(slugs))
    def backlinksForSource(citekey: String): List[Backlink] = db.read(ArticleRepo.backlinksForSource(citekey))
    def citing(citekeys: List[String], limit: Int): List[EventArticleRef] =
      if citekeys.isEmpty then Nil else db.read(ArticleRepo.citing(citekeys, limit))
    def sourcesCited(slug: String): List[CitedSource] = db.read(ArticleRepo.sourcesCited(slug))
    def versions(slug: String): List[ArticleVersion] = db.read(ArticleRepo.listVersions(slug))
    def version(slug: String, id: Long): Option[ArticleVersionFull] = db.read(ArticleRepo.getVersion(slug, id))
    def latestVersion(slug: String): Option[Long] = db.read(ArticleRepo.latestVersion(slug))
    def critiqueReasons(slug: String): List[CritiqueReason] = db.read(CritiqueRepo.list(slug))
    def articlesByGoal: Map[String, List[String]] = db.read(ArticleRepo.articlesByGoal)
    def put(article: Article, caller: Caller): String = db.transact(ArticleRepo.put(article))
    def patch(slug: String, patch: ArticlePatch, caller: Caller): Either[ApiError, String] =
      db.transact(ArticleRepo.patch(slug, patch))
    def delete(slug: String, caller: Caller): Boolean = db.transact(ArticleRepo.delete(slug))
    def rename(from: String, to: String, caller: Caller): Either[ApiError, List[String]] =
      db.transact(ArticleRepo.rename(from, to))

  /** Over the articles of `state`, as a database holding only research's rows answers: no source is stored, so every
    * link is an article link, nothing cites a source and a critique reason names no citekey; no goal links an article.
    * `clock` stands in for the database's `now()`.
    */
  final class InMemory(state: AtomicReference[ResearchState], clock: Clock) extends ArticleService:

    private val Token = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'").withZone(ZoneOffset.UTC)
    private def token(at: Instant): String = Token.format(at)

    private def canonicalIn(st: ResearchState, slug: String): String =
      if st.articles.contains(slug) then slug else st.aliases.getOrElse(slug, slug)

    private def loaded(stored: ResearchState.Stored): Article =
      stored.article.modify(_.updatedAt).setTo(Some(token(stored.updatedAt)))

    def canonical(slug: String): String = canonicalIn(state.get, slug)
    def load(slug: String): Option[Article] = state.get.articles.get(slug).map(loaded)

    def view(requested: String): Option[ArticleView] =
      val st = state.get
      val slug = canonicalIn(st, requested)
      st.articles.get(slug).map { stored =>
        val a = stored.article
        val row = ArticleRow(
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
        ArticleView(row, backlinks, Nil)
      }

    def exists(slug: String): Boolean = state.get.articles.contains(slug)
    def slugs: List[String] = state.get.articles.keys.toList.sorted
    def updatedAtMillis: Map[String, Long] = state.get.articles.view.mapValues(_.updatedAt.toEpochMilli).toMap

    def refs(slugs: List[String]): List[ArticleRef] =
      val st = state.get
      slugs
        .map(_.trim.toLowerCase)
        .filter(_.nonEmpty)
        .distinct
        .sorted
        .flatMap(s => st.articles.get(s).map(o => ArticleRef(s, o.article.title, o.article.summary)))

    def backlinksForSource(citekey: String): List[Backlink] = Nil
    def citing(citekeys: List[String], limit: Int): List[EventArticleRef] = Nil
    def sourcesCited(slug: String): List[CitedSource] = Nil

    def versions(slug: String): List[ArticleVersion] =
      state.get.versions
        .filter(_.slug == slug)
        .sortBy(v => (-v.createdAt.toEpochMilli, -v.id))
        .take(20)
        .map(v =>
          ArticleVersion(
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
    private def wordCount(body: String): Int =
      body.dropWhile(_ == ' ').reverse.dropWhile(_ == ' ').reverse.split("\\s+", -1).length

    def version(slug: String, id: Long): Option[ArticleVersionFull] =
      state.get.versions
        .find(v => v.slug == slug && v.id == id)
        .map(v => ArticleVersionFull(v.id, v.slug, v.body, v.summary, token(v.updatedAt), token(v.createdAt)))

    def latestVersion(slug: String): Option[Long] = state.get.versions.filter(_.slug == slug).map(_.id).maxOption

    def critiqueReasons(slug: String): List[CritiqueReason] = state.get.critiques.getOrElse(slug, Nil)
    def articlesByGoal: Map[String, List[String]] = Map.empty

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
      val links = ArticleLinks.targets(a.body).filter(_ != a.slug).distinct
      val stored = ResearchState.Stored(
        a.modify(_.updatedAt).setTo(None),
        before.fold(UUID.randomUUID())(_.uid),
        now,
        links
      )
      versioned.modify(_.articles).using(_.updated(a.slug, stored))

    def put(article: Article, caller: Caller): String =
      val now = Instant.now(clock)
      state.updateAndGet(written(_, article, now))
      token(now)

    def patch(slug: String, patch: ArticlePatch, caller: Caller): Either[ApiError, String] =
      val now = Instant.now(clock)
      @tailrec def attempt(): Either[ApiError, String] =
        val st = state.get
        val outcome = st.articles.get(slug).map(loaded) match
          case None           => Left(Articles.notFound(slug))
          case Some(existing) =>
            Articles.precondition(existing, patch) match
              case Left(msg) => Left(ApiError(msg, Some(Write.CodePrecondition)))
              case Right(_)  =>
                Articles.applyPatch(existing, patch) match
                  case Left(msg)      => Left(ApiError(msg, Some(Write.CodeInvalid)))
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
    private def critiqueRefusal(reasons: List[CritiqueReason]): Option[ApiError] =
      val tidy = reasons.map(CritiqueReason.tidy)
      tidy.collectFirst { case Left(m) => ApiError(m, Some(Write.CodeInvalid)) }.orElse {
        tidy.collectFirst { case Right(r) =>
          ApiError(s"critique reason ${r.ref.wire}: '${r.src}' is not a citekey", Some(Write.CodeInvalid))
        }
      }

    def delete(slug: String, caller: Caller): Boolean =
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

    def rename(from: String, to: String, caller: Caller): Either[ApiError, List[String]] =
      val (oldSlug, newSlug) = (from.trim, to.trim)
      val now = Instant.now(clock)
      @tailrec def attempt(): Either[ApiError, List[String]] =
        val st = state.get
        val aliasOf = st.aliases.get(newSlug)
        val outcome =
          if !st.articles.contains(oldSlug) then Left(Articles.notFound(oldSlug))
          else if st.articles.contains(newSlug) then
            Left(ApiError(s"'$newSlug' is already the slug of another article", Some(Write.CodeInvalid)))
          else if aliasOf.exists(_ != oldSlug) then
            Left(
              ApiError(
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
        oldSlug: String,
        newSlug: String,
        now: Instant
    ): (ResearchState, List[String]) =
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
            val (body, nb) = ArticleLinks.retarget(a.body, to)
            val summary = a.summary.map(ArticleLinks.retarget(_, to))
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
