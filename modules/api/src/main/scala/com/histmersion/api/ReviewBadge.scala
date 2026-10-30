package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

/** An article's review state as a badge: the state, its short `label` (`in review · round 2`, `3 majors open`) and the
  * `detail` sentence its tooltip shows. The counts are of the review ledger's findings; `round` is the pass of the run
  * that is going on now.
  */
final case class ReviewBadge(
    state: ReviewState,
    label: String,
    detail: String,
    openMajors: Int = 0,
    openMinors: Int = 0,
    findings: Int = 0,
    runs: Int = 0,
    round: Option[Int] = None
) derives KindlingsCodecAsObject,
      KindlingsSchema
object ReviewBadge:
  given Schema[ReviewBadge] = summon[KindlingsSchema[ReviewBadge]].schema
