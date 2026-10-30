package com.histmersion.fixtures.hellodeps

import com.histmersion.plugin.*
import com.histmersion.plugin.PluginConfig.given

final class HelloDepsFetcher(greeting: String) extends SourceFetcher:
  val access = Access.Open
  def fetch(work: WorkKey, ctx: FetchContext): Fetch = Fetch.Miss(s"hello-deps has no copy ($greeting)")
