package com.histmersion.db

import com.histmersion.api.catalogue.AttestationPut
import com.augustnagro.magnum.*

import javax.sql.DataSource

/** Attestations written for a db suite's fixture, as the catalogue's evidence stores them; the new row's id. */
object AttestationRows:
  def create(ds: DataSource, in: AttestationPut): Long =
    transact(ds):
      sql"""INSERT INTO attestations (citekey, target_type, target_id,
                quote, prefix, suffix, char_start, char_end, chunk_ord,
                page, canvas, xywh, cts_urn, note)
            VALUES (${in.citekey}, ${in.targetType}, ${in.targetId},
                    ${in.quote}, ${in.prefix}, ${in.suffix},
                    ${in.charStart}, ${in.charEnd}, ${in.chunkOrd},
                    ${in.page}, ${in.canvas}, ${in.xywh},
                    ${in.ctsUrn}, ${in.note})
            RETURNING id""".query[Long].run().head
