package com.histmersion
package api.automation.approvaldigest

import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.tolerated
import com.histmersion.lint.LintRule
import com.histmersion.api.Plural
import com.histmersion.api.automation.ApprovalDigest.Line
import com.histmersion.api.automation.ApprovalDigest.Waits

final case class Digest(
    at: Digest.At,
    total: Digest.Total = Digest.Total(0),
    kinds: List[KindGroup] = Nil,
    heldForPerson: Digest.HeldForPerson = Digest.HeldForPerson(0),
    lastWritten: Option[Digest.LastWritten] = None,
    goalPlansWait: Option[Digest.GoalPlansWait] = None
) derives JsonCodec,
      KindlingsSchema,
      api.json.Schema

object Digest:
  type At = At.T
  object At extends Newtype[String, Pure]
  type Total = Total.T
  object Total extends Newtype[Int, Pure]
  type HeldForPerson = HeldForPerson.T
  object HeldForPerson extends Newtype[Int, Pure]
  type LastWritten = LastWritten.T
  object LastWritten extends Newtype[String, Pure]
  type GoalPlansWait = GoalPlansWait.T
  @tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
  object GoalPlansWait extends Newtype[String, Pure]

  extension (d: Digest)
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def line: Line =
      if d.total == Digest.Total(0) && d.heldForPerson == Digest.HeldForPerson(0) then
        Line("approval digest: nothing waits for a person")
      else
        Line(
          s"approval digest: ${Plural.count(Plural.CountN(d.total.value.toLong), Plural.CountSingular("approval"))} wait for a person" +
            (if d.kinds.isEmpty then ""
             else
               " — " + d.kinds
                 .map(g =>
                   s"${g.kind} ${g.count}" + g.oldest.fold("")(o => s" (oldest $o)") +
                     (if g.decidedBy.value == "checked" then ", the autopilot's checks decide them within the day's cap"
                      else if g.decidedBy.value == Waits then ", waiting: nobody needs to decide them now"
                      else "")
                 )
                 .mkString("; ")) +
            (if d.heldForPerson.value > 0 then
               s"; ${Plural.count(Plural.CountN(d.heldForPerson.value.toLong), Plural.CountSingular("manager item"))} held for a person"
             else "") +
            d.goalPlansWait.fold("")(w => s"; $w") +
            "; decide a whole kind at once: histmersion agent decide-all approve --kind <kind> (or the Approvals window)"
        )
