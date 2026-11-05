package com.histmersion.plugin

/** What one part of an import answered: its records, and the part to read after it when the catalogue pages its answers
  * (`next`), which the host reads in turn.
  */
final case class CataloguePage(events: List[CatalogueEvent], next: Option[String] = None)
