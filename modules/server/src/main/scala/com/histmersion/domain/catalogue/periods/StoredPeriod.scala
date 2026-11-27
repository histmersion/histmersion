package com.histmersion
package domain.catalogue.periods

import com.histmersion.api.Dataset
import com.histmersion.api.catalogue.HistoricalPeriod
import com.histmersion.lint.{tolerated, LintRule}

/** A period as the in-memory store keeps it: the row and the dataset it came with (a seed's, or a collection's own). */
@tolerated(LintRule.Layer, "pending G7 (separate types per layer)")
@tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
final case class StoredPeriod(period: HistoricalPeriod, dataset: Dataset)
