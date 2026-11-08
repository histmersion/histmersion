package com.histmersion
package domain.catalogue.evidence

import com.histmersion.api.library.{DeleteCount, DeletePart}
import com.histmersion.domain.library.sources.referrers.*

import com.histmersion.util.database.given
import com.histmersion.util.database.NewtypeColumns.given

import com.augustnagro.magnum.*
import com.histmersion.api.library.Rename

/** The attestations that name a source: those it makes (they go with it), and those of other sources whose target is
  * one of its findings, an edge from it or a dating it gives (they lose their target).
  */
final class AttestationSourceReferrer extends SourceReferrer:

  override def renamed(rename: SourceRename)(using DbCon): RenamedReferences =
    val (to, prefix, cut) = (rename.to, rename.findingPrefix, rename.findingPrefix.value.length)
    val findings =
      sql"""UPDATE attestations SET target_id = $to || substr(target_id, $cut)
            WHERE target_type = 'finding' AND left(target_id, $cut) = $prefix""".update.run()
    // an edge or a per-source dating EMBEDS the citekey: it is replaced as a whole token only
    val embedding = sql"""SELECT id, target_id FROM attestations
          WHERE target_type IN ('edge', 'event_dating') AND strpos(target_id, ${rename.from}) > 0"""
      .query[(Long, SourceKeys.Text)]
      .run()
      .toList
      .map((id, t) => (id, rename.inText(t), t))
      .collect {
        case (id, next, was) if next != was =>
          sql"UPDATE attestations SET target_id = $next WHERE id = $id".update.run()
      }
      .sum
    RenamedReferences(
      List(
        "attestations.target_id (finding)" -> findings,
        "attestations.target_id (edge, event_dating)" -> embedding
      ).collect { case (t, n) if n > 0 => Rename.Count(Rename.Count.Table(t), Rename.Count.Rows(n)) }
    )

  override def report(source: SourceKeys)(using DbCon): DeleteReport =
    val ck = source.citekey
    val own = sql"SELECT count(*)::int FROM attestations WHERE citekey = $ck".query[Int].run().headOption.getOrElse(0)
    DeleteReport(counts =
      List(DeletePart.Attestations -> own, DeletePart.AttestationsOf -> losing(source).size).collect {
        case (p, rows) if rows > 0 => DeleteCount(p, DeleteCount.Rows(rows))
      }
    )

  override def deleting(source: SourceKeys, report: DeleteReport)(using DbCon): Unit =
    val gone = losing(source).map(_.toString)
    if gone.nonEmpty then sql"DELETE FROM attestations WHERE id::text = ANY($gone::text[])".update.run()

  /** Other sources' attestations whose target goes with the source. */
  private def losing(source: SourceKeys)(using DbCon): List[AttestationRepo.AttestationId] =
    val ck = source.citekey
    sql"""SELECT id, target_type, target_id FROM attestations
          WHERE citekey <> $ck AND target_type IN ('finding', 'edge', 'event_dating') AND strpos(target_id, $ck) > 0"""
      .query[(AttestationRepo.AttestationId, BAttestationFullRow.TargetType, BAttestationFullRow.TargetId)]
      .run()
      .toList
      .collect { case (id, t, target) if AttestationSourceReferrer.goes(t, target, source) => id }

object AttestationSourceReferrer:

  /** Whether an attestation of another source loses its target with the source: one of its findings, an edge FROM it, a
    * dating it gives. An edge TO it is parked, not deleted, so its attestations stay.
    */

  def goes(targetType: BAttestationFullRow.TargetType, targetId: BAttestationFullRow.TargetId, source: SourceKeys): Boolean =
    targetType.value match
      case "finding"      => targetId.value.startsWith(source.findingPrefix.value)
      case "edge"         => targetId.value.split('|').headOption.contains(source.citekey.value)
      case "event_dating" => source.tokenIn(SourceKeys.Text(targetId.value))
      case _              => false
