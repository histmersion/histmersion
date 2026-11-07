package com.histmersion.domain.research.articles

import hearth.kindlings.optics.syntax.*

import com.histmersion.domain.library.sources.CitekeyResolver
import com.histmersion.domain.library.search.FilterSql
import com.histmersion.domain.library.search.Search

import com.histmersion.domain.catalogue.places.PlaceScope

import com.histmersion.util.database.RangeSql

import com.histmersion.api.Write
import com.histmersion.util.database.{Jsonb, Param, Sql}
import com.histmersion.util.database.given

import com.augustnagro.magnum.*
import com.histmersion.util.database.NewtypeColumns.given
import com.histmersion.api.catalogue.{EventArticleRef, Period}
import com.histmersion.api.library.Filter
import com.histmersion.api.library.SourceColumns.given
import com.histmersion.api.research.{Article, Articles, Backlink, CitedSource}
import com.histmersion.api.Api

/** Magnum codecs for the article wire DTOs (the types live in `core`, which knows nothing about JDBC). */
given DbCodec[Backlink] = DbCodec.derived
given given_DbCodec_ArticleVersion: DbCodec[Article.Version] = DbCodec.derived
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
  object Count extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Limit = Limit.T
  object Limit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type VersionId = VersionId.T
  object VersionId extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]



  private given DbCodec[Backlink] = DbCodec.derived
  private given DbCodec[Article.Version] = DbCodec.derived
  private given DbCodec[Article.VersionFull] = DbCodec.derived
  private given DbCodec[Article.Row] = DbCodec.derived
  private given DbCodec[CitedSource] = DbCodec.derived
  private given DbCodec[Article.Ref] = DbCodec.derived

  import Param.*

  /** `\` escapes `%`/`_` in every ILIKE here, as in [[FilterSql]] (whose copy is private). */
  private val Esc = " ESCAPE '\\'"

  /** `updated_at` rendered UTC-normalised: stable to compare, safe to echo back as `ifUpdatedAt`. */
  private val Token = "to_char(a.updated_at AT TIME ZONE 'UTC', 'YYYY-MM-DD\"T\"HH24:MI:SS.US\"Z\"')"

  /** One projection per shape, reused — no duplicated column lists. The row and the domain shape read the same columns;
    * `text[]` arrives as a list in both.
    */
  private val SummaryCols =
    s"a.slug, a.title, a.kind, a.summary, a.tags, a.regions, ${RangeSql.cols("a.period")}, $Token"
  private val RowCols =
    s"a.slug, a.title, a.kind, a.body, a.summary, a.tags, a.regions, ${RangeSql.cols("a.period")}, $Token"

  /** [[ArticleRow]], the wire shape: the row plus its uid (V030). */
  private val ViewCols = s"$RowCols, a.uid"

  /** [[FullArticleRow]], the domain read: the row plus its recorded filter and reasons (V066), as JSON text. */
  private val LoadCols = s"$RowCols, a.coverage_filter::text, a.coverage_reasons::text"

  /** The filter and the reasons as the jsonb columns store them. */
  private def filterJson(a: Article): Option[String] =
    import io.circe.syntax.*
    a.filter.map(_.asJson.deepDropNullValues.noSpaces)
  private def reasonsJson(a: Article): String =
    io.circe.Json.obj(a.reasons.toList.sortBy(_._1.value).map((k, v) => k.value -> io.circe.Json.fromString(v.value))*).noSpaces

  /** The stored JSON back; a value that no longer decodes (an older shape) reads as none, never fails the read. */
  private[articles] def filterOf(json: Option[String]): Option[Article.Filter] =
    json.flatMap(j => Jsonb.read[Article.Filter](Jsonb.JsonText(j)))
  private[articles] def reasonsOf(json: String): Map[com.histmersion.api.library.Citekey, Article.ReasonsValue] =
    Jsonb
      .read[Map[String, String]](Jsonb.JsonText(json))
      .getOrElse(Map.empty)
      .map((ck, reason) => com.histmersion.api.library.Citekey(ck) -> Article.ReasonsValue(reason))

  // ── writes ─────────────────────────────────────────────────────────────────────────────────
  /** Full replace (`put_article`). Returns the fresh `updatedAt` concurrency token. */
  def put(a: Article)(using DbCon): Article.UpdatedAt =
    write(a)
    Article.UpdatedAt(token(a.slug.value).getOrElse(""))

  /** Selective merge (`patch_article`): read → [[Articles.applyPatch]] → write, all in ONE transaction, so two
    * concurrent patches cannot interleave and `ifUpdatedAt` means something.
    */
  def patch(slug: com.histmersion.domain.research.Slug, p: Article.Patch, by: com.histmersion.domain.research.articles.CritiqueRepo.By = com.histmersion.domain.research.articles.CritiqueRepo.By("person"))(using DbCon): Either[Api.Error, Article.UpdatedAt] =
    // FOR UPDATE before the read: `ifUpdatedAt` is a compare-and-swap, and without the row lock
    // two concurrent patches both saw the token they were given and both "succeeded", the second
    // discarding the first. See the twin comment in `SourceRepo.patch`.
    val _ = sql"SELECT slug FROM articles WHERE slug = $slug FOR UPDATE".query[String].run()
    load(slug) match
      case None           => Left(Articles.notFound(Article.Slug(slug.value)))
      case Some(existing) =>
        Articles.precondition(existing, p) match
          case Left(msg) => Left(Api.Error(Api.Error.Message(msg.value), Some(Api.Error.Code(Write.CodePrecondition))))
          case Right(_)  =>
            Articles.applyPatch(existing, p) match
              case Left(msg) => Left(Api.Error(Api.Error.Message(msg.value), Some(Api.Error.Code(Write.CodeInvalid))))
              // the critique reasons (V091) sit beside the row: checked first, so a refused one
              // writes nothing; a patch of them alone leaves the article, its versions and its
              // token as they are (`by`: who set them, when a reason does not say)
              case Right(updated) =>
                CritiqueRepo
                  .applyIn(slug, p.critiqueReasons, by)(if !p.onlyCritiques then write(updated))
                  .map(_ => Article.UpdatedAt(token(slug.value).getOrElse("")))

  def delete(slug: com.histmersion.domain.research.Slug)(using DbCon): Boolean =
    sql"DELETE FROM articles WHERE slug = $slug".update.run() > 0

  /** The write itself, inside the caller's transaction: snapshot the previous body into `article_versions` (when the
    * article already exists), then upsert the row and its link graph.
    */
  private def write(a: Article)(using DbCon): Unit =
    // Snapshot the PREVIOUS version before the update overwrites it.
    sql"""INSERT INTO article_versions (slug, body, summary, updated_at)
          SELECT slug, body, summary, updated_at FROM articles WHERE slug = ${a.slug}""".update.run()
    val periodLit = RangeSql.literal(a.period)
    val filter = filterJson(a)
    val reasons = reasonsJson(a)
    sql"""INSERT INTO articles (slug, title, kind, body, summary, tags, period, regions, coverage_filter, coverage_reasons)
          VALUES (${a.slug}, ${a.title}, ${a.kind}, ${a.body}, ${a.summary},
                  ${a.tags}::text[], $periodLit::int8range, ${a.regions}::text[], $filter::jsonb, $reasons::jsonb)
          ON CONFLICT (slug) DO UPDATE SET
            title=EXCLUDED.title, kind=EXCLUDED.kind, body=EXCLUDED.body, summary=EXCLUDED.summary,
            tags=EXCLUDED.tags, period=EXCLUDED.period, regions=EXCLUDED.regions,
            coverage_filter=EXCLUDED.coverage_filter, coverage_reasons=EXCLUDED.coverage_reasons,
            updated_at=now()""".update.run()
    linkRows(a.slug.value, a.body.value)

  /** Rewrite one article's outgoing links. A `[[x]]` is a SOURCE link iff x is citekey-shaped AND a source with that
    * citekey exists — resolved with one query over the citekey-shaped candidates.
    */
  private[histmersion] def writeLinks(slug: com.histmersion.domain.research.Slug, body: com.histmersion.domain.research.articles.BArticleRow.Body)(using DbCon): Unit =
    linkRows(slug.value, body.value)

  private def linkRows(slug: String, body: String)(using DbCon): Unit =
    sql"DELETE FROM article_links WHERE src_slug = $slug".update.run()
    val written = Article.Links.targets(com.histmersion.api.text.WikiLinks.Body(body)).filter(_.value != slug) // an article does not link to itself
    if written.nonEmpty then
      // an OLD citekey (V027) links the source it was renamed to, and is recorded under the new one
      // so `backlinksForSource` finds it
      val known: Map[String, String] = CitekeyResolver.stored
        .canonical(
          written
            .filter(t => Article.Links.isCitekeyShaped(t.value))
            .flatMap(t => com.histmersion.domain.library.Citekey.either(t.value).toOption)
        )
        .map((from, to) => from.value -> to.value)
      val targets = written.map(t => known.get(t.value).fold(t)(Article.Link.Target(_))).distinct
      val links = Article.Links.classify(targets, t => known.values.toSet.contains(t.value))
      val _ = batchUpdate(links)(l => sql"""INSERT INTO article_links (src_slug, target_kind, target)
              VALUES ($slug, ${l.targetKind}, ${l.target}) ON CONFLICT DO NOTHING""".update)

  /** Retarget wikilinks in every article whose body or summary mentions `needle` (case-insensitive) — the article half
    * of a citekey rename — and the coverage reasons (V066) keyed by a moved citekey. Each changed article goes through
    * [[write]], so the previous text is kept in `article_versions` and `article_links` is recomputed. Returns (slug,
    * links moved) for each article that changed.
    */
  private[histmersion] def retargetLinks(needle: Article.Link.Target, to: Article.Link.Target => Option[Article.Link.Target])(using
      DbCon
  ): List[(com.histmersion.domain.research.Slug, com.histmersion.RowCount)] =
    sql"""SELECT slug FROM articles
          WHERE strpos(lower(body || ' ' || coalesce(summary, '') || ' ' || coverage_reasons::text), ${needle.value.toLowerCase}) > 0
          ORDER BY slug""".query[com.histmersion.domain.research.Slug].run().toList.flatMap { slug =>
      load(slug).flatMap { a =>
        val (body, nb) = Article.Links.retarget(com.histmersion.api.text.WikiLinks.Body(a.body.value), to)
        val summary = a.summary.map(s => Article.Links.retarget(com.histmersion.api.text.WikiLinks.Body(s.value), to))
        val reasons = a.reasons.map((ck, r) =>
          to(Article.Link.Target(ck.value)).fold(ck)(t => com.histmersion.api.library.Citekey(t.value)) -> r
        )
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
          slug -> com.histmersion.RowCount(moved)
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
  private[histmersion] def renameIn(oldSlug: com.histmersion.domain.research.Slug, newSlug: com.histmersion.domain.research.Slug)(using DbCon): Either[Api.Error, List[com.histmersion.domain.research.Slug]] =
    def exists(s: com.histmersion.domain.research.Slug) = sql"SELECT 1 FROM articles WHERE slug = $s".query[Int].run().nonEmpty
    val aliasOf = sql"SELECT slug FROM article_aliases WHERE alias = $newSlug"
      .query[com.histmersion.domain.research.Slug]
      .run()
      .headOption
    if !exists(oldSlug) then Left(Articles.notFound(Article.Slug(oldSlug.value)))
    else if exists(newSlug) then
      Left(
        Api.Error(
          Api.Error.Message(s"'${newSlug.value}' is already the slug of another article"),
          Some(Api.Error.Code(Write.CodeInvalid))
        )
      )
    else if aliasOf.exists(_ != oldSlug) then
      Left(
        Api.Error(
          Api.Error.Message(s"'${newSlug.value}' is an old slug of '${aliasOf.get.value}' and still resolves to it — pick another"),
          Some(Api.Error.Code(Write.CodeInvalid))
        )
      )
    else
      sql"DELETE FROM article_aliases WHERE alias = $newSlug".update.run()
      sql"UPDATE articles SET slug = $newSlug WHERE slug = $oldSlug".update.run()
      sql"""UPDATE article_links SET target = $newSlug
            WHERE target_kind = 'article' AND target = $oldSlug""".update.run()
      val moved = retargetLinks(
        Article.Link.Target(oldSlug.value),
        t => Option.when(t.value == oldSlug.value)(Article.Link.Target(newSlug.value))
      )
      sql"""INSERT INTO article_aliases (alias, slug) VALUES ($oldSlug, $newSlug)
            ON CONFLICT (alias) DO UPDATE SET slug = EXCLUDED.slug""".update.run()
      // a goal linked to the article follows it (goal_links has no foreign key: a link may name a
      // record that is gone)
      sql"""UPDATE goal_links SET ref = $newSlug WHERE kind = 'article' AND ref = $oldSlug
            AND NOT EXISTS (SELECT 1 FROM goal_links g2 WHERE g2.goal_slug = goal_links.goal_slug
                            AND g2.kind = 'article' AND g2.ref = $newSlug)""".update.run()
      Right(moved.map(_._1))

  /** `histmersion articles rename`: a new slug, the old one kept as an alias (V042); every `[[old]]` in other articles
    * is rewritten through the write path. One transaction; returns the articles rewritten.
    */
  def rename(oldSlug: com.histmersion.domain.research.Slug, newSlug: com.histmersion.domain.research.Slug)(using DbCon): Either[Api.Error, List[com.histmersion.domain.research.Slug]] =
    renameIn(oldSlug, newSlug)

  /** The slug an old slug was renamed to (`article_aliases`), or the slug itself. */
  def canonical(slug: com.histmersion.domain.research.Slug)(using DbCon): com.histmersion.domain.research.Slug =
    sql"SELECT slug FROM article_aliases WHERE alias = $slug AND NOT EXISTS (SELECT 1 FROM articles WHERE slug = $slug)"
      .query[com.histmersion.domain.research.Slug]
      .run()
      .headOption
      .getOrElse(slug)

  private def token(slug: String)(using DbCon): Option[String] =
    Sql.of(Sql.Text(s"SELECT $Token FROM articles a WHERE a.slug = ?"), S(Param.S.V(slug))).frag.query[String].run().headOption

  // ── reads ──────────────────────────────────────────────────────────────────────────────────
  /** The whole article as the DOMAIN model — the input side of a patch, and what `export` renders. */
  def load(slug: com.histmersion.domain.research.Slug)(using DbCon): Option[Article] =
    Sql
      .of(Sql.Text(s"SELECT $LoadCols FROM articles a WHERE a.slug = ?"), S(Param.S.V(slug.value)))
      .frag
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
              Some(Period(com.histmersion.api.catalogue.Year(a.value.toLong), com.histmersion.api.catalogue.Year(b.value.toLong)))
            case _ => None,
          updatedAt = Some(Article.UpdatedAt(r.updatedAt.value)),
          filter = filterOf(r.filter.map(_.value)),
          reasons = reasonsOf(r.reasons.value)
        )
      }

  def get(slug: com.histmersion.domain.research.Slug)(using DbCon): Option[Article.Row] =
    Sql
      .of(Sql.Text(s"SELECT $ViewCols FROM articles a WHERE a.slug = ?"), S(Param.S.V(canonical(slug).value)))
      .frag
      .query[Article.Row]
      .run()
      .headOption

  /** The article plus both directions of its link graph — one connection, three queries. */
  def view(requested: com.histmersion.domain.research.Slug)(using DbCon): Option[Article.View] =
    val slug = canonical(requested) // an old slug (V042) shows the article it was renamed to
    Sql
      .of(Sql.Text(s"SELECT $ViewCols FROM articles a WHERE a.slug = ?"), S(Param.S.V(slug.value)))
      .frag
      .query[Article.Row]
      .run()
      .headOption
      .map(row => Article.View(row, backlinks(slug), sourcesCited(slug)))

  /** What points HERE: other articles through `article_links`, and sources whose agent-written `notes` contain a
    * `[[slug]]` (the source notes are Markdown, not a link table). `strpos` rather than `LIKE` so an underscore in a
    * slug stays a literal underscore.
    */
  def backlinks(slug: com.histmersion.domain.research.Slug)(using DbCon): List[Backlink] =
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
  def citing(citekeys: List[com.histmersion.domain.library.Citekey], limit: com.histmersion.domain.research.articles.ArticleRepo.Limit)(using DbCon): List[EventArticleRef] =
    if citekeys.isEmpty then Nil
    else sql"""SELECT DISTINCT ON (a.slug) a.slug, a.title, l.target
            FROM article_links l JOIN articles a ON a.slug = l.src_slug
            WHERE l.target_kind = 'source' AND l.target = ANY($citekeys::text[])
            ORDER BY a.slug, l.target LIMIT $limit""".query[EventArticleRef].run().toList

  /** The other direction, for a SOURCE: which compiled articles cite it (`article_links` rows with
    * `target_kind = 'source'`). An index lookup, like [[backlinks]], because the classification was done at write time
    * — no LIKE scan of every article body.
    *
    * Reuses [[Backlink]] deliberately: "something that points at me, with a kind, an id and a title" is the same shape
    * whichever end you stand at, and a second near-identical DTO would only give the two sides a way to drift.
    */
  def backlinksForSource(citekey: com.histmersion.domain.library.Citekey)(using DbCon): List[Backlink] =
    sql"""SELECT 'article' AS kind, a.slug AS id, a.title
            FROM article_links l JOIN articles a ON a.slug = l.src_slug
            WHERE l.target_kind = 'source' AND l.target = $citekey
            ORDER BY a.title, a.slug""".query[Backlink].run().toList

  /** Which of `slugs` actually exist — the article half of resolving a `[[wikilink]]`. One query for the whole body, so
    * rendering an article is still three round trips.
    */
  def existingSlugs(slugs: List[com.histmersion.domain.research.Slug])(using DbCon): Set[com.histmersion.domain.research.Slug] =
    val wanted = slugs.map(_.value.trim.toLowerCase).filter(_.nonEmpty).distinct
    if wanted.isEmpty then Set.empty
    else
      sql"SELECT slug FROM articles WHERE slug = ANY($wanted::text[])"
        .query[com.histmersion.domain.research.Slug]
        .run()
        .toSet

  /** The link view (slug, title, summary) of the given articles; unknown slugs are absent. */
  def refs(slugs: List[com.histmersion.domain.research.Slug])(using DbCon): List[Article.Ref] =
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
  def sourcesCited(slug: com.histmersion.domain.research.Slug)(using DbCon): List[CitedSource] =
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
  def list(
      filter: Filter,
      scope: PlaceScope,
      kind: Option[com.histmersion.domain.research.Article.Kind],
      text: Option[com.histmersion.domain.library.search.Search.Query],
      limit: com.histmersion.domain.research.articles.ArticleRepo.Limit,
      offset: com.histmersion.Offset
  )(using DbCon): Article.List =
    val where = listWhere(filter, scope, kind, text)
    val order = text.map(t => com.histmersion.domain.library.search.Search.Query(t.value.trim)).filter(_.value.nonEmpty) match
      case Some(t) => Sql.raw(Sql.Text("ORDER BY ts_rank_cd(a.tsv, ")) ++ tsQuery(t) ++ ") DESC, a.title"
      case None    => Sql.raw(Sql.Text("ORDER BY a.kind, a.title"))
    val kinds = Article.List.byKind(
      (Sql.raw(Sql.Text("SELECT a.kind, count(*)::int FROM articles a WHERE ")) ++ where ++ " GROUP BY a.kind").frag
        .query[(String, Int)]
        .run()
        .toList
        .map((kind, n) => Article.KindCount(Article.Kind.Wire(kind), Article.KindCount.Count(n)))
    )
    val total = Article.List.Total(kinds.map(_.count.value).sum)
    val rows = (Sql.raw(Sql.Text(s"SELECT $SummaryCols FROM articles a WHERE ")) ++ where ++ " " ++
      order ++ Sql.of(Sql.Text(" LIMIT ? OFFSET ?"), I(Param.I.V(limit.value)), I(Param.I.V(offset.value.max(0))))).frag
      .query[ArticleSummaryRow]
      .run()
    Article.List.of(total, rows.toList.map(_.summaryRow), com.histmersion.api.personal.UnreadPage.Offset(offset.value.max(0)), kinds)

  /** The WHERE of [[list]], as a value — pure, so it is unit-tested without a database. The filter's part is
    * [[FilterSql.articleWhere]] (the one rule relating articles to sources).
    */
  def listWhere(filter: Filter, scope: PlaceScope, kind: Option[com.histmersion.domain.research.Article.Kind], text: Option[com.histmersion.domain.library.search.Search.Query]): Sql =
    val ps = List.newBuilder[Sql]
    val related = FilterSql.articleWhere(filter, scope)
    if related.text.value != "TRUE" then ps += related
    kind.map(_.value.trim.toLowerCase).filter(_.nonEmpty).foreach(k => ps += Sql.of(Sql.OfText("lower(a.kind) = ?"), S(Param.S.V(k))))
    text.map(_.value.trim).filter(_.nonEmpty).foreach { t =>
      val fts = Sql.raw(Sql.RawText("(a.tsv @@ ")) ++ tsQuery(com.histmersion.domain.library.search.Search.Query(t)) ++
        Sql.of(Sql.OfText(s" OR a.title ILIKE ?$Esc)"), S(Param.S.V(FilterSql.contains(t))))
      ps += fts
    }
    val parts = ps.result()
    if parts.isEmpty then Sql.raw(Sql.RawText("TRUE"))
    else Sql(Sql.Text(parts.map(_.text.value).mkString(" AND ")), parts.flatMap(_.params))

  private def tsQuery(q: com.histmersion.domain.library.search.Search.Query): Sql = Search.tsQuery(q)

  def slugs(using DbCon): Vector[com.histmersion.domain.research.Slug] =
    sql"""SELECT slug FROM articles ORDER BY slug COLLATE "C"""".query[com.histmersion.domain.research.Slug].run()

  /** `slug -> updated_at` in epoch millis, for the seed guard (one query for the whole wiki). */
  def updatedAtMillis(using DbCon): Map[com.histmersion.domain.research.Slug, com.histmersion.domain.library.sources.CitekeyMillis.Millis] =
    sql"SELECT slug, (extract(epoch from updated_at) * 1000)::bigint FROM articles"
      .query[SlugMillis]
      .run()
      .map(r => r.slug -> r.millis)
      .toMap

  // ── article versions (V013) ─────────────────────────────────────────────────────────────────

  /** The `updated_at` / `created_at` timestamps rendered UTC-normalised, as the article token does. */
  private val VerToken =
    "to_char(v.updated_at AT TIME ZONE 'UTC', 'YYYY-MM-DD\"T\"HH24:MI:SS.US\"Z\"')"
  private val VerCreated =
    "to_char(v.created_at AT TIME ZONE 'UTC', 'YYYY-MM-DD\"T\"HH24:MI:SS.US\"Z\"')"

  /** List the version history of one article: id, slug, summary (first 200 chars), updatedAt, createdAt, and word count
    * (approximated as non-empty whitespace-split tokens). Newest first.
    */
  def listVersions(slug: com.histmersion.domain.research.Slug, limit: com.histmersion.domain.research.articles.ArticleRepo.Limit = com.histmersion.domain.research.articles.ArticleRepo.Limit(20))(using DbCon): List[Article.Version] =
    Sql
      .of(
        Sql.Text(s"""SELECT v.id, v.slug, left(v.summary, 200),
            $VerToken, $VerCreated,
            array_length(regexp_split_to_array(trim(v.body), '\\s+'), 1)
            FROM article_versions v WHERE v.slug = ? ORDER BY v.created_at DESC LIMIT ?"""),
        Param.S(Param.S.V(slug.value)),
        Param.I(Param.I.V(limit.value))
      )
      .frag
      .query[Article.Version]
      .run()
      .toList

  /** One historical version's full body. */
  /** How many articles are stored. */
  def count()(using DbCon): com.histmersion.domain.research.articles.ArticleRepo.Count = Count(sql"SELECT count(*)::int FROM articles".query[Int].run().head.toLong)

  /** The newest saved version of an article: after a write, the row holding the text it replaced. */
  def latestVersion(slug: com.histmersion.domain.research.Slug)(using DbCon): Option[com.histmersion.domain.research.articles.ArticleRepo.VersionId] =
    sql"SELECT max(id) FROM article_versions WHERE slug = $slug".query[Option[Long]].run().headOption.flatten.map(VersionId(_))

  def getVersion(slug: com.histmersion.domain.research.Slug, versionId: com.histmersion.domain.research.articles.ArticleRepo.VersionId)(using DbCon): Option[Article.VersionFull] =
    Sql
      .of(
        Sql.Text(s"""SELECT v.id, v.slug, v.body, v.summary,
            $VerToken, $VerCreated
            FROM article_versions v WHERE v.slug = ? AND v.id = ?"""),
        Param.S(Param.S.V(slug.value)),
        Param.L(Param.L.V(versionId.value))
      )
      .frag
      .query[Article.VersionFull]
      .run()
      .headOption

  /** Every goal's linked articles that exist: goal → slugs. */
  /** Is there an article with this slug? */
  def slugExists(slug: com.histmersion.domain.research.Slug)(using DbCon): Boolean =
    sql"SELECT count(*) FROM articles WHERE slug = $slug".query[Long].run().head > 0

  def articlesByGoal(using DbCon): Map[com.histmersion.domain.automation.Goal.Slug, List[com.histmersion.domain.research.Slug]] =
    sql"""SELECT l.goal_slug, l.ref FROM goal_links l JOIN articles a ON a.slug = l.ref
            WHERE l.kind = 'article' ORDER BY l.goal_slug, l.ref"""
      .query[(com.histmersion.domain.automation.Goal.Slug, com.histmersion.domain.research.Slug)]
      .run()
      .toList
      .groupMap(_._1)(_._2)
