package com.histmersion
package api.catalogue
import com.histmersion.api.given
import com.histmersion.lint.{tolerated, LintRule}
@tolerated(LintRule.Prefix, "pending G9 (namespaces, not prefixes)")
@tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
object EventTier:

  val Attested = "attested"
  val Catalogue = "catalogue"
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  val all: List[EventSummary.Tier] = List(EventSummary.Tier(Attested), EventSummary.Tier(Catalogue))
