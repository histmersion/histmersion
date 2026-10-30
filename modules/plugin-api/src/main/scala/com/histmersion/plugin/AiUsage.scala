package com.histmersion.plugin

/** Tokens of one call. `input` counts ALL prompt tokens, cached ones included; `cacheRead` and `cacheWrite` are parts
  * of it.
  */
final case class AiUsage(input: Long, output: Long, cacheRead: Long = 0, cacheWrite: Long = 0)
