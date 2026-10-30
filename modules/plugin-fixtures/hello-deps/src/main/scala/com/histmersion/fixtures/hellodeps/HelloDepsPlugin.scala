package com.histmersion.fixtures.hellodeps

import com.histmersion.fixtures.hellolib.Shout
import com.histmersion.plugin.*
import com.histmersion.plugin.PluginConfig.given
import hearth.kindlings.sconfigderivation.ConfigReader

/** A TEST FIXTURE, not a product: a plugin with a dependency of its own (`hello-lib`), published with a POM that lists
  * it, as a plugin author publishes one. It registers only when that library's class loads, so a registered
  * `hello-deps` proves the dependency was installed and linked with it.
  */
final case class HelloDepsConfig(greeting: String) derives ConfigReader

final class HelloDepsPlugin extends Plugin:
  type Config = HelloDepsConfig
  val info = PluginInfo("hello-deps", "0.1.0", Spi.Version)

  def register(config: HelloDepsConfig, ctx: PluginContext): Either[String, Unit] =
    ctx.log.info(s"hello-deps: ${Shout(config.greeting)}")
    ctx.sourceFetchers.add("hello-deps", HelloDepsFetcher(Shout(config.greeting)))

final class HelloDepsFetcher(greeting: String) extends SourceFetcher:
  val access = Access.Open
  def fetch(work: WorkKey, ctx: FetchContext): Fetch = Fetch.Miss(s"hello-deps has no copy ($greeting)")
