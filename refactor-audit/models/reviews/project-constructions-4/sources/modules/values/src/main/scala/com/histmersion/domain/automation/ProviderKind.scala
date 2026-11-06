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
  case Plugin(name: ProviderKind.Plugin.Name) extends ProviderKind(name)

  /** Runs a local command-line agent (one process per step attempt) rather than an HTTP API. */
  def isCli: com.histmersion.domain.automation.ProviderKind.IsCliResult = _root_.com.histmersion.domain.automation.ProviderKind.IsCliResult(this == ClaudeCode || this == Codex || this == Opencode)

object ProviderKind:
  /** The host's own providers, in their order. */
  object Plugin:
    type Name = Name.T
    object Name extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type Wire = Wire.T
  object Wire extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type IsCliResult = IsCliResult.T
  object IsCliResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ParseS = ParseS.T
  object ParseS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ParseKnown = ParseKnown.T
  object ParseKnown extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type NamesResult = NamesResult.T
  object NamesResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type NamesKnown = NamesKnown.T
  object NamesKnown extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]:
    given _root_.scala.math.Ordering[T] = _root_.scala.math.Ordering.by[T, _root_.java.lang.String](_.value)

  val values: List[ProviderKind] = List(Anthropic, OpenAI, Local, ClaudeCode, Codex, Opencode)

  /** A provider name: one of the host's, else one of the `known` plugin providers' (lower case). */
  def parse(s: com.histmersion.domain.automation.ProviderKind.ParseS, known: Set[com.histmersion.domain.automation.ProviderKind.ParseKnown] = Set.empty): Option[ProviderKind] =
    val t = (s).value.trim.toLowerCase
    values.find(_.wire == _root_.com.histmersion.domain.automation.ProviderKind.Wire(t)).orElse(Option.when(known(_root_.com.histmersion.domain.automation.ProviderKind.ParseKnown(t)))(Plugin(_root_.com.histmersion.domain.automation.ProviderKind.Plugin.Name(t))))

  /** The names a reference may use, for an error message: the host's, then the plugins'. */
  def names(known: Set[com.histmersion.domain.automation.ProviderKind.NamesKnown] = Set.empty): com.histmersion.domain.automation.ProviderKind.NamesResult =
    _root_.com.histmersion.domain.automation.ProviderKind.NamesResult((values.map(_.wire) ++ known.toList.sorted.filterNot(k => values.exists(_.wire == k))).mkString(", "))
