package com.histmersion.api.library.collections

import com.histmersion.api.given_Configuration
import com.histmersion.api.*
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

/** A source collection the server can run: its name, title and description, what it lets the server do (`read`,
  * `add`, `update`), and whether the server can reach it now (`problem` says what is missing when it cannot).
  */
final case class CollectionInfo(
    name: String,
    title: String,
    description: String,
    capabilities: List[String],
    reachable: Boolean,
    problem: Option[String]
) derives KindlingsCodecAsObject,
      KindlingsSchema

object CollectionInfo:
  given Schema[CollectionInfo] = summon[KindlingsSchema[CollectionInfo]].schema
