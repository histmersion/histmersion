package com.histmersion.domain.automation.goals

import com.histmersion.util.database.given
import com.augustnagro.magnum.DbCodec

import java.util.UUID

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
  object Kind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Ref = Ref.T
  object Ref extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Note = Note.T
  object Note extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CreatedAt = CreatedAt.T
  object CreatedAt extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
