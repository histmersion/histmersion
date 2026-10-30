package com.histmersion.api

import com.histmersion.values.BundleKind

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

/** What renaming an article, an event or a goal did: the old label is kept as an alias (it keeps resolving, as an old
  * citekey does), and `rewritten` names the articles whose `[[old]]` links were rewritten (an article rename only).
  */
final case class LabelRenamed(entity: BundleKind, from: String, to: String, rewritten: List[String] = Nil)
    derives KindlingsCodecAsObject,
      KindlingsSchema
object LabelRenamed:
  given Schema[LabelRenamed] = summon[KindlingsSchema[LabelRenamed]].schema
