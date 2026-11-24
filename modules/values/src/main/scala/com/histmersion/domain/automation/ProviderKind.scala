package com.histmersion
package domain.automation

import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{LintRule, tolerated}
enum ProviderKind(val wire: ProviderKind.Wire) derives CanEqual:
  case Anthropic extends ProviderKind(ProviderKind.Wire("anthropic"))
  case OpenAI extends ProviderKind(ProviderKind.Wire("openai"))
  case Local extends ProviderKind(ProviderKind.Wire("local"))
  case ClaudeCode extends ProviderKind(ProviderKind.Wire("claude-code"))

  case Codex extends ProviderKind(ProviderKind.Wire("codex"))

  case Opencode extends ProviderKind(ProviderKind.Wire("opencode"))

  case Plugin(name: ProviderKind.Wire) extends ProviderKind(name)

  def isCli: Boolean = (this == ClaudeCode || this == Codex || this == Opencode)

object ProviderKind:
  type Wire = Wire.T
  object Wire extends Newtype[String, Pure]

  val values: List[ProviderKind] = List(Anthropic, OpenAI, Local, ClaudeCode, Codex, Opencode)

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def parse(s: Wire, known: Set[Wire] = Set.empty): Option[ProviderKind] =
    val t = (s).value.trim.toLowerCase
    values.find(_.wire == Wire(t)).orElse(Option.when(known(Wire(t)))(Plugin(Wire(t))))

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def names(known: Set[Wire] = Set.empty): String =
    (values.map(_.wire.value) ++ known.toList.map(_.value).sorted.filterNot(k => values.exists(_.wire.value == k)))
      .mkString(", ")
