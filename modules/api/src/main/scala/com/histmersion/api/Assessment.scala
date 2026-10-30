package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.values.SourceStatus

/** @param verdict
  *   one of [[Acquire.Verdicts]]: retracted|corrected|current|low-impact|unassessed
  * @param applied
  *   the `status` written back to the source, when `--apply` was used
  */
final case class Assessment(
    target: String,
    doi: Option[String],
    citekey: Option[String],
    year: Option[Int],
    citedByCount: Option[Int],
    influentialCitations: Option[Int],
    isRetracted: Boolean,
    retractionWatch: List[RetractionNotice],
    crossrefUpdates: List[UpdateNotice],
    flagged: Boolean,
    verdict: String,
    recommendation: String,
    applied: Option[String] = None,
    checkedAt: Option[String] = None,
    /** With `apply`: the stored status that was KEPT instead of the verdict's (it was already set). */
    kept: Option[SourceStatus] = None
) derives KindlingsCodecAsObject,
      KindlingsSchema
object Assessment:
  given Schema[Assessment] = summon[KindlingsSchema[Assessment]].schema
