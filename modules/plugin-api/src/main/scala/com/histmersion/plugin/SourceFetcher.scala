package com.histmersion.plugin

/** Source fetching, the first extension point that moves part of the app into plugins. A plugin adds one or more
  * [[SourceFetcher]]s to [[PluginContext.sourceFetchers]]; the host runs them as ONE chain, in the configured order,
  * until one answers [[Fetch.Found]]. The host owns everything around the fetchers: the order, the legal gate on
  * [[Access]], the check that a "PDF" is one, the per-work [[FetchContext.memo]] and the log.
  */

/** One step of the acquisition chain. Called by the host, possibly from several threads at once (one per work being
  * fetched), so a fetcher keeps no per-work state of its own: that goes in [[FetchContext.memo]]. Failures are values
  * ([[Fetch.Miss]] / [[Fetch.Refused]]); an exception is caught by the host and read as a miss.
  */
trait SourceFetcher:
  def access: Access
  def fetch(work: WorkKey, ctx: FetchContext): Fetch
