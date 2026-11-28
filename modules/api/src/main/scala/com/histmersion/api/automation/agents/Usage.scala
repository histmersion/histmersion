package com.histmersion
package api.automation.agents

import hearth.kindlings.optics.syntax.*
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.lint.tolerated
import com.histmersion.lint.LintRule
import io.github.iltotore.iron.constraint.any.Pure

@tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
final case class Usage(
    input: Usage.Input = Usage.Input((0).toLong),
    output: Usage.Output = Usage.Output((0).toLong),
    cacheRead: Usage.CacheRead = Usage.CacheRead((0).toLong),
    cacheWrite: Usage.CacheWrite = Usage.CacheWrite((0).toLong)
) derives JsonCodec,
      KindlingsSchema,
      api.json.Schema:
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def +(o: Usage): Usage =
    Usage(
      Usage.Input((input).value + (o.input).value),
      Usage.Output((output).value + (o.output).value),
      Usage.CacheRead((cacheRead).value + (o.cacheRead).value),
      Usage.CacheWrite((cacheWrite).value + (o.cacheWrite).value)
    )
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def total: Usage.TotalTokens = Usage.TotalTokens((input).value + (output).value)

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def budgeted(cacheReadWeight: Usage.CacheReadWeight): Usage.BudgetedTokens =
    Usage.BudgetedTokens(
      math.max(0L, (input).value - (cacheRead).value) + (output).value + math.round(
        (cacheRead).value.toDouble * math.max(0.0, cacheReadWeight.value)
      )
    )

object Usage:
  type BudgetedTokens = BudgetedTokens.T
  object BudgetedTokens extends Newtype[Long, Pure]
  type CacheReadWeight = CacheReadWeight.T
  @tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
  object CacheReadWeight extends Newtype[Double, Pure]
  type TotalTokens = TotalTokens.T
  object TotalTokens extends Newtype[Long, Pure]

  type Input = Input.T
  object Input extends Newtype[Long, Pure]
  type Output = Output.T
  object Output extends Newtype[Long, Pure]
  type CacheRead = CacheRead.T
  object CacheRead extends Newtype[Long, Pure]
  type CacheWrite = CacheWrite.T
  object CacheWrite extends Newtype[Long, Pure]

  val Zero: Usage = Usage()
