package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import io.circe.Codec

final case class AssetPatch(
    kind: Option[String] = None,
    caption: Option[String] = None,
    license: Option[String] = None,
    lat: Option[Double] = None,
    lon: Option[Double] = None,
    periodStart: Option[Long] = None,
    periodEnd: Option[Long] = None
) derives KindlingsSchema

object AssetPatch:
  given Schema[AssetPatch] = summon[KindlingsSchema[AssetPatch]].schema

  given Codec.AsObject[AssetPatch] = StrictJson.codec(KindlingsCodecAsObject.derived[AssetPatch])
