package com.histmersion
package api.automation.agents

import hearth.kindlings.optics.syntax.*
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.lint.tolerated
import com.histmersion.lint.LintRule
import com.histmersion.api.automation.agents.Usage
import com.histmersion.api.automation.Agents.Prices

final case class Price(
    input: BigDecimal,
    output: BigDecimal,
    cacheRead: Option[BigDecimal] = None,
    cacheWrite: Option[BigDecimal] = None
):
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def cost(u: Usage): BigDecimal =
    val plain = math.max(u.input.value - u.cacheRead.value - u.cacheWrite.value, 0L)
    (BigDecimal(plain) * input + BigDecimal(u.cacheRead.value) * cacheRead.getOrElse(input) +
      BigDecimal(u.cacheWrite.value) * cacheWrite.getOrElse(input) + BigDecimal(
        u.output.value
      ) * output) / Prices.Million

  def text: String =
    val n = (d: BigDecimal) => d.bigDecimal.stripTrailingZeros.toPlainString
    ((List(input, output) ++ cacheRead.zip(cacheWrite).toList.flatMap((r, w) => List(r, w))).map(n).mkString("/"))
