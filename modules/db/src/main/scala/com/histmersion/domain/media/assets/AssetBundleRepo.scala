package com.histmersion
package domain.media.assets

import com.histmersion.util.data.exchange.BundleImport
import com.histmersion.util.data.exchange.BundleScope
import com.histmersion.util.data.exchange.BundleSql.*

import com.histmersion.util.database.given
import com.histmersion.util.database.NewtypeColumns.given
import com.augustnagro.magnum.*
import java.util.UUID
import com.histmersion.api.exchange.Bundle
import com.histmersion.api.Sha256
import com.histmersion.util.database.Timestamps
import com.histmersion.util.database.Unused
import com.histmersion.domain.Creator
import com.histmersion.util.database.StoredCreator

/** The figures as a bundle carries them (docs/research-merge-import.md §4): this collection's assets with their
  * source's uid, read out in uid order, the bytes of each one a blob — and written in by an import, the metadata of the
  * ones whose bytes are here already first, the rest as their bytes arrive.
  */
private[assets] object AssetBundleRepo:

  // ── out ──────────────────────────────────────────────────────────────────────────────────────

  def assets(scope: BundleScope)(using DbCon): Vector[AssetBundleRow] =
    sql"""SELECT x.uid, x.origin, s.uid, x.name, x.mime, x.sha256, length(x.bytes)::bigint,
                       x.width, x.height, x.kind, x.caption, x.license, encode(ST_AsEWKB(x.geom), 'hex'),
                       ${rangeCols(sql"x.period")}, ${Timestamps.iso(sql"x.updated_at")}
                FROM assets x JOIN sources s ON s.citekey = x.citekey WHERE ${where(
        scope,
        sql"x.uid",
        sql"x.updated_at"
      )} ORDER BY x.uid""".query[AssetBundleRow].run()

  def line(r: AssetBundleRow): Bundle.Asset =
    Bundle.Asset(
      r.uid,
      r.origin,
      r.sourceUid,
      Bundle.Asset.Name(r.name.value),
      Bundle.Asset.Mime(r.mime.value),
      Sha256(r.sha.value),
      r.width.map(w => Bundle.Asset.Width(w.value)),
      r.height.map(h => Bundle.Asset.Height(h.value)),
      r.kind.map(k => Bundle.Asset.Kind(k.value)),
      r.caption.map(c => Bundle.Asset.Caption(c.value)),
      r.license.map(l => Bundle.Asset.License(l.value)),
      r.geom.map(g => Bundle.Asset.Geom(g.value)),
      range(
        util.data.exchange.Bundle.RangeIsNull(r.pNull.value),
        r.pLo.map(lo => util.data.exchange.Bundle.RangeLower(lo.value)),
        r.pHi.map(hi => util.data.exchange.Bundle.RangeUpper(hi.value))
      ),
      Bundle.Asset.UpdatedAt(r.updatedAt.value)
    )

  /** The bytes of one asset, written as a blob. */
  def bytes(uid: UUID)(using DbCon): java.io.OutputStream => Unit =
    out =>
      out.write(
        sql"SELECT bytes FROM assets WHERE uid = ${uid.toString}::uuid"
          .query[Array[Byte]]
          .run()
          .head
      )

  // ── in ───────────────────────────────────────────────────────────────────────────────────────

  /** The bundle's assets by uid, computed once per import. */
  val byUid: BundleImport.Once[Map[UUID, Bundle.Asset]] = BundleImport.Once(w => w.es.assets.map(a => a.uid -> a).toMap)

  /** Asset metadata whose bytes are already here (same sha): no blob needed. */
  def metadataIn(w: BundleImport.Writer): Unit =
    val localSha = w.local.assets.map(a => a.uid -> a.sha256).toMap
    w.es.assets.filter(a => w.writes(a.uid) && localSha.get(a.uid).contains(a.sha256)).foreach(write(w, _, None))

  /** One asset written: with its bytes when they came in the bundle, else its metadata over the row here. */
  def write(w: BundleImport.Writer, a: Bundle.Asset, bytes: Option[AssetBytesRow.Bytes]): Unit =
    given DbCon = w.transaction
    w.touchSource(a.sourceUid)
    val ck = w.citekeyOf(a.sourceUid)
    bytes match
      case Some(b) =>
        sql"""INSERT INTO assets (uid, origin, citekey, name, mime, bytes, sha256, width, height, kind, caption,
                license, geom, period, updated_at, creator)
              VALUES (${a.uid}, ${a.origin}, $ck, ${a.name}, ${a.mime}, $b, ${a.sha256}, ${a.width}, ${a.height},
                ${a.kind}, ${a.caption}, ${a.license}, ST_GeomFromEWKB(decode(${a.geom}, 'hex')),
                ${rangeLiteral(a.period)}::int8range,
                ${a.updatedAt}::timestamptz, ${StoredCreator.write(Creator.unrecorded)}::jsonb)
              ON CONFLICT (uid) DO UPDATE SET origin = EXCLUDED.origin, name = EXCLUDED.name,
                mime = EXCLUDED.mime, bytes = EXCLUDED.bytes, sha256 = EXCLUDED.sha256, width = EXCLUDED.width,
                height = EXCLUDED.height, kind = EXCLUDED.kind, caption = EXCLUDED.caption,
                license = EXCLUDED.license, geom = EXCLUDED.geom, period = EXCLUDED.period,
                updated_at = EXCLUDED.updated_at""".update.run()
      case None =>
        Unused(
          sql"""UPDATE assets SET origin = ${a.origin}, name = ${a.name}, mime = ${a.mime}, width = ${a.width},
                height = ${a.height}, kind = ${a.kind}, caption = ${a.caption}, license = ${a.license},
                geom = ST_GeomFromEWKB(decode(${a.geom}, 'hex')),
                period = ${rangeLiteral(a.period)}::int8range,
                updated_at = ${a.updatedAt}::timestamptz
              WHERE uid = ${a.uid}""".update.run(),
          Unused.Why.Held
        )

  // ── a merge ──────────────────────────────────────────────────────────────────────────────────

  /** (source uid, asset name) → asset uid here, for the sources of `assets`. */
  def namesHere(sourceUids: List[UUID])(using DbCon): Map[(UUID, AssetBundleRow.Name), UUID] =
    if sourceUids.isEmpty then Map.empty
    else
      sql"""SELECT s.uid, x.name, x.uid FROM assets x JOIN sources s ON s.citekey = x.citekey
               WHERE s.uid = ANY(${sourceUids.map(_.toString).distinct}::uuid[])"""
        .query[(UUID, String, UUID)]
        .run()
        .map((s, n, u) => (s, AssetBundleRow.Name(n)) -> u)
        .toMap
