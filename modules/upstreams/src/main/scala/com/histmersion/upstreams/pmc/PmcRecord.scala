package com.histmersion.upstreams.pmc
final case class PmcRecord(
    pmcid: String,
    openAccess: Boolean,
    manuscript: Boolean,
    license: Option[String],
    pdf: Option[PmcObject],
    xml: Option[PmcObject]
)
