package com.histmersion.fixtures.hello

import com.histmersion.plugin.*
import com.histmersion.plugin.Plugin.Config.given

final class HelloFetcher(mirrors: HelloFetcher.Mirrors) extends SourceFetcher:
  val access = Access.Open
  def fetch(work: WorkKey, ctx: Fetch.Context): Fetch =
    Fetch.Miss(_root_.com.histmersion.plugin.Fetch.Miss.Why(s"hello has no copy (asked $mirrors mirror(s))"))

object HelloFetcher:
  type Mirrors = Mirrors.T
  object Mirrors extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
