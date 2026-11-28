package com.histmersion
package api.catalogue.eventtaxonomy

import com.histmersion.api.given
import com.histmersion.lint.tolerated
import com.histmersion.lint.LintRule
import com.histmersion.api.catalogue.EventSummary
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.json.TwoStates
import com.histmersion.api.catalogue.EventTaxonomy.group
import com.histmersion.api.catalogue.EventTaxonomy.canonical

@tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
final case class KindCondition(kinds: List[EventSummary.Kind], negate: KindCondition.Negate)

object KindCondition:
  type Filter = Filter.T
  object Filter extends Newtype[String, Pure]
  enum Negate derives CanEqual:
    case Negate, Affirm
  object Negate extends TwoStates[Negate]:
    extension (negate: Negate) def negate: Boolean = negate == Negate

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def matches(filter: KindCondition.Filter, kind: EventSummary.Kind): Boolean =
    val f = (filter).value.trim.toLowerCase(java.util.Locale.ROOT)
    f.isEmpty || f == group(kind).value || canonical(EventSummary.Kind(f)) == canonical(kind)
