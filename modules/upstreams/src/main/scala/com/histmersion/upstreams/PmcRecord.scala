package com.histmersion.upstreams

/** A PMC Article Datasets metadata record: which files NCBI publishes for one article version. */
final case class PmcRecord(
    pmcid: String,
    openAccess: Boolean,
    manuscript: Boolean,
    license: Option[String],
    pdf: Option[PmcObject],
    xml: Option[PmcObject]
)
