package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

// ── wire DTOs (declared last: see AGENTS.md on `derives KindlingsCodecAsObject` ordering) ─────────

/** One image as it travels on the wire — from the converter, and into `POST /api/sources/{ck}/assets`. */
final case class AssetInput(
    name: String,
    mime: String,
    base64: String,
    width: Option[Int] = None,
    height: Option[Int] = None,
    sha256: Option[String] = None
) derives KindlingsCodecAsObject,
      KindlingsSchema
object AssetInput:
  given Schema[AssetInput] = summon[KindlingsSchema[AssetInput]].schema
