package com.histmersion.fixtures.hellodeps

import com.histmersion.plugin.*
import com.histmersion.plugin.Plugin.Config.given
import hearth.kindlings.sconfigderivation.ConfigReader

/** A TEST FIXTURE, not a product: a plugin with a dependency of its own (`hello-lib`), published with a POM that lists
  * it, as a plugin author publishes one. It registers only when that library's class loads, so a registered
  * `hello-deps` proves the dependency was installed and linked with it.
  */
final case class HelloDepsConfig(greeting: String) derives ConfigReader
