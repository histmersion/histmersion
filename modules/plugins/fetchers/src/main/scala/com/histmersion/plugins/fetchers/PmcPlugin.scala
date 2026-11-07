package com.histmersion.plugins.fetchers

/** `pmc`: Europe PMC / PMC Article Datasets — the PDF (`pmc`), else the JATS full text (`pmc-text`), by DOI. Two
  * fetchers, because their steps are not adjacent in the chain (the PMC PDF comes before arXiv, its JATS text after the
  * landing pages), sharing one upstream record per work through the fetch's memo.
  */
import com.histmersion.plugin.Plugin
final class PmcPlugin extends FetcherPlugin("pmc"):
  type Config = HostAcquire
  def register(c: HostAcquire, ctx: Plugin.Context): Either[com.histmersion.plugins.fetchers.PmcPlugin.Problem, Unit] =
    val pmc = Pmc(c.acquire, () => ctx.http)
    ctx.sourceFetchers
      .add(com.histmersion.plugin.Registry.AddId("pmc"), pmc.pdfStep)
      .flatMap(_ => ctx.sourceFetchers.add(com.histmersion.plugin.Registry.AddId("pmc-text"), pmc.textStep))
      .left
      .map(e => PmcPlugin.Problem(e.value))

object PmcPlugin:
  type Problem = Problem.T
  object Problem extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
