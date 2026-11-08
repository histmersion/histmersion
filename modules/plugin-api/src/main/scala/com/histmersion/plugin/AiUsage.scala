package com.histmersion
package plugin

import io.github.iltotore.iron.constraint.any.Pure

/** Tokens of one call. `input` counts ALL prompt tokens, cached ones included; `cacheRead` and `cacheWrite` are parts
  * of it. `reasoning` is the part of `output` the model spent thinking, where the provider says.
  */
final case class AiUsage(
    input: AiUsage.Input,
    output: AiUsage.Output,
    cacheRead: AiUsage.CacheRead = AiUsage.CacheRead((0).toLong),
    cacheWrite: AiUsage.CacheWrite = AiUsage.CacheWrite((0).toLong),
    reasoning: AiUsage.Reasoning = AiUsage.Reasoning((0).toLong)
):
  /** The constructor of a provider compiled before reasoning tokens were counted. */
  def this(input: Long, output: Long, cacheRead: Long, cacheWrite: Long) = this(
    AiUsage.Input(input),
    AiUsage.Output(output),
    AiUsage.CacheRead(cacheRead),
    AiUsage.CacheWrite(cacheWrite),
    AiUsage.Reasoning((0).toLong)
  )

object AiUsage:
  /** The usage as a provider compiled before reasoning tokens were counted wrote it. */
  type ApplyInput = Input
  val ApplyInput: Input.type = Input
  type ApplyOutput = Output
  val ApplyOutput: Output.type = Output
  type ApplyCacheRead = CacheRead
  val ApplyCacheRead: CacheRead.type = CacheRead
  type ApplyCacheWrite = CacheWrite
  val ApplyCacheWrite: CacheWrite.type = CacheWrite

  type Input = Input.T
  object Input extends Newtype[Long, Pure]
  type Output = Output.T
  object Output extends Newtype[Long, Pure]
  type CacheRead = CacheRead.T
  object CacheRead extends Newtype[Long, Pure]
  type CacheWrite = CacheWrite.T
  object CacheWrite extends Newtype[Long, Pure]
  type Reasoning = Reasoning.T
  object Reasoning extends Newtype[Long, Pure]

  def apply(input: ApplyInput, output: ApplyOutput, cacheRead: ApplyCacheRead, cacheWrite: ApplyCacheWrite): AiUsage =
    new AiUsage(input, output, cacheRead, cacheWrite, Reasoning((0).toLong))
