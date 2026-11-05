package com.histmersion.fixtures.hellodeps

import com.histmersion.plugin.*
import com.histmersion.plugin.Plugin.Config.given

final class HelloDepsFetcher(greeting: HelloDepsFetcher.Greeting) extends SourceFetcher:
  val access = Access.Open
  def fetch(work: WorkKey, ctx: Fetch.Context): Fetch = Fetch.Miss(s"hello-deps has no copy ($greeting)")

object HelloDepsFetcher:
  type Greeting = Greeting.T
  object Greeting extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
