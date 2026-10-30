package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import io.circe.Json

import java.util.UUID

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
