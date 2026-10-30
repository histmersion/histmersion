package com.histmersion.fixtures.hello

import com.histmersion.plugin.*
import com.histmersion.plugin.PluginConfig.given
import hearth.kindlings.sconfigderivation.ConfigReader

/** A TEST FIXTURE, not a product: the smallest third-party plugin, compiled against plugin-api alone and packaged as
  * its own jar, which the relink tests and `scripts/bundle/smoke.sh` drop into a plugins directory
  * (docs/research-plugins.md §7). It began as spike A's `hello` (§10.1).
  *
  * Its greeter is chosen BY NAME from the config, so native-image cannot see the class statically: the reflective
  * lookup works in a relinked binary only because this jar carries its own
  * `META-INF/native-image/com.histmersion/plugin-hello/reachability-metadata.json`.
  */
final case class HelloConfig(greeting: String, greeter: String, mirrors: List[String]) derives ConfigReader
