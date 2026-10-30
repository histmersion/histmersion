package com.histmersion.plugin

/** What a provider can do. A provider without `tools` is offered no tools (a step that needs them should not chain its
  * models); without `structuredOutput` the schema still travels in the request.
  */
final case class AiCapabilities(tools: Boolean = true, structuredOutput: Boolean = false)
