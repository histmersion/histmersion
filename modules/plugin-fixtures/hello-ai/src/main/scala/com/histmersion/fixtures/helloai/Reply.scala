package com.histmersion.fixtures.helloai

import com.histmersion.plugin.*
import com.histmersion.plugin.PluginConfig.given
import hearth.kindlings.sconfigderivation.ConfigReader

/** A TEST FIXTURE, not a product: the smallest third-party AI provider, compiled against plugin-api alone and packaged
  * as its own jar (docs/research-plugins.md §22–§23). It calls no model and opens no socket: its answers are SCRIPTED
  * in its config, so the suites can run a real pipeline through the host's tool loop with it.
  *
  * Each turn answers with the first reply whose `when` occurs in the step's answer schema. A reply with a `tool` calls
  * that tool once first (when the step offers it), then answers. The model `limited` reports its plan's usage limit, so
  * a chain can be seen to move on.
  */
final case class Reply(when: String, text: String, tool: Option[String] = None, input: Option[String] = None)
    derives ConfigReader
