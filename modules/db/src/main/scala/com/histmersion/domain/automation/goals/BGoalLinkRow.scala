package com.histmersion
package domain.automation.goals

import com.histmersion.util.database.given
import com.augustnagro.magnum.DbCodec

import java.util.UUID
import com.histmersion.util.database.NewtypeColumns.given
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

/** A goal's link as a bundle reads it, the linked record by its uid (Magnum maps a SELECT's columns positionally). */
final private[histmersion] case class BGoalLinkRow(
    goalUid: UUID,
    kind: BGoalLinkRow.Kind,
    ref: BGoalLinkRow.Ref,
    refUid: Option[UUID],
    note: Option[BGoalLinkRow.Note],
    createdAt: BGoalLinkRow.CreatedAt
) derives DbCodec

object BGoalLinkRow:
  type Kind = Kind.T
  object Kind extends RefinedType[String, Pure]
  type Ref = Ref.T
  object Ref extends RefinedType[String, Pure]
  type Note = Note.T
  object Note extends RefinedType[String, Pure]
  type CreatedAt = CreatedAt.T
  object CreatedAt extends RefinedType[String, Pure]
