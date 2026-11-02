package com.histmersion.fixtures.hello

import com.histmersion.plugin.*
import com.histmersion.plugin.Plugin.Config.given

final class HelloFetcher(mirrors: Int) extends SourceFetcher:
  val access = Access.Open
  def fetch(work: WorkKey, ctx: Fetch.Context): Fetch =
    Fetch.Miss(s"hello has no copy (asked $mirrors mirror(s))")
