package com.histmersion.domain.media.links

import com.histmersion.api.library.{DeleteCount, DeletePart, RenameCount}
import com.histmersion.domain.library.sources.referrers.*

import com.histmersion.util.database.given

import com.augustnagro.magnum.*

/** The figures of a source (they go with it) and the links of other figures to it or to one of its findings. */
final class AssetLinkSourceReferrer extends SourceReferrer:

  override def renamed(rename: SourceRename)(using DbCon): RenamedReferences =
    val (to, prefix, cut) = (rename.to, rename.findingPrefix, rename.findingPrefix.length)
    RenamedReferences(
      List(
        "asset_links.target_id (finding)" ->
          sql"""UPDATE asset_links SET target_id = $to || substr(target_id, $cut)
                WHERE target_type = 'finding' AND left(target_id, $cut) = $prefix""".update.run(),
        "asset_links.target_id (source)" ->
          sql"""UPDATE asset_links SET target_id = $to
                WHERE target_type = 'source' AND target_id = ${rename.from}""".update.run()
      ).collect { case (t, n) if n > 0 => RenameCount(t, n) }
    )

  override def report(source: SourceKeys)(using DbCon): DeleteReport =
    val ck = source.citekey
    def n(q: Frag): Int = q.query[Int].run().headOption.getOrElse(0)
    DeleteReport(counts =
      List(
        DeletePart.Assets -> n(sql"SELECT count(*)::int FROM assets WHERE citekey = $ck"),
        DeletePart.AssetLinks -> n(sql"SELECT count(*)::int FROM asset_links WHERE citekey = $ck"),
        DeletePart.AssetLinksTo -> linksTo(source).size
      ).collect { case (p, rows) if rows > 0 => DeleteCount(p, rows) }
    )

  override def deleting(source: SourceKeys, report: DeleteReport)(using DbCon): Unit =
    val links = linksTo(source).map(_.toString)
    if links.nonEmpty then sql"DELETE FROM asset_links WHERE id::text = ANY($links::text[])".update.run()

  /** Other figures' links to the source or one of its findings. */
  private def linksTo(source: SourceKeys)(using DbCon): List[Long] =
    val (ck, keys, hash) = (source.citekey, source.keys, source.findingPrefix)
    sql"""SELECT id FROM asset_links WHERE citekey <> $ck
            AND ((target_type = 'source' AND target_id = ANY($keys::text[]))
              OR (target_type = 'finding' AND left(target_id, ${hash.length}) = $hash))"""
      .query[Long]
      .run()
      .toList
