package com.histmersion.plugin

/** Tokens of one call. `input` counts ALL prompt tokens, cached ones included; `cacheRead` and `cacheWrite` are parts
  * of it. `reasoning` is the part of `output` the model spent thinking, where the provider says.
  */
final case class AiUsage(input: AiUsage.Input, output: AiUsage.Output, cacheRead: AiUsage.CacheRead = 0, cacheWrite: AiUsage.CacheWrite = 0, reasoning: AiUsage.Reasoning = 0):
  /** The constructor of a provider compiled before reasoning tokens were counted. */
  def this(input: Long, output: Long, cacheRead: Long, cacheWrite: Long) = this(input, output, cacheRead, cacheWrite, 0)

object AiUsage:
  /** The usage as a provider compiled before reasoning tokens were counted wrote it. */
  type ApplyInput = ApplyInput.T
  object ApplyInput extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ApplyOutput = ApplyOutput.T
  object ApplyOutput extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ApplyCacheRead = ApplyCacheRead.T
  object ApplyCacheRead extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ApplyCacheWrite = ApplyCacheWrite.T
  object ApplyCacheWrite extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type Input = Input.T
  object Input extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Output = Output.T
  object Output extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CacheRead = CacheRead.T
  object CacheRead extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CacheWrite = CacheWrite.T
  object CacheWrite extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Reasoning = Reasoning.T
  object Reasoning extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def apply(input: com.histmersion.plugin.AiUsage.ApplyInput, output: com.histmersion.plugin.AiUsage.ApplyOutput, cacheRead: com.histmersion.plugin.AiUsage.ApplyCacheRead, cacheWrite: com.histmersion.plugin.AiUsage.ApplyCacheWrite): AiUsage =
    new AiUsage(input, output, cacheRead, cacheWrite, 0)
