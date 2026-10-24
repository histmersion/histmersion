package com.histmersion.db

import com.histmersion.api.*
import com.histmersion.config.*
import com.histmersion.domain.*

import com.augustnagro.magnum.*
import javax.sql.DataSource

/** Magnum codecs for the article wire DTOs (the types live in `core`, which knows nothing about JDBC). */
given DbCodec[Backlink]         = DbCodec.derived
given DbCodec[ArticleVersion]     = DbCodec.derived
given DbCodec[ArticleVersionFull] = DbCodec.derived

/** `text[]` columns decode straight into `List[String]` through [[Codecs]], so the wire DTOs are
  * read directly — no joined-string intermediate. */
given DbCodec[ArticleSummary] = DbCodec.derived
given DbCodec[ArticleRow]     = DbCodec.derived
given DbCodec[CitedSource]    = DbCodec.derived
given DbCodec[ArticleRef]     = DbCodec.derived

/** Flat projection of the whole article row, for the domain read (`load`). */
private[db] final case class FullArticleRow(
    slug: String, title: String, kind: String, body: String, summary: Option[String],
    tags: List[String], regions: List[String], periodStart: Option[Long], periodEnd: Option[Long],
    updatedAt: String, filter: Option[String], reasons: String
)
private[db] final case class SlugMillis(slug: String, millis: Long)

given DbCodec[FullArticleRow] = DbCodec.derived
given DbCodec[SlugMillis]     = DbCodec.derived

/** The ARTICLES store (plan §5.2 stream 2.7) — the compiled wiki.
  *
  * Every write also rewrites that article's rows in `article_links`, from the `[[…]]` links in its
  * body: the classification (source vs article) is the pure [[ArticleLinks.classify]] fed by ONE
  * query asking which of the citekey-shaped targets actually exist. That is what makes `backlinks`
  * and `sourcesCited` index lookups rather than a LIKE scan of every article.
  *
  * Stateless, like every repo here: the `DataSource` is a parameter.
  */
object ArticleRepo:

  import Param.*

  /** `\` escapes `%`/`_` in every ILIKE here, as in [[FilterSql]] (whose copy is private). */
  private val Esc = " ESCAPE '\\'"

  /** `updated_at` rendered UTC-normalised: stable to compare, safe to echo back as `ifUpdatedAt`. */
  private val Token = "to_char(a.updated_at AT TIME ZONE 'UTC', 'YYYY-MM-DD\"T\"HH24:MI:SS.US\"Z\"')"

  /** One projection per shape, reused — no duplicated column lists. The row and the domain shape
    * read the same columns; `text[]` arrives as a list in both. */
  private val SummaryCols =
    s"a.slug, a.title, a.kind, a.summary, a.tags, a.regions, ${PeriodSql.cols("a.period")}, $Token"
  private val RowCols =
    s"a.slug, a.title, a.kind, a.body, a.summary, a.tags, a.regions, ${PeriodSql.cols("a.period")}, $Token"
  /** [[ArticleRow]], the wire shape: the row plus its uid (V030). */
  private val ViewCols = s"$RowCols, a.uid"
  /** [[FullArticleRow]], the domain read: the row plus its recorded filter and reasons (V066), as JSON text. */
  private val LoadCols = s"$RowCols, a.coverage_filter::text, a.coverage_reasons::text"

  /** The filter and the reasons as the jsonb columns store them. */
  private def filterJson(a: Article): Option[String] =
    import io.circe.syntax.*
    a.filter.map(_.asJson.deepDropNullValues.noSpaces)
  private def reasonsJson(a: Article): String =
    io.circe.Json.obj(a.reasons.toList.sortBy(_._1).map((k, v) => k -> io.circe.Json.fromString(v))*).noSpaces

  /** The stored JSON back; a value that no longer decodes (an older shape) reads as none, never fails the read. */
  private[db] def filterOf(json: Option[String]): Option[ArticleFilter] =
    json.flatMap(Jsonb.read[ArticleFilter])
  private[db] def reasonsOf(json: String): Map[String, String] =
    Jsonb.read[Map[String, String]](json).getOrElse(Map.empty)

  // ── writes ─────────────────────────────────────────────────────────────────────────────────
  /** Full replace (`put_article`). Returns the fresh `updatedAt` concurrency token. */
  def put(ds: DataSource, a: Article): String =
    transact(ds):
      write(a)
      token(a.slug).getOrElse("")

  /** Selective merge (`patch_article`): read → [[Articles.applyPatch]] → write, all in ONE
    * transaction, so two concurrent patches cannot interleave and `ifUpdatedAt` means something. */
  def patch(ds: DataSource, slug: String, p: ArticlePatch, by: String = "person"): Either[ApiError, String] =
    transact(ds):
      // FOR UPDATE before the read: `ifUpdatedAt` is a compare-and-swap, and without the row lock
      // two concurrent patches both saw the token they were given and both "succeeded", the second
      // discarding the first. See the twin comment in `SourceRepo.patch`.
      val _ = sql"SELECT slug FROM articles WHERE slug = $slug FOR UPDATE".query[String].run()
      load(slug) match
        case None => Left(Articles.notFound(slug))
        case Some(existing) =>
          Articles.precondition(existing, p) match
            case Left(msg) => Left(ApiError(msg, Some(Write.CodePrecondition)))
            case Right(_)  =>
              Articles.applyPatch(existing, p) match
                case Left(msg) => Left(ApiError(msg, Some(Write.CodeInvalid)))
                // the critique reasons (V091) sit beside the row: checked first, so a refused one
                // writes nothing; a patch of them alone leaves the article, its versions and its
                // token as they are (`by`: who set them, when a reason does not say)
                case Right(updated) =>
                  CritiqueRepo.applyIn(slug, p.critiqueReasons, by)(if !p.onlyCritiques then write(updated))
                    .map(_ => token(slug).getOrElse(""))

  def delete(ds: DataSource, slug: String): Boolean =
    transact(ds)(sql"DELETE FROM articles WHERE slug = $slug".update.run() > 0)

  /** The write itself, inside the caller's transaction: snapshot the previous body into
    * `article_versions` (when the article already exists), then upsert the row and its link graph. */
  private def write(a: Article)(using DbCon): Unit =
    // Snapshot the PREVIOUS version before the update overwrites it.
    sql"""INSERT INTO article_versions (slug, body, summary, updated_at)
          SELECT slug, body, summary, updated_at FROM articles WHERE slug = ${a.slug}"""
      .update.run()
    val periodLit = PeriodSql.literal(a.period)
    val filter  = filterJson(a)
    val reasons = reasonsJson(a)
    sql"""INSERT INTO articles (slug, title, kind, body, summary, tags, period, regions, coverage_filter, coverage_reasons)
          VALUES (${a.slug}, ${a.title}, ${a.kind}, ${a.body}, ${a.summary},
                  ${a.tags}::text[], ${periodLit}::int8range, ${a.regions}::text[], ${filter}::jsonb, ${reasons}::jsonb)
          ON CONFLICT (slug) DO UPDATE SET
            title=EXCLUDED.title, kind=EXCLUDED.kind, body=EXCLUDED.body, summary=EXCLUDED.summary,
            tags=EXCLUDED.tags, period=EXCLUDED.period, regions=EXCLUDED.regions,
            coverage_filter=EXCLUDED.coverage_filter, coverage_reasons=EXCLUDED.coverage_reasons,
            updated_at=now()""".update.run()
    writeLinks(a.slug, a.body)

  /** Rewrite one article's outgoing links. A `[[x]]` is a SOURCE link iff x is citekey-shaped AND a
    * source with that citekey exists — resolved with one query over the citekey-shaped candidates. */
  private[db] def writeLinks(slug: String, body: String)(using DbCon): Unit =
    sql"DELETE FROM article_links WHERE src_slug = $slug".update.run()
    val written = ArticleLinks.targets(body).filter(_ != slug) // an article does not link to itself
    if written.nonEmpty then
      // an OLD citekey (V027) links the source it was renamed to, and is recorded under the new one
      // so `backlinksForSource` finds it
      val known   = SourceRepo.canonicalAll(written.filter(ArticleLinks.isCitekeyShaped))
      val targets = written.map(t => known.getOrElse(t, t)).distinct
      val links   = ArticleLinks.classify(targets, known.values.toSet.contains)
      val _ = batchUpdate(links)(l =>
        sql"""INSERT INTO article_links (src_slug, target_kind, target)
              VALUES ($slug, ${l.targetKind}, ${l.target}) ON CONFLICT DO NOTHING""".update)

  /** Retarget wikilinks in every article whose body or summary mentions `needle` (case-insensitive)
    * — the article half of a citekey rename — and the coverage reasons (V066) keyed by a moved
    * citekey. Each changed article goes through [[write]], so the previous text is kept in
    * `article_versions` and `article_links` is recomputed. Returns (slug, links moved) for each
    * article that changed. */
  private[db] def retargetLinks(needle: String, to: String => Option[String])(using DbCon): List[(String, Int)] =
    sql"""SELECT slug FROM articles
          WHERE strpos(lower(body || ' ' || coalesce(summary, '') || ' ' || coverage_reasons::text), ${needle.toLowerCase}) > 0
          ORDER BY slug""".query[String].run().toList.flatMap { slug =>
      load(slug).flatMap { a =>
        val (body, nb) = ArticleLinks.retarget(a.body, to)
        val summary    = a.summary.map(ArticleLinks.retarget(_, to))
        val reasons    = a.reasons.map((ck, r) => to(ck).getOrElse(ck) -> r)
        val nr         = a.reasons.keys.count(to(_).isDefined)
        val moved      = nb + summary.fold(0)(_._2) + nr
        Option.when(moved > 0) {
          write(a.copy(body = body, summary = summary.map(_._1), reasons = reasons))
          slug -> moved
        }
      }
    }

  /** Rename an article (V042), inside the caller's transaction: the slug moves with one UPDATE (its
    * versions and outgoing links cascade), the link rows that point at it follow, every `[[old]]` in
    * other articles is rewritten through the write path (so each rewrite is a recorded version), and
    * `old` becomes an alias that keeps resolving. Refused when `newSlug` is another article's slug or
    * still an old slug of another one. What a bundle import does when an article was renamed on the
    * other side, and what `merge apply` does to keep two articles apart. Returns the articles whose
    * links moved. */
  private[db] def renameIn(oldSlug: String, newSlug: String)(using DbCon): Either[ApiError, List[String]] =
    def exists(s: String) = sql"SELECT 1 FROM articles WHERE slug = $s".query[Int].run().nonEmpty
    val aliasOf = sql"SELECT slug FROM article_aliases WHERE alias = $newSlug".query[String].run().headOption
    if !exists(oldSlug) then Left(Articles.notFound(oldSlug))
    else if exists(newSlug) then Left(ApiError(s"'$newSlug' is already the slug of another article", Some(Write.CodeInvalid)))
    else if aliasOf.exists(_ != oldSlug) then
      Left(ApiError(s"'$newSlug' is an old slug of '${aliasOf.get}' and still resolves to it — pick another",
        Some(Write.CodeInvalid)))
    else
      sql"DELETE FROM article_aliases WHERE alias = $newSlug".update.run()
      sql"UPDATE articles SET slug = $newSlug WHERE slug = $oldSlug".update.run()
      sql"""UPDATE article_links SET target = $newSlug
            WHERE target_kind = 'article' AND target = $oldSlug""".update.run()
      val moved = retargetLinks(oldSlug, t => Option.when(t == oldSlug)(newSlug))
      sql"""INSERT INTO article_aliases (alias, slug) VALUES ($oldSlug, $newSlug)
            ON CONFLICT (alias) DO UPDATE SET slug = EXCLUDED.slug""".update.run()
      // a goal linked to the article follows it (goal_links has no foreign key: a link may name a
      // record that is gone)
      sql"""UPDATE goal_links SET ref = $newSlug WHERE kind = 'article' AND ref = $oldSlug
            AND NOT EXISTS (SELECT 1 FROM goal_links g2 WHERE g2.goal_slug = goal_links.goal_slug
                            AND g2.kind = 'article' AND g2.ref = $newSlug)""".update.run()
      Right(moved.map(_._1))

  /** `histmersion articles rename`: a new slug, the old one kept as an alias (V042); every `[[old]]`
    * in other articles is rewritten through the write path. One transaction; returns the articles
    * rewritten. */
  def rename(ds: DataSource, oldSlug: String, newSlug: String): Either[ApiError, List[String]] =
    transact(ds)(renameIn(oldSlug.trim, newSlug.trim))

  /** The slug an old slug was renamed to (`article_aliases`), or the slug itself. */
  def canonical(ds: DataSource, slug: String): String = connect(ds)(canonical(slug))

  private def canonical(slug: String)(using DbCon): String =
    sql"SELECT slug FROM article_aliases WHERE alias = $slug AND NOT EXISTS (SELECT 1 FROM articles WHERE slug = $slug)"
      .query[String].run().headOption.getOrElse(slug)

  private def token(slug: String)(using DbCon): Option[String] =
    Sql.of(s"SELECT $Token FROM articles a WHERE a.slug = ?", S(slug)).frag.query[String].run().headOption

  // ── reads ──────────────────────────────────────────────────────────────────────────────────
  /** The whole article as the DOMAIN model — the input side of a patch, and what `export` renders. */
  def load(ds: DataSource, slug: String): Option[Article] = connect(ds)(load(slug))

  private def load(slug: String)(using DbCon): Option[Article] =
    Sql.of(s"SELECT $LoadCols FROM articles a WHERE a.slug = ?", S(slug))
      .frag.query[FullArticleRow].run().headOption.map { r =>
        Article(
          slug = r.slug, title = r.title, kind = r.kind, body = r.body, summary = r.summary,
          tags = r.tags, regions = r.regions,
          period = (r.periodStart, r.periodEnd) match
            case (Some(a), Some(b)) => Some(Period(a, b))
            case _                  => None,
          updatedAt = Some(r.updatedAt),
          filter = filterOf(r.filter),
          reasons = reasonsOf(r.reasons))
      }

  def get(ds: DataSource, slug: String): Option[ArticleRow] =
    connect(ds)(Sql.of(s"SELECT $ViewCols FROM articles a WHERE a.slug = ?", S(canonical(slug)))
      .frag.query[ArticleRow].run().headOption)

  /** The article plus both directions of its link graph — one connection, three queries. */
  def view(ds: DataSource, requested: String): Option[ArticleView] =
    connect(ds):
      val slug = canonical(requested) // an old slug (V042) shows the article it was renamed to
      Sql.of(s"SELECT $ViewCols FROM articles a WHERE a.slug = ?", S(slug))
        .frag.query[ArticleRow].run().headOption
        .map(row => ArticleView(row, backlinks(slug), sourcesCited(slug)))

  /** What points HERE: other articles through `article_links`, and sources whose agent-written
    * `notes` contain a `[[slug]]` (the source notes are Markdown, not a link table). `strpos` rather
    * than `LIKE` so an underscore in a slug stays a literal underscore. */
  def backlinks(ds: DataSource, slug: String): List[Backlink] = connect(ds)(backlinks(slug))

  private def backlinks(slug: String)(using DbCon): List[Backlink] =
    sql"""SELECT 'article' AS kind, a.slug AS id, a.title
          FROM article_links l JOIN articles a ON a.slug = l.src_slug
          WHERE l.target_kind = 'article' AND l.target = $slug AND a.slug <> $slug
          UNION ALL
          SELECT 'source' AS kind, s.citekey AS id, s.title
          FROM sources s
          WHERE strpos(coalesce(s.notes, ''), '[[' || $slug || ']]') > 0
             OR strpos(coalesce(s.notes, ''), '[[' || $slug || '|') > 0
          ORDER BY 1, 2""".query[Backlink].run().toList

  /** The other direction, for a SOURCE: which compiled articles cite it (`article_links` rows with
    * `target_kind = 'source'`). An index lookup, like [[backlinks]], because the classification was
    * done at write time — no LIKE scan of every article body.
    *
    * Reuses [[Backlink]] deliberately: "something that points at me, with a kind, an id and a title"
    * is the same shape whichever end you stand at, and a second near-identical DTO would only give
    * the two sides a way to drift. */
  def backlinksForSource(ds: DataSource, citekey: String): List[Backlink] =
    connect(ds):
      sql"""SELECT 'article' AS kind, a.slug AS id, a.title
            FROM article_links l JOIN articles a ON a.slug = l.src_slug
            WHERE l.target_kind = 'source' AND l.target = $citekey
            ORDER BY a.title, a.slug""".query[Backlink].run().toList

  /** Which of `slugs` actually exist — the article half of resolving a `[[wikilink]]`. One query for
    * the whole body, so rendering an article is still three round trips. */
  def existingSlugs(ds: DataSource, slugs: List[String]): Set[String] =
    val wanted = slugs.map(_.trim.toLowerCase).filter(_.nonEmpty).distinct
    if wanted.isEmpty then Set.empty
    else
      connect(ds):
        sql"SELECT slug FROM articles WHERE slug = ANY($wanted::text[])"
          .query[String].run().toSet

  /** The link view (slug, title, summary) of the given articles; unknown slugs are absent. */
  def refs(ds: DataSource, slugs: List[String]): List[ArticleRef] =
    val wanted = slugs.map(_.trim.toLowerCase).filter(_.nonEmpty).distinct
    if wanted.isEmpty then Nil
    else
      connect(ds):
        sql"SELECT slug, title, summary FROM articles WHERE slug = ANY($wanted::text[]) ORDER BY slug"
          .query[ArticleRef].run().toList

  /** The sources this article cites, with the metadata the footer shows. A citekey that is not in
    * the corpus still appears (with a NULL title) — a dangling citation is a lint finding, not a
    * reason to hide it. */
  def sourcesCited(ds: DataSource, slug: String): List[CitedSource] = connect(ds)(sourcesCited(slug))

  private def sourcesCited(slug: String)(using DbCon): List[CitedSource] =
    sql"""SELECT l.target AS citekey, s.title, s.year, s.status, s.authors, s.doi
          FROM article_links l LEFT JOIN sources s ON s.citekey = l.target
          WHERE l.src_slug = $slug AND l.target_kind = 'source'
          ORDER BY l.target""".query[CitedSource].run().toList

  /** `GET /api/articles` — the articles related to the filter's sources ([[FilterSql.articleWhere]]),
    * narrowed by kind, optionally searched by `text`.
    *
    * `text` is Postgres FTS over the weighted title(A)/summary(B)/body(C) vector, in `websearch`
    * syntax ("phrase", -not, or), OR'd with a case-insensitive substring of the TITLE, so `peop`
    * finds "The Sea Peoples" before the word is finished. `total` is the count for the filter,
    * ignoring limit/offset. */
  def list(
      ds: DataSource,
      filter: Filter,
      scope: PlaceScope,
      kind: Option[String],
      text: Option[String],
      limit: Int,
      offset: Int
  ): ArticleList =
    val where = listWhere(filter, scope, kind, text)
    val order = text.map(_.trim).filter(_.nonEmpty) match
      case Some(t) => Sql.raw("ORDER BY ts_rank_cd(a.tsv, ") ++ tsQuery(t) ++ Sql.raw(") DESC, a.title")
      case None    => Sql.raw("ORDER BY a.kind, a.title")
    connect(ds):
      val total = (Sql.raw("SELECT count(*) FROM articles a WHERE ") ++ where)
        .frag.query[Int].run().headOption.getOrElse(0)
      val rows = (Sql.raw(s"SELECT $SummaryCols FROM articles a WHERE ") ++ where ++ Sql.raw(" ") ++
        order ++ Sql.of(" LIMIT ? OFFSET ?", I(limit), I(offset.max(0))))
        .frag.query[ArticleSummary].run()
      ArticleList.of(total, rows.toList, offset.max(0))

  /** The WHERE of [[list]], as a value — pure, so it is unit-tested without a database. The
    * filter's part is [[FilterSql.articleWhere]] (the one rule relating articles to sources). */
  def listWhere(filter: Filter, scope: PlaceScope, kind: Option[String], text: Option[String]): Sql =
    val ps = List.newBuilder[Sql]
    val related = FilterSql.articleWhere(filter, scope)
    if related.text != "TRUE" then ps += related
    kind.map(_.trim.toLowerCase).filter(_.nonEmpty).foreach(k => ps += Sql.of("lower(a.kind) = ?", S(k)))
    text.map(_.trim).filter(_.nonEmpty).foreach { t =>
      val fts = Sql.raw("(a.tsv @@ ") ++ tsQuery(t) ++
        Sql.of(s" OR a.title ILIKE ?$Esc)", S(FilterSql.contains(t)))
      ps += fts
    }
    val parts = ps.result()
    if parts.isEmpty then Sql.raw("TRUE")
    else Sql(parts.map(_.text).mkString(" AND "), parts.flatMap(_.params))

  private def tsQuery(q: String): Sql = Search.tsQuery(q)

  def slugs(ds: DataSource): Vector[String] =
    connect(ds)(sql"""SELECT slug FROM articles ORDER BY slug COLLATE "C"""".query[String].run())

  /** `slug -> updated_at` in epoch millis, for the seed guard (one query for the whole wiki). */
  def updatedAtMillis(ds: DataSource): Map[String, Long] =
    connect(ds):
      sql"SELECT slug, (extract(epoch from updated_at) * 1000)::bigint FROM articles"
        .query[SlugMillis].run().map(r => r.slug -> r.millis).toMap

  // ── article versions (V013) ─────────────────────────────────────────────────────────────────

  /** The `updated_at` / `created_at` timestamps rendered UTC-normalised, as the article token does. */
  private val VerToken =
    "to_char(v.updated_at AT TIME ZONE 'UTC', 'YYYY-MM-DD\"T\"HH24:MI:SS.US\"Z\"')"
  private val VerCreated =
    "to_char(v.created_at AT TIME ZONE 'UTC', 'YYYY-MM-DD\"T\"HH24:MI:SS.US\"Z\"')"

  /** List the version history of one article: id, slug, summary (first 200 chars), updatedAt,
    * createdAt, and word count (approximated as non-empty whitespace-split tokens). Newest first. */
  def listVersions(ds: DataSource, slug: String, limit: Int = 20): List[ArticleVersion] =
    connect(ds):
      Sql.of(
        s"""SELECT v.id, v.slug, left(v.summary, 200),
            $VerToken, $VerCreated,
            array_length(regexp_split_to_array(trim(v.body), '\\s+'), 1)
            FROM article_versions v WHERE v.slug = ? ORDER BY v.created_at DESC LIMIT ?""",
        Param.S(slug), Param.I(limit))
        .frag.query[ArticleVersion].run().toList

  /** One historical version's full body. */
  def getVersion(ds: DataSource, slug: String, versionId: Long): Option[ArticleVersionFull] =
    connect(ds):
      Sql.of(
        s"""SELECT v.id, v.slug, v.body, v.summary,
            $VerToken, $VerCreated
            FROM article_versions v WHERE v.slug = ? AND v.id = ?""",
        Param.S(slug), Param.L(versionId))
        .frag.query[ArticleVersionFull].run().headOption
