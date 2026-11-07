package com.histmersion.util.identity

import com.histmersion.util.database.given
import com.augustnagro.magnum.DbCodec

import java.util.UUID
import com.histmersion.util.database.NewtypeColumns.given

/** A tombstone as a bundle reads it (Magnum maps a SELECT's columns positionally). */
final private[histmersion] case class BTombstoneRow(uid: UUID, kind: BTombstoneRow.Kind, origin: UUID, deletedAt: BTombstoneRow.DeletedAt)
    derives DbCodec

object BTombstoneRow:
  type Kind = Kind.T
  object Kind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DeletedAt = DeletedAt.T
  object DeletedAt extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
