package com.histmersion
package util.database

import com.histmersion.lint.{LintRule, tolerated}
/** A write's answer its caller has no use for, said so with why (G94). A write answers whether it changed, found or
  * moved something, or how many rows it touched; dropping that answer silently is refused by the guideline inventory
  * (`discardedOutcome`, pinned at 0). Where every answer leaves the same state, the site says which case it is, and the
  * inventory counts those sites (`acceptedOutcome`, a ratchet the owner accepts rises of).
  */
object Unused:

  /** Why no answer of the write changes what its caller does. */
  enum Why derives CanEqual:
    /** The statement establishes a state, whatever it found: deleting rows that may be gone, inserting rows that may be
      * there (`ON CONFLICT DO NOTHING`), moving the rows that refer to a renamed or merged key (none is a fine number),
      * rewriting a derived table, touching or marking a row that may have gone meanwhile (nothing is left to change).
      * Zero rows, or any number, leave nothing the caller should act on.
      */
    case Idempotent

    /** The row is there by construction: the same transaction created it, locked it or checked it just before (a rename
      * after its existence checks, the one-row settings table made on the way), so a count other than one is not a race
      * the caller could meet.
      */
    case Held

    /** A count that is only a report (how many rows a bulk write touched), which nobody reads at this site, or which
      * the callee already said in the log.
      */
    case Reported

    /** The caller reads the row back right after and answers from what it finds, so a row that went away meanwhile
      * surfaces there (as the read's not-found), not here.
      */
    case ReadBack

  /** `answer`, dropped for `why`. */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def apply[A](answer: A, why: Why): Unit = ()
