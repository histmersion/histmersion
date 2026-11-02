package com.histmersion.domain.research.articles

import com.histmersion.util.data.exchange.BundleImport
import com.histmersion.util.database.{Param, Sql}
import com.histmersion.util.data.exchange.BundleEntities.BlobUse
import com.histmersion.util.data.exchange.{BlobSink, BundleScope}
import com.histmersion.util.data.exchange.BundleSql.*

import com.histmersion.util.database.given
import com.augustnagro.magnum.*
import java.nio.charset.StandardCharsets.UTF_8
import java.util.UUID
import com.histmersion.api.exchange.{BundleArticle, BundleArticleAlias, BundleArticleVersion}

/** The articles as a bundle carries them (docs/research-merge-import.md §4): each article, its versions (their bodies
  * as blobs) and its old slugs, read out in a deterministic order (by uid; the old slugs by alias, in the "C"
  * collation); and written in by an import: the renames the other side made, the rows, the versions a merge records, a
  * version's body as its blob arrives, and the links of what was written.
  */
private[articles] object ArticleBundleRepo:

  // ── out ──────────────────────────────────────────────────────────────────────────────────────

  def articles(scope: BundleScope)(using DbCon): Vector[BundleArticle] =
    (Sql.raw(s"""SELECT a.uid, a.origin, a.slug, a.title, a.kind, a.body, a.summary, a.tags, a.regions,
                       ${rangeCols("a.period")}, ${ts("a.updated_at")}
                FROM articles a WHERE """) ++ where(scope, "a.uid", "a.updated_at") ++ " ORDER BY a.uid").frag
      .query[BArticleRow]
      .run()
      .map { r =>
        BundleArticle(
          r.uid,
          r.origin,
          r.slug,
          r.title,
          r.kind,
          r.body,
          r.summary,
          r.tags,
          r.regions,
          range(r.pNull, r.pLo, r.pHi),
          r.updatedAt
        )
      }

  def versions(scope: BundleScope)(using DbCon): Vector[BVersionRow] =
    (Sql.raw(s"""SELECT v.uid, v.origin, a.uid, encode(sha256(convert_to(v.body, 'UTF8')), 'hex'),
                       octet_length(convert_to(v.body, 'UTF8'))::bigint, v.summary,
                       ${ts("v.updated_at")}, ${ts("v.created_at")}
                FROM article_versions v JOIN articles a ON a.slug = v.slug WHERE """) ++
      where(scope, "v.uid", "v.created_at") ++ " ORDER BY v.uid").frag.query[BVersionRow].run()

  /** Old article slugs (V042) of the articles in scope. */
  def articleAliases(scope: BundleScope)(using DbCon): Vector[BundleArticleAlias] =
    (Sql.raw(s"""SELECT x.alias, a.uid, ${ts("x.created_at")}
                FROM article_aliases x JOIN articles a ON a.slug = x.slug WHERE """) ++
      where(scope, "a.uid", "x.created_at") ++ """ ORDER BY x.alias COLLATE "C"""").frag
      .query[(String, UUID, String)]
      .run()
      .map(BundleArticleAlias.apply)

  def versionLine(r: BVersionRow): BundleArticleVersion =
    BundleArticleVersion(r.uid, r.origin, r.articleUid, r.bodySha, r.summary, r.versionOf, r.createdAt)

  /** The bytes of a version's body, written as a blob. */
  def versionBody(uid: UUID)(using DbCon): java.io.OutputStream => Unit =
    out =>
      out.write(
        Sql
          .of("SELECT convert_to(body, 'UTF8') FROM article_versions WHERE uid = ?::uuid", Param.S(uid.toString))
          .frag
          .query[Array[Byte]]
          .run()
          .head
      )

  // ── in ───────────────────────────────────────────────────────────────────────────────────────

  /** The articles an import kept beside one here (`both`): their links are written in the derived step. */
  private val copiedArticles = BundleImport.Once(_ => scala.collection.mutable.LinkedHashSet.empty[UUID])

  /** The bundle's versions by uid, for their bodies as they arrive. */
  private val versionsByUid = BundleImport.Once(w => w.es.versions.map(v => v.uid -> v).toMap)

  private def slugOf(u: UUID)(using DbCon): String =
    sql"SELECT slug FROM articles WHERE uid = $u".query[String].run().head

  /** Rename what the other side renamed (the same uid, a new slug), through the ordinary rename path. */
  def articleRenamesIn(w: BundleImport.Writer): Unit =
    given DbCon = w.transaction
    w.followRenames(w.es.articles, _.uid, _.slug, w.local.articles.map(a => a.uid -> a.slug).toMap, "article")(
      ArticleRepo.renameIn
    )

  /** The articles: the text a merge replaces kept as a version first, the rows, and an article kept beside one here
    * (`both`).
    */
  def articlesIn(w: BundleImport.Writer): Unit =
    given DbCon = w.transaction
    w.merged.snapshots.toList.filter(w.writes).foreach { u =>
      sql"""INSERT INTO article_versions (slug, body, summary, updated_at)
            SELECT slug, body, summary, updated_at FROM articles WHERE uid = $u""".update.run()
    }
    w.es.articles.filter(a => w.writes(a.uid)).foreach { a =>
      sql"""INSERT INTO articles (uid, origin, slug, title, kind, body, summary, tags, regions, period, updated_at)
            VALUES (${a.uid}, ${a.origin}, ${a.slug}, ${a.title}, ${a.kind}, ${a.body}, ${a.summary},
              ${a.tags}::text[], ${a.regions}::text[],
              ${rangeLiteral(a.period)}::int8range,
              ${a.updatedAt}::timestamptz)
            ON CONFLICT (uid) DO UPDATE SET origin = EXCLUDED.origin, title = EXCLUDED.title,
              kind = EXCLUDED.kind, body = EXCLUDED.body, summary = EXCLUDED.summary, tags = EXCLUDED.tags,
              regions = EXCLUDED.regions, period = EXCLUDED.period, updated_at = EXCLUDED.updated_at""".update.run()
    }
    // an article kept beside one here (`both`), and the bundle's texts the merge replaced or
    // rewrote, recorded as versions: the next merge finds them in this collection's history
    w.merged.copies.foreach { a =>
      sql"""INSERT INTO articles (uid, slug, title, kind, body, summary, tags, regions, period)
            VALUES (${a.uid}, ${a.slug}, ${a.title}, ${a.kind}, ${a.body}, ${a.summary}, ${a.tags}::text[],
              ${a.regions}::text[],
              ${rangeLiteral(a.period)}::int8range)
            ON CONFLICT (uid) DO NOTHING""".update.run()
      w.once(copiedArticles) += a.uid
    }

  /** Old slugs, as old citekeys: never one that is a slug here, never one another article has. */
  def articleAliasesIn(w: BundleImport.Writer): Unit =
    given DbCon = w.transaction
    w.es.articleAliases.foreach { a =>
      sql"""INSERT INTO article_aliases (alias, slug, created_at)
            SELECT ${a.alias}, slug, ${a.createdAt}::timestamptz FROM articles
            WHERE uid = ${a.articleUid} AND slug <> ${a.alias}
              AND NOT EXISTS (SELECT 1 FROM articles WHERE slug = ${a.alias})
            ON CONFLICT (alias) DO NOTHING""".update.run()
    }

  /** The versions a merge records beside the article texts it wrote. */
  def mergedVersionsIn(w: BundleImport.Writer): Unit =
    given DbCon = w.transaction
    w.merged.versions.foreach { v =>
      sql"""INSERT INTO article_versions (slug, body, summary, updated_at)
            SELECT slug, ${v.body}, ${v.summary}, ${v.versionOf}::timestamptz FROM articles WHERE uid = ${v.articleUid}""".update
        .run()
    }

  /** A version's text into its row. */
  def blobIn(use: BlobUse, w: BundleImport.Writer): Option[BlobSink] =
    given DbCon = w.transaction
    use match
      case BlobUse.VersionBody(u) =>
        Some(BlobSink.Held { bytes =>
          val v = w.once(versionsByUid)(u)
          sql"""INSERT INTO article_versions (uid, origin, slug, body, summary, updated_at, created_at)
                VALUES (${v.uid}, ${v.origin}, ${slugOf(v.articleUid)}, ${String(bytes, UTF_8)},
                  ${v.summary}, ${v.versionOf}::timestamptz, ${v.updatedAt}::timestamptz)
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
        sql"SELECT slug, body FROM articles ORDER BY slug".query[(String, String)].run().toList
      else
        (w.es.articles.filter(a => w.writes(a.uid)).map(_.uid) ++ w.once(copiedArticles)).flatMap(u =>
          sql"SELECT slug, body FROM articles WHERE uid = $u".query[(String, String)].run().headOption
        )
    relink.foreach((slug, body) => ArticleRepo.writeLinks(slug, body))
