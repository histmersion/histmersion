package com.histmersion
package domain.automation.agents

import scala.annotation.tailrec

import scala.compiletime.asMatchable
import scala.jdk.CollectionConverters.*
import io.github.iltotore.iron.constraint.any.Pure

/** Which of the runtime's own web tools a role may use: none, Histmersion's own `discover` (OpenAlex, Crossref,
  * Wikipedia references — still MCP only), or the runtime's web search / fetch.
  */
enum WebAccess(val wire: WebAccess.Wire) derives CanEqual:
  case None extends WebAccess(WebAccess.Wire("none"))
  case Discover extends WebAccess(WebAccess.Wire("discover"))
  case WebSearch extends WebAccess(WebAccess.Wire("search"))
  case Fetch extends WebAccess(WebAccess.Wire("fetch"))

object WebAccess:
  type Wire = Wire.T
  object Wire extends Newtype[String, Pure]
