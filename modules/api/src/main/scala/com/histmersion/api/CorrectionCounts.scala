package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import io.circe.syntax.*
import sttp.tapir.Schema

/** What a refresh did with the corrections it met: kept applying, retired, newly in conflict. */
final case class CorrectionCounts(kept: Int = 0, retired: Int = 0, conflicts: Int = 0)
    derives KindlingsCodecAsObject,
      KindlingsSchema:
  def +(o: CorrectionCounts): CorrectionCounts =
    CorrectionCounts(kept + o.kept, retired + o.retired, conflicts + o.conflicts)
  def isEmpty: Boolean = kept == 0 && retired == 0 && conflicts == 0

  /** Pure: the counts in words, `None` when there were none. */
  def summary: Option[String] =
    Option.when(!isEmpty)(
      s"corrections by hand: $kept kept, $retired retired (upstream agrees), $conflicts in conflict"
    )
object CorrectionCounts:
  given Schema[CorrectionCounts] = summon[KindlingsSchema[CorrectionCounts]].schema
