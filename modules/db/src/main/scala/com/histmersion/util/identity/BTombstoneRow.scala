package com.histmersion
package util.identity

import com.histmersion.util.database.given
import com.augustnagro.magnum.DbCodec

import java.util.UUID
import com.histmersion.util.database.NewtypeColumns.given
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

/** A tombstone as a bundle reads it (Magnum maps a SELECT's columns positionally). */
final private[histmersion] case class BTombstoneRow(uid: UUID, kind: BTombstoneRow.Kind, origin: UUID, deletedAt: BTombstoneRow.DeletedAt)
    derives DbCodec

object BTombstoneRow:
  type Kind = Kind.T
  object Kind extends RefinedType[String, Pure]
  type DeletedAt = DeletedAt.T
  object DeletedAt extends RefinedType[String, Pure]
