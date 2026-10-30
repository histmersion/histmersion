package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import io.circe.Json

import java.util.UUID

/** What a CATALOGUE says about an event (`event_catalogue`, V085), one line per (provider, provider id). Not an entity
  * of its own: it travels beside its event, by the event's uid, and an import keeps the newer read of a record
  * (`retrievedAt`).
  */
final case class BundleEventCatalogue(
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
    record: Json,
    retrievedAt: String
) derives KindlingsCodecAsObject
