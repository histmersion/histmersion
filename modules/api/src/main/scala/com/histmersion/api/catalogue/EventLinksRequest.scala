package com.histmersion
package api.catalogue
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.StrictJson
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.automation.Creation
import com.histmersion.lint.{tolerated, LintRule}
import com.histmersion.api.TwoStates

@tolerated(LintRule.Prefix, "pending G9 (namespaces, not prefixes)")
@tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
final case class EventLinksRequest(
    recompute: EventLinksRequest.Recompute = EventLinksRequest.Recompute.Reuse,
    confirm: List[EventLinksRequest.Confirm] = Nil,
    reject: List[EventLinksRequest.Reject] = Nil,
    createdBy: Option[Creation.By] = None,
    via: Option[Creation.Via] = None
) extends Creation.Requested derives StrictJson, KindlingsSchema, api.json.Schema
object EventLinksRequest:
  enum Recompute derives CanEqual:
    case Recompute, Reuse
  object Recompute extends TwoStates[Recompute]:
    extension (recompute: Recompute) def recompute: Boolean = recompute == Recompute
  type Confirm = Confirm.T
  object Confirm extends Newtype[String, Pure]
  type Reject = Reject.T
  object Reject extends Newtype[String, Pure]
