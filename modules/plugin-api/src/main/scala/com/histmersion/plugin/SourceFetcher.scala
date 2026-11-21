package com.histmersion
package plugin

/** One step of the acquisition chain. Called by the host, possibly from several threads at once (one per work being
  * fetched), so a fetcher keeps no per-work state of its own: that goes in [[Fetch.Context.memo]]. Failures are values
  * ([[Fetch.Miss]] / [[Fetch.Refused]]); an exception is caught by the host and read as a miss.
  */
trait SourceFetcher:
  def access: Access
  def fetch(work: WorkKey, ctx: Fetch.Context): Fetch
