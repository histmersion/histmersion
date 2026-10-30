package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

/** One passage of a source that names an event: `[start, end)` are character offsets into the source's body (the
  * offsets `get_source` and `passages` use), `quote` the matched text, `name` the name it matched, `context` the text
  * around it.
  */
final case class EventPassage(start: Int, end: Int, quote: String, name: String, context: String = "")
    derives KindlingsCodecAsObject,
      KindlingsSchema
object EventPassage:
  given Schema[EventPassage] = summon[KindlingsSchema[EventPassage]].schema
