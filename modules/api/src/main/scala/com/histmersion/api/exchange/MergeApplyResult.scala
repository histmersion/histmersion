package com.histmersion.api.exchange
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

import java.util.UUID
import com.histmersion.api.given

final case class MergeApplyResult(
    bundleId: UUID,
    from: Bundle.Instance,
    applied: Boolean,
    planHash: String,
    mergeId: Option[UUID] = None,
    backup: Option[String] = None,
    counts: List[Bundle.KindCounts] = Nil,
    tally: MergeTally = MergeTally(),
    reviews: List[String] = Nil,
    logEntry: Option[Int] = None,
    replanChanges: Option[Int] = None,
    problems: List[String] = Nil,
    issues: List[MergeIssue] = Nil,
    notes: List[String] = Nil
) derives KindlingsCodecAsObject,
      KindlingsSchema:
  def changed: Int = counts.map(_.changed).sum
object MergeApplyResult:
  given Schema[MergeApplyResult] = summon[KindlingsSchema[MergeApplyResult]].schema
