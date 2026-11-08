package com.histmersion
package domain.media.links

import com.histmersion.util.data.exchange.BundleImport
import com.histmersion.util.database.Sql
import com.histmersion.util.data.exchange.BundleScope
import com.histmersion.util.data.exchange.BundleSql.*

import com.augustnagro.magnum.*
import com.histmersion.util.database.NewtypeColumns.given
import java.util.UUID
import com.histmersion.api.exchange.Bundle

/** The figures' links as a bundle carries them: each with its figure's uid and its target's uid when the target is a
  * record — read out in uid order, and written in by an import once every figure is here, the target named by its label
  * here.
  */
private[links] object AssetLinkBundleRepo:

  def assetLinks(scope: BundleScope)(using DbCon): Vector[Bundle.AssetLink] =
    (Sql.raw(Sql.RawText(s"""SELECT l.uid, l.origin, x.uid, l.relation, l.target_type, l.target_id,
                       ${targetUid("l.target_type", "l.target_id")}, l.xywh, l.notes, ${ts("l.updated_at")}
                FROM asset_links l JOIN assets x ON x.citekey = l.citekey AND x.name = l.asset_name WHERE """)) ++
      where(scope, "l.uid", "l.updated_at") ++ " ORDER BY l.uid").frag
      .query[(UUID, UUID, UUID, String, String, String, Option[UUID], Option[String], Option[String], String)]
      .run()
      .map((uid, origin, assetUid, relation, targetType, targetId, targetUid, xywh, notes, updatedAt) =>
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
          Bundle.AssetLink.UpdatedAt(updatedAt)
        )
      )

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
      sql"""INSERT INTO asset_links (uid, origin, citekey, asset_name, relation, target_type, target_id, xywh, notes, updated_at)
            VALUES (${l.uid}, ${l.origin}, $ck, $name, ${l.relation}, ${l.targetType}, $target, ${l.xywh},
              ${l.notes}, ${l.updatedAt}::timestamptz)
            ON CONFLICT (uid) DO UPDATE SET origin = EXCLUDED.origin, citekey = EXCLUDED.citekey,
              asset_name = EXCLUDED.asset_name, relation = EXCLUDED.relation,
              target_type = EXCLUDED.target_type, target_id = EXCLUDED.target_id, xywh = EXCLUDED.xywh,
              notes = EXCLUDED.notes, updated_at = EXCLUDED.updated_at""".update.run()
    }
