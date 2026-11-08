package com.histmersion
package domain.research.articles

import com.histmersion.api.library.{DeleteBlocker, DeleteCount, DeletePart}
import com.histmersion.domain.library.sources.referrers.*

import com.histmersion.util.database.given

import com.augustnagro.magnum.*
import com.histmersion.util.database.NewtypeColumns.given
import com.histmersion.api.library.Rename
import com.histmersion.api.research.Article
import com.histmersion.domain.library.Citekey
import com.histmersion.domain.research.Slug

/** The articles that cite a source (`[[citekey]]`, the link graph) or keep a coverage reason for it: a rename rewrites
  * their links through the article write path (each rewrite a recorded version), a citing article refuses a delete, and
  * a delete drops the reasons.
  */
final class ArticleSourceReferrer extends SourceReferrer:

  override def renamed(rename: SourceRename)(using DbCon): RenamedReferences =
    val links = sql"""UPDATE article_links SET target = ${rename.to}
                      WHERE target_kind = 'source' AND target = ${rename.from}""".update.run()
    RenamedReferences(
      List("article_links.target" -> links).collect {
        case (t, n) if n > 0 => Rename.Count(Rename.Count.Table(t), Rename.Count.Rows(n))
      },
      ArticleRepo
        .retargetLinks(
          Article.Link.Target(rename.needle.value),
          t =>
            Citekey
              .option(t.value)
              .flatMap(rename.target)
              .map(c => Article.Link.Target(c.value))
        )
        .map((slug, n) => (RenamedReferences.Text.Owner(slug.value), RenamedReferences.Text.Links(n.value)))
    )

  override def report(source: SourceKeys)(using DbCon): DeleteReport =
    val coverage = coverageOf(source.keys)
    DeleteReport(
      blockers = citing(source).map((slug, title) =>
        DeleteBlocker(DeleteBlocker.Kind.Article, DeleteBlocker.Id(slug.value), Some(DeleteBlocker.Title(title)))
      ),
      counts = List(DeletePart.CoverageReasons -> coverage.size).collect {
        case (p, rows) if rows > 0 => DeleteCount(p, DeleteCount.Rows(rows))
      },
      coverage = coverage
    )

  override def deleting(source: SourceKeys, report: DeleteReport)(using DbCon): Unit =
    if report.coverage.nonEmpty then
      sql"""UPDATE articles SET coverage_reasons = coverage_reasons - ${source.keys}::text[], updated_at = now()
            WHERE slug = ANY(${report.coverage}::text[])""".update.run()

  /** The articles whose body or summary cites the source: the recorded link graph (exact and old citekeys, resolved at
    * write time), and a scan of the text for the short forms the renderer resolves to it (`[[beckman2025]]` for
    * `beckman2025_ahhiyawa` while that is unambiguous).
    */
  private def citing(source: SourceKeys)(using DbCon): List[(Slug, String)] =
    val ck = source.citekey
    val linked = sql"""SELECT a.slug, a.title FROM article_links l JOIN articles a ON a.slug = l.src_slug
                       WHERE l.target_kind = 'source' AND l.target = $ck"""
      .query[(Slug, String)]
      .run()
      .toList
    val needles = source.keys.map(_.value.toLowerCase.takeWhile(_ != '_')).distinct
    val scanned = sql"""SELECT slug, title, body, summary FROM articles
                        WHERE EXISTS (SELECT 1 FROM unnest($needles::text[]) AS n(needle)
                                      WHERE strpos(lower(body || ' ' || coalesce(summary, '')), n.needle) > 0)"""
      .query[(Slug, String, String, Option[String])]
      .run()
      .toList
      .flatMap((slug, title, body, summary) =>
        Option.when(source.citedIn((body :: summary.toList).map(SourceKeys.Text(_))))(slug -> title)
      )
    (linked ++ scanned).distinctBy(_._1).sortBy(_._1.value)

  /** Articles whose coverage reasons (V066) are keyed by the source. */
  private def coverageOf(keys: List[Citekey])(using DbCon): List[DeleteReport.Coverage] =
    sql"""SELECT slug FROM articles a
          WHERE EXISTS (SELECT 1 FROM jsonb_object_keys(a.coverage_reasons) AS k(key) WHERE k.key = ANY($keys::text[]))
          ORDER BY slug""".query[DeleteReport.Coverage].run().toList
