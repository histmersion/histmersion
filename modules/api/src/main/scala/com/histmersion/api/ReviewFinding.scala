package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

/** One finding of the review ledger, where it was raised and what became of it: `outcome` is the ledger's status
  * (`open`, `fixed`, `rejected`, `wont-fix`, `set-aside`); `note` the closure's evidence (the record's sentence of a
  * rejection, the reason a minor one was declined).
  */
final case class ReviewFinding(
    id: String,
    reviewer: String,
    severity: String,
    kind: String,
    where: String,
    problem: String,
    fix: String,
    quote: String,
    outcome: String,
    note: String = "",
    closedBy: Option[String] = None,
    closedRound: Option[Int] = None,
    closedRunNumber: Option[Int] = None,
    reopened: Int = 0,
    /** Raised by the final pass of its run: the next run's work. */
    deferred: Boolean = false
) derives KindlingsCodecAsObject,
      KindlingsSchema
object ReviewFinding:
  given Schema[ReviewFinding] = summon[KindlingsSchema[ReviewFinding]].schema
