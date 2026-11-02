package com.histmersion.domain.catalogue.events

import com.histmersion.util.database.given
import com.augustnagro.magnum.DbCodec
import java.util.UUID

/** An event as its bundle line reads it: the row, its parent's and its source's uids. */
final private[events] case class BEventRow(
    uid: UUID,
    origin: UUID,
    id: String,
    label: String,
    kind: String,
    notBefore: Option[Long],
    beginBy: Option[Long],
    endFrom: Option[Long],
    notAfter: Option[Long],
    historicity: String,
    geom: Option[String],
    placeId: Option[String],
    parentUid: Option[UUID],
    description: Option[String],
    citekey: Option[String],
    sourceUid: Option[UUID],
    updatedAt: String,
    scale: Option[String],
    catalogue: Option[String],
    names: List[String],
    wikidata: Option[String],
    central: Option[Long],
    plusMinus: Option[Long],
    datingMethod: Option[String]
) derives DbCodec
