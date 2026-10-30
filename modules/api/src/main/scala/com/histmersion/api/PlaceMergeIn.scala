package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import io.circe.Codec
import sttp.tapir.Schema

/** `POST /api/places/{id}/merge` — the place is the same as `into`: its findings, links, children, events, attestations
  * and asset links move there, it is deleted, and its id stays an old id of `into` — which no re-seed brings back as a
  * place of its own.
  */
final case class PlaceMergeIn(into: String, reason: Option[String] = None)
object PlaceMergeIn:
  given Schema[PlaceMergeIn] = KindlingsSchema
    .derived[PlaceMergeIn]
    .schema
    .modify(_.into)(_.description("the id of the place it is the same as"))
  given Codec.AsObject[PlaceMergeIn] = StrictJson.codec(KindlingsCodecAsObject.derived[PlaceMergeIn])
