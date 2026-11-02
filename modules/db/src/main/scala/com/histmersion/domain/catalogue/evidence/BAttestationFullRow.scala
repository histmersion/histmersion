package com.histmersion.domain.catalogue.evidence

import com.histmersion.util.database.given
import com.augustnagro.magnum.DbCodec
import java.util.UUID

/** An attestation, as its bundle line reads it: the row, its source's and its target's uids. */
final private[evidence] case class BAttestationFullRow(
    uid: UUID,
    origin: UUID,
    sourceUid: UUID,
    targetType: String,
    targetId: String,
    targetUid: Option[UUID],
    quote: Option[String],
    prefix: Option[String],
    suffix: Option[String],
    charStart: Option[Int],
    charEnd: Option[Int],
    chunkOrd: Option[Int],
    page: Option[String],
    canvas: Option[String],
    xywh: Option[String],
    ctsUrn: Option[String],
    note: Option[String],
    createdAt: String,
    updatedAt: String
) derives DbCodec
