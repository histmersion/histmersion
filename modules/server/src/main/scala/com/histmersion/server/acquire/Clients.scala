package com.histmersion.server.acquire

import com.histmersion.api.*
import com.histmersion.config.*
import com.histmersion.domain.*

/** The upstream clients of the acquisition toolset, each a SMALL trait so tests can hand the handlers a fake that
  * answers from a captured fixture. The implementations below hold no state beyond the config and an [[Http]]; all
  * shaping of the responses happens in [[AcquireParse]], which is pure and tested on its own.
  */

/** A downloaded file plus where it came from — the plugin SPI's type, so a fetcher plugin's copy is the one ingest
  * stores.
  */
type FetchedFile = com.histmersion.plugin.FetchedFile
val FetchedFile = com.histmersion.plugin.FetchedFile
