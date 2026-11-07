package com.histmersion.domain.media.assets

import com.histmersion.util.database.RangeSql

import com.histmersion.api.Write

import com.histmersion.util.database.given
import com.augustnagro.magnum.*
import com.histmersion.api.media.{AssetDetail, AssetInfo, AssetPatch}
import com.histmersion.api.Api

/** The figures that belong with a source's Markdown (plan §1.8).
  *
  * Stored as bytea keyed by `(citekey, name)`, which is exactly the `assets/<name>` link the body carries — so serving
  * one is a primary-key lookup, and re-running a conversion replaces the set (keeping the rows of the pictures it still
  * has, see [[replace]]). Within a source the same picture (a logo on every page) is stored ONCE: writes are
  * deduplicated by sha256, and the later names are dropped. Every method runs on the connection it is given.
  */
private[assets] object AssetRepo:
  type ReplaceExisting = ReplaceExisting.T
  object ReplaceExisting extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** REPLACE the whole set for one source (what a re-conversion does). Returns how many were kept after sha256
    * deduplication.
    *
    * The set is replaced, the ROWS are not (#826): an asset the new set still has keeps its uid, its descriptive
    * metadata and its asset links — the same name is updated in place, and a picture that only moved to a new name (the
    * same sha256) is renamed, its links following by V027's ON UPDATE CASCADE. Only what the new conversion no longer
    * has is deleted. A bundle then sees "changed" rather than "deleted + new", and a re-OCR no longer throws away
    * hand-made links.
    */


  def replace(citekey: com.histmersion.domain.library.Citekey, assets: List[NewFigure])(using DbCon): com.histmersion.RowCount =
    val incoming = NewFigure.dedupe(assets)
    val existing = sql"SELECT name, sha256 FROM assets WHERE citekey = $citekey ORDER BY name"
      .query[(String, String)]
      .run()
      .toList
    val plan = Reconciled.of(existing, incoming.map(a => a.name -> a.sha256))
    plan.deletes.foreach(n => sql"DELETE FROM assets WHERE citekey = $citekey AND name = $n".update.run())
    plan.renames.foreach((from, to) =>
      sql"UPDATE assets SET name = $to WHERE citekey = $citekey AND name = $from".update.run()
    )
    val n = insert(citekey, incoming, replaceExisting = true)
    touch(citekey)
    n

  /** Add to the set, leaving anything already stored under the same name alone (the seed importer's figures, and
    * `POST /api/sources/{ck}/assets`). Returns how many rows were written.
    */
  def add(citekey: com.histmersion.domain.library.Citekey, assets: List[NewFigure])(using DbCon): com.histmersion.RowCount =
    val n = insert(citekey, NewFigure.dedupe(assets), replaceExisting = false)
    touch(citekey)
    n

  /** A source's figures are part of the source, so changing them makes `updated_at` — the concurrency token every
    * `ifUpdatedAt` compares and the seed guard's "is the row newer than the file?" — move. It did not, so a client
    * could patch against a token that no longer described what it had, and a reseed could overwrite freshly-attached
    * figures.
    */
  private def touch(citekey: com.histmersion.domain.library.Citekey)(using DbCon): Unit =
    val _ = sql"UPDATE sources SET updated_at = now() WHERE citekey = $citekey".update.run()

  /** `replaceExisting = false` is what [[add]] documents — "leave anything already stored under the same name alone" —
    * and it used to say `DO UPDATE`, i.e. the exact opposite: the seed importer's figures overwrote the (better) ones a
    * conversion had produced. For [[replace]] the clause is what updates a kept row in place.
    */
  private def insert(citekey: com.histmersion.domain.library.Citekey, assets: List[NewFigure], replaceExisting: com.histmersion.domain.media.assets.AssetRepo.ReplaceExisting)(using DbCon): com.histmersion.RowCount =
    assets.foreach { a =>
      val sha = a.sha256
      if replaceExisting then
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
    assets.size

  def list(citekey: com.histmersion.domain.library.Citekey)(using DbCon): Vector[AssetInfo] =
    sql"""SELECT name, mime, length(bytes), sha256, width, height
          FROM assets WHERE citekey = $citekey ORDER BY name"""
      .query[AssetInfoRow]
      .run()
      .map(r => AssetInfo(r.name, r.mime, r.bytes, com.histmersion.api.Sha256(r.sha256), r.width, r.height))

  /** The bytes to stream, with the mime type to serve them as. */
  def read(citekey: com.histmersion.domain.library.Citekey, name: com.histmersion.domain.media.assets.AssetInfoRow.Name)(using DbCon): Option[(com.histmersion.domain.media.assets.AssetBytesRow.Mime, com.histmersion.domain.media.assets.AssetBytesRow.Bytes)] =
    sql"SELECT mime, bytes FROM assets WHERE citekey = $citekey AND name = $name"
      .query[AssetBytesRow]
      .run()
      .headOption
      .map(r => (r.mime, r.bytes))

  def count(citekey: com.histmersion.domain.library.Citekey)(using DbCon): com.histmersion.RowCount =
    sql"SELECT count(*) FROM assets WHERE citekey = $citekey".query[Int].run().headOption.getOrElse(0)

  def exists(citekey: com.histmersion.domain.library.Citekey, name: com.histmersion.domain.media.assets.AssetInfoRow.Name)(using DbCon): Boolean =
    sql"SELECT 1 FROM assets WHERE citekey = $citekey AND name = $name".query[Int].run().nonEmpty

  def detail(citekey: com.histmersion.domain.library.Citekey, name: com.histmersion.domain.media.assets.AssetInfoRow.Name)(using DbCon): Option[AssetDetail] =
    sql"""SELECT name, mime, length(bytes), sha256, width, height,
                 kind, caption, license,
                 ST_Y(geom::geometry), ST_X(geom::geometry),
                 ${SqlLiteral(RangeSql.cols("period"))}
          FROM assets WHERE citekey = $citekey AND name = $name"""
      .query[AssetDetailRow]
      .run()
      .headOption
      .map { r =>
        AssetDetail(
          r.name,
          r.mime,
          r.bytes,
          r.sha256,
          r.width,
          r.height,
          r.kind,
          r.caption,
          r.license,
          r.lat,
          r.lon,
          r.periodStart,
          r.periodEnd
        )
      }

  /** A figure's description changed: each field given, its point when both coordinates are, a bound of its period given
    * alone replacing that bound ([[RangeSql.merge]]). The figure must exist.
    */
  def patch(citekey: com.histmersion.domain.library.Citekey, name: com.histmersion.domain.media.assets.AssetInfoRow.Name, p: AssetPatch)(using DbCon): Either[Api.Error, Unit] =
    if !exists(citekey, name) then Left(Api.Error(s"No asset '$citekey/$name'", Some(Write.CodeNotFound)))
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
        sql"SELECT ${SqlLiteral(RangeSql.cols("period"))} FROM assets WHERE citekey = $citekey AND name = $name"
          .query[(Option[Long], Option[Long])]
          .run()
          .head
      RangeSql.merge(stored, p.periodStart, p.periodEnd, s"asset '$citekey/$name'").getOrElse(Right(stored)).map {
        (s, e) =>
          if (s, e) != stored then
            val range = RangeSql.literal(s, e)
            sql"UPDATE assets SET period = $range::int8range WHERE citekey = $citekey AND name = $name".update.run()
      }
