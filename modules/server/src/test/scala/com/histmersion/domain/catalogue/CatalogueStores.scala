package com.histmersion.domain.catalogue

import com.histmersion.domain.catalogue.events.{CorrectionService, EventImportService, EventService}
import com.histmersion.domain.catalogue.evidence.{AttestationService, EventLinkService, LeadService}
import com.histmersion.domain.catalogue.periods.PeriodService
import com.histmersion.domain.catalogue.places.SiteTypeService

/** The catalogue's services one test runs against, in memory or over a scratch database, and how a test seeds what
  * another domain owns.
  */
trait CatalogueStores:
  def periods: PeriodService
  def attestations: AttestationService
  def events: EventService
  def corrections: CorrectionService
  def eventImports: EventImportService
  def eventLinks: EventLinkService
  def leads: LeadService
  def siteTypes: SiteTypeService

  /** A source of the library's (titled by its citekey), for what points at one. */
  def source(citekey: String): Unit

  /** A Wikidata site of the gazetteer, `onMap` when a finding is placed at it, its import's class `cls`. */
  def site(id: String, onMap: Boolean, cls: Option[String]): Unit

  /** A `dataset = 'seed'` row count straight from the store (the in-memory one counts its own). */
  def seedRows(): List[String]
