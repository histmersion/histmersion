package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

/** One of the database's largest tables, indexes and TOAST included (`pg_total_relation_size`). */
final case class TableSize(name: String, bytes: Long) derives KindlingsCodecAsObject, KindlingsSchema
object TableSize:
  given Schema[TableSize] = summon[KindlingsSchema[TableSize]].schema
