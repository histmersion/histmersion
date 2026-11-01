package com.histmersion.domain.catalogue.events

import com.augustnagro.magnum.DbCodec
import com.histmersion.db.given_DbCodec_List

/** A stored event as the import's merge reads it, with its catalogue keys as `provider|id`. */
final private[events] case class StoredEventRow(
    id: String,
    kind: String,
    notBefore: Option[Long],
    beginBy: Option[Long],
    endFrom: Option[Long],
    notAfter: Option[Long],
    lat: Option[Double],
    lon: Option[Double],
    wikidata: Option[String],
    keys: List[String],
    catalogue: Option[String],
    label: String
) derives DbCodec
