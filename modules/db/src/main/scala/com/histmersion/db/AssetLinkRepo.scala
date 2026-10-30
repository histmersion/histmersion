package com.histmersion.db

import com.histmersion.api.*
import com.histmersion.config.*
import com.histmersion.domain.*

import com.augustnagro.magnum.*
import javax.sql.DataSource

given DbCodec[AssetLink] = DbCodec.derived

object AssetLinkRepo:

  def list(
      ds: DataSource,
      citekey: Option[String],
      asset: Option[String],
      targetType: Option[String],
      targetId: Option[String]
  ): AssetLinkList =
    val ck = citekey.map(_.trim).filter(_.nonEmpty)
    val an = asset.map(_.trim).filter(_.nonEmpty)
    val tt = targetType.map(_.trim).filter(_.nonEmpty)
    val ti = targetId.map(_.trim).filter(_.nonEmpty)
    connect(ds):
      val rows = sql"""SELECT id, citekey, asset_name, relation, target_type, target_id, xywh, notes
            FROM asset_links
            WHERE ($ck::text IS NULL OR citekey = $ck)
              AND ($an::text IS NULL OR asset_name = $an)
              AND ($tt::text IS NULL OR target_type = $tt)
              AND ($ti::text IS NULL OR target_id = $ti)
            ORDER BY id""".query[AssetLink].run().toList
      AssetLinkList.of(rows.size, rows, 0)

  def create(ds: DataSource, in: AssetLinkPut): Either[ApiError, AssetLink] =
    transact(ds):
      val exists = sql"SELECT 1 FROM assets WHERE citekey = ${in.citekey} AND name = ${in.assetName}"
        .query[Int]
        .run()
        .nonEmpty
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
