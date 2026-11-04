package com.histmersion.api.library
import com.histmersion.api.given_Configuration
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

/** A push: the item the original went into, the source's title, and with a replace the format replaced. */
final case class CollectionPushed(
    citekey: String,
    id: String,
    title: String,
    replaced: Option[String] = None
) derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema
