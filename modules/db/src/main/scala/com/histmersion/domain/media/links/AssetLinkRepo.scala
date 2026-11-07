package com.histmersion.domain.media.links

import com.histmersion.util.database.given
import com.augustnagro.magnum.*
import com.histmersion.api.media.AssetLink

/** A source's figure tied to a place, an event, a finding, a source or an article (plan §1.6): what it depicts, where
  * it was taken, what it illustrates, whose page it is. Every method runs on the connection it is given.
  */
private[links] object AssetLinkRepo:

  given DbCodec[AssetLink] = DbCodec.derived

  def list(
      citekey: Option[com.histmersion.domain.library.Citekey],
      asset: Option[com.histmersion.domain.media.assets.MediaItemRow.Uid],
      targetType: Option[com.histmersion.domain.media.imports.Target.TargetType],
      targetId: Option[com.histmersion.domain.media.imports.Target.Id]
  )(using DbCon): List[AssetLink] =
    sql"""SELECT id, citekey, asset_name, relation, target_type, target_id, xywh, notes
          FROM asset_links
          WHERE ($citekey::text IS NULL OR citekey = $citekey)
            AND ($asset::text IS NULL OR asset_name = $asset)
            AND ($targetType::text IS NULL OR target_type = $targetType)
            AND ($targetId::text IS NULL OR target_id = $targetId)
          ORDER BY id""".query[AssetLink].run().toList

  /** Whether the figure a link names is stored. */
  def figureExists(citekey: com.histmersion.domain.library.Citekey, name: com.histmersion.domain.media.assets.AssetInfoRow.Name)(using DbCon): Boolean =
    sql"SELECT 1 FROM assets WHERE citekey = $citekey AND name = $name".query[Int].run().nonEmpty

  def insert(in: AssetLink.Put)(using DbCon): AssetLink =
    val id = sql"""INSERT INTO asset_links (citekey, asset_name, relation, target_type, target_id, xywh, notes)
          VALUES (${in.citekey}, ${in.assetName}, ${in.relation}, ${in.targetType}, ${in.targetId},
                  ${in.xywh}, ${in.notes})
          RETURNING id""".query[AssetLink.Id].run().head
    AssetLink(id, in.citekey, in.assetName, in.relation, in.targetType, in.targetId, in.xywh, in.notes)

  def delete(id: AssetLink.Id)(using DbCon): Boolean =
    sql"DELETE FROM asset_links WHERE id = $id".update.run() > 0
