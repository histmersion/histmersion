package com.histmersion.upstreams

/** Whether a free copy of a work exists, as an OA index (Unpaywall) reports it. */
final case class OaAccess(isOa: Boolean, status: Option[String], url: Option[String])
