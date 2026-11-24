package com.histmersion
package domain.research.articles

import com.histmersion.util.data.exchange.BundleImport
import com.histmersion.util.data.exchange.BundleEntities.BlobUse
import com.histmersion.util.data.exchange.{BlobSink, BundleScope}
import com.histmersion.util.data.exchange.BundleSql.*

import com.histmersion.util.database.given
import com.augustnagro.magnum.*
import com.histmersion.util.database.NewtypeColumns.given
import java.nio.charset.StandardCharsets.UTF_8
import java.util.UUID
import com.histmersion.api.exchange.Bundle
import com.histmersion.domain.research.Slug
import com.histmersion.util.data.exchange.PlanQuestions
import com.histmersion.util.database.Timestamps
import com.histmersion.util.database.Unused
import com.histmersion.domain.Creator
import com.histmersion.util.database.StoredCreator

/** The articles as a bundle carries them (docs/research-merge-import.md §4): each article, its versions (their bodies
  * as blobs) and its old slugs, read out in a deterministic order (by uid; the old slugs by alias, in the "C"
  * collation); and written in by an import: the renames the other side made, the rows, the versions a merge records, a
  * version's body as its blob arrives, and the links of what was written.
  */
private[articles] object ArticleBundleRepo:

  // ── out ──────────────────────────────────────────────────────────────────────────────────────

  def articles(scope: BundleScope)(using DbCon): Vector[Bundle.Article] =
    sql"""SELECT a.uid, a.origin, a.slug, a.title, a.kind, a.body, a.summary, a.tags, a.regions,
                       ${rangeCols(sql"a.period")}, ${Timestamps.iso(sql"a.updated_at")}
                FROM articles a WHERE ${where(scope, sql"a.uid", sql"a.updated_at")} ORDER BY a.uid"""
      .query[BArticleRow]
      .run()
      .map { r =>
        Bundle.Article(
          r.uid,
          r.origin,
          Bundle.Article.Slug(r.slug.value),
          Bundle.Article.Title(r.title.value),
          Bundle.Article.Kind(r.kind.value),
          Bundle.Article.Body(r.body.value),
          r.summary.map(s => Bundle.Article.Summary(s.value)),
          r.tags.map(t => Bundle.Article.Tags(t.value)),
          r.regions.map(g => Bundle.Article.Regions(g.value)),
          range(
            util.data.exchange.Bundle.RangeIsNull(r.pNull.value),
            r.pLo.map(l => util.data.exchange.Bundle.RangeLower(l.value)),
            r.pHi.map(h => util.data.exchange.Bundle.RangeUpper(h.value))
          ),
          Bundle.Article.UpdatedAt(r.updatedAt.value)
        )
      }

  def versions(scope: BundleScope)(using DbCon): Vector[BVersionRow] =
    sql"""SELECT v.uid, v.origin, a.uid, encode(sha256(convert_to(v.body, 'UTF8')), 'hex'),
                       octet_length(convert_to(v.body, 'UTF8'))::bigint, v.summary,
                       ${Timestamps.iso(sql"v.updated_at")}, ${Timestamps.iso(sql"v.created_at")}
                FROM article_versions v JOIN articles a ON a.slug = v.slug WHERE ${where(
        scope,
        sql"v.uid",
        sql"v.created_at"
      )} ORDER BY v.uid""".query[BVersionRow].run()

  /** Old article slugs (V042) of the articles in scope. */
  def articleAliases(scope: BundleScope)(using DbCon): Vector[Bundle.ArticleAlias] =
    sql"""SELECT x.alias, a.uid, ${Timestamps.iso(sql"x.created_at")}
                FROM article_aliases x JOIN articles a ON a.slug = x.slug WHERE ${where(
        scope,
        sql"a.uid",
        sql"x.created_at"
      )} ORDER BY x.alias COLLATE "C\""""
      .query[(Bundle.ArticleAlias.Alias, UUID, Bundle.ArticleAlias.CreatedAt)]
      .run()
      .map(Bundle.ArticleAlias.apply)

  def versionLine(r: BVersionRow): Bundle.ArticleVersion =
    Bundle.ArticleVersion(
      r.uid,
      r.origin,
      r.articleUid,
      Bundle.ArticleVersion.BodySha256(r.bodySha.value),
      r.summary.map(s => Bundle.ArticleVersion.Summary(s.value)),
      Bundle.ArticleVersion.VersionOf(r.versionOf.value),
      Bundle.ArticleVersion.UpdatedAt(r.createdAt.value)
    )

  /** The bytes of a version's body, written as a blob. */
  def versionBody(uid: UUID)(using DbCon): java.io.OutputStream => Unit =
    out =>
      out.write(
        sql"SELECT convert_to(body, 'UTF8') FROM article_versions WHERE uid = ${uid.toString}::uuid"
          .query[Array[Byte]]
          .run()
          .head
      )

  // ── in ───────────────────────────────────────────────────────────────────────────────────────

  /** The articles an import kept beside one here (`both`): their links are written in the derived step. */
  private val copiedArticles = BundleImport.Once(_ => scala.collection.mutable.LinkedHashSet.empty[UUID])

  /** The bundle's versions by uid, for their bodies as they arrive. */
  private val versionsByUid = BundleImport.Once(w => w.es.versions.map(v => v.uid -> v).toMap)

  private def slugOf(u: UUID)(using DbCon): Slug =
    sql"SELECT slug FROM articles WHERE uid = $u".query[Slug].run().head

  /** Rename what the other side renamed (the same uid, a new slug), through the ordinary rename path. */
  def articleRenamesIn(w: BundleImport.Writer): Unit =
    given DbCon = w.transaction
    given Creator.New = Creator.New.FromBundle
    w.followRenames(
      w.es.articles,
      _.uid,
      _.slug,
      w.local.articles.map(a => a.uid -> a.slug).toMap,
      PlanQuestions.LabelTakenWhat("article")
    )((old, now) =>
      ArticleRepo.renameIn(
        Slug.assume(old.value),
        Slug.assume(now.value)
      )
    )

  /** The articles: the text a merge replaces kept as a version first, the rows, and an article kept beside one here
    * (`both`).
    */
  def articlesIn(w: BundleImport.Writer): Unit =
    given DbCon = w.transaction
    w.merged.snapshots.toList.filter(w.writes).foreach { u =>
      Unused(
        sql"""INSERT INTO article_versions (slug, body, summary, updated_at, creator)
            SELECT slug, body, summary, updated_at, ${StoredCreator.write(
            Creator.unrecorded
          )}::jsonb FROM articles WHERE uid = $u""".update.run(),
        Unused.Why.Idempotent
      )
    }
    w.es.articles.filter(a => w.writes(a.uid)).foreach { a =>
      sql"""INSERT INTO articles (uid, origin, slug, title, kind, body, summary, tags, regions, period, updated_at, creator)
            VALUES (${a.uid}, ${a.origin}, ${a.slug}, ${a.title}, ${a.kind}, ${a.body}, ${a.summary},
              ${a.tags}::text[], ${a.regions}::text[],
              ${rangeLiteral(a.period)}::int8range,
              ${a.updatedAt}::timestamptz, ${StoredCreator.write(Creator.unrecorded)}::jsonb)
            ON CONFLICT (uid) DO UPDATE SET origin = EXCLUDED.origin, title = EXCLUDED.title,
              kind = EXCLUDED.kind, body = EXCLUDED.body, summary = EXCLUDED.summary, tags = EXCLUDED.tags,
              regions = EXCLUDED.regions, period = EXCLUDED.period, updated_at = EXCLUDED.updated_at""".update.run()
    }
    // an article kept beside one here (`both`), and the bundle's texts the merge replaced or
    // rewrote, recorded as versions: the next merge finds them in this collection's history
    w.merged.copies.foreach { a =>
      Unused(
        sql"""INSERT INTO articles (uid, slug, title, kind, body, summary, tags, regions, period, creator)
            VALUES (${a.uid}, ${a.slug}, ${a.title}, ${a.kind}, ${a.body}, ${a.summary}, ${a.tags}::text[],
              ${a.regions}::text[],
              ${rangeLiteral(a.period)}::int8range, ${StoredCreator.write(Creator.unrecorded)}::jsonb)
            ON CONFLICT (uid) DO NOTHING""".update.run(),
        Unused.Why.Idempotent
      )
      w.once(copiedArticles) += a.uid
    }

  /** Old slugs, as old citekeys: never one that is a slug here, never one another article has. */
  def articleAliasesIn(w: BundleImport.Writer): Unit =
    given DbCon = w.transaction
    w.es.articleAliases.foreach { a =>
      Unused(
        sql"""INSERT INTO article_aliases (alias, slug, created_at)
            SELECT ${a.alias}, slug, ${a.createdAt}::timestamptz FROM articles
            WHERE uid = ${a.articleUid} AND slug <> ${a.alias}
              AND NOT EXISTS (SELECT 1 FROM articles WHERE slug = ${a.alias})
            ON CONFLICT (alias) DO NOTHING""".update.run(),
        Unused.Why.Idempotent
      )
    }

  /** The versions a merge records beside the article texts it wrote. */
  def mergedVersionsIn(w: BundleImport.Writer): Unit =
    given DbCon = w.transaction
    w.merged.versions.foreach { v =>
      Unused(
        sql"""INSERT INTO article_versions (slug, body, summary, updated_at, creator)
            SELECT slug, ${v.body}, ${v.summary}, ${v.versionOf}::timestamptz, ${StoredCreator.write(
            Creator.unrecorded
          )}::jsonb FROM articles WHERE uid = ${v.articleUid}""".update
          .run(),
        Unused.Why.Idempotent
      )
    }

  /** A version's text into its row. */
  def blobIn(use: BlobUse, w: BundleImport.Writer): Option[BlobSink] =
    given DbCon = w.transaction
    use match
      case BlobUse.VersionBody(u) =>
        Some(BlobSink.Held { bytes =>
          val v = w.once(versionsByUid)(u)
          sql"""INSERT INTO article_versions (uid, origin, slug, body, summary, updated_at, created_at, creator)
                VALUES (${v.uid}, ${v.origin}, ${slugOf(v.articleUid)}, ${String(bytes, UTF_8)},
                  ${v.summary}, ${v.versionOf}::timestamptz, ${v.updatedAt}::timestamptz, ${StoredCreator.write(
              Creator.unrecorded
            )}::jsonb)
                ON CONFLICT (uid) DO UPDATE SET origin = EXCLUDED.origin, slug = EXCLUDED.slug,
                  body = EXCLUDED.body, summary = EXCLUDED.summary,
                  updated_at = EXCLUDED.updated_at, created_at = EXCLUDED.created_at""".update.run(): Unit
        })
      case _ => None

  /** The links of the articles written — of every article when a new source can turn an `[[x]]` that named nothing into
    * a source link.
    */
  def articleLinksIn(w: BundleImport.Writer): Unit =
    given DbCon = w.transaction
    val relink =
      if w.insertedSources.nonEmpty || w.merged.renames.nonEmpty then
        sql"SELECT slug, body FROM articles ORDER BY slug"
          .query[(Slug, BArticleRow.Body)]
          .run()
          .toList
      else
        (w.es.articles.filter(a => w.writes(a.uid)).map(_.uid) ++ w.once(copiedArticles)).flatMap(u =>
          sql"SELECT slug, body FROM articles WHERE uid = $u"
            .query[(Slug, BArticleRow.Body)]
            .run()
            .headOption
        )
    relink.foreach((slug, body) => ArticleRepo.writeLinks(slug, body))
