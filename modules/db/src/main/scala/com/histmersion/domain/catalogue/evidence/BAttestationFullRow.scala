package com.histmersion
package domain.catalogue.evidence

import com.histmersion.util.database.given
import com.augustnagro.magnum.DbCodec
import java.util.UUID
import com.histmersion.util.database.NewtypeColumns.given
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.util.database.Jsonb
import com.histmersion.lint.{tolerated, LintRule}

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
    updatedAt: BAttestationFullRow.UpdatedAt,
    creator: Option[Jsonb.JsonText] = None
) derives DbCodec

object BAttestationFullRow:
  type TargetType = TargetType.T
  @tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
  object TargetType extends Newtype[String, Pure]
  type TargetId = TargetId.T
  @tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
  object TargetId extends Newtype[String, Pure]
  type Quote = Quote.T
  object Quote extends Newtype[String, Pure]
  type Prefix = Prefix.T
  object Prefix extends Newtype[String, Pure]
  type Suffix = Suffix.T
  object Suffix extends Newtype[String, Pure]
  type CharStart = CharStart.T
  object CharStart extends Newtype[Int, Pure]
  type CharEnd = CharEnd.T
  object CharEnd extends Newtype[Int, Pure]
  type ChunkOrd = ChunkOrd.T
  object ChunkOrd extends Newtype[Int, Pure]
  type Page = Page.T
  object Page extends Newtype[String, Pure]
  type Canvas = Canvas.T
  object Canvas extends Newtype[String, Pure]
  type Xywh = Xywh.T
  object Xywh extends Newtype[String, Pure]
  type CtsUrn = CtsUrn.T
  object CtsUrn extends Newtype[String, Pure]
  type Note = Note.T
  object Note extends Newtype[String, Pure]
  type CreatedAt = CreatedAt.T
  object CreatedAt extends Newtype[String, Pure]
  type UpdatedAt = UpdatedAt.T
  object UpdatedAt extends Newtype[String, Pure]
