package com.histmersion.domain.catalogue

import com.histmersion.domain.catalogue.evidence.AttestationService
import com.histmersion.domain.catalogue.periods.PeriodService

/** The catalogue's services one test runs against, in memory or over a scratch database, and how a test seeds what
  * another domain owns.
  */
trait CatalogueStores:
  def periods: PeriodService
  def attestations: AttestationService

  /** A source of the library's, for what points at one (in memory, nothing to do). */
  def source(citekey: String): Unit

  /** A `dataset = 'seed'` row count straight from the store (the in-memory one counts its own). */
  def seedRows(): List[String]
