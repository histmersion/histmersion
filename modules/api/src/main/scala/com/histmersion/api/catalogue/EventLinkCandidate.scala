package com.histmersion
package api.catalogue
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.api.library.Citekey
import com.histmersion.api.library.Title
import com.histmersion.api.library.Finding
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{LintRule, tolerated}

@tolerated(LintRule.Direction, "pending G12 (a domain names only lower places)")
@tolerated(LintRule.Prefix, "pending G9 (namespaces, not prefixes)")
@tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
@tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
final case class EventLinkCandidate(
    eventId: Event.Id,
    citekey: Citekey,
    score: EventLinkCandidate.Score,
    status: EventLinkCandidate.StatusValue = EventLinkCandidate.StatusValue(EventLinkCandidate.Status.Proposed),
    title: Option[Title] = None,
    findings: List[Finding.Id] = Nil,
    passages: List[EventPassage] = Nil,
    reasons: List[EventLinkCandidate.Reasons] = Nil
) derives JsonCodec,
      KindlingsSchema,
      api.json.Schema
object EventLinkCandidate:

  /** Where a candidate link between an event and a source stands. */
  type Score = Score.T
  object Score extends Newtype[Double, Pure]
  type StatusValue = StatusValue.T
  @tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
  object StatusValue extends Newtype[String, Pure]
  type Reasons = Reasons.T
  object Reasons extends Newtype[String, Pure]

  object Status:
    type All = All.T
    object All extends Newtype[String, Pure]

    val Proposed = "proposed"
    val Confirmed = "confirmed"
    val Rejected = "rejected"
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    val all: List[All] = List(All(Proposed), All(Confirmed), All(Rejected))
