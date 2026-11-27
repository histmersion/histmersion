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
import com.histmersion.lint.{LintRule, tolerated}

@tolerated(LintRule.Prefix, "pending G9 (namespaces, not prefixes)")
@tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
final case class EventLinksRequest(
    recompute: EventLinksRequest.Recompute = EventLinksRequest.Recompute(false),
    confirm: List[EventLinksRequest.Confirm] = Nil,
    reject: List[EventLinksRequest.Reject] = Nil,
    createdBy: Option[Creation.By] = None,
    via: Option[Creation.Via] = None
) extends Creation.Requested derives StrictJson, KindlingsSchema, api.json.Schema
object EventLinksRequest:

  type Recompute = Recompute.T
  object Recompute extends Newtype[Boolean, Pure]
  type Confirm = Confirm.T
  object Confirm extends Newtype[String, Pure]
  type Reject = Reject.T
  object Reject extends Newtype[String, Pure]
