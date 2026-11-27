package com.histmersion
package domain.research.articles
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.util.json.RawJson.*
import hearth.kindlings.jsoniterjson.Json
import com.histmersion.util.problem.Problem
import com.histmersion.util.problem.Text.sentence

import hearth.kindlings.optics.syntax.*

import com.histmersion.domain.library.sources.CitekeyResolver
import com.histmersion.domain.library.search.FilterSql
import com.histmersion.domain.library.search.Search

import com.histmersion.domain.catalogue.places.PlaceScope

import com.histmersion.util.database.{joined, RangeSql, Timestamps}

import com.histmersion.api.Write
import com.histmersion.util.database.Jsonb
import com.histmersion.util.database.given

import com.augustnagro.magnum.*
import com.histmersion.util.database.NewtypeColumns.given
import com.histmersion.api.catalogue.{EventArticleRef, Period}
import com.histmersion.api.library.Filter
import com.histmersion.api.library.SourceColumns.given
import com.histmersion.api.research.{Article, Articles, Backlink, CitedSource, Coverage}
import com.histmersion.api.Api
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.library.Citekey
import com.histmersion.domain.research.Slug
import com.histmersion.api.text.WikiLinks
import com.histmersion.api.catalogue.Year
import com.histmersion.api.personal.UnreadPage
import com.histmersion.domain.library.sources.CitekeyMillis
import com.histmersion.domain.automation.Goal
import com.histmersion.util.database.Unused
import com.histmersion.domain.{CreatedAt, Creator}
import com.histmersion.util.database.StoredCreator
import com.histmersion.api.automation.Creation
import com.histmersion.lint.{atBoundary, tolerated, BoundaryKind, LintRule}

/** Magnum codecs for the article wire DTOs (the types live in `core`, which knows nothing about JDBC). */
@tolerated(LintRule.Derives, "pending G18 (derives instead of hand-written givens)")
@tolerated(LintRule.Direction, "pending G12 (a domain names only lower places)")
@tolerated(LintRule.Layer, "pending G7 (separate types per layer)")
@atBoundary(BoundaryKind.Sql, "reads and writes the article tables and their row codecs")
given DbCodec[Backlink] = DbCodec.derived
@tolerated(LintRule.Derives, "pending G18 (derives instead of hand-written givens)")
given given_DbCodec_ArticleVersion: DbCodec[Article.Version] = DbCodec.derived
@tolerated(LintRule.Derives, "pending G18 (derives instead of hand-written givens)")
given given_DbCodec_ArticleVersionFull: DbCodec[Article.VersionFull] = DbCodec.derived

/** The ARTICLES store (plan §5.2 stream 2.7) — the compiled wiki.
  *
  * Every write also rewrites that article's rows in `article_links`, from the `[[…]]` links in its body: the
  * classification (source vs article) is the pure [[ArticleLinks.classify]] fed by ONE query asking which of the
  * citekey-shaped targets actually exist. That is what makes `backlinks` and `sourcesCited` index lookups rather than a
  * LIKE scan of every article.
  *
  * Every method runs on the connection it is given; the service opens the transaction. Private to research but for the
  * bundle import's rename and link rewrite (`BundleImport`), which research's carried kinds take over.
  */
private[articles] object ArticleRepo:
  type Count = Count.T
  object Count extends Newtype[Long, Pure]
  type Limit = Limit.T
  object Limit extends Newtype[Int, Pure]
  type VersionId = VersionId.T
  object VersionId extends Newtype[Long, Pure]

  @tolerated(LintRule.Derives, "pending G18 (derives instead of hand-written givens)")
  private given DbCodec[Backlink] = DbCodec.derived
  @tolerated(LintRule.Derives, "pending G18 (derives instead of hand-written givens)")
  private given DbCodec[Article.Version] = DbCodec.derived
  @tolerated(LintRule.Derives, "pending G18 (derives instead of hand-written givens)")
  private given DbCodec[Article.VersionFull] = DbCodec.derived
  private given DbCodec[Creation] = Jsonb.codec(Creation(Creation.Unreadable))
  @tolerated(LintRule.Derives, "pending G18 (derives instead of hand-written givens)")
  private given DbCodec[Article.Row] = DbCodec.derived
  @tolerated(LintRule.Derives, "pending G18 (derives instead of hand-written givens)")
  private given DbCodec[CitedSource] = DbCodec.derived
  @tolerated(LintRule.Derives, "pending G18 (derives instead of hand-written givens)")
  private given DbCodec[Article.Ref] = DbCodec.derived

  /** `\` escapes `%`/`_` in every ILIKE here, as in [[FilterSql]] (whose copy is private). */
  private val Esc = sql" ESCAPE '\\'"

  /** `updated_at` rendered UTC-normalised: stable to compare, safe to echo back as `ifUpdatedAt`. */
  private val Token = Timestamps.iso(sql"a.updated_at")

  /** One projection per shape, reused — no duplicated column lists. The row and the domain shape read the same columns;
    * `text[]` arrives as a list in both.
    */
  private val SummaryCols =
    sql"a.slug, a.title, a.kind, a.summary, a.tags, a.regions, ${RangeSql.cols(sql"a.period")}, $Token"
  private val RowCols =
    sql"a.slug, a.title, a.kind, a.body, a.summary, a.tags, a.regions, ${RangeSql.cols(sql"a.period")}, $Token"

  /** [[ArticleRow]], the wire shape: the row plus its uid (V030). */
  private val ViewCols = sql"$RowCols, a.uid, a.creator::text, ${StoredCreator.at(sql"a.created_at")}"

  /** [[FullArticleRow]], the domain read: the row plus its recorded filter and reasons (V066), as JSON text. */
  private val LoadCols = sql"$RowCols, a.coverage_filter::text, a.coverage_reasons::text"

  /** The filter and the reasons as the jsonb columns store them. */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  private def filterJson(a: Article): Option[String] =
    a.filter.map(_.asJson.withoutNulls.noSpaces)
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  private def reasonsJson(a: Article): String =
    Json
      .obj(a.reasons.toList.sortBy(_._1.value).map((k, v) => k.value -> Json.fromString(s"$v"))*)
      .noSpaces

  /** The stored JSON back; a value that no longer decodes (an older shape) reads as none, never fails the read. */
  private[articles] def filterOf(json: Option[String]): Option[Article.Filter] =
    json.flatMap(j => Jsonb.read[Article.Filter](Jsonb.JsonText(j)))
  private[articles] def reasonsOf(json: String): Map[Citekey, Coverage.Rationale] =
    Jsonb
      .read[Map[String, String]](Jsonb.JsonText(json))
      .getOrElse(Map.empty)
      .map((ck, reason) => Citekey(ck) -> Coverage.Rationale(s"$reason"))

  // ── writes ─────────────────────────────────────────────────────────────────────────────────
  /** Full replace (`put_article`). Returns the fresh `updatedAt` concurrency token. */
  def put(a: Article)(using DbCon)(using Creator.New, CreatedAt): Article.UpdatedAt =
    write(a)
    Article.UpdatedAt(token(a.slug.value).getOrElse(""))

  /** Selective merge (`patch_article`): read → [[Articles.applyPatch]] → write, all in ONE transaction, so two
    * concurrent patches cannot interleave and `ifUpdatedAt` means something.
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  def patch(slug: Slug, p: Article.Patch, by: CritiqueRepo.By = CritiqueRepo.By("person"))(using
      DbCon
  )(using Creator.New, CreatedAt): Either[Api.Error, Article.UpdatedAt] =
    // FOR UPDATE before the read: `ifUpdatedAt` is a compare-and-swap, and without the row lock
    // two concurrent patches both saw the token they were given and both "succeeded", the second
    // discarding the first. See the twin comment in `SourceRepo.patch`.
    val _ = sql"SELECT slug FROM articles WHERE slug = $slug FOR UPDATE".query[String].run()
    load(slug) match
      case None           => Left(Articles.notFound(Article.Slug(slug.value)))
      case Some(existing) =>
        Articles.precondition(existing, p) match
          case Left(msg) => Left(Api.Error(msg, Some(Api.Error.Code(Write.CodePrecondition))))
          case Right(_)  =>
            Articles.applyPatch(existing, p) match
              case Left(msg) => Left(Api.Error(msg, Some(Api.Error.Code(Write.CodeInvalid))))
              // the critique reasons (V091) sit beside the row: checked first, so a refused one
              // writes nothing; a patch of them alone leaves the article, its versions and its
              // token as they are (`by`: who set them, when a reason does not say)
              case Right(updated) =>
                CritiqueRepo
                  .applyIn(slug, p.critiqueReasons, by)(if !p.onlyCritiques then write(updated))
                  .map(_ => Article.UpdatedAt(token(slug.value).getOrElse("")))

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def delete(slug: Slug)(using DbCon): Boolean =
    sql"DELETE FROM articles WHERE slug = $slug".update.run() > 0

  /** The write itself, inside the caller's transaction: snapshot the previous body into `article_versions` (when the
    * article already exists), then upsert the row and its link graph.
    */
  private def write(a: Article)(using DbCon)(using creator: Creator.New, at: CreatedAt): Unit =
    // Snapshot the PREVIOUS version before the update overwrites it.
    Unused(
      sql"""INSERT INTO article_versions (slug, body, summary, updated_at, creator, created_at)
          SELECT slug, body, summary, updated_at, ${StoredCreator.column(
          creator
        )}::jsonb, $at::timestamptz FROM articles WHERE slug = ${a.slug}""".update.run(),
      Unused.Why.Idempotent
    )
    val periodLit = RangeSql.literal(a.period)
    val filter = filterJson(a)
    val reasons = reasonsJson(a)
    sql"""INSERT INTO articles (slug, title, kind, body, summary, tags, period, regions, coverage_filter, coverage_reasons, creator,
                                created_at)
          VALUES (${a.slug}, ${a.title}, ${a.kind}, ${a.body}, ${a.summary},
                  ${a.tags}::text[], $periodLit::int8range, ${a.regions}::text[], $filter::jsonb, $reasons::jsonb, ${StoredCreator
        .column(creator)}::jsonb, $at::timestamptz)
          ON CONFLICT (slug) DO UPDATE SET
            title=EXCLUDED.title, kind=EXCLUDED.kind, body=EXCLUDED.body, summary=EXCLUDED.summary,
            tags=EXCLUDED.tags, period=EXCLUDED.period, regions=EXCLUDED.regions,
            coverage_filter=EXCLUDED.coverage_filter, coverage_reasons=EXCLUDED.coverage_reasons,
            updated_at=now()""".update.run()
    linkRows(a.slug.value, a.body.value)

  /** Rewrite one article's outgoing links. A `[[x]]` is a SOURCE link iff x is citekey-shaped AND a source with that
    * citekey exists — resolved with one query over the citekey-shaped candidates.
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  private[histmersion] def writeLinks(slug: Slug, body: BArticleRow.Body)(using DbCon): Unit =
    linkRows(slug.value, body.value)

  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  private def linkRows(slug: String, body: String)(using DbCon): Unit =
    Unused(sql"DELETE FROM article_links WHERE src_slug = $slug".update.run(), Unused.Why.Idempotent)
    val written =
      Article.Links.targets(WikiLinks.Body(body)).filter(_.value != slug) // an article does not link to itself
    if written.nonEmpty then
      // an OLD citekey (V027) links the source it was renamed to, and is recorded under the new one
      // so `backlinksForSource` finds it
      val known: Map[String, String] = CitekeyResolver.stored
        .canonical(
          written
            .filter(t => Article.Links.isCitekeyShaped(t.value))
            .flatMap(t => domain.library.Citekey.either(t.value).toOption)
        )
        .map((from, to) => from.value -> to.value)
      val targets = written.map(t => known.get(t.value).fold(t)(Article.Link.Target(_))).distinct
      val links = Article.Links.classify(targets, t => known.values.toSet.contains(t.value))
      Unused(
        batchUpdate(links)(l => sql"""INSERT INTO article_links (src_slug, target_kind, target)
              VALUES ($slug, ${l.targetKind}, ${l.target}) ON CONFLICT DO NOTHING""".update),
        Unused.Why.Idempotent
      )

  /** Retarget wikilinks in every article whose body or summary mentions `needle` (case-insensitive) — the article half
    * of a citekey rename — and the coverage reasons (V066) keyed by a moved citekey. Each changed article goes through
    * [[write]], so the previous text is kept in `article_versions` and `article_links` is recomputed. Returns (slug,
    * links moved) for each article that changed.
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  private[histmersion] def retargetLinks(
      needle: Article.Link.Target,
      to: Article.Link.Target => Option[Article.Link.Target]
  )(using
      DbCon
  )(using Creator.New, CreatedAt): List[(Slug, RowCount)] =
    sql"""SELECT slug FROM articles
          WHERE strpos(lower(body || ' ' || coalesce(summary, '') || ' ' || coverage_reasons::text), ${needle.value.toLowerCase}) > 0
          ORDER BY slug""".query[Slug].run().toList.flatMap { slug =>
      load(slug).flatMap { a =>
        val (body, nb) = Article.Links.retarget(WikiLinks.Body(a.body.value), to)
        val summary = a.summary.map(s => Article.Links.retarget(WikiLinks.Body(s.value), to))
        val reasons = a.reasons.map((ck, r) => to(Article.Link.Target(ck.value)).fold(ck)(t => Citekey(t.value)) -> r)
        val nr = a.reasons.keys.count(ck => to(Article.Link.Target(ck.value)).isDefined)
        val moved = nb.value + summary.fold(0)(_._2.value) + nr
        Option.when(moved > 0) {
          write(
            a.modify(_.body)
              .setTo(Article.Body(body.value))
              .modify(_.summary)
              .setTo(summary.map(s => Article.SummaryText(s._1.value)))
              .modify(_.reasons)
              .setTo(reasons)
          )
          slug -> RowCount(moved)
        }
      }
    }

  /** Rename an article (V042), inside the caller's transaction: the slug moves with one UPDATE (its versions and
    * outgoing links cascade), the link rows that point at it follow, every `[[old]]` in other articles is rewritten
    * through the write path (so each rewrite is a recorded version), and `old` becomes an alias that keeps resolving.
    * Refused when `newSlug` is another article's slug or still an old slug of another one. What a bundle import does
    * when an article was renamed on the other side, and what `merge apply` does to keep two articles apart. Returns the
    * articles whose links moved.
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  private[histmersion] def renameIn(oldSlug: Slug, newSlug: Slug)(using
      DbCon
  )(using Creator.New, CreatedAt): Either[Api.Error, List[Slug]] =
    def exists(s: Slug) = sql"SELECT 1 FROM articles WHERE slug = $s".query[Int].run().nonEmpty
    val aliasOf = sql"SELECT slug FROM article_aliases WHERE alias = $newSlug"
      .query[Slug]
      .run()
      .headOption
    if !exists(oldSlug) then Left(Articles.notFound(Article.Slug(oldSlug.value)))
    else if exists(newSlug) then
      Left(
        Api.Error(
          Problem.Conflict.AlreadyExists(Problem.TypeName.of(Slug), Problem.Raw.of(newSlug)),
          Some(Api.Error.Code(Write.CodeInvalid))
        )
      )
    else if aliasOf.exists(_ != oldSlug) then
      Left(
        Api.Error(
          Problem.Conflict.Aliased(Problem.TypeName.of(Slug), Problem.Raw.of(newSlug), Problem.Raw.of(aliasOf.get)),
          Some(Api.Error.Code(Write.CodeInvalid))
        )
      )
    else
      Unused(sql"DELETE FROM article_aliases WHERE alias = $newSlug".update.run(), Unused.Why.Idempotent)
      Unused(sql"UPDATE articles SET slug = $newSlug WHERE slug = $oldSlug".update.run(), Unused.Why.Held)
      Unused(
        sql"""UPDATE article_links SET target = $newSlug
            WHERE target_kind = 'article' AND target = $oldSlug""".update.run(),
        Unused.Why.Idempotent
      )
      val moved = retargetLinks(
        Article.Link.Target(oldSlug.value),
        t => Option.when(t.value == oldSlug.value)(Article.Link.Target(newSlug.value))
      )
      sql"""INSERT INTO article_aliases (alias, slug) VALUES ($oldSlug, $newSlug)
            ON CONFLICT (alias) DO UPDATE SET slug = EXCLUDED.slug""".update.run()
      // a goal linked to the article follows it (goal_links has no foreign key: a link may name a
      // record that is gone)
      Unused(
        sql"""UPDATE goal_links SET ref = $newSlug WHERE kind = 'article' AND ref = $oldSlug
            AND NOT EXISTS (SELECT 1 FROM goal_links g2 WHERE g2.goal_slug = goal_links.goal_slug
                            AND g2.kind = 'article' AND g2.ref = $newSlug)""".update.run(),
        Unused.Why.Idempotent
      )
      Right(moved.map(_._1))

  /** `histmersion articles rename`: a new slug, the old one kept as an alias (V042); every `[[old]]` in other articles
    * is rewritten through the write path. One transaction; returns the articles rewritten.
    */
  def rename(oldSlug: Slug, newSlug: Slug)(using
      DbCon
  )(using
      Creator.New,
      CreatedAt
  ): Either[Api.Error, List[Slug]] =
    renameIn(oldSlug, newSlug)

  /** The slug an old slug was renamed to (`article_aliases`), or the slug itself. */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def canonical(slug: Slug)(using DbCon): Slug =
    sql"SELECT slug FROM article_aliases WHERE alias = $slug AND NOT EXISTS (SELECT 1 FROM articles WHERE slug = $slug)"
      .query[Slug]
      .run()
      .headOption
      .getOrElse(slug)

  private def token(slug: String)(using DbCon): Option[String] =
    sql"SELECT $Token FROM articles a WHERE a.slug = $slug"
      .query[String]
      .run()
      .headOption

  // ── reads ──────────────────────────────────────────────────────────────────────────────────
  /** The whole article as the DOMAIN model — the input side of a patch, and what `export` renders. */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  def load(slug: Slug)(using DbCon): Option[Article] =
    sql"SELECT $LoadCols FROM articles a WHERE a.slug = $slug"
      .query[FullArticleRow]
      .run()
      .headOption
      .map { r =>
        Article(
          slug = Article.Slug(r.slug.value),
          title = Article.Title(r.title.value),
          kind = Article.KindValue(r.kind.value),
          body = Article.Body(r.body.value),
          summary = r.summary.map(v => Article.SummaryText(v.value)),
          tags = r.tags.map(v => Article.Tags(v.value)),
          regions = r.regions.map(v => Article.Regions(v.value)),
          period = (r.periodStart, r.periodEnd) match
            case (Some(a), Some(b)) =>
              Some(Period(Year(a.value.toLong), Year(b.value.toLong)))
            case _ => None,
          updatedAt = Some(Article.UpdatedAt(r.updatedAt.value)),
          filter = filterOf(r.filter.map(_.value)),
          reasons = reasonsOf(r.reasons.value)
        )
      }

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def get(slug: Slug)(using DbCon): Option[Article.Row] =
    sql"SELECT $ViewCols FROM articles a WHERE a.slug = ${canonical(slug)}"
      .query[Article.Row]
      .run()
      .headOption

  /** The article plus both directions of its link graph — one connection, three queries. */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def view(requested: Slug)(using DbCon): Option[Article.View] =
    val slug = canonical(requested) // an old slug (V042) shows the article it was renamed to
    sql"SELECT $ViewCols FROM articles a WHERE a.slug = $slug"
      .query[Article.Row]
      .run()
      .headOption
      .map(row => Article.View(row, backlinks(slug), sourcesCited(slug)))

  /** What points HERE: other articles through `article_links`, and sources whose agent-written `notes` contain a
    * `[[slug]]` (the source notes are Markdown, not a link table). `strpos` rather than `LIKE` so an underscore in a
    * slug stays a literal underscore.
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def backlinks(slug: Slug)(using DbCon): List[Backlink] =
    sql"""SELECT 'article' AS kind, a.slug AS id, a.title
          FROM article_links l JOIN articles a ON a.slug = l.src_slug
          WHERE l.target_kind = 'article' AND l.target = $slug AND a.slug <> $slug
          UNION ALL
          SELECT 'source' AS kind, s.citekey AS id, s.title
          FROM sources s
          WHERE strpos(coalesce(s.notes, ''), '[[' || $slug || ']]') > 0
             OR strpos(coalesce(s.notes, ''), '[[' || $slug || '|') > 0
          ORDER BY 1, 2""".query[Backlink].run().toList

  /** The articles citing any of `citekeys` (one row per article, its first cited key), at most `limit` — an event's
    * card lists those citing the sources that attest it.
    */
  def citing(citekeys: List[domain.library.Citekey], limit: PageSize)(using
      DbCon
  ): List[EventArticleRef] =
    if citekeys.isEmpty then Nil
    else
      sql"""SELECT DISTINCT ON (a.slug) a.slug, a.title, l.target
            FROM article_links l JOIN articles a ON a.slug = l.src_slug
            WHERE l.target_kind = 'source' AND l.target = ANY($citekeys::text[])
            ORDER BY a.slug, l.target LIMIT $limit"""
        .query[(Article.Slug, Article.Title, Citekey)]
        .run()
        .toList
        .map(EventArticleRef.apply)

  /** The other direction, for a SOURCE: which compiled articles cite it (`article_links` rows with
    * `target_kind = 'source'`). An index lookup, like [[backlinks]], because the classification was done at write time
    * — no LIKE scan of every article body.
    *
    * Reuses [[Backlink]] deliberately: "something that points at me, with a kind, an id and a title" is the same shape
    * whichever end you stand at, and a second near-identical DTO would only give the two sides a way to drift.
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def backlinksForSource(citekey: domain.library.Citekey)(using DbCon): List[Backlink] =
    sql"""SELECT 'article' AS kind, a.slug AS id, a.title
            FROM article_links l JOIN articles a ON a.slug = l.src_slug
            WHERE l.target_kind = 'source' AND l.target = $citekey
            ORDER BY a.title, a.slug""".query[Backlink].run().toList

  /** Which of `slugs` actually exist — the article half of resolving a `[[wikilink]]`. One query for the whole body, so
    * rendering an article is still three round trips.
    */
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  def existingSlugs(slugs: List[Slug])(using DbCon): Set[Slug] =
    val wanted = slugs.map(_.value.trim.toLowerCase).filter(_.nonEmpty).distinct
    if wanted.isEmpty then Set.empty
    else
      sql"SELECT slug FROM articles WHERE slug = ANY($wanted::text[])"
        .query[Slug]
        .run()
        .toSet

  /** The link view (slug, title, summary) of the given articles; unknown slugs are absent. */
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  def refs(slugs: List[Slug])(using DbCon): List[Article.Ref] =
    val wanted = slugs.map(_.value.trim.toLowerCase).filter(_.nonEmpty).distinct
    if wanted.isEmpty then Nil
    else
      sql"SELECT slug, title, summary FROM articles WHERE slug = ANY($wanted::text[]) ORDER BY slug"
        .query[Article.Ref]
        .run()
        .toList

  /** The sources this article cites, with the metadata the footer shows. A citekey that is not in the corpus still
    * appears (with a NULL title) — a dangling citation is a lint finding, not a reason to hide it.
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def sourcesCited(slug: Slug)(using DbCon): List[CitedSource] =
    sql"""SELECT l.target AS citekey, s.title, s.year, s.status, s.authors, s.doi
          FROM article_links l LEFT JOIN sources s ON s.citekey = l.target
          WHERE l.src_slug = $slug AND l.target_kind = 'source'
          ORDER BY l.target""".query[CitedSource].run().toList

  /** `GET /api/articles` — the articles related to the filter's sources ([[FilterSql.articleWhere]]), narrowed by kind,
    * optionally searched by `text`.
    *
    * `text` is Postgres FTS over the weighted title(A)/summary(B)/body(C) vector, in `websearch` syntax ("phrase",
    * -not, or), OR'd with a case-insensitive substring of the TITLE, so `peop` finds "The Sea Peoples" before the word
    * is finished. `total` is the count for the filter, ignoring limit/offset.
    */
  @tolerated(LintRule.TotalOrder, "pending G58 (ORDER BY ends with a unique key)")
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  def list(
      filter: Filter,
      scope: PlaceScope,
      kind: Option[domain.research.Article.Kind],
      text: Option[Search.Query],
      limit: Limit,
      offset: Offset
  )(using DbCon): Article.List =
    val where = listWhere(filter, scope, kind, text)
    val order = text.map(t => Search.Query(t.value.trim)).filter(_.value.nonEmpty) match
      case Some(t) => sql"ORDER BY ts_rank_cd(a.tsv, ${tsQuery(t)}) DESC, a.title, a.slug"
      case None    => sql"ORDER BY a.kind, a.title, a.slug"
    val kinds = Article.List.byKind(
      sql"SELECT a.kind, count(*)::int FROM articles a WHERE $where GROUP BY a.kind"
        .query[(String, Int)]
        .run()
        .toList
        .map((kind, n) => Article.KindCount(Article.Kind.Wire(kind), Article.KindCount.Count(n)))
    )
    val total = Article.List.Total(kinds.map(_.count.value).sum)
    val rows =
      sql"SELECT $SummaryCols FROM articles a WHERE $where $order LIMIT $limit OFFSET ${offset.value.max(0)}"
        .query[ArticleSummaryRow]
        .run()
    Article.List.of(total, rows.toList.map(_.summaryRow), UnreadPage.Offset(offset.value.max(0)), kinds)

  /** The WHERE of [[list]], as a value — pure, so it is unit-tested without a database. The filter's part is
    * [[FilterSql.articleWhere]] (the one rule relating articles to sources).
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  def listWhere(
      filter: Filter,
      scope: PlaceScope,
      kind: Option[domain.research.Article.Kind],
      text: Option[Search.Query]
  ): Frag =
    val ps = List.newBuilder[Frag]
    val related = FilterSql.articleWhere(filter, scope)
    if related.sqlString != "TRUE" then ps += related
    kind
      .map(_.value.trim.toLowerCase)
      .filter(_.nonEmpty)
      .foreach(k => ps += sql"lower(a.kind) = $k")
    text.map(_.value.trim).filter(_.nonEmpty).foreach { t =>
      val fts = sql"(a.tsv @@ ${tsQuery(Search.Query(t))} OR a.title ILIKE ${FilterSql.contains(t)}$Esc)"
      ps += fts
    }
    val parts = ps.result()
    if parts.isEmpty then sql"TRUE"
    else parts.joined(sql" AND ")

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  private def tsQuery(q: Search.Query): Frag = Search.tsQuery(q)

  def slugs(using DbCon): Vector[Slug] =
    sql"""SELECT slug FROM articles ORDER BY slug COLLATE "C"""".query[Slug].run()

  /** `slug -> updated_at` in epoch millis, for the seed guard (one query for the whole wiki). */
  def updatedAtMillis(using DbCon): Map[Slug, CitekeyMillis.Millis] =
    sql"SELECT slug, (extract(epoch from updated_at) * 1000)::bigint FROM articles"
      .query[SlugMillis]
      .run()
      .map(r => r.slug -> r.millis)
      .toMap

  // ── article versions (V013) ─────────────────────────────────────────────────────────────────

  /** The `updated_at` / `created_at` timestamps rendered UTC-normalised, as the article token does. */
  private val VerToken =
    sql"to_char(v.updated_at AT TIME ZONE 'UTC', 'YYYY-MM-DD\"T\"HH24:MI:SS.US\"Z\"')"
  private val VerCreated =
    sql"to_char(v.created_at AT TIME ZONE 'UTC', 'YYYY-MM-DD\"T\"HH24:MI:SS.US\"Z\"')"

  /** List the version history of one article: id, slug, summary (first 200 chars), updatedAt, createdAt, and word count
    * (approximated as non-empty whitespace-split tokens). Newest first.
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def listVersions(slug: Slug, limit: Limit = Limit(20))(using DbCon): List[Article.Version] =
    sql"""SELECT v.id, v.slug, left(v.summary, 200),
            $VerToken, $VerCreated,
            array_length(regexp_split_to_array(trim(v.body), '\\s+'), 1)
            FROM article_versions v WHERE v.slug = $slug ORDER BY v.created_at DESC, v.id DESC LIMIT $limit"""
      .query[Article.Version]
      .run()
      .toList

  /** One historical version's full body. */
  /** How many articles are stored. */
  def count()(using DbCon): Count = Count(sql"SELECT count(*)::int FROM articles".query[Int].run().head.toLong)

  /** The newest saved version of an article: after a write, the row holding the text it replaced. */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def latestVersion(slug: Slug)(using DbCon): Option[VersionId] =
    sql"SELECT max(id) FROM article_versions WHERE slug = $slug"
      .query[Option[Long]]
      .run()
      .headOption
      .flatten
      .map(VersionId(_))

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def getVersion(slug: Slug, versionId: VersionId)(using DbCon): Option[Article.VersionFull] =
    sql"""SELECT v.id, v.slug, v.body, v.summary,
            $VerToken, $VerCreated
            FROM article_versions v WHERE v.slug = $slug AND v.id = $versionId"""
      .query[Article.VersionFull]
      .run()
      .headOption

  /** Every goal's linked articles that exist: goal → slugs. */
  /** Is there an article with this slug? */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def slugExists(slug: Slug)(using DbCon): Boolean =
    sql"SELECT count(*) FROM articles WHERE slug = $slug".query[Long].run().head > 0

  def articlesByGoal(using DbCon): Map[Goal.Slug, List[Slug]] =
    sql"""SELECT l.goal_slug, l.ref FROM goal_links l JOIN articles a ON a.slug = l.ref
            WHERE l.kind = 'article' ORDER BY l.goal_slug, l.ref"""
      .query[(Goal.Slug, Slug)]
      .run()
      .toList
      .groupMap(_._1)(_._2)
