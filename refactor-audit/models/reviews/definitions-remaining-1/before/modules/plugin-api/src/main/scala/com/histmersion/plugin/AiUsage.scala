package com.histmersion.plugin

/** Tokens of one call. `input` counts ALL prompt tokens, cached ones included; `cacheRead` and `cacheWrite` are parts
  * of it. `reasoning` is the part of `output` the model spent thinking, where the provider says.
  */
final case class AiUsage(input: Long, output: Long, cacheRead: Long = 0, cacheWrite: Long = 0, reasoning: Long = 0):
  /** The constructor of a provider compiled before reasoning tokens were counted. */
  def this(input: Long, output: Long, cacheRead: Long, cacheWrite: Long) = this(input, output, cacheRead, cacheWrite, 0)

object AiUsage:
  /** The usage as a provider compiled before reasoning tokens were counted wrote it. */
  def apply(input: Long, output: Long, cacheRead: Long, cacheWrite: Long): AiUsage =
    new AiUsage(input, output, cacheRead, cacheWrite, 0)
