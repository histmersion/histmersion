package com.histmersion.domain.catalogue

import com.histmersion.domain.catalogue.periods.PeriodService

/** The catalogue's services one test runs against, in memory or over a scratch database, and how a test seeds what
  * another domain owns.
  */
trait CatalogueStores:
  def periods: PeriodService

  /** A `dataset = 'seed'` row count straight from the store (the in-memory one counts its own). */
  def seedRows(): List[String]
