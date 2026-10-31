package com.histmersion.upstreams

/** What Europe PMC's search knows about one DOI: the PMC copy, if any, whether it is in the open-access subset, and the
  * PDF links it lists as free to read (`OA` open access, `F` free — an author manuscript); `S` (subscription) and `R`
  * (registration) links are never taken.
  */
final case class EuropePmcHit(pmcid: Option[String], isOpenAccess: Boolean, pdfUrls: List[String])
