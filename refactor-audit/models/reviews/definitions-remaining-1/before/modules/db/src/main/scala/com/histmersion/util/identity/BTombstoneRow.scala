package com.histmersion.util.identity

import com.histmersion.util.database.given
import com.augustnagro.magnum.DbCodec

import java.util.UUID

/** A tombstone as a bundle reads it (Magnum maps a SELECT's columns positionally). */
final private[histmersion] case class BTombstoneRow(uid: UUID, kind: String, origin: UUID, deletedAt: String)
    derives DbCodec
