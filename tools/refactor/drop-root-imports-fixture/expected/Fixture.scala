package com.histmersion
package api.agents

import scala.util.Try
import com.histmersion.util
import com.histmersion.util.Text

final case class Probe(model: Probe.Model)

object Probe:
  type Model = Model.T
  object Model extends Newtype[String]
  given Display[Probe] = Display(_ => Try("probe").getOrElse(Text.empty))
