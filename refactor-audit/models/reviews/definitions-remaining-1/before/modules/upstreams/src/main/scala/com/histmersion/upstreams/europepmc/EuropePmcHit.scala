package com.histmersion.upstreams.europepmc
final case class EuropePmcHit(pmcid: Option[String], isOpenAccess: Boolean, pdfUrls: List[String])
