package com.histmersion.domain.automation
enum ProviderKind(val wire: ProviderKind.Wire):
  case Anthropic extends ProviderKind(_root_.com.histmersion.domain.automation.ProviderKind.Wire("anthropic"))
  case OpenAI extends ProviderKind(_root_.com.histmersion.domain.automation.ProviderKind.Wire("openai"))
  case Local extends ProviderKind(_root_.com.histmersion.domain.automation.ProviderKind.Wire("local"))
  case ClaudeCode extends ProviderKind(_root_.com.histmersion.domain.automation.ProviderKind.Wire("claude-code"))

  /** The person's own `codex` CLI (`codex exec`) on its ChatGPT plan login. */
  case Codex extends ProviderKind(_root_.com.histmersion.domain.automation.ProviderKind.Wire("codex"))

  /** `opencode run`; the model is `<provider>/<model>` as opencode names it (`openai/gpt-…`). */
  case Opencode extends ProviderKind(_root_.com.histmersion.domain.automation.ProviderKind.Wire("opencode"))

  /** A provider a PLUGIN registered under this name (docs/research-plugins.md §22): metered, served through the host's
    * tool loop. Parsed only while such a provider is registered (`known`).
    */
  case Plugin(name: ProviderKind.Wire) extends ProviderKind(name)

  /** Runs a local command-line agent (one process per step attempt) rather than an HTTP API. */
  def isCli: Boolean = (this == ClaudeCode || this == Codex || this == Opencode)

object ProviderKind:
  /** The host's own providers, in their order. */
  type Wire = Wire.T
  object Wire extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  val values: List[ProviderKind] = List(Anthropic, OpenAI, Local, ClaudeCode, Codex, Opencode)

  /** A provider name: one of the host's, else one of the `known` plugin providers' (lower case). */
  def parse(s: com.histmersion.domain.automation.ProviderKind.Wire, known: Set[com.histmersion.domain.automation.ProviderKind.Wire] = Set.empty): Option[ProviderKind] =
    val t = (s).value.trim.toLowerCase
    values.find(_.wire == _root_.com.histmersion.domain.automation.ProviderKind.Wire(t)).orElse(Option.when(known(_root_.com.histmersion.domain.automation.ProviderKind.Wire(t)))(Plugin(_root_.com.histmersion.domain.automation.ProviderKind.Wire(t))))

  /** The names a reference may use, for an error message: the host's, then the plugins'. */
  def names(known: Set[com.histmersion.domain.automation.ProviderKind.Wire] = Set.empty): String =
    (values.map(_.wire.value) ++ known.toList.map(_.value).sorted.filterNot(k => values.exists(_.wire.value == k))).mkString(", ")
