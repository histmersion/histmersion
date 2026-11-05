package com.histmersion.domain.catalogue.events

import com.histmersion.util.database.given
import com.augustnagro.magnum.DbCodec
import java.util.UUID

/** What a catalogue says about an event, as its bundle line reads it. */
final private[events] case class BCatalogueRow(
    eventUid: UUID,
    provider: String,
    providerId: String,
    url: Option[String],
    label: String,
    kind: String,
    notBefore: Option[Long],
    beginBy: Option[Long],
    endFrom: Option[Long],
    notAfter: Option[Long],
    lat: Option[Double],
    lon: Option[Double],
    vei: Option[Int],
    magnitude: Option[Double],
    deaths: Option[Long],
    historicity: String,
    record: String,
    retrievedAt: String
) derives DbCodec
