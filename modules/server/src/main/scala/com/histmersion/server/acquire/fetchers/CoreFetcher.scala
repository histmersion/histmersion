package com.histmersion.server.acquire.fetchers

import com.histmersion.server.acquire.rules
import com.histmersion.plugin.{Fetch, FetchContext, SourceFetcher, WorkKey}

/** The `core` fetcher, with the key its plugin resolved — which the new-source search's `core` index uses too, so that
  * ONE setting (`plugins.oa-core.api-key`, a secret reference) enables both.
  */
final class CoreFetcher(val key: Option[String], lookup: (FetchContext, String) => Fetch) extends SourceFetcher:
  val access: com.histmersion.plugin.Access = com.histmersion.plugin.Access.Open
  def fetch(work: WorkKey, ctx: FetchContext): Fetch =
    work.doi.map(_.value).fold[Fetch](Fetch.Miss("core needs a DOI"))(lookup(ctx, _))

object CoreFetcher:
  /** The key the `oa-core` plugin of a generation registered its fetcher with (`None`: no `core` fetcher, or one
    * without a key).
    */
  def key(fetchers: scala.collection.immutable.ListMap[String, SourceFetcher]): Option[String] =
    fetchers.get("core").collect { case c: CoreFetcher => c.key }.flatten
