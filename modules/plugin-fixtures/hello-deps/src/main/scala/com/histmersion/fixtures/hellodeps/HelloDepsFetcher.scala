package com.histmersion.fixtures.hellodeps

import com.histmersion.plugin.*
import com.histmersion.plugin.Plugin.Config.given

final class HelloDepsFetcher(greeting: String) extends SourceFetcher:
  val access = Access.Open
  def fetch(work: WorkKey, ctx: Fetch.Context): Fetch = Fetch.Miss(s"hello-deps has no copy ($greeting)")
