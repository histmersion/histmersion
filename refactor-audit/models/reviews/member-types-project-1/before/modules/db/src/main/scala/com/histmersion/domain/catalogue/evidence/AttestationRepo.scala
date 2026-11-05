package com.histmersion.domain.catalogue.evidence

import com.histmersion.api.catalogue.Attestation

import com.augustnagro.magnum.*

/** The attestations (`attestations`): which passage of a source attests a finding, an event, a dating, a place or an
  * edge. Every method runs on the connection it is given.
  */
private[evidence] object AttestationRepo:

  private given DbCodec[Attestation] = DbCodec.derived

  private val cols =
    "a.id, a.citekey, a.target_type, a.target_id, " +
      "a.quote, a.prefix, a.suffix, a.char_start, a.char_end, a.chunk_ord, " +
      "a.page, a.canvas, a.xywh, a.cts_urn, a.note"

  def list(citekey: Option[String], targetType: Option[String], targetId: Option[String], limit: Int, offset: Int)(using
      DbCon
  ): Attestation.List =
    val ck = citekey.map(_.trim).filter(_.nonEmpty)
    val tt = targetType.map(_.trim).filter(_.nonEmpty)
    val ti = targetId.map(_.trim).filter(_.nonEmpty)
    val total = sql"""SELECT count(*)::int FROM attestations a
          WHERE ($ck::text IS NULL OR a.citekey = $ck)
            AND ($tt::text IS NULL OR a.target_type = $tt)
            AND ($ti::text IS NULL OR a.target_id = $ti)
          """.query[Int].run().head
    val rows = sql"""SELECT ${SqlLiteral(cols)} FROM attestations a
          WHERE ($ck::text IS NULL OR a.citekey = $ck)
            AND ($tt::text IS NULL OR a.target_type = $tt)
            AND ($ti::text IS NULL OR a.target_id = $ti)
          ORDER BY a.id
          LIMIT ${limit.max(1)} OFFSET ${offset.max(0)}
          """.query[Attestation].run().toList
    Attestation.List.of(total, rows, offset.max(0))

  def get(id: Long)(using DbCon): Option[Attestation] =
    sql"""SELECT ${SqlLiteral(cols)} FROM attestations a WHERE a.id = $id""".query[Attestation].run().headOption

  def create(in: Attestation.Put)(using DbCon): Attestation =
    val id = sql"""INSERT INTO attestations (citekey, target_type, target_id,
                      quote, prefix, suffix, char_start, char_end, chunk_ord,
                      page, canvas, xywh, cts_urn, note)
                   VALUES (${in.citekey}, ${in.targetType}, ${in.targetId},
                           ${in.quote}, ${in.prefix}, ${in.suffix},
                           ${in.charStart}, ${in.charEnd}, ${in.chunkOrd},
                           ${in.page}, ${in.canvas}, ${in.xywh},
                           ${in.ctsUrn}, ${in.note})
                   RETURNING id""".query[Long].run().head
    get(id).get

  def exists(id: Long)(using DbCon): Boolean = sql"SELECT 1 FROM attestations WHERE id = $id".query[Int].run().nonEmpty

  def update(id: Long, p: Attestation.Patch)(using DbCon): Unit =
    p.quote.foreach(v => sql"UPDATE attestations SET quote = $v WHERE id = $id".update.run())
    p.prefix.foreach(v => sql"UPDATE attestations SET prefix = $v WHERE id = $id".update.run())
    p.suffix.foreach(v => sql"UPDATE attestations SET suffix = $v WHERE id = $id".update.run())
    p.charStart.foreach(v => sql"UPDATE attestations SET char_start = $v WHERE id = $id".update.run())
    p.charEnd.foreach(v => sql"UPDATE attestations SET char_end = $v WHERE id = $id".update.run())
    p.chunkOrd.foreach(v => sql"UPDATE attestations SET chunk_ord = $v WHERE id = $id".update.run())
    p.page.foreach(v => sql"UPDATE attestations SET page = $v WHERE id = $id".update.run())
    p.canvas.foreach(v => sql"UPDATE attestations SET canvas = $v WHERE id = $id".update.run())
    p.xywh.foreach(v => sql"UPDATE attestations SET xywh = $v WHERE id = $id".update.run())
    p.ctsUrn.foreach(v => sql"UPDATE attestations SET cts_urn = $v WHERE id = $id".update.run())
    p.note.foreach(v => sql"UPDATE attestations SET note = $v WHERE id = $id".update.run())

  def delete(id: Long)(using DbCon): Boolean = sql"DELETE FROM attestations WHERE id = $id".update.run() > 0
