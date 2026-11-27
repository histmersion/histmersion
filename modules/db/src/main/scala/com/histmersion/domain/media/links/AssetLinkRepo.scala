package com.histmersion
package domain.media.links

import com.histmersion.util.database.given
import com.histmersion.util.database.NewtypeColumns.given
import com.augustnagro.magnum.*
import com.histmersion.api.media.AssetLink
import com.histmersion.domain.library.Citekey
import com.histmersion.domain.media.assets.MediaItemRow
import com.histmersion.domain.media.imports.Target
import com.histmersion.domain.media.assets.AssetInfoRow
import com.histmersion.domain.{CreatedAt, Creator}
import com.histmersion.util.database.StoredCreator
import com.histmersion.lint.{atBoundary, BoundaryKind}
import com.histmersion.lint.{tolerated, LintRule}

/** A source's figure tied to a place, an event, a finding, a source or an article (plan §1.6): what it depicts, where
  * it was taken, what it illustrates, whose page it is. Every method runs on the connection it is given.
  */
@tolerated(LintRule.Derives, "pending G18 (derives instead of hand-written givens)")
@tolerated(LintRule.Direction, "pending G12 (a domain names only lower places)")
@tolerated(LintRule.Layer, "pending G7 (separate types per layer)")
@atBoundary(BoundaryKind.Sql, "reads and writes the asset link tables and their row codecs")
private[links] object AssetLinkRepo:

  @tolerated(LintRule.Derives, "pending G18 (derives instead of hand-written givens)")
  given DbCodec[AssetLink] = DbCodec.derived

  def list(
      citekey: Option[Citekey],
      asset: Option[MediaItemRow.Uid],
      targetType: Option[Target.TargetType],
      targetId: Option[Target.Id]
  )(using DbCon): List[AssetLink] =
    sql"""SELECT id, citekey, asset_name, relation, target_type, target_id, xywh, notes
          FROM asset_links
          WHERE ($citekey::text IS NULL OR citekey = $citekey)
            AND ($asset::text IS NULL OR asset_name = $asset)
            AND ($targetType::text IS NULL OR target_type = $targetType)
            AND ($targetId::text IS NULL OR target_id = $targetId)
          ORDER BY id""".query[AssetLink].run().toList

  /** Whether the figure a link names is stored. */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def figureExists(citekey: Citekey, name: AssetInfoRow.Name)(using DbCon): Boolean =
    sql"SELECT 1 FROM assets WHERE citekey = $citekey AND name = $name".query[Int].run().nonEmpty

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def insert(in: AssetLink.Put)(using DbCon)(using creator: Creator.New, at: CreatedAt): AssetLink =
    val id = sql"""INSERT INTO asset_links (citekey, asset_name, relation, target_type, target_id, xywh, notes, creator,
                                            created_at)
          VALUES (${in.citekey}, ${in.assetName}, ${in.relation}, ${in.targetType}, ${in.targetId},
                  ${in.xywh}, ${in.notes}, ${StoredCreator.column(creator)}::jsonb, $at::timestamptz)
          RETURNING id""".query[AssetLink.Id].run().head
    AssetLink(id, in.citekey, in.assetName, in.relation, in.targetType, in.targetId, in.xywh, in.notes)

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def delete(id: AssetLink.Id)(using DbCon): Boolean =
    sql"DELETE FROM asset_links WHERE id = $id".update.run() > 0
