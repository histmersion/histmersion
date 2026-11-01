package com.histmersion.domain.catalogue.periods

import com.histmersion.api.{Dataset, HistoricalPeriod}

/** A period as the in-memory store keeps it: the row and the dataset it came with (a seed's, or a collection's own). */
final case class StoredPeriod(period: HistoricalPeriod, dataset: Dataset)
