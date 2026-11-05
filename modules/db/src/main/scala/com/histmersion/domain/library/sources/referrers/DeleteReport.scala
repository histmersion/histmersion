package com.histmersion.domain.library.sources.referrers

import com.histmersion.api.library.{DeleteBlocker, DeleteCount}

/** What a delete would touch of a referrer's records: what refuses it, how many go, the records whose reasons name it
  * (`coverage`) and the items that mention it and stay (`mentions`).
  */
final case class DeleteReport(
    blockers: List[DeleteBlocker] = Nil,
    counts: List[DeleteCount] = Nil,
    coverage: List[DeleteReport.Coverage] = Nil,
    mentions: List[DeleteReport.Mentions] = Nil
)

object DeleteReport:
  type Coverage = Coverage.T
  object Coverage extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Mentions = Mentions.T
  object Mentions extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
