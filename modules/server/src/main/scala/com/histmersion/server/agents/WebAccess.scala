package com.histmersion.server.agents

import scala.annotation.tailrec

import scala.compiletime.asMatchable
import scala.jdk.CollectionConverters.*

/** Which of the runtime's own web tools a role may use: none, Histmersion's own `discover` (OpenAlex, Crossref,
  * Wikipedia references — still MCP only), or the runtime's web search / fetch.
  */
enum WebAccess(val wire: String):
  case None extends WebAccess("none")
  case Discover extends WebAccess("discover")
  case Search extends WebAccess("search")
  case Fetch extends WebAccess("fetch")
