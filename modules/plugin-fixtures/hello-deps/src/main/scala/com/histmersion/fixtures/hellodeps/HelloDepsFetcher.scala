package com.histmersion
package fixtures.hellodeps
import com.histmersion.util.problem.{External, Problem}

import com.histmersion.plugin.*
import com.histmersion.plugin.Plugin.Config.given

final class HelloDepsFetcher(greeting: HelloDepsFetcher.Greeting) extends SourceFetcher:
  val access = Access.Open
  def fetch(work: WorkKey, ctx: Fetch.Context): Fetch =
    Fetch.Miss(
      Problem.Plugin.Said(
        Problem.Plugin.Id("hello-deps"),
        Problem.Plugin.Claim.Failed,
        External.PluginSaid.of(s"no copy ($greeting)")
      )
    )

object HelloDepsFetcher:
  export fixtures.hellolib.Shout.Shouted as Greeting
