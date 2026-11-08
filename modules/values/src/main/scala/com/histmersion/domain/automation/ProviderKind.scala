package com.histmersion
package domain.automation

import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure
enum ProviderKind(val wire: ProviderKind.Wire):
  case Anthropic extends ProviderKind(ProviderKind.Wire("anthropic"))
  case OpenAI extends ProviderKind(ProviderKind.Wire("openai"))
  case Local extends ProviderKind(ProviderKind.Wire("local"))
  case ClaudeCode extends ProviderKind(ProviderKind.Wire("claude-code"))

  /** The person's own `codex` CLI (`codex exec`) on its ChatGPT plan login. */
  case Codex extends ProviderKind(ProviderKind.Wire("codex"))

  /** `opencode run`; the model is `<provider>/<model>` as opencode names it (`openai/gpt-…`). */
  case Opencode extends ProviderKind(ProviderKind.Wire("opencode"))

  /** A provider a PLUGIN registered under this name (docs/research-plugins.md §22): metered, served through the host's
    * tool loop. Parsed only while such a provider is registered (`known`).
    */
  case Plugin(name: ProviderKind.Wire) extends ProviderKind(name)

  /** Runs a local command-line agent (one process per step attempt) rather than an HTTP API. */
  def isCli: Boolean = (this == ClaudeCode || this == Codex || this == Opencode)

object ProviderKind:
  /** The host's own providers, in their order. */
  type Wire = Wire.T
  object Wire extends RefinedType[String, Pure]

  val values: List[ProviderKind] = List(Anthropic, OpenAI, Local, ClaudeCode, Codex, Opencode)

  /** A provider name: one of the host's, else one of the `known` plugin providers' (lower case). */
  def parse(s: Wire, known: Set[Wire] = Set.empty): Option[ProviderKind] =
    val t = (s).value.trim.toLowerCase
    values.find(_.wire == Wire(t)).orElse(Option.when(known(Wire(t)))(Plugin(Wire(t))))

  /** The names a reference may use, for an error message: the host's, then the plugins'. */
  def names(known: Set[Wire] = Set.empty): String =
    (values.map(_.wire.value) ++ known.toList.map(_.value).sorted.filterNot(k => values.exists(_.wire.value == k))).mkString(", ")
