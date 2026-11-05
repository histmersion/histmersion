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

  type RenameInExistsS = RenameInExistsS.T
  object RenameInExistsS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type FilterJsonResult = FilterJsonResult.T
  object FilterJsonResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ReasonsJsonResult = ReasonsJsonResult.T
  object ReasonsJsonResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FilterOfJson = FilterOfJson.T
  object FilterOfJson extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ReasonsOfResultPart1 = ReasonsOfResultPart1.T
  object ReasonsOfResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ReasonsOfResultPart2 = ReasonsOfResultPart2.T
  object ReasonsOfResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ReasonsOfJson = ReasonsOfJson.T
  object ReasonsOfJson extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PutResult = PutResult.T
  object PutResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PatchResultPart2 = PatchResultPart2.T
  object PatchResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PatchSlug = PatchSlug.T
  object PatchSlug extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PatchBy = PatchBy.T
  object PatchBy extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DeleteResult = DeleteResult.T
  object DeleteResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DeleteSlug = DeleteSlug.T
  object DeleteSlug extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type WriteLinksSlug = WriteLinksSlug.T
  object WriteLinksSlug extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type WriteLinksBody = WriteLinksBody.T
  object WriteLinksBody extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RetargetLinksResultPart1 = RetargetLinksResultPart1.T
  object RetargetLinksResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RetargetLinksResultPart2 = RetargetLinksResultPart2.T
  object RetargetLinksResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RetargetLinksNeedle = RetargetLinksNeedle.T
  object RetargetLinksNeedle extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RetargetLinksToArg1 = RetargetLinksToArg1.T
  object RetargetLinksToArg1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RetargetLinksToResult = RetargetLinksToResult.T
  object RetargetLinksToResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RenameInResultPart2 = RenameInResultPart2.T
  object RenameInResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RenameInOldSlug = RenameInOldSlug.T
  object RenameInOldSlug extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RenameInNewSlug = RenameInNewSlug.T
  object RenameInNewSlug extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RenameResultPart2 = RenameResultPart2.T
  object RenameResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RenameOldSlug = RenameOldSlug.T
  object RenameOldSlug extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RenameNewSlug = RenameNewSlug.T
  object RenameNewSlug extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CanonicalResult = CanonicalResult.T
  object CanonicalResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CanonicalSlug = CanonicalSlug.T
  object CanonicalSlug extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TokenResult = TokenResult.T
  object TokenResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TokenSlug = TokenSlug.T
  object TokenSlug extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LoadSlug = LoadSlug.T
  object LoadSlug extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type GetSlug = GetSlug.T
  object GetSlug extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ViewRequested = ViewRequested.T
  object ViewRequested extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BacklinksSlug = BacklinksSlug.T
  object BacklinksSlug extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CitingCitekeys = CitingCitekeys.T
  object CitingCitekeys extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CitingLimit = CitingLimit.T
  object CitingLimit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ExistingSlugsResult = ExistingSlugsResult.T
  object ExistingSlugsResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ExistingSlugsSlugs = ExistingSlugsSlugs.T
  object ExistingSlugsSlugs extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RefsSlugs = RefsSlugs.T
  object RefsSlugs extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SourcesCitedSlug = SourcesCitedSlug.T
  object SourcesCitedSlug extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ListKind = ListKind.T
  object ListKind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ListText = ListText.T
  object ListText extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ListLimit = ListLimit.T
  object ListLimit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ListOffset = ListOffset.T
  object ListOffset extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ListWhereKind = ListWhereKind.T
  object ListWhereKind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ListWhereText = ListWhereText.T
  object ListWhereText extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TsQueryQ = TsQueryQ.T
  object TsQueryQ extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SlugsResult = SlugsResult.T
  object SlugsResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type UpdatedAtMillisResultPart1 = UpdatedAtMillisResultPart1.T
  object UpdatedAtMillisResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type UpdatedAtMillisResultPart2 = UpdatedAtMillisResultPart2.T
  object UpdatedAtMillisResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ListVersionsSlug = ListVersionsSlug.T
  object ListVersionsSlug extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ListVersionsLimit = ListVersionsLimit.T
  object ListVersionsLimit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CountResult = CountResult.T
  object CountResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LatestVersionResult = LatestVersionResult.T
  object LatestVersionResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LatestVersionSlug = LatestVersionSlug.T
  object LatestVersionSlug extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type GetVersionSlug = GetVersionSlug.T
  object GetVersionSlug extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type GetVersionVersionId = GetVersionVersionId.T
  object GetVersionVersionId extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SlugExistsResult = SlugExistsResult.T
  object SlugExistsResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SlugExistsSlug = SlugExistsSlug.T
  object SlugExistsSlug extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ArticlesByGoalResultPart1 = ArticlesByGoalResultPart1.T
  object ArticlesByGoalResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ArticlesByGoalResultPart2 = ArticlesByGoalResultPart2.T
  object ArticlesByGoalResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

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
  private def filterJson(a: Article): Option[com.histmersion.domain.research.articles.ArticleRepo.FilterJsonResult] =
    import io.circe.syntax.*
    a.filter.map(_.asJson.deepDropNullValues.noSpaces)
  private def reasonsJson(a: Article): com.histmersion.domain.research.articles.ArticleRepo.ReasonsJsonResult =
    io.circe.Json.obj(a.reasons.toList.sortBy(_._1).map((k, v) => k -> io.circe.Json.fromString(v))*).noSpaces

  /** The stored JSON back; a value that no longer decodes (an older shape) reads as none, never fails the read. */
  private[articles] def filterOf(json: Option[com.histmersion.domain.research.articles.ArticleRepo.FilterOfJson]): Option[Article.Filter] =
    json.flatMap(Jsonb.read[Article.Filter])
  private[articles] def reasonsOf(json: com.histmersion.domain.research.articles.ArticleRepo.ReasonsOfJson): Map[com.histmersion.domain.research.articles.ArticleRepo.ReasonsOfResultPart1, com.histmersion.domain.research.articles.ArticleRepo.ReasonsOfResultPart2] =
    Jsonb.read[Map[String, String]](json).getOrElse(Map.empty)

  // ── writes ─────────────────────────────────────────────────────────────────────────────────
  /** Full replace (`put_article`). Returns the fresh `updatedAt` concurrency token. */
  def put(a: Article)(using DbCon): com.histmersion.domain.research.articles.ArticleRepo.PutResult =
    write(a)
    token(a.slug).getOrElse("")

  /** Selective merge (`patch_article`): read → [[Articles.applyPatch]] → write, all in ONE transaction, so two
    * concurrent patches cannot interleave and `ifUpdatedAt` means something.
    */
  def patch(slug: com.histmersion.domain.research.articles.ArticleRepo.PatchSlug, p: Article.Patch, by: com.histmersion.domain.research.articles.ArticleRepo.PatchBy = "person")(using DbCon): Either[Api.Error, com.histmersion.domain.research.articles.ArticleRepo.PatchResultPart2] =
    // FOR UPDATE before the read: `ifUpdatedAt` is a compare-and-swap, and without the row lock
    // two concurrent patches both saw the token they were given and both "succeeded", the second
    // discarding the first. See the twin comment in `SourceRepo.patch`.
    val _ = sql"SELECT slug FROM articles WHERE slug = $slug FOR UPDATE".query[String].run()
    load(slug) match
      case None           => Left(Articles.notFound(slug))
      case Some(existing) =>
        Articles.precondition(existing, p) match
          case Left(msg) => Left(Api.Error(msg, Some(Write.CodePrecondition)))
          case Right(_)  =>
            Articles.applyPatch(existing, p) match
              case Left(msg) => Left(Api.Error(msg, Some(Write.CodeInvalid)))
              // the critique reasons (V091) sit beside the row: checked first, so a refused one
              // writes nothing; a patch of them alone leaves the article, its versions and its
              // token as they are (`by`: who set them, when a reason does not say)
              case Right(updated) =>
                CritiqueRepo
                  .applyIn(slug, p.critiqueReasons, by)(if !p.onlyCritiques then write(updated))
                  .map(_ => token(slug).getOrElse(""))

  def delete(slug: com.histmersion.domain.research.articles.ArticleRepo.DeleteSlug)(using DbCon): com.histmersion.domain.research.articles.ArticleRepo.DeleteResult =
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
    writeLinks(a.slug, a.body)

  /** Rewrite one article's outgoing links. A `[[x]]` is a SOURCE link iff x is citekey-shaped AND a source with that
    * citekey exists — resolved with one query over the citekey-shaped candidates.
    */
  private[histmersion] def writeLinks(slug: com.histmersion.domain.research.articles.ArticleRepo.WriteLinksSlug, body: com.histmersion.domain.research.articles.ArticleRepo.WriteLinksBody)(using DbCon): Unit =
    sql"DELETE FROM article_links WHERE src_slug = $slug".update.run()
    val written = Article.Links.targets(body).filter(_ != slug) // an article does not link to itself
    if written.nonEmpty then
      // an OLD citekey (V027) links the source it was renamed to, and is recorded under the new one
      // so `backlinksForSource` finds it
      val known = CitekeyResolver.stored.canonical(written.filter(Article.Links.isCitekeyShaped))
      val targets = written.map(t => known.getOrElse(t, t)).distinct
      val links = Article.Links.classify(targets, known.values.toSet.contains)
      val _ = batchUpdate(links)(l => sql"""INSERT INTO article_links (src_slug, target_kind, target)
              VALUES ($slug, ${l.targetKind}, ${l.target}) ON CONFLICT DO NOTHING""".update)

  /** Retarget wikilinks in every article whose body or summary mentions `needle` (case-insensitive) — the article half
    * of a citekey rename — and the coverage reasons (V066) keyed by a moved citekey. Each changed article goes through
    * [[write]], so the previous text is kept in `article_versions` and `article_links` is recomputed. Returns (slug,
    * links moved) for each article that changed.
    */
  private[histmersion] def retargetLinks(needle: com.histmersion.domain.research.articles.ArticleRepo.RetargetLinksNeedle, to: com.histmersion.domain.research.articles.ArticleRepo.RetargetLinksToArg1 => Option[com.histmersion.domain.research.articles.ArticleRepo.RetargetLinksToResult])(using
      DbCon
  ): List[(com.histmersion.domain.research.articles.ArticleRepo.RetargetLinksResultPart1, com.histmersion.domain.research.articles.ArticleRepo.RetargetLinksResultPart2)] =
    sql"""SELECT slug FROM articles
          WHERE strpos(lower(body || ' ' || coalesce(summary, '') || ' ' || coverage_reasons::text), ${needle.toLowerCase}) > 0
          ORDER BY slug""".query[String].run().toList.flatMap { slug =>
      load(slug).flatMap { a =>
        val (body, nb) = Article.Links.retarget(a.body, to)
        val summary = a.summary.map(Article.Links.retarget(_, to))
        val reasons = a.reasons.map((ck, r) => to(ck).getOrElse(ck) -> r)
        val nr = a.reasons.keys.count(to(_).isDefined)
        val moved = nb + summary.fold(0)(_._2) + nr
        Option.when(moved > 0) {
          write(a.modify(_.body).setTo(body).modify(_.summary).setTo(summary.map(_._1)).modify(_.reasons).setTo(reasons))
          slug -> moved
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
  private[histmersion] def renameIn(oldSlug: com.histmersion.domain.research.articles.ArticleRepo.RenameInOldSlug, newSlug: com.histmersion.domain.research.articles.ArticleRepo.RenameInNewSlug)(using DbCon): Either[Api.Error, List[com.histmersion.domain.research.articles.ArticleRepo.RenameInResultPart2]] =
    def exists(s: com.histmersion.domain.research.articles.ArticleRepo.RenameInExistsS) = sql"SELECT 1 FROM articles WHERE slug = $s".query[Int].run().nonEmpty
    val aliasOf = sql"SELECT slug FROM article_aliases WHERE alias = $newSlug".query[String].run().headOption
    if !exists(oldSlug) then Left(Articles.notFound(oldSlug))
    else if exists(newSlug) then
      Left(Api.Error(s"'$newSlug' is already the slug of another article", Some(Write.CodeInvalid)))
    else if aliasOf.exists(_ != oldSlug) then
      Left(
        Api.Error(
          s"'$newSlug' is an old slug of '${aliasOf.get}' and still resolves to it — pick another",
          Some(Write.CodeInvalid)
        )
      )
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

  /** `histmersion articles rename`: a new slug, the old one kept as an alias (V042); every `[[old]]` in other articles
    * is rewritten through the write path. One transaction; returns the articles rewritten.
    */
  def rename(oldSlug: com.histmersion.domain.research.articles.ArticleRepo.RenameOldSlug, newSlug: com.histmersion.domain.research.articles.ArticleRepo.RenameNewSlug)(using DbCon): Either[Api.Error, List[com.histmersion.domain.research.articles.ArticleRepo.RenameResultPart2]] =
    renameIn(oldSlug.trim, newSlug.trim)

  /** The slug an old slug was renamed to (`article_aliases`), or the slug itself. */
  def canonical(slug: com.histmersion.domain.research.articles.ArticleRepo.CanonicalSlug)(using DbCon): com.histmersion.domain.research.articles.ArticleRepo.CanonicalResult =
    sql"SELECT slug FROM article_aliases WHERE alias = $slug AND NOT EXISTS (SELECT 1 FROM articles WHERE slug = $slug)"
      .query[String]
      .run()
      .headOption
      .getOrElse(slug)

  private def token(slug: com.histmersion.domain.research.articles.ArticleRepo.TokenSlug)(using DbCon): Option[com.histmersion.domain.research.articles.ArticleRepo.TokenResult] =
    Sql.of(s"SELECT $Token FROM articles a WHERE a.slug = ?", S(slug)).frag.query[String].run().headOption

  // ── reads ──────────────────────────────────────────────────────────────────────────────────
  /** The whole article as the DOMAIN model — the input side of a patch, and what `export` renders. */
  def load(slug: com.histmersion.domain.research.articles.ArticleRepo.LoadSlug)(using DbCon): Option[Article] =
    Sql
      .of(s"SELECT $LoadCols FROM articles a WHERE a.slug = ?", S(slug))
      .frag
      .query[FullArticleRow]
      .run()
      .headOption
      .map { r =>
        Article(
          slug = r.slug,
          title = r.title,
          kind = r.kind,
          body = r.body,
          summary = r.summary,
          tags = r.tags,
          regions = r.regions,
          period = (r.periodStart, r.periodEnd) match
            case (Some(a), Some(b)) => Some(Period(a, b))
            case _                  => None,
          updatedAt = Some(r.updatedAt),
          filter = filterOf(r.filter),
          reasons = reasonsOf(r.reasons)
        )
      }

  def get(slug: com.histmersion.domain.research.articles.ArticleRepo.GetSlug)(using DbCon): Option[Article.Row] =
    Sql
      .of(s"SELECT $ViewCols FROM articles a WHERE a.slug = ?", S(canonical(slug)))
      .frag
      .query[Article.Row]
      .run()
      .headOption

  /** The article plus both directions of its link graph — one connection, three queries. */
  def view(requested: com.histmersion.domain.research.articles.ArticleRepo.ViewRequested)(using DbCon): Option[Article.View] =
    val slug = canonical(requested) // an old slug (V042) shows the article it was renamed to
    Sql
      .of(s"SELECT $ViewCols FROM articles a WHERE a.slug = ?", S(slug))
      .frag
      .query[Article.Row]
      .run()
      .headOption
      .map(row => Article.View(row, backlinks(slug), sourcesCited(slug)))

  /** What points HERE: other articles through `article_links`, and sources whose agent-written `notes` contain a
    * `[[slug]]` (the source notes are Markdown, not a link table). `strpos` rather than `LIKE` so an underscore in a
    * slug stays a literal underscore.
    */
  def backlinks(slug: com.histmersion.domain.research.articles.ArticleRepo.BacklinksSlug)(using DbCon): List[Backlink] =
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
  def citing(citekeys: List[com.histmersion.domain.research.articles.ArticleRepo.CitingCitekeys], limit: com.histmersion.domain.research.articles.ArticleRepo.CitingLimit)(using DbCon): List[EventArticleRef] =
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
  def existingSlugs(slugs: List[com.histmersion.domain.research.articles.ArticleRepo.ExistingSlugsSlugs])(using DbCon): Set[com.histmersion.domain.research.articles.ArticleRepo.ExistingSlugsResult] =
    val wanted = slugs.map(_.trim.toLowerCase).filter(_.nonEmpty).distinct
    if wanted.isEmpty then Set.empty
    else
      sql"SELECT slug FROM articles WHERE slug = ANY($wanted::text[])"
        .query[String]
        .run()
        .toSet

  /** The link view (slug, title, summary) of the given articles; unknown slugs are absent. */
  def refs(slugs: List[com.histmersion.domain.research.articles.ArticleRepo.RefsSlugs])(using DbCon): List[Article.Ref] =
    val wanted = slugs.map(_.trim.toLowerCase).filter(_.nonEmpty).distinct
    if wanted.isEmpty then Nil
    else
      sql"SELECT slug, title, summary FROM articles WHERE slug = ANY($wanted::text[]) ORDER BY slug"
        .query[Article.Ref]
        .run()
        .toList

  /** The sources this article cites, with the metadata the footer shows. A citekey that is not in the corpus still
    * appears (with a NULL title) — a dangling citation is a lint finding, not a reason to hide it.
    */
  def sourcesCited(slug: com.histmersion.domain.research.articles.ArticleRepo.SourcesCitedSlug)(using DbCon): List[CitedSource] =
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
      kind: Option[com.histmersion.domain.research.articles.ArticleRepo.ListKind],
      text: Option[com.histmersion.domain.research.articles.ArticleRepo.ListText],
      limit: com.histmersion.domain.research.articles.ArticleRepo.ListLimit,
      offset: com.histmersion.domain.research.articles.ArticleRepo.ListOffset
  )(using DbCon): Article.List =
    val where = listWhere(filter, scope, kind, text)
    val order = text.map(_.trim).filter(_.nonEmpty) match
      case Some(t) => Sql.raw("ORDER BY ts_rank_cd(a.tsv, ") ++ tsQuery(t) ++ Sql.raw(") DESC, a.title")
      case None    => Sql.raw("ORDER BY a.kind, a.title")
    val kinds = Article.List.byKind(
      (Sql.raw("SELECT a.kind, count(*)::int FROM articles a WHERE ") ++ where ++ Sql
        .raw(" GROUP BY a.kind")).frag.query[(String, Int)].run().toList.map(Article.KindCount(_, _))
    )
    val total = kinds.map(_.count).sum
    val rows = (Sql.raw(s"SELECT $SummaryCols FROM articles a WHERE ") ++ where ++ Sql.raw(" ") ++
      order ++ Sql.of(" LIMIT ? OFFSET ?", I(limit), I(offset.max(0)))).frag.query[ArticleSummaryRow].run()
    Article.List.of(total, rows.toList.map(_.summaryRow), offset.max(0), kinds)

  /** The WHERE of [[list]], as a value — pure, so it is unit-tested without a database. The filter's part is
    * [[FilterSql.articleWhere]] (the one rule relating articles to sources).
    */
  def listWhere(filter: Filter, scope: PlaceScope, kind: Option[com.histmersion.domain.research.articles.ArticleRepo.ListWhereKind], text: Option[com.histmersion.domain.research.articles.ArticleRepo.ListWhereText]): Sql =
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

  private def tsQuery(q: com.histmersion.domain.research.articles.ArticleRepo.TsQueryQ): Sql = Search.tsQuery(q)

  def slugs(using DbCon): Vector[com.histmersion.domain.research.articles.ArticleRepo.SlugsResult] =
    sql"""SELECT slug FROM articles ORDER BY slug COLLATE "C"""".query[String].run()

  /** `slug -> updated_at` in epoch millis, for the seed guard (one query for the whole wiki). */
  def updatedAtMillis(using DbCon): Map[com.histmersion.domain.research.articles.ArticleRepo.UpdatedAtMillisResultPart1, com.histmersion.domain.research.articles.ArticleRepo.UpdatedAtMillisResultPart2] =
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
  def listVersions(slug: com.histmersion.domain.research.articles.ArticleRepo.ListVersionsSlug, limit: com.histmersion.domain.research.articles.ArticleRepo.ListVersionsLimit = 20)(using DbCon): List[Article.Version] =
    Sql
      .of(
        s"""SELECT v.id, v.slug, left(v.summary, 200),
            $VerToken, $VerCreated,
            array_length(regexp_split_to_array(trim(v.body), '\\s+'), 1)
            FROM article_versions v WHERE v.slug = ? ORDER BY v.created_at DESC LIMIT ?""",
        Param.S(slug),
        Param.I(limit)
      )
      .frag
      .query[Article.Version]
      .run()
      .toList

  /** One historical version's full body. */
  /** How many articles are stored. */
  def count()(using DbCon): com.histmersion.domain.research.articles.ArticleRepo.CountResult = sql"SELECT count(*)::int FROM articles".query[Int].run().head.toLong

  /** The newest saved version of an article: after a write, the row holding the text it replaced. */
  def latestVersion(slug: com.histmersion.domain.research.articles.ArticleRepo.LatestVersionSlug)(using DbCon): Option[com.histmersion.domain.research.articles.ArticleRepo.LatestVersionResult] =
    sql"SELECT max(id) FROM article_versions WHERE slug = $slug".query[Option[Long]].run().headOption.flatten

  def getVersion(slug: com.histmersion.domain.research.articles.ArticleRepo.GetVersionSlug, versionId: com.histmersion.domain.research.articles.ArticleRepo.GetVersionVersionId)(using DbCon): Option[Article.VersionFull] =
    Sql
      .of(
        s"""SELECT v.id, v.slug, v.body, v.summary,
            $VerToken, $VerCreated
            FROM article_versions v WHERE v.slug = ? AND v.id = ?""",
        Param.S(slug),
        Param.L(versionId)
      )
      .frag
      .query[Article.VersionFull]
      .run()
      .headOption

  /** Every goal's linked articles that exist: goal → slugs. */
  /** Is there an article with this slug? */
  def slugExists(slug: com.histmersion.domain.research.articles.ArticleRepo.SlugExistsSlug)(using DbCon): com.histmersion.domain.research.articles.ArticleRepo.SlugExistsResult =
    sql"SELECT count(*) FROM articles WHERE slug = $slug".query[Long].run().head > 0

  def articlesByGoal(using DbCon): Map[com.histmersion.domain.research.articles.ArticleRepo.ArticlesByGoalResultPart1, List[com.histmersion.domain.research.articles.ArticleRepo.ArticlesByGoalResultPart2]] =
    sql"""SELECT l.goal_slug, l.ref FROM goal_links l JOIN articles a ON a.slug = l.ref
            WHERE l.kind = 'article' ORDER BY l.goal_slug, l.ref"""
      .query[(String, String)]
      .run()
      .toList
      .groupMap(_._1)(_._2)
