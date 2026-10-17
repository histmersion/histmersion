package com.histmersion.db

import com.histmersion.core.*

import com.augustnagro.magnum.*
import javax.sql.DataSource

given DbCodec[AssetLink] = DbCodec.derived

private[db] final case class AssetDetailRow(
    name: String, mime: String, bytes: Int, sha256: String,
    width: Option[Int], height: Option[Int],
    kind: Option[String], caption: Option[String], license: Option[String],
    lat: Option[Double], lon: Option[Double],
    periodStart: Option[Long], periodEnd: Option[Long])

given DbCodec[AssetDetailRow] = DbCodec.derived

object AssetMetaRepo:

  def detail(ds: DataSource, citekey: String, name: String): Option[AssetDetail] =
    connect(ds):
      sql"""SELECT name, mime, length(bytes), sha256, width, height,
                   kind, caption, license,
                   ST_Y(geom::geometry), ST_X(geom::geometry),
                   ${SqlLiteral(PeriodSql.cols("period"))}
            FROM assets WHERE citekey = $citekey AND name = $name"""
        .query[AssetDetailRow].run().headOption.map { r =>
          AssetDetail(r.name, r.mime, r.bytes, r.sha256, r.width, r.height,
            r.kind, r.caption, r.license, r.lat, r.lon, r.periodStart, r.periodEnd)
        }

  def patch(ds: DataSource, citekey: String, name: String, p: AssetPatch): Either[ApiError, AssetDetail] =
    transact(ds):
      val exists = sql"SELECT 1 FROM assets WHERE citekey = $citekey AND name = $name".query[Int].run().nonEmpty
      if !exists then Left(ApiError(s"No asset '$citekey/$name'", Some(Write.CodeNotFound)))
      else
        p.kind.foreach(v => sql"UPDATE assets SET kind = $v WHERE citekey = $citekey AND name = $name".update.run())
        p.caption.foreach(v => sql"UPDATE assets SET caption = $v WHERE citekey = $citekey AND name = $name".update.run())
        p.license.foreach(v => sql"UPDATE assets SET license = $v WHERE citekey = $citekey AND name = $name".update.run())
        (p.lat, p.lon) match
          case (Some(lat), Some(lon)) =>
            sql"UPDATE assets SET geom = ST_SetSRID(ST_Point($lon, $lat), 4326) WHERE citekey = $citekey AND name = $name".update.run()
          case _ => ()
        // a bound given alone replaces that bound; the other stays as stored ([[PeriodSql.merge]])
        val stored = sql"SELECT ${SqlLiteral(PeriodSql.cols("period"))} FROM assets WHERE citekey = $citekey AND name = $name"
          .query[(Option[Long], Option[Long])].run().head
        PeriodSql.merge(stored, p.periodStart, p.periodEnd, s"asset '$citekey/$name'").getOrElse(Right(stored)).map { (s, e) =>
          if (s, e) != stored then
            val range = PeriodSql.literal(s, e)
            sql"UPDATE assets SET period = ${range}::int8range WHERE citekey = $citekey AND name = $name".update.run()
        }
    .flatMap(_ => detail(ds, citekey, name).toRight(ApiError(s"No asset '$citekey/$name'", Some(Write.CodeNotFound))))

object AssetLinkRepo:

  def list(ds: DataSource, citekey: Option[String], asset: Option[String],
           targetType: Option[String], targetId: Option[String]): AssetLinkList =
    val ck = citekey.map(_.trim).filter(_.nonEmpty)
    val an = asset.map(_.trim).filter(_.nonEmpty)
    val tt = targetType.map(_.trim).filter(_.nonEmpty)
    val ti = targetId.map(_.trim).filter(_.nonEmpty)
    connect(ds):
      val rows = sql"""SELECT id, citekey, asset_name, relation, target_type, target_id, xywh, notes
            FROM asset_links
            WHERE (${ck}::text IS NULL OR citekey = ${ck})
              AND (${an}::text IS NULL OR asset_name = ${an})
              AND (${tt}::text IS NULL OR target_type = ${tt})
              AND (${ti}::text IS NULL OR target_id = ${ti})
            ORDER BY id""".query[AssetLink].run().toList
      AssetLinkList(rows.size, rows)

  def create(ds: DataSource, in: AssetLinkPut): Either[ApiError, AssetLink] =
    transact(ds):
      val exists = sql"SELECT 1 FROM assets WHERE citekey = ${in.citekey} AND name = ${in.assetName}"
        .query[Int].run().nonEmpty
      if !exists then Left(ApiError(s"No asset '${in.citekey}/${in.assetName}'", Some(Write.CodeNotFound)))
      else
        val id = sql"""INSERT INTO asset_links (citekey, asset_name, relation, target_type, target_id, xywh, notes)
              VALUES (${in.citekey}, ${in.assetName}, ${in.relation}, ${in.targetType}, ${in.targetId},
                      ${in.xywh}, ${in.notes})
              RETURNING id""".query[Long].run().head
        Right(AssetLink(id, in.citekey, in.assetName, in.relation, in.targetType, in.targetId, in.xywh, in.notes))

  def delete(ds: DataSource, id: Long): Boolean =
    transact(ds):
      sql"DELETE FROM asset_links WHERE id = $id".update.run() > 0
