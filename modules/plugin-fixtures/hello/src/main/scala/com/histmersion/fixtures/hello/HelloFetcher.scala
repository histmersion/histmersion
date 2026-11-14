package com.histmersion.fixtures.hello
import com.histmersion.util.problem.{External, Problem}

import com.histmersion.Newtype
import com.histmersion.plugin.*
import com.histmersion.plugin.Plugin.Config.given

final class HelloFetcher(mirrors: HelloFetcher.Mirrors) extends SourceFetcher:
  val access = Access.Open
  def fetch(work: WorkKey, ctx: Fetch.Context): Fetch =
    Fetch.Miss(
      Problem.Plugin.Said(
        Problem.Plugin.Id("hello"),
        Problem.Plugin.Claim.Failed,
        External.PluginSaid.of(s"no copy (asked $mirrors mirror(s))")
      )
    )

object HelloFetcher:
  type Mirrors = Mirrors.T
  object Mirrors extends Newtype[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
