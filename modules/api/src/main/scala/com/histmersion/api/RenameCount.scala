package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

/** How many rows of one table/column a rename rewrote — for the columns that hold a citekey (or a finding id) WITHOUT a
  * foreign key, which the cascade cannot reach.
  */
final case class RenameCount(table: String, rows: Int) derives KindlingsCodecAsObject, KindlingsSchema
object RenameCount:
  given Schema[RenameCount] = summon[KindlingsSchema[RenameCount]].schema
