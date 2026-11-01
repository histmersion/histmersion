package com.histmersion.domain.library.sources.referrers

import com.histmersion.api.{DeleteBlocker, DeleteCount}

/** What a delete would touch of a referrer's records: what refuses it, how many go, the records whose reasons name it
  * (`coverage`) and the items that mention it and stay (`mentions`).
  */
final case class DeleteReport(
    blockers: List[DeleteBlocker] = Nil,
    counts: List[DeleteCount] = Nil,
    coverage: List[String] = Nil,
    mentions: List[Int] = Nil
)
