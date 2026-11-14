package com.histmersion
package plugins.fetchers
import com.histmersion.util.problem.{Problem, Problems}

/** `pmc`: Europe PMC / PMC Article Datasets — the PDF (`pmc`), else the JATS full text (`pmc-text`), by DOI. Two
  * fetchers, because their steps are not adjacent in the chain (the PMC PDF comes before arXiv, its JATS text after the
  * landing pages), sharing one upstream record per work through the fetch's memo.
  */
import com.histmersion.plugin.Plugin
import com.histmersion.plugin.Registry
final class PmcPlugin extends FetcherPlugin("pmc"):
  type Config = HostAcquire
  def register(c: HostAcquire, ctx: Plugin.Context): Either[Problems, Unit] =
    val pmc = Pmc(c.acquire, () => ctx.http)
    ctx.sourceFetchers
      .add(Registry.AddId("pmc"), pmc.pdfStep)
      .flatMap(_ => ctx.sourceFetchers.add(Registry.AddId("pmc-text"), pmc.textStep))
