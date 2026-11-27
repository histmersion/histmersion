package com.histmersion
package plugin

import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{tolerated, LintRule}

/** Tokens of one call. `input` counts ALL prompt tokens, cached ones included; `cacheRead` and `cacheWrite` are parts
  * of it. `reasoning` is the part of `output` the model spent thinking, where the provider says.
  */
@tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
final case class AiUsage(
    input: AiUsage.Input,
    output: AiUsage.Output,
    cacheRead: AiUsage.CacheRead = AiUsage.CacheRead((0).toLong),
    cacheWrite: AiUsage.CacheWrite = AiUsage.CacheWrite((0).toLong),
    reasoning: AiUsage.Reasoning = AiUsage.Reasoning((0).toLong)
):
  def this(input: Long, output: Long, cacheRead: Long, cacheWrite: Long) = this(
    AiUsage.Input(input),
    AiUsage.Output(output),
    AiUsage.CacheRead(cacheRead),
    AiUsage.CacheWrite(cacheWrite),
    AiUsage.Reasoning((0).toLong)
  )

object AiUsage:
  @tolerated(LintRule.G46, "pending G46 (re-export, don't alias)")
  type ApplyInput = Input
  val ApplyInput: Input.type = Input
  @tolerated(LintRule.G46, "pending G46 (re-export, don't alias)")
  type ApplyOutput = Output
  val ApplyOutput: Output.type = Output
  @tolerated(LintRule.G46, "pending G46 (re-export, don't alias)")
  type ApplyCacheRead = CacheRead
  val ApplyCacheRead: CacheRead.type = CacheRead
  @tolerated(LintRule.G46, "pending G46 (re-export, don't alias)")
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

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def apply(input: ApplyInput, output: ApplyOutput, cacheRead: ApplyCacheRead, cacheWrite: ApplyCacheWrite): AiUsage =
    new AiUsage(input, output, cacheRead, cacheWrite, Reasoning((0).toLong))
