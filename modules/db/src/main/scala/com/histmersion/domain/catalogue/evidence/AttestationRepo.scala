package com.histmersion
package domain.catalogue.evidence

import com.histmersion.api.catalogue.Attestation

import com.augustnagro.magnum.*
import com.histmersion.util.database.NewtypeColumns.given
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.domain.library.Citekey
import com.histmersion.api.RowCount
import com.histmersion.api.personal.UnreadPage
import com.histmersion.util.database.Unused
import com.histmersion.domain.{CreatedAt, Creator}
import com.histmersion.util.database.StoredCreator

/** The attestations (`attestations`): which passage of a source attests a finding, an event, a dating, a place or an
  * edge. Every method runs on the connection it is given.
  */
private[evidence] object AttestationRepo:
  type AttestationId = AttestationId.T
  object AttestationId extends Newtype[Long, Pure]

  private given DbCodec[Attestation] = DbCodec.derived

  private val cols =
    sql"a.id, a.citekey, a.target_type, a.target_id, a.quote, a.prefix, a.suffix, a.char_start, a.char_end, a.chunk_ord, a.page, a.canvas, a.xywh, a.cts_urn, a.note"

  def list(
      citekey: Option[Citekey],
      targetType: Option[BAttestationFullRow.TargetType],
      targetId: Option[BAttestationFullRow.TargetId],
      limit: PageSize,
      offset: Offset
  )(using
      DbCon
  ): Attestation.List =
    val ck = citekey.map(_.value.trim).filter(_.nonEmpty)
    val tt = targetType.map(_.value.trim).filter(_.nonEmpty)
    val ti = targetId.map(_.value.trim).filter(_.nonEmpty)
    val total = sql"""SELECT count(*)::int FROM attestations a
          WHERE ($ck::text IS NULL OR a.citekey = $ck)
            AND ($tt::text IS NULL OR a.target_type = $tt)
            AND ($ti::text IS NULL OR a.target_id = $ti)
          """.query[Int].run().head
    val rows = sql"""SELECT $cols FROM attestations a
          WHERE ($ck::text IS NULL OR a.citekey = $ck)
            AND ($tt::text IS NULL OR a.target_type = $tt)
            AND ($ti::text IS NULL OR a.target_id = $ti)
          ORDER BY a.id
          LIMIT ${limit.value.max(1)} OFFSET ${offset.value.max(0)}
          """.query[Attestation].run().toList
    Attestation.List.of(
      RowCount(total),
      rows,
      UnreadPage.Offset(offset.value.max(0))
    )

  def get(id: AttestationId)(using DbCon): Option[Attestation] =
    sql"""SELECT $cols FROM attestations a WHERE a.id = $id""".query[Attestation].run().headOption

  def create(in: Attestation.Put)(using DbCon)(using creator: Creator.New, at: CreatedAt): Attestation =
    val id = sql"""INSERT INTO attestations (citekey, target_type, target_id,
                      quote, prefix, suffix, char_start, char_end, chunk_ord,
                      page, canvas, xywh, cts_urn, note, creator, created_at)
                   VALUES (${in.citekey}, ${in.targetType}, ${in.targetId},
                           ${in.quote}, ${in.prefix}, ${in.suffix},
                           ${in.charStart}, ${in.charEnd}, ${in.chunkOrd},
                           ${in.page}, ${in.canvas}, ${in.xywh},
                           ${in.ctsUrn}, ${in.note}, ${StoredCreator.column(creator)}::jsonb,
                           ${at.iso}::timestamptz)
                   RETURNING id""".query[Long].run().head
    get(AttestationId(id)).get

  def exists(id: AttestationId)(using DbCon): Boolean =
    sql"SELECT 1 FROM attestations WHERE id = $id".query[Int].run().nonEmpty

  def update(id: AttestationId, p: Attestation.Patch)(using DbCon): Unit =
    p.quote.foreach(v => Unused(sql"UPDATE attestations SET quote = $v WHERE id = $id".update.run(), Unused.Why.Held))
    p.prefix.foreach(v => Unused(sql"UPDATE attestations SET prefix = $v WHERE id = $id".update.run(), Unused.Why.Held))
    p.suffix.foreach(v => Unused(sql"UPDATE attestations SET suffix = $v WHERE id = $id".update.run(), Unused.Why.Held))
    p.charStart.foreach(v =>
      Unused(sql"UPDATE attestations SET char_start = $v WHERE id = $id".update.run(), Unused.Why.Held)
    )
    p.charEnd.foreach(v =>
      Unused(sql"UPDATE attestations SET char_end = $v WHERE id = $id".update.run(), Unused.Why.Held)
    )
    p.chunkOrd.foreach(v =>
      Unused(sql"UPDATE attestations SET chunk_ord = $v WHERE id = $id".update.run(), Unused.Why.Held)
    )
    p.page.foreach(v => Unused(sql"UPDATE attestations SET page = $v WHERE id = $id".update.run(), Unused.Why.Held))
    p.canvas.foreach(v => Unused(sql"UPDATE attestations SET canvas = $v WHERE id = $id".update.run(), Unused.Why.Held))
    p.xywh.foreach(v => Unused(sql"UPDATE attestations SET xywh = $v WHERE id = $id".update.run(), Unused.Why.Held))
    p.ctsUrn.foreach(v =>
      Unused(sql"UPDATE attestations SET cts_urn = $v WHERE id = $id".update.run(), Unused.Why.Held)
    )
    p.note.foreach(v => Unused(sql"UPDATE attestations SET note = $v WHERE id = $id".update.run(), Unused.Why.Held))

  def delete(id: AttestationId)(using DbCon): Boolean = sql"DELETE FROM attestations WHERE id = $id".update.run() > 0
