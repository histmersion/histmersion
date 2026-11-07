package com.histmersion.domain.catalogue.evidence

import com.histmersion.util.database.given
import com.augustnagro.magnum.DbCodec
import java.util.UUID
import com.histmersion.util.database.NewtypeColumns.given

/** An attestation, as its bundle line reads it: the row, its source's and its target's uids. */
final private[evidence] case class BAttestationFullRow(
    uid: UUID,
    origin: UUID,
    sourceUid: UUID,
    targetType: BAttestationFullRow.TargetType,
    targetId: BAttestationFullRow.TargetId,
    targetUid: Option[UUID],
    quote: Option[BAttestationFullRow.Quote],
    prefix: Option[BAttestationFullRow.Prefix],
    suffix: Option[BAttestationFullRow.Suffix],
    charStart: Option[BAttestationFullRow.CharStart],
    charEnd: Option[BAttestationFullRow.CharEnd],
    chunkOrd: Option[BAttestationFullRow.ChunkOrd],
    page: Option[BAttestationFullRow.Page],
    canvas: Option[BAttestationFullRow.Canvas],
    xywh: Option[BAttestationFullRow.Xywh],
    ctsUrn: Option[BAttestationFullRow.CtsUrn],
    note: Option[BAttestationFullRow.Note],
    createdAt: BAttestationFullRow.CreatedAt,
    updatedAt: BAttestationFullRow.UpdatedAt
) derives DbCodec

object BAttestationFullRow:
  type TargetType = TargetType.T
  object TargetType extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TargetId = TargetId.T
  object TargetId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Quote = Quote.T
  object Quote extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Prefix = Prefix.T
  object Prefix extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Suffix = Suffix.T
  object Suffix extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CharStart = CharStart.T
  object CharStart extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CharEnd = CharEnd.T
  object CharEnd extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ChunkOrd = ChunkOrd.T
  object ChunkOrd extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Page = Page.T
  object Page extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Canvas = Canvas.T
  object Canvas extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Xywh = Xywh.T
  object Xywh extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CtsUrn = CtsUrn.T
  object CtsUrn extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Note = Note.T
  object Note extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CreatedAt = CreatedAt.T
  object CreatedAt extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type UpdatedAt = UpdatedAt.T
  object UpdatedAt extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
