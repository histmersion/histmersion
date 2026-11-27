package com.histmersion
package fixtures.hello
import com.histmersion.util.problem.{External, Problem}

import com.histmersion.Newtype
import com.histmersion.plugin.*
import com.histmersion.plugin.Plugin.Config.given
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{LintRule, tolerated}

@tolerated(LintRule.G44, "a test plugin standing in for a third-party author: it builds and reads SPI values directly")
final class HelloFetcher(mirrors: HelloFetcher.Mirrors) extends SourceFetcher:
  val access = Access.Open
  def fetch(work: WorkKey, ctx: Fetch.Context): Fetch =
    Fetch.Miss(
      Fetch.Absence.NotHeld,
      Problem.Plugin.Said(
        Problem.Plugin.Id("hello"),
        Problem.Plugin.Claim.Failed,
        External.PluginSaid.of(s"no copy (asked $mirrors mirror(s))")
      )
    )

object HelloFetcher:
  type Mirrors = Mirrors.T
  object Mirrors extends Newtype[Int, Pure]
