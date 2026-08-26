package histdb.db

import histdb.core.*

import com.augustnagro.magnum.*
import javax.sql.DataSource

/** Magnum codecs for the article wire DTOs (the types live in `core`, which knows nothing about JDBC). */
given DbCodec[ArticleSummary]    = DbCodec.derived
given DbCodec[ArticleRow]       = DbCodec.derived
given DbCodec[Backlink]         = DbCodec.derived
given DbCodec[CitedSource]      = DbCodec.derived
given DbCodec[ArticleVersion]     = DbCodec.derived
given DbCodec[ArticleVersionFull] = DbCodec.derived

/** Flat projection of the whole article row, for the domain read (`load`). */
private[db] final case class FullArticleRow(
    slug: String, title: String, kind: String, body: String, summary: Option[String],
    tags: String, regions: String, periodStart: Option[Long], periodEnd: Option[Long], updatedAt: String
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

  /** The `text[]` join separator used across this module — it cannot occur in the data. */
  private val Sep = "|~|"

  /** `\` escapes `%`/`_` in every ILIKE here, as in [[FilterSql]] (whose copy is private). */
  private val Esc = " ESCAPE '\\'"

  private def splitArr(s: String): List[String] =
    if s.isEmpty then Nil else s.split(java.util.regex.Pattern.quote(Sep), -1).toList

  /** `updated_at` rendered UTC-normalised: stable to compare, safe to echo back as `ifUpdatedAt`. */
  private val Token = "to_char(a.updated_at AT TIME ZONE 'UTC', 'YYYY-MM-DD\"T\"HH24:MI:SS.US\"Z\"')"

  /** One projection per shape, reused — no duplicated column lists. Arrays are comma-joined for the
    * WIRE shapes (as `SearchHit` does) and `|~|`-joined for the DOMAIN shape (which splits them). */
  private val SummaryCols =
    s"a.slug, a.title, a.kind, a.summary, array_to_string(a.tags, ', '), " +
      s"array_to_string(a.regions, ', '), lower(a.period), upper(a.period) - 1, $Token"
  private val RowCols =
    s"a.slug, a.title, a.kind, a.body, a.summary, array_to_string(a.tags, ', '), " +
      s"array_to_string(a.regions, ', '), lower(a.period), upper(a.period) - 1, $Token"
  private val DomainCols =
    s"a.slug, a.title, a.kind, a.body, a.summary, array_to_string(a.tags, '$Sep'), " +
      s"array_to_string(a.regions, '$Sep'), lower(a.period), upper(a.period) - 1, $Token"

  // ── writes ─────────────────────────────────────────────────────────────────────────────────
  /** Full replace (`put_article`). Returns the fresh `updatedAt` concurrency token. */
  def put(ds: DataSource, a: Article): String =
    transact(ds):
      write(a)
      token(a.slug).getOrElse("")

  /** Selective merge (`patch_article`): read → [[Articles.applyPatch]] → write, all in ONE
    * transaction, so two concurrent patches cannot interleave and `ifUpdatedAt` means something. */
  def patch(ds: DataSource, slug: String, p: ArticlePatch): Either[ApiError, String] =
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
                case Left(msg)      => Left(ApiError(msg, Some(Write.CodeInvalid)))
                case Right(updated) => write(updated); Right(token(slug).getOrElse(""))

  def delete(ds: DataSource, slug: String): Boolean =
    transact(ds)(sql"DELETE FROM articles WHERE slug = $slug".update.run() > 0)

  /** The write itself, inside the caller's transaction: snapshot the previous body into
    * `article_versions` (when the article already exists), then upsert the row and its link graph. */
  private def write(a: Article)(using DbCon): Unit =
    // Snapshot the PREVIOUS version before the update overwrites it.
    sql"""INSERT INTO article_versions (slug, body, summary, updated_at)
          SELECT slug, body, summary, updated_at FROM articles WHERE slug = ${a.slug}"""
      .update.run()
    val tags      = a.tags.mkString(Sep)
    val regions   = a.regions.mkString(Sep)
    val periodLit = a.period.map(p => s"[${p.start},${p.end}]")
    sql"""INSERT INTO articles (slug, title, kind, body, summary, tags, period, regions)
          VALUES (${a.slug}, ${a.title}, ${a.kind}, ${a.body}, ${a.summary},
                  COALESCE(string_to_array(NULLIF($tags, ''), $Sep), '{}'::text[]),
                  ${periodLit}::int8range,
                  COALESCE(string_to_array(NULLIF($regions, ''), $Sep), '{}'::text[]))
          ON CONFLICT (slug) DO UPDATE SET
            title=EXCLUDED.title, kind=EXCLUDED.kind, body=EXCLUDED.body, summary=EXCLUDED.summary,
            tags=EXCLUDED.tags, period=EXCLUDED.period, regions=EXCLUDED.regions,
            updated_at=now()""".update.run()
    writeLinks(a.slug, a.body)

  /** Rewrite one article's outgoing links. A `[[x]]` is a SOURCE link iff x is citekey-shaped AND a
    * source with that citekey exists — resolved with one query over the citekey-shaped candidates. */
  private def writeLinks(slug: String, body: String)(using DbCon): Unit =
    sql"DELETE FROM article_links WHERE src_slug = $slug".update.run()
    val targets = ArticleLinks.targets(body).filter(_ != slug) // an article does not link to itself
    if targets.nonEmpty then
      val candidates = targets.filter(ArticleLinks.isCitekeyShaped).mkString(Sep)
      val known =
        if candidates.isEmpty then Set.empty[String]
        else
          sql"SELECT citekey FROM sources WHERE citekey = ANY(string_to_array($candidates, $Sep))"
            .query[String].run().toSet
      val links = ArticleLinks.classify(targets, known.contains)
      val _ = batchUpdate(links)(l =>
        sql"""INSERT INTO article_links (src_slug, target_kind, target)
              VALUES ($slug, ${l.targetKind}, ${l.target}) ON CONFLICT DO NOTHING""".update)

  private def token(slug: String)(using DbCon): Option[String] =
    Sql.of(s"SELECT $Token FROM articles a WHERE a.slug = ?", S(slug)).frag.query[String].run().headOption

  // ── reads ──────────────────────────────────────────────────────────────────────────────────
  /** The whole article as the DOMAIN model — the input side of a patch, and what `export` renders. */
  def load(ds: DataSource, slug: String): Option[Article] = connect(ds)(load(slug))

  private def load(slug: String)(using DbCon): Option[Article] =
    Sql.of(s"SELECT $DomainCols FROM articles a WHERE a.slug = ?", S(slug))
      .frag.query[FullArticleRow].run().headOption.map { r =>
        Article(
          slug = r.slug, title = r.title, kind = r.kind, body = r.body, summary = r.summary,
          tags = splitArr(r.tags), regions = splitArr(r.regions),
          period = (r.periodStart, r.periodEnd) match
            case (Some(a), Some(b)) => Some(Period(a, b))
            case _                  => None,
          updatedAt = Some(r.updatedAt))
      }

  def get(ds: DataSource, slug: String): Option[ArticleRow] =
    connect(ds)(Sql.of(s"SELECT $RowCols FROM articles a WHERE a.slug = ?", S(slug))
      .frag.query[ArticleRow].run().headOption)

  /** The article plus both directions of its link graph — one connection, three queries. */
  def view(ds: DataSource, slug: String): Option[ArticleView] =
    connect(ds):
      Sql.of(s"SELECT $RowCols FROM articles a WHERE a.slug = ?", S(slug))
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
      val joined = wanted.mkString(Sep)
      connect(ds):
        sql"SELECT slug FROM articles WHERE slug = ANY(string_to_array($joined, $Sep))"
          .query[String].run().toSet

  /** The sources this article cites, with the metadata the footer shows. A citekey that is not in
    * the corpus still appears (with a NULL title) — a dangling citation is a lint finding, not a
    * reason to hide it. */
  def sourcesCited(ds: DataSource, slug: String): List[CitedSource] = connect(ds)(sourcesCited(slug))

  private def sourcesCited(slug: String)(using DbCon): List[CitedSource] =
    sql"""SELECT l.target AS citekey, s.title, s.year, s.status
          FROM article_links l LEFT JOIN sources s ON s.citekey = l.target
          WHERE l.src_slug = $slug AND l.target_kind = 'source'
          ORDER BY l.target""".query[CitedSource].run().toList

  /** `GET /api/articles` — filter by kind and/or tag, optionally full-text.
    *
    * `q` is Postgres FTS over the weighted title(A)/summary(B)/body(C) vector, in `websearch` syntax
    * ("phrase", -not, or), OR'd with a case-insensitive substring of the TITLE: the atlas tab
    * searches as you type, and `peop` must find "The Sea Peoples" before the word is finished — a
    * lexeme match alone would not. `total` is the count for the filter, ignoring limit/offset. */
  def list(
      ds: DataSource,
      kind: Option[String],
      q: Option[String],
      tag: Option[String],
      limit: Int,
      offset: Int
  ): ArticleList =
    val where = listWhere(kind, q, tag)
    val order = q.map(_.trim).filter(_.nonEmpty) match
      case Some(t) => Sql.raw("ORDER BY ts_rank_cd(a.tsv, ") ++ tsQuery(t) ++ Sql.raw(") DESC, a.title")
      case None    => Sql.raw("ORDER BY a.kind, a.title")
    connect(ds):
      val total = (Sql.raw("SELECT count(*) FROM articles a WHERE ") ++ where)
        .frag.query[Int].run().headOption.getOrElse(0)
      val rows = (Sql.raw(s"SELECT $SummaryCols FROM articles a WHERE ") ++ where ++ Sql.raw(" ") ++
        order ++ Sql.of(" LIMIT ? OFFSET ?", I(limit), I(offset.max(0))))
        .frag.query[ArticleSummary].run()
      ArticleList(total, rows.toList)

  /** The WHERE of [[list]], as a value — pure, so it is unit-tested without a database. */
  def listWhere(kind: Option[String], q: Option[String], tag: Option[String]): Sql =
    val ps = List.newBuilder[Sql]
    kind.map(_.trim.toLowerCase).filter(_.nonEmpty).foreach(k => ps += Sql.of("lower(a.kind) = ?", S(k)))
    tag.map(_.trim).filter(_.nonEmpty).foreach(t =>
      ps += Sql.of("EXISTS (SELECT 1 FROM unnest(a.tags) x WHERE lower(x) = lower(?))", S(t)))
    q.map(_.trim).filter(_.nonEmpty).foreach { t =>
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
