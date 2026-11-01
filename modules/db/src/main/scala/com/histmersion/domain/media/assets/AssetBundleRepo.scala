package com.histmersion.domain.media.assets

import com.histmersion.api.BundleAsset
import com.histmersion.db.{BundleImport, Param, Sql}
import com.histmersion.util.data.exchange.BundleScope
import com.histmersion.util.data.exchange.BundleSql.*

import com.histmersion.db.given
import com.augustnagro.magnum.*
import java.util.UUID

/** The figures as a bundle carries them (docs/research-merge-import.md §4): this collection's assets with their
  * source's uid, read out in uid order, the bytes of each one a blob — and written in by an import, the metadata of the
  * ones whose bytes are here already first, the rest as their bytes arrive.
  */
private[assets] object AssetBundleRepo:

  // ── out ──────────────────────────────────────────────────────────────────────────────────────

  def assets(scope: BundleScope)(using DbCon): Vector[AssetBundleRow] =
    (Sql.raw(s"""SELECT x.uid, x.origin, s.uid, x.name, x.mime, x.sha256, length(x.bytes)::bigint,
                       x.width, x.height, x.kind, x.caption, x.license, encode(ST_AsEWKB(x.geom), 'hex'),
                       ${rangeCols("x.period")}, ${ts("x.updated_at")}
                FROM assets x JOIN sources s ON s.citekey = x.citekey WHERE """) ++
      where(scope, "x.uid", "x.updated_at") ++ " ORDER BY x.uid").frag.query[AssetBundleRow].run()

  def line(r: AssetBundleRow): BundleAsset =
    BundleAsset(
      r.uid,
      r.origin,
      r.sourceUid,
      r.name,
      r.mime,
      r.sha,
      r.width,
      r.height,
      r.kind,
      r.caption,
      r.license,
      r.geom,
      range(r.pNull, r.pLo, r.pHi),
      r.updatedAt
    )

  /** The bytes of one asset, written as a blob. */
  def bytes(uid: UUID)(using DbCon): java.io.OutputStream => Unit =
    out =>
      out.write(
        Sql.of("SELECT bytes FROM assets WHERE uid = ?::uuid", Param.S(uid.toString)).frag.query[Array[Byte]].run().head
      )

  // ── in ───────────────────────────────────────────────────────────────────────────────────────

  /** The bundle's assets by uid, computed once per import. */
  val byUid: BundleImport.Once[Map[UUID, BundleAsset]] = BundleImport.Once(w => w.es.assets.map(a => a.uid -> a).toMap)

  /** Asset metadata whose bytes are already here (same sha): no blob needed. */
  def metadataIn(w: BundleImport.Writer): Unit =
    val localSha = w.local.assets.map(a => a.uid -> a.sha256).toMap
    w.es.assets.filter(a => w.writes(a.uid) && localSha.get(a.uid).contains(a.sha256)).foreach(write(w, _, None))

  /** One asset written: with its bytes when they came in the bundle, else its metadata over the row here. */
  def write(w: BundleImport.Writer, a: BundleAsset, bytes: Option[Array[Byte]]): Unit =
    given DbCon = w.transaction
    w.touchSource(a.sourceUid)
    val ck = w.citekeyOf(a.sourceUid)
    bytes match
      case Some(b) =>
        sql"""INSERT INTO assets (uid, origin, citekey, name, mime, bytes, sha256, width, height, kind, caption,
                license, geom, period, updated_at)
              VALUES (${a.uid}, ${a.origin}, $ck, ${a.name}, ${a.mime}, $b, ${a.sha256}, ${a.width}, ${a.height},
                ${a.kind}, ${a.caption}, ${a.license}, ST_GeomFromEWKB(decode(${a.geom}, 'hex')),
                ${rangeLiteral(a.period)}::int8range,
                ${a.updatedAt}::timestamptz)
              ON CONFLICT (uid) DO UPDATE SET origin = EXCLUDED.origin, name = EXCLUDED.name,
                mime = EXCLUDED.mime, bytes = EXCLUDED.bytes, sha256 = EXCLUDED.sha256, width = EXCLUDED.width,
                height = EXCLUDED.height, kind = EXCLUDED.kind, caption = EXCLUDED.caption,
                license = EXCLUDED.license, geom = EXCLUDED.geom, period = EXCLUDED.period,
                updated_at = EXCLUDED.updated_at""".update.run()
      case None =>
        sql"""UPDATE assets SET origin = ${a.origin}, name = ${a.name}, mime = ${a.mime}, width = ${a.width},
                height = ${a.height}, kind = ${a.kind}, caption = ${a.caption}, license = ${a.license},
                geom = ST_GeomFromEWKB(decode(${a.geom}, 'hex')),
                period = ${rangeLiteral(a.period)}::int8range,
                updated_at = ${a.updatedAt}::timestamptz
              WHERE uid = ${a.uid}""".update.run()

  // ── a merge ──────────────────────────────────────────────────────────────────────────────────

  /** (source uid, asset name) → asset uid here, for the sources of `assets`. */
  def namesHere(sourceUids: List[UUID])(using DbCon): Map[(UUID, String), UUID] =
    if sourceUids.isEmpty then Map.empty
    else
      sql"""SELECT s.uid, x.name, x.uid FROM assets x JOIN sources s ON s.citekey = x.citekey
               WHERE s.uid = ANY(${sourceUids.map(_.toString).distinct}::uuid[])"""
        .query[(UUID, String, UUID)]
        .run()
        .map((s, n, u) => (s, n) -> u)
        .toMap
