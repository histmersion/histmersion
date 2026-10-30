package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

/** An approval about the article that waits for a person (an `article-filter`, an `article-publish`). */
final case class ReviewApproval(id: Long, kind: String) derives KindlingsCodecAsObject, KindlingsSchema
object ReviewApproval:
  given Schema[ReviewApproval] = summon[KindlingsSchema[ReviewApproval]].schema
