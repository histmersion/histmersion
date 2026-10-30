package com.histmersion.pluginhost

import com.histmersion.api.*
import com.histmersion.api.given

/** PURE parsers for the JSON the acquisition upstreams return (OpenAlex, Crossref, Unpaywall, CORE, Semantic Scholar,
  * archive.org, Wikipedia).
  *
  * Keeping them here — separate from the HTTP clients — is what makes the whole toolset testable from fixtures: a test
  * feeds real captured JSON (`src/test/resources/acquire/`) straight into these functions with no network involved.
  * Everything is total: a missing or unexpected field yields `None`/`Nil`, never an exception, because upstreams do
  * change shape.
  */
/** Whether a free copy of a work exists, as an OA index (Unpaywall) reports it. */
final case class OaAccess(isOa: Boolean, status: Option[String], url: Option[String])
