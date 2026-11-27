package com.histmersion
package api.catalogue
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.automation.Creation
import com.histmersion.lint.{LintRule, tolerated}

@tolerated(LintRule.Prefix, "pending G9 (namespaces, not prefixes)")
@tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
final case class EventRederiveRequest(
    provider: Option[EventRederiveRequest.Provider] = None,
    dryRun: EventRederiveRequest.DryRun = EventRederiveRequest.DryRun(false),
    createdBy: Option[Creation.By] = None,
    via: Option[Creation.Via] = None
) extends Creation.Requested derives JsonCodec, KindlingsSchema, api.json.Schema

object EventRederiveRequest:
  export EventImportReport.Provider
  type DryRun = DryRun.T
  object DryRun extends Newtype[Boolean, Pure]
