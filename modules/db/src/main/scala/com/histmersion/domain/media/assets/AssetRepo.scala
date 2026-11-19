package com.histmersion
package domain.media.assets
import com.histmersion.util.problem.{Legacy, Problem}

import com.histmersion.util.database.RangeSql

import com.histmersion.api.Write

import com.histmersion.util.database.given
import com.histmersion.util.database.NewtypeColumns.given
import com.augustnagro.magnum.*
import com.histmersion.api.media.{AssetDetail, AssetInfo, AssetPatch}
import com.histmersion.api.Api
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.domain.library.Citekey
import com.histmersion.api.ByteCount
import com.histmersion.api.exchange.Bundle
import com.histmersion.api.media.ImageSize
import com.histmersion.api.media.MediaItem
import com.histmersion.api.catalogue.Latitude
import com.histmersion.api.catalogue.Longitude
import com.histmersion.api.catalogue.Year
import com.histmersion.domain.DeepTime

/** The figures that belong with a source's Markdown (plan §1.8).
  *
  * Stored as bytea keyed by `(citekey, name)`, which is exactly the `assets/<name>` link the body carries — so serving
  * one is a primary-key lookup, and re-running a conversion replaces the set (keeping the rows of the pictures it still
  * has, see [[replace]]). Within a source the same picture (a logo on every page) is stored ONCE: writes are
  * deduplicated by sha256, and the later names are dropped. Every method runs on the connection it is given.
  */
private[assets] object AssetRepo:
  type ReplaceExisting = ReplaceExisting.T
  object ReplaceExisting extends Newtype[Boolean, Pure]

  /** REPLACE the whole set for one source (what a re-conversion does). Returns how many were kept after sha256
    * deduplication.
    *
    * The set is replaced, the ROWS are not (#826): an asset the new set still has keeps its uid, its descriptive
    * metadata and its asset links — the same name is updated in place, and a picture that only moved to a new name (the
    * same sha256) is renamed, its links following by V027's ON UPDATE CASCADE. Only what the new conversion no longer
    * has is deleted. A bundle then sees "changed" rather than "deleted + new", and a re-OCR no longer throws away
    * hand-made links.
    */

  def replace(citekey: Citekey, assets: List[NewFigure])(using DbCon): RowCount =
    val incoming = NewFigure.dedupe(assets)
    val existing = sql"SELECT name, sha256 FROM assets WHERE citekey = $citekey ORDER BY name"
      .query[(NewFigure.Name, Sha256)]
      .run()
      .toList
    val plan = Reconciled.of(existing, incoming.map(a => a.name -> a.sha256))
    plan.deletes.foreach(n => sql"DELETE FROM assets WHERE citekey = $citekey AND name = $n".update.run())
    plan.renames.foreach((from, to) =>
      sql"UPDATE assets SET name = $to WHERE citekey = $citekey AND name = $from".update.run()
    )
    val n = insert(citekey, incoming, replaceExisting = ReplaceExisting(true))
    touch(citekey)
    n

  /** Add to the set, leaving anything already stored under the same name alone (the seed importer's figures, and
    * `POST /api/sources/{ck}/assets`). Returns how many rows were written.
    */
  def add(citekey: Citekey, assets: List[NewFigure])(using DbCon): RowCount =
    val n = insert(citekey, NewFigure.dedupe(assets), replaceExisting = ReplaceExisting(false))
    touch(citekey)
    n

  /** A source's figures are part of the source, so changing them makes `updated_at` — the concurrency token every
    * `ifUpdatedAt` compares and the seed guard's "is the row newer than the file?" — move. It did not, so a client
    * could patch against a token that no longer described what it had, and a reseed could overwrite freshly-attached
    * figures.
    */
  private def touch(citekey: Citekey)(using DbCon): Unit =
    val _ = sql"UPDATE sources SET updated_at = now() WHERE citekey = $citekey".update.run()

  /** `replaceExisting = false` is what [[add]] documents — "leave anything already stored under the same name alone" —
    * and it used to say `DO UPDATE`, i.e. the exact opposite: the seed importer's figures overwrote the (better) ones a
    * conversion had produced. For [[replace]] the clause is what updates a kept row in place.
    */
  private def insert(citekey: Citekey, assets: List[NewFigure], replaceExisting: ReplaceExisting)(using
      DbCon
  ): RowCount =
    assets.foreach { a =>
      val sha = a.sha256
      if replaceExisting.value then
        // an unchanged picture is not rewritten: its row (and `updated_at`) stays as it was
        sql"""INSERT INTO assets (citekey, name, mime, bytes, sha256, width, height)
              VALUES ($citekey, ${a.name}, ${a.mime}, ${a.bytes}, $sha, ${a.width}, ${a.height})
              ON CONFLICT (citekey, name) DO UPDATE SET
                mime = EXCLUDED.mime, bytes = EXCLUDED.bytes, sha256 = EXCLUDED.sha256,
                width = EXCLUDED.width, height = EXCLUDED.height
              WHERE (assets.mime, assets.sha256, assets.width, assets.height)
                IS DISTINCT FROM (EXCLUDED.mime, EXCLUDED.sha256, EXCLUDED.width, EXCLUDED.height)""".update.run()
      else sql"""INSERT INTO assets (citekey, name, mime, bytes, sha256, width, height)
              VALUES ($citekey, ${a.name}, ${a.mime}, ${a.bytes}, $sha, ${a.width}, ${a.height})
              ON CONFLICT (citekey, name) DO NOTHING""".update.run()
    }
    RowCount(assets.size)

  def list(citekey: Citekey)(using DbCon): Vector[AssetInfo] =
    sql"""SELECT name, mime, length(bytes), sha256, width, height
          FROM assets WHERE citekey = $citekey ORDER BY name"""
      .query[AssetInfoRow]
      .run()
      .map(r =>
        AssetInfo(
          AssetInfo.Name(r.name.value),
          AssetInfo.Mime(r.mime.value),
          ByteCount(r.bytes.value.toLong),
          com.histmersion.api.Sha256(r.sha256.value),
          r.width.map(w => AssetInfo.Width(w.value)),
          r.height.map(h => AssetInfo.Height(h.value))
        )
      )

  /** The bytes to stream, with the mime type to serve them as. */
  def read(citekey: Citekey, name: AssetInfoRow.Name)(using DbCon): Option[(AssetBytesRow.Mime, AssetBytesRow.Bytes)] =
    sql"SELECT mime, bytes FROM assets WHERE citekey = $citekey AND name = $name"
      .query[AssetBytesRow]
      .run()
      .headOption
      .map(r => (r.mime, r.bytes))

  def count(citekey: Citekey)(using DbCon): RowCount =
    sql"SELECT count(*) FROM assets WHERE citekey = $citekey"
      .query[Int]
      .run()
      .headOption
      .map(RowCount(_))
      .getOrElse(RowCount(0))

  def exists(citekey: Citekey, name: AssetInfoRow.Name)(using DbCon): Boolean =
    sql"SELECT 1 FROM assets WHERE citekey = $citekey AND name = $name".query[Int].run().nonEmpty

  def detail(citekey: Citekey, name: AssetInfoRow.Name)(using DbCon): Option[AssetDetail] =
    sql"""SELECT name, mime, length(bytes), sha256, width, height,
                 kind, caption, license,
                 ST_Y(geom::geometry), ST_X(geom::geometry),
                 ${RangeSql.cols(sql"period")}
          FROM assets WHERE citekey = $citekey AND name = $name"""
      .query[AssetDetailRow]
      .run()
      .headOption
      .map { r =>
        AssetDetail(
          Bundle.Asset.FileName(r.name.value),
          Bundle.Asset.MimeText(r.mime.value),
          ByteCount(r.bytes.value.toLong),
          com.histmersion.api.Sha256(r.sha256.value),
          r.width.map(w => ImageSize.Width(w.value)),
          r.height.map(h => ImageSize.Height(h.value)),
          r.kind.map(k => MediaItem.Kind(k.value)),
          r.caption.map(c => MediaItem.Caption(c.value)),
          r.license.map(l => MediaItem.License(l.value)),
          r.lat.map(l => Latitude(l.value)),
          r.lon.map(l => Longitude(l.value)),
          r.periodStart.map(y => Year(y.value.toLong)),
          r.periodEnd.map(y => Year(y.value.toLong))
        )
      }

  /** A figure's description changed: each field given, its point when both coordinates are, a bound of its period given
    * alone replacing that bound ([[RangeSql.merge]]). The figure must exist.
    */
  def patch(citekey: Citekey, name: AssetInfoRow.Name, p: AssetPatch)(using DbCon): Either[Api.Error, Unit] =
    if !exists(citekey, name) then
      Left(
        Api.Error(
          Problem.Unclassified(Legacy.Text(s"No asset '${citekey.value}/${name.value}'")),
          Some(Api.Error.Code(Write.CodeNotFound))
        )
      )
    else
      p.kind.foreach(v => sql"UPDATE assets SET kind = $v WHERE citekey = $citekey AND name = $name".update.run())
      p.caption.foreach(v => sql"UPDATE assets SET caption = $v WHERE citekey = $citekey AND name = $name".update.run())
      p.license.foreach(v => sql"UPDATE assets SET license = $v WHERE citekey = $citekey AND name = $name".update.run())
      (p.lat, p.lon) match
        case (Some(lat), Some(lon)) =>
          sql"UPDATE assets SET geom = ST_SetSRID(ST_Point($lon, $lat), 4326) WHERE citekey = $citekey AND name = $name".update
            .run()
        case _ => ()
      val stored =
        sql"SELECT ${RangeSql.cols(sql"period")} FROM assets WHERE citekey = $citekey AND name = $name"
          .query[(Option[Long], Option[Long])]
          .run()
          .head
      val storedBounds = (
        stored._1.map(DeepTime.SignedYear(_)),
        stored._2.map(DeepTime.SignedYear(_))
      )
      RangeSql
        .merge(
          storedBounds,
          p.periodStart.map(y => DeepTime.SignedYear(y.value)),
          p.periodEnd.map(y => DeepTime.SignedYear(y.value)),
          s"asset '${citekey.value}/${name.value}'"
        )
        .getOrElse(Right(storedBounds))
        .map { (s, e) =>
          if (s, e) != storedBounds then
            val range = RangeSql.literal(s, e)
            sql"UPDATE assets SET period = $range::int8range WHERE citekey = $citekey AND name = $name".update.run()
        }
