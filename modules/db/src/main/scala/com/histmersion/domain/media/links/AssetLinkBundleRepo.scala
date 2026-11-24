package com.histmersion
package domain.media.links

import com.histmersion.util.data.exchange.BundleImport
import com.histmersion.util.data.exchange.BundleScope
import com.histmersion.util.data.exchange.BundleSql.*

import com.augustnagro.magnum.*
import com.histmersion.util.database.NewtypeColumns.given
import java.util.UUID
import com.histmersion.api.exchange.Bundle
import com.histmersion.util.database.Timestamps
import com.histmersion.util.database.{Jsonb, StoredCreator}

/** The figures' links as a bundle carries them: each with its figure's uid and its target's uid when the target is a
  * record — read out in uid order, and written in by an import once every figure is here, the target named by its label
  * here.
  */
@tolerated(LintRule.Layer, "pending G7 (separate types per layer)")
@atBoundary(BoundaryKind.Sql, "reads and writes the asset link rows for bundle export and import")
private[links] object AssetLinkBundleRepo:

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def assetLinks(scope: BundleScope)(using DbCon): Vector[Bundle.AssetLink] =
    sql"""SELECT l.uid, l.origin, x.uid, l.relation, l.target_type, l.target_id,
                       ${targetUid(sql"l.target_type", sql"l.target_id")}, l.xywh, l.notes, ${Timestamps.iso(
        sql"l.updated_at"
      )}, l.creator::text
                FROM asset_links l JOIN assets x ON x.citekey = l.citekey AND x.name = l.asset_name WHERE ${where(
        scope,
        sql"l.uid",
        sql"l.updated_at"
      )} ORDER BY l.uid"""
      .query[
        (
            UUID,
            UUID,
            UUID,
            String,
            String,
            String,
            Option[UUID],
            Option[String],
            Option[String],
            String,
            Option[Jsonb.JsonText]
        )
      ]
      .run()
      .map((uid, origin, assetUid, relation, targetType, targetId, targetUid, xywh, notes, updatedAt, creator) =>
        Bundle.AssetLink(
          uid,
          origin,
          assetUid,
          Bundle.AssetLink.Relation(relation),
          Bundle.AssetLink.TargetType(targetType),
          Bundle.AssetLink.TargetId(targetId),
          targetUid,
          xywh.map(x => Bundle.AssetLink.Xywh(x)),
          notes.map(x => Bundle.AssetLink.Notes(x)),
          Bundle.AssetLink.UpdatedAt(updatedAt),
          creator = StoredCreator.document(creator)
        )
      )

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  def assetLinksIn(w: BundleImport.Writer): Unit =
    given DbCon = w.transaction
    w.es.assetLinks.filter(l => w.writes(l.uid)).foreach { l =>
      val (ck, name) =
        sql"""SELECT citekey, name FROM assets WHERE uid = ${l.assetUid}""".query[(String, String)].run().head
      val target = w.targetLabel(
        BundleImport.Writer.TargetType(l.targetType.value),
        BundleImport.Writer.TargetId(l.targetId.value),
        l.targetUid
      )
      sql"""INSERT INTO asset_links (uid, origin, citekey, asset_name, relation, target_type, target_id, xywh, notes, updated_at, creator,
                                     created_at)
            VALUES (${l.uid}, ${l.origin}, $ck, $name, ${l.relation}, ${l.targetType}, $target, ${l.xywh},
              ${l.notes}, ${l.updatedAt}::timestamptz, ${StoredCreator.carried(
          l.creator
        )}::jsonb, ${w.at}::timestamptz)
            ON CONFLICT (uid) DO UPDATE SET origin = EXCLUDED.origin, citekey = EXCLUDED.citekey,
              asset_name = EXCLUDED.asset_name, relation = EXCLUDED.relation,
              target_type = EXCLUDED.target_type, target_id = EXCLUDED.target_id, xywh = EXCLUDED.xywh,
              notes = EXCLUDED.notes, updated_at = EXCLUDED.updated_at""".update.run()
    }
