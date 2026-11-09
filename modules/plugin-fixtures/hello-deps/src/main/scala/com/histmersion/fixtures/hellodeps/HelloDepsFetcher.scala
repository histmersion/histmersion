package com.histmersion.fixtures.hellodeps
import com.histmersion.util.problem.{Legacy, Problem}

import com.histmersion.plugin.*
import com.histmersion.plugin.Plugin.Config.given

final class HelloDepsFetcher(greeting: HelloDepsFetcher.Greeting) extends SourceFetcher:
  val access = Access.Open
  def fetch(work: WorkKey, ctx: Fetch.Context): Fetch =
    Fetch.Miss(Problem.Unclassified(Legacy.Text(s"hello-deps has no copy ($greeting)")))

object HelloDepsFetcher:
  type Greeting = _root_.com.histmersion.fixtures.hellolib.Shout.Shouted
  val Greeting: _root_.com.histmersion.fixtures.hellolib.Shout.Shouted.type =
    _root_.com.histmersion.fixtures.hellolib.Shout.Shouted
