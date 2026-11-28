package com.histmersion
package api.automation.managers

import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.api.automation.Managers.Rationale
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.tolerated
import com.histmersion.lint.LintRule

final case class Hold(
    until: Option[Hold.Until] = None,
    untilSource: Option[Hold.UntilSource] = None,
    reason: Rationale = Rationale(""),
    hint: Option[Hold.Hint] = None,
    untilApproval: Option[Hold.UntilApproval] = None,
    focus: Option[Hold.Focus] = None
) derives JsonCodec,
      KindlingsSchema,
      api.json.Schema

object Hold:
  type Until = Until.T
  object Until extends Newtype[String, Pure]
  type UntilSource = UntilSource.T
  @tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
  object UntilSource extends Newtype[String, Pure]
  type Hint = Hint.T
  object Hint extends Newtype[Long, Pure]
  type UntilApproval = UntilApproval.T
  @tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
  object UntilApproval extends Newtype[Long, Pure]
  type Focus = Focus.T
  object Focus extends Newtype[String, Pure]
