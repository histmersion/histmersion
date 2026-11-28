package com.histmersion
package api.automation.autopilot

import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.lint.tolerated
import com.histmersion.lint.LintRule
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.automation.Approval

@tolerated(LintRule.G9PrefixRole, "pending G9 (namespaces, not prefixes)")
final case class ApprovalRule(
    kind: ApprovalRule.Kind,
    mode: ApprovalRule.Mode,
    cap: ApprovalRule.Cap = ApprovalRule.Cap("")
) derives JsonCodec,
      KindlingsSchema,
      api.json.Schema

object ApprovalRule:
  export Approval.Kind.Wire as Kind
  export Approval.Mode.Wire as Mode
  type Cap = Cap.T
  object Cap extends Newtype[String, Pure]
