package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

/** One region of a three-way merge of two article texts: agreed lines, or a conflict between this collection's lines
  * and the bundle's over the common version's.
  */
final case class IssueHunk(
    conflict: Boolean,
    base: List[String] = Nil,
    left: List[String] = Nil,
    right: List[String] = Nil
) derives KindlingsCodecAsObject,
      KindlingsSchema
object IssueHunk:
  given Schema[IssueHunk] = summon[KindlingsSchema[IssueHunk]].schema
