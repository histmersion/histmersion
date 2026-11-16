package com.histmersion
package domain.research.articles
import com.histmersion.util.problem.Problem
import com.histmersion.util.problem.Text.sentence

import com.histmersion.api.Write
import com.histmersion.domain.Caller
import com.histmersion.domain.research.{ResearchState, Slug}
import com.histmersion.domain.library.sources.CitekeyMillis
import com.histmersion.api.text.WikiLinks
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
import com.histmersion.domain.library.Citekey
import com.histmersion.domain.automation.Goal

/** Research's articles as records: one article with the links it makes and what links to it, written whole or merged
  * from a patch (its critique reasons beside it), renamed (the old slug kept, every `[[old]]` rewritten), deleted, and
  * its recorded versions. Slugs are taken as given; the controllers normalise them.
  */
trait ArticleService:

  /** The slug an old slug was renamed to, or the slug itself. */
  def canonical(slug: Slug): Slug

  /** The whole article as written, under its own slug (no old slug followed). */
  def load(slug: Slug): Option[Article]

  /** The article an old slug or its own names, with both directions of its link graph. */
  def view(requested: Slug): Option[Article.View]

  /** Whether an article has this slug, as stored. */
  def exists(slug: Slug): Boolean

  /** Every slug, in byte order. */
  def slugs: List[Slug]

  /** `slug -> its last write` in epoch milliseconds, for the seed's staleness guard. */
  def updatedAtMillis: Map[Slug, CitekeyMillis.Millis]

  /** The link view of the stored articles among `slugs`, in slug order. */
  def refs(slugs: List[Slug]): List[Article.Ref]

  /** The articles that cite a source, by title. */
  def backlinksForSource(citekey: Citekey): List[Backlink]

  /** The articles citing any of `citekeys` (each once, with its first cited key), at most `limit`. */
  def citing(citekeys: List[Citekey], limit: PageSize): List[EventArticleRef]

  /** The sources the article cites, a dangling citekey with no title. */
  def sourcesCited(slug: Slug): List[CitedSource]

  /** The versions of an article, newest first, at most twenty. */
  def versions(slug: Slug): List[Article.Version]

  def version(slug: Slug, id: ArticleRepo.VersionId): Option[Article.VersionFull]

  /** The newest saved version of an article: after a write, the one holding the text it replaced. */
  def latestVersion(slug: Slug): Option[ArticleRepo.VersionId]

  /** The article's stored critique reasons, by pair. */
  def critiqueReasons(slug: Slug): List[CritiqueReason]

  /** Every goal's linked articles that exist: goal → slugs. */
  def articlesByGoal: Map[Goal.Slug, List[Slug]]

  /** Replace everything under `article.slug`; the new last-write token. */
  def put(article: Article, caller: Caller): Article.UpdatedAt

  /** Merge `patch` into the article `slug`, its critique reasons beside it; the new last-write token. */
  def patch(slug: Slug, patch: Article.Patch, caller: Caller): Either[Api.Error, Article.UpdatedAt]

  def delete(slug: Slug, caller: Caller): Boolean

  /** A new slug, the old one kept as an alias, every `[[old]]` rewritten; the articles rewritten. */
  def rename(from: Slug, to: Slug, caller: Caller): Either[Api.Error, List[Slug]]

object ArticleService:
  def apply(db: Transactor): ArticleService = Implementation(db)

  final private class Implementation(db: Transactor) extends ArticleService:
    def canonical(slug: Slug): Slug = db.read(ArticleRepo.canonical(slug))
    def load(slug: Slug): Option[Article] = db.read(ArticleRepo.load(slug))
    def view(requested: Slug): Option[Article.View] = db.read(ArticleRepo.view(requested))
    def exists(slug: Slug): Boolean = db.read(ArticleRepo.slugExists(slug))
    def slugs: List[Slug] = db.read(ArticleRepo.slugs).toList
    def updatedAtMillis: Map[Slug, CitekeyMillis.Millis] = db.read(ArticleRepo.updatedAtMillis)
    def refs(slugs: List[Slug]): List[Article.Ref] = db.read(ArticleRepo.refs(slugs))
    def backlinksForSource(citekey: Citekey): List[Backlink] = db.read(ArticleRepo.backlinksForSource(citekey))
    def citing(citekeys: List[Citekey], limit: PageSize): List[EventArticleRef] =
      if citekeys.isEmpty then Nil else db.read(ArticleRepo.citing(citekeys, limit))
    def sourcesCited(slug: Slug): List[CitedSource] = db.read(ArticleRepo.sourcesCited(slug))
    def versions(slug: Slug): List[Article.Version] = db.read(ArticleRepo.listVersions(slug))
    def version(slug: Slug, id: ArticleRepo.VersionId): Option[Article.VersionFull] =
      db.read(ArticleRepo.getVersion(slug, id))
    def latestVersion(slug: Slug): Option[ArticleRepo.VersionId] = db.read(ArticleRepo.latestVersion(slug))
    def critiqueReasons(slug: Slug): List[CritiqueReason] = db.read(CritiqueRepo.list(slug))
    def articlesByGoal: Map[Goal.Slug, List[Slug]] = db.read(ArticleRepo.articlesByGoal)
    def put(article: Article, caller: Caller): Article.UpdatedAt = db.transact(ArticleRepo.put(article))
    def patch(slug: Slug, patch: Article.Patch, caller: Caller): Either[Api.Error, Article.UpdatedAt] =
      db.transact(ArticleRepo.patch(slug, patch))
    def delete(slug: Slug, caller: Caller): Boolean = db.transact(ArticleRepo.delete(slug))
    def rename(from: Slug, to: Slug, caller: Caller): Either[Api.Error, List[Slug]] =
      db.transact(ArticleRepo.rename(from, to))

  /** Over the articles of `state`, as a database holding only research's rows answers: no source is stored, so every
    * link is an article link, nothing cites a source and a critique reason names no citekey; no goal links an article.
    * `clock` stands in for the database's `now()`.
    */
  final class InMemory(state: AtomicReference[ResearchState], clock: Clock) extends ArticleService:

    private val Stamp = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'").withZone(ZoneOffset.UTC)
    private def stamp(at: Instant): String = Stamp.format(at)
    private def token(at: Instant): Article.UpdatedAt = Article.UpdatedAt(stamp(at))

    private def articleKey(slug: Slug): ResearchState.ArticlesKey = ResearchState.ArticlesKey(slug.value)
    private def aliasKey(slug: Slug): ResearchState.AliasesKey = ResearchState.AliasesKey(slug.value)
    private def critiqueKey(slug: Slug): ResearchState.CritiquesKey = ResearchState.CritiquesKey(slug.value)
    private def ledgerKey(slug: Slug): ResearchState.LedgersKey = ResearchState.LedgersKey(slug.value)
    private def slugOf(key: ResearchState.ArticlesKey): Slug = Slug.assume(key.value)

    private def canonicalIn(st: ResearchState, slug: Slug): Slug =
      if st.articles.contains(articleKey(slug)) then slug
      else st.aliases.get(aliasKey(slug)).fold(slug)(a => Slug.assume(a.value))

    private def loaded(stored: ResearchState.Stored): Article =
      stored.article.modify(_.updatedAt).setTo(Some(token(stored.updatedAt)))

    def canonical(slug: Slug): Slug = canonicalIn(state.get, slug)
    def load(slug: Slug): Option[Article] = state.get.articles.get(articleKey(slug)).map(loaded)

    def view(requested: Slug): Option[Article.View] =
      val st = state.get
      val slug = canonicalIn(st, requested)
      st.articles.get(articleKey(slug)).map { stored =>
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
          .filter(o => o.article.slug.value != slug.value && o.links.exists(_.value == slug.value))
          .map(o =>
            Backlink(
              Backlink.Kind("article"),
              Backlink.Id(o.article.slug.value),
              Some(Backlink.Title(o.article.title.value))
            )
          )
          .sortBy(_.id.value)
        Article.View(row, backlinks, Nil)
      }

    def exists(slug: Slug): Boolean = state.get.articles.contains(articleKey(slug))
    def slugs: List[Slug] = state.get.articles.keys.toList.sorted.map(slugOf)
    def updatedAtMillis: Map[Slug, CitekeyMillis.Millis] =
      state.get.articles.map((k, v) => slugOf(k) -> CitekeyMillis.Millis(v.updatedAt.toEpochMilli))

    def refs(slugs: List[Slug]): List[Article.Ref] =
      val st = state.get
      slugs.distinct.sorted
        .flatMap(s =>
          st.articles
            .get(articleKey(s))
            .map(o => Article.Ref(Article.Slug(s.value), o.article.title, o.article.summary))
        )

    def backlinksForSource(citekey: Citekey): List[Backlink] = Nil
    def citing(citekeys: List[Citekey], limit: PageSize): List[EventArticleRef] = Nil
    def sourcesCited(slug: Slug): List[CitedSource] = Nil

    def versions(slug: Slug): List[Article.Version] =
      state.get.versions
        .filter(_.slug.value == slug.value)
        .sortBy(v => (-v.createdAt.toEpochMilli, -v.id.value))
        .take(20)
        .map(v =>
          Article.Version(
            Article.Version.Id(v.id.value),
            Article.Slug(v.slug.value),
            v.summary.map(sm => Article.SummaryText(sm.value.take(200))),
            Article.Version.UpdatedAt(stamp(v.updatedAt)),
            Article.Version.CreatedAt(stamp(v.createdAt)),
            Article.Version.WordCount(wordCount(v.body.value))
          )
        )
        .toList

    /** The words the database counts: `trim` drops spaces only, and a split keeps the empty ends. */
    private def wordCount(body: String): Int =
      body.dropWhile(_ == ' ').reverse.dropWhile(_ == ' ').reverse.split("\\s+", -1).length

    def version(slug: Slug, id: ArticleRepo.VersionId): Option[Article.VersionFull] =
      state.get.versions
        .find(v => v.slug.value == slug.value && v.id.value == id.value)
        .map(v =>
          Article.VersionFull(
            Article.Version.Id(v.id.value),
            Article.Slug(v.slug.value),
            Article.Body(v.body.value),
            v.summary.map(sm => Article.SummaryText(sm.value)),
            Article.VersionFull.UpdatedAt(stamp(v.updatedAt)),
            Article.VersionFull.CreatedAt(stamp(v.createdAt))
          )
        )

    def latestVersion(slug: Slug): Option[ArticleRepo.VersionId] =
      state.get.versions.filter(_.slug.value == slug.value).map(_.id.value).maxOption.map(ArticleRepo.VersionId(_))

    def critiqueReasons(slug: Slug): List[CritiqueReason] = state.get.critiques.getOrElse(critiqueKey(slug), Nil)
    def articlesByGoal: Map[Goal.Slug, List[Slug]] = Map.empty

    /** The write path: the previous body and summary kept as a version, the row replaced, its links recomputed. */
    private def written(st: ResearchState, a: Article, now: Instant): ResearchState =
      val key = ResearchState.ArticlesKey(a.slug.value)
      val before = st.articles.get(key)
      val versioned = before.fold(st)(b =>
        st.modify(_.versions)
          .using(
            _ :+ ResearchState.Replaced(
              ResearchState.Replaced.Id(st.nextVersion.value),
              ResearchState.Replaced.Slug(a.slug.value),
              ResearchState.Replaced.Body(b.article.body.value),
              b.article.summary.map(sm => ResearchState.Replaced.Summary(sm.value)),
              b.updatedAt,
              now
            )
          )
          .modify(_.nextVersion)
          .using(n => ResearchState.NextVersion(n.value + 1))
      )
      val links = Article.Links
        .targets(WikiLinks.Body(a.body.value))
        .filter(_.value != a.slug.value)
        .distinct
        .map(t => ResearchState.Stored.Links(t.value))
      val stored = ResearchState.Stored(
        a.modify(_.updatedAt).setTo(None),
        before.fold(UUID.randomUUID())(_.uid),
        now,
        links
      )
      versioned.modify(_.articles).using(_.updated(key, stored))

    def put(article: Article, caller: Caller): Article.UpdatedAt =
      val now = Instant.now(clock)
      state.updateAndGet(written(_, article, now))
      token(now)

    def patch(slug: Slug, patch: Article.Patch, caller: Caller): Either[Api.Error, Article.UpdatedAt] =
      val now = Instant.now(clock)
      @tailrec def attempt(): Either[Api.Error, Article.UpdatedAt] =
        val st = state.get
        val outcome = st.articles.get(articleKey(slug)).map(loaded) match
          case None           => Left(Articles.notFound(Article.Slug(slug.value)))
          case Some(existing) =>
            Articles.precondition(existing, patch) match
              case Left(msg) => Left(refused(msg, Write.CodePrecondition))
              case Right(_)  =>
                Articles.applyPatch(existing, patch) match
                  case Left(msg)      => Left(refused(msg, Write.CodeInvalid))
                  case Right(updated) =>
                    critiqueRefusal(patch.critiqueReasons) match
                      case Some(refusal) => Left(refusal)
                      case None          =>
                        val next = if patch.onlyCritiques then st else written(st, updated, now)
                        Right(
                          next -> next.articles
                            .get(articleKey(slug))
                            .fold(Article.UpdatedAt(""))(s => token(s.updatedAt))
                        )
        outcome match
          case Left(e)                => Left(e)
          case Right((next, stamped)) => if state.compareAndSet(st, next) then Right(stamped) else attempt()
      attempt()

    private def refused(problem: Problem, code: String): Api.Error = Api.Error(problem, Some(Api.Error.Code(code)))

    /** A reason names its two sources by citekey; with no source stored, the first reason is refused as the database
      * refuses it (its text checked first).
      */
    private def critiqueRefusal(reasons: List[CritiqueReason]): Option[Api.Error] =
      val tidy = reasons.map(CritiqueReason.tidy)
      tidy.collectFirst { case Left(m) => refused(m, Write.CodeInvalid) }.orElse {
        tidy.collectFirst { case Right(r) =>
          refused(
            Problem.Invalid.Unknown(
              Problem.TypeName.of(Citekey),
              Problem.Raw(s"${r.src}"),
              Vector.empty,
              Vector(Problem.Field("reasons"), Problem.Field(s"${r.ref.wire}"))
            ),
            Write.CodeInvalid
          )
        }
      }

    def delete(slug: Slug, caller: Caller): Boolean =
      val before = state.getAndUpdate { st =>
        if !st.articles.contains(articleKey(slug)) then st
        else
          st.modify(_.articles)
            .using(_ - articleKey(slug))
            .modify(_.versions)
            .using(_.filterNot(_.slug.value == slug.value))
            .modify(_.aliases)
            .using(_.filterNot(_._2.value == slug.value))
            .modify(_.critiques)
            .using(_ - critiqueKey(slug))
            .modify(_.ledgers)
            .using(_ - ledgerKey(slug))
      }
      before.articles.contains(articleKey(slug))

    def rename(from: Slug, to: Slug, caller: Caller): Either[Api.Error, List[Slug]] =
      val (oldSlug, newSlug) = (from, to)
      val now = Instant.now(clock)
      @tailrec def attempt(): Either[Api.Error, List[Slug]] =
        val st = state.get
        val aliasOf = st.aliases.get(aliasKey(newSlug))
        val outcome =
          if !st.articles.contains(articleKey(oldSlug)) then Left(Articles.notFound(Article.Slug(oldSlug.value)))
          else if st.articles.contains(articleKey(newSlug)) then
            Left(
              refused(
                Problem.Conflict.AlreadyExists(Problem.TypeName.of(Slug), Problem.Raw.of(newSlug)),
                Write.CodeInvalid
              )
            )
          else if aliasOf.exists(_.value != oldSlug.value) then
            Left(
              refused(
                Problem.Conflict
                  .Aliased(Problem.TypeName.of(Slug), Problem.Raw.of(newSlug), Problem.Raw(s"${aliasOf.get}")),
                Write.CodeInvalid
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
        oldSlug: Slug,
        newSlug: Slug,
        now: Instant
    ): (ResearchState, List[Slug]) =
      val stored = st.articles(articleKey(oldSlug))
      val moved = st
        .modify(_.aliases)
        .using(_ - aliasKey(newSlug))
        .modify(_.articles)
        .using(as =>
          (as - articleKey(oldSlug))
            .updated(
              articleKey(newSlug),
              stored.modify(_.article).using(_.modify(_.slug).setTo(Article.Slug(newSlug.value)))
            )
            .map { (s, o) =>
              s -> o
                .modify(_.links)
                .using(_.map(t => if t.value == oldSlug.value then ResearchState.Stored.Links(newSlug.value) else t))
            }
        )
        .modify(_.versions)
        .using(
          _.map(v =>
            if v.slug.value == oldSlug.value then v.modify(_.slug).setTo(ResearchState.Replaced.Slug(newSlug.value))
            else v
          )
        )
        .modify(_.critiques)
        .using(cs =>
          cs.get(critiqueKey(oldSlug)).fold(cs)(rs => (cs - critiqueKey(oldSlug)).updated(critiqueKey(newSlug), rs))
        )
        .modify(_.ledgers)
        .using(ls => ls.get(ledgerKey(oldSlug)).fold(ls)(l => (ls - ledgerKey(oldSlug)).updated(ledgerKey(newSlug), l)))
      val to: WikiLinks.Occurrence.Target => Option[WikiLinks.Occurrence.Target] =
        t => Option.when(t.value == oldSlug.value)(WikiLinks.Occurrence.Target(newSlug.value))
      val needle = oldSlug.value.toLowerCase
      val rewritten = moved.articles.keys.toList.sorted.filter { s =>
        val a = moved.articles(s).article
        (a.body.value + " " + a.summary
          .fold("")(_.value) + " " + a.reasons.map((k, v) => s"$k $v").mkString(" ")).toLowerCase
          .contains(needle)
      }
      @tailrec def loop(
          rest: List[ResearchState.ArticlesKey],
          acc: ResearchState,
          done: List[Slug]
      ): (ResearchState, List[Slug]) =
        rest match
          case Nil       => (acc, done.reverse)
          case s :: tail =>
            val a = loaded(acc.articles(s))
            val (body, nb) = Article.Links.retarget(WikiLinks.Body(a.body.value), to)
            val summary = a.summary.map(sm => Article.Links.retarget(WikiLinks.Body(sm.value), to))
            val reasons = a.reasons.map((ck, r) =>
              to(WikiLinks.Occurrence.Target(ck.value)).fold(ck)(t => com.histmersion.api.library.Citekey(t.value)) -> r
            )
            val count = nb.value + summary.fold(0)(_._2.value) +
              a.reasons.keys.count(ck => to(WikiLinks.Occurrence.Target(ck.value)).isDefined)
            if count == 0 then loop(tail, acc, done)
            else
              val edited =
                a.modify(_.body)
                  .setTo(Article.Body(body.value))
                  .modify(_.summary)
                  .setTo(summary.map(sm => Article.SummaryText(sm._1.value)))
                  .modify(_.reasons)
                  .setTo(reasons)
              val next = written(acc, edited, now)
              loop(tail, next, slugOf(s) :: done)
      val (after, done) = loop(rewritten, moved, Nil)
      (after.modify(_.aliases).using(_.updated(aliasKey(oldSlug), ResearchState.AliasesValue(newSlug.value))), done)
