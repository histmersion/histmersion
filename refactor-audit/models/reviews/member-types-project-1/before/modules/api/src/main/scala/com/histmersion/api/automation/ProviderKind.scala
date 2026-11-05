package com.histmersion.api.automation
enum ProviderKind(val wire: String):
  case Anthropic extends ProviderKind("anthropic")
  case OpenAI extends ProviderKind("openai")
  case Local extends ProviderKind("local")
  case ClaudeCode extends ProviderKind("claude-code")

  /** The person's own `codex` CLI (`codex exec`) on its ChatGPT plan login. */
  case Codex extends ProviderKind("codex")

  /** `opencode run`; the model is `<provider>/<model>` as opencode names it (`openai/gpt-…`). */
  case Opencode extends ProviderKind("opencode")

  /** A provider a PLUGIN registered under this name (docs/research-plugins.md §22): metered, served through the host's
    * tool loop. Parsed only while such a provider is registered (`known`).
    */
  case Plugin(name: String) extends ProviderKind(name)

  /** Runs a local command-line agent (one process per step attempt) rather than an HTTP API. */
  def isCli: Boolean = this == ClaudeCode || this == Codex || this == Opencode

object ProviderKind:
  /** The host's own providers, in their order. */
  val values: List[ProviderKind] = List(Anthropic, OpenAI, Local, ClaudeCode, Codex, Opencode)

  /** A provider name: one of the host's, else one of the `known` plugin providers' (lower case). */
  def parse(s: String, known: Set[String] = Set.empty): Option[ProviderKind] =
    val t = s.trim.toLowerCase
    values.find(_.wire == t).orElse(Option.when(known(t))(Plugin(t)))

  /** The names a reference may use, for an error message: the host's, then the plugins'. */
  def names(known: Set[String] = Set.empty): String =
    (values.map(_.wire) ++ known.toList.sorted.filterNot(k => values.exists(_.wire == k))).mkString(", ")
