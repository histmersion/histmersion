package com.histmersion
package db

import com.augustnagro.magnum.*

import javax.sql.DataSource
import com.histmersion.api.catalogue.Attestation
import com.histmersion.util.database.given
import com.histmersion.util.database.NewtypeColumns.given

/** Attestations written for a db suite's fixture, as the catalogue's evidence stores them; the new row's id. */
object AttestationRows:
  def create(ds: DataSource, in: Attestation.Put): Long =
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
