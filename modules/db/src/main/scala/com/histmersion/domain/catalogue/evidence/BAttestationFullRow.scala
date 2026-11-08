package com.histmersion
package domain.catalogue.evidence

import com.histmersion.util.database.given
import com.augustnagro.magnum.DbCodec
import java.util.UUID
import com.histmersion.util.database.NewtypeColumns.given
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

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
  object TargetType extends RefinedType[String, Pure]
  type TargetId = TargetId.T
  object TargetId extends RefinedType[String, Pure]
  type Quote = Quote.T
  object Quote extends RefinedType[String, Pure]
  type Prefix = Prefix.T
  object Prefix extends RefinedType[String, Pure]
  type Suffix = Suffix.T
  object Suffix extends RefinedType[String, Pure]
  type CharStart = CharStart.T
  object CharStart extends RefinedType[Int, Pure]
  type CharEnd = CharEnd.T
  object CharEnd extends RefinedType[Int, Pure]
  type ChunkOrd = ChunkOrd.T
  object ChunkOrd extends RefinedType[Int, Pure]
  type Page = Page.T
  object Page extends RefinedType[String, Pure]
  type Canvas = Canvas.T
  object Canvas extends RefinedType[String, Pure]
  type Xywh = Xywh.T
  object Xywh extends RefinedType[String, Pure]
  type CtsUrn = CtsUrn.T
  object CtsUrn extends RefinedType[String, Pure]
  type Note = Note.T
  object Note extends RefinedType[String, Pure]
  type CreatedAt = CreatedAt.T
  object CreatedAt extends RefinedType[String, Pure]
  type UpdatedAt = UpdatedAt.T
  object UpdatedAt extends RefinedType[String, Pure]
