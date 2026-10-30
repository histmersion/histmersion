package com.histmersion.fixtures.hello

import com.histmersion.plugin.*
import com.histmersion.plugin.PluginConfig.given

final class HelloFetcher(mirrors: Int) extends SourceFetcher:
  val access = Access.Open
  def fetch(work: WorkKey, ctx: FetchContext): Fetch =
    Fetch.Miss(s"hello has no copy (asked $mirrors mirror(s))")
