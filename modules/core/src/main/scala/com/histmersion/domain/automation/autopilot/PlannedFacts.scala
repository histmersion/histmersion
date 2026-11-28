package com.histmersion
package domain.automation.autopilot

import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.tolerated
import com.histmersion.lint.LintRule

final case class PlannedFacts(slug: PlannedFacts.Slug, slugFree: PlannedFacts.SlugFree, hits: PlannedFacts.Hits)

object PlannedFacts:
  type Slug = Slug.T
  object Slug extends Newtype[String, Pure]
  type SlugFree = SlugFree.T
  @tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
  object SlugFree extends Newtype[Boolean, Pure]
  type Hits = Hits.T
  object Hits extends Newtype[Int, Pure]
