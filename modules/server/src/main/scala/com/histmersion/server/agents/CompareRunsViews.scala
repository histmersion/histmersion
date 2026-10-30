package com.histmersion.server.agents

import com.histmersion.api.Agents.{RunDetail, RunInfo}
import com.histmersion.api.RunCompare
import com.histmersion.domain.CompareRuns
import io.scalaland.chimney.Transformer
import io.scalaland.chimney.dsl.*

/** `agent compare` at the server's edge: the recorded runs, as the store reads them, into what the domain compares; the
  * domain's comparison into what the wire shows.
  */
object CompareRunsViews:
  private given Transformer[RunInfo, CompareRuns.RunHead] =
    Transformer
      .define[RunInfo, CompareRuns.RunHead]
      .withFieldComputed(_.modelFor, _.params.overrides.map(_.transformInto[CompareRuns.ModelChoice]))
      .buildTransformer

  def run(detail: RunDetail): CompareRuns.Run = detail.transformInto[CompareRuns.Run]

  def comparison(c: CompareRuns.Comparison): RunCompare.Comparison = c.transformInto[RunCompare.Comparison]
