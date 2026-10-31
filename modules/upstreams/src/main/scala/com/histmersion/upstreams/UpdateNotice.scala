package com.histmersion.upstreams

/** A Crossref `update-to` notice: a retraction, a correction or an expression of concern attached to a DOI. */
final case class UpdateNotice(kind: Option[String], label: Option[String], doi: Option[String])
