package com.histmersion.fixtures.hellodeps

import com.histmersion.plugin.Problem
import com.histmersion.plugin.*
import com.histmersion.plugin.Plugin.Config.given

final class HelloDepsFetcher(greeting: HelloDepsFetcher.Greeting) extends SourceFetcher:
  val access = Access.Open
  def fetch(work: WorkKey, ctx: Fetch.Context): Fetch =
    Fetch.Miss(Problem(s"hello-deps has no copy ($greeting)"))

object HelloDepsFetcher:
  type Greeting = _root_.com.histmersion.fixtures.hellolib.Shout.ApplyResult
  val Greeting: _root_.com.histmersion.fixtures.hellolib.Shout.ApplyResult.type =
    _root_.com.histmersion.fixtures.hellolib.Shout.ApplyResult
