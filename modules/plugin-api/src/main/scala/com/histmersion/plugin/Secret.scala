package com.histmersion.plugin

import hearth.kindlings.sconfigderivation.{ConfigDecodingError, ConfigReader}

/** A credential in a config: a REFERENCE to the value, parsed when the config is read and resolved by the host when the
  * plugin is registered (through [[Plugin.Context.secret]]). `toString` shows the reference —
  * `Secret(env:PORTAL_TOKEN)`, `Secret(value:***)` — never the value.
  */
final class Secret private (val ref: com.histmersion.plugin.Secret.Ref):
  override def toString: String = s"Secret(${ref.shown})"
  override def equals(o: Any): Boolean = o.isInstanceOf[Secret] && o.asInstanceOf[Secret].ref == ref
  override def hashCode: Int = ref.hashCode

object Secret:
  type ParseResultPart1 = _root_.com.histmersion.plugin.Secret.Ref.ParseResultPart1
  val ParseResultPart1: _root_.com.histmersion.plugin.Secret.Ref.ParseResultPart1.type = _root_.com.histmersion.plugin.Secret.Ref.ParseResultPart1
  type ParseText = _root_.com.histmersion.plugin.Secret.Ref.ParseText
  val ParseText: _root_.com.histmersion.plugin.Secret.Ref.ParseText.type = _root_.com.histmersion.plugin.Secret.Ref.ParseText

  def apply(ref: com.histmersion.plugin.Secret.Ref): Secret = new Secret(ref)

  def parse(text: com.histmersion.plugin.Secret.ParseText): Either[com.histmersion.plugin.Secret.ParseResultPart1, Secret] = com.histmersion.plugin.Secret.Ref.parse(text).map(Secret(_))

  /** The HOCON reader: a string holding a reference. It parses the reference only — nothing is resolved while the
    * config is read.
    */
  given ConfigReader[Secret] = new ConfigReader[Secret]:
    def from(v: org.ekrich.config.ConfigValue) =
      ConfigReader.stringReader
        .from(v)
        .flatMap(s => parse(_root_.com.histmersion.plugin.Secret.ParseText(s)).left.map(e => ConfigDecodingError.CannotConvert(Nil, (e).value, None)))

  /** Where a secret's value comes from. The config holds only this reference, never the value (except the discouraged
    * `value:` form, which is masked wherever it is shown).
    */
  enum Ref:
    /** `value:<literal>` — the value itself, in the config file. Allowed, discouraged: the host warns. */
    case Value(literal: Ref.Value.Literal)

    /** `env:<NAME>` — a variable of the server's environment. */
    case Env(name: Ref.Env.Name)

    /** `<vault>:<ref>` — resolved by the [[Secret.Vault]] registered under `vault`. */
    case Vault(vault: Ref.Vault.Value, ref: Ref.Vault.Ref)

    /** The reference as it may be shown: a literal is masked, everything else is shown as written. */
    def shown: com.histmersion.plugin.Secret.Ref.ShownResult = this match
      case Value(_)    => _root_.com.histmersion.plugin.Secret.Ref.ShownResult(s"${Ref.ValueScheme}:${Ref.Mask}")
      case Env(n)      => _root_.com.histmersion.plugin.Secret.Ref.ShownResult(s"${Ref.EnvScheme}:$n")
      case _root_.com.histmersion.plugin.Secret.Ref.Vault(v, r) => _root_.com.histmersion.plugin.Secret.Ref.ShownResult(s"$v:$r")

  object Ref:
    object Value:
      type Literal = Literal.T
      object Literal extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    object Env:
      type Name = Name.T
      object Name extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    object Vault:
      type Value = Value.T
      object Value extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
      type Ref = Ref.T
      object Ref extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    type ShownResult = ShownResult.T
    object ShownResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ParseResultPart1 = ParseResultPart1.T
    object ParseResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ParseText = ParseText.T
    object ParseText extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    val ValueScheme = "value"
    val EnvScheme = "env"

    /** What a literal value is shown as. */
    val Mask = "***"

    private val EnvName = "[A-Za-z_][A-Za-z0-9_]*".r
    private val VaultName = "[a-z0-9][a-z0-9-]*".r

    val Forms = "value:<literal>, env:<NAME> or <vault>:<ref>"

    /** Pure: a reference from its text. Never echoes the text back in an error (it may be a value). */
    def parse(text: com.histmersion.plugin.Secret.Ref.ParseText): Either[com.histmersion.plugin.Secret.Ref.ParseResultPart1, Ref] =
      val t = Option((text).value).getOrElse("").trim
      t.indexOf(':') match
        case i if i <= 0 => Left(_root_.com.histmersion.plugin.Secret.Ref.ParseResultPart1(s"a secret is a reference ($Forms), not a bare value"))
        case i           =>
          val (scheme, rest) = (t.take(i), t.drop(i + 1))
          scheme match
            case ValueScheme =>
              if rest.isEmpty then Left(_root_.com.histmersion.plugin.Secret.Ref.ParseResultPart1("value: has no value")) else Right(Value(_root_.com.histmersion.plugin.Secret.Ref.Value.Literal(rest)))
            case EnvScheme =>
              rest.trim match
                case EnvName() => Right(Env(_root_.com.histmersion.plugin.Secret.Ref.Env.Name(rest.trim)))
                case _         => Left(_root_.com.histmersion.plugin.Secret.Ref.ParseResultPart1(s"env:${rest.trim} is not an environment variable name"))
            case VaultName() =>
              if rest.trim.isEmpty then Left(_root_.com.histmersion.plugin.Secret.Ref.ParseResultPart1(s"$scheme: names no entry")) else Right(Vault(_root_.com.histmersion.plugin.Secret.Ref.Vault.Value(scheme), _root_.com.histmersion.plugin.Secret.Ref.Vault.Ref(rest.trim)))
            case _ => Left(_root_.com.histmersion.plugin.Secret.Ref.ParseResultPart1(s"a secret is a reference ($Forms); '$scheme' is not a vault name ([a-z0-9][a-z0-9-]*)"))

  /** A secret backend (KeePassXC, 1Password, Bitwarden, …), registered by a plugin under its [[name]]: the host hands
    * it the part of a `<name>:<ref>` reference after the colon. `Left` names what went wrong (locked, no such entry,
    * the tool is missing) and NEVER contains the value.
    */
  trait Vault:
    def name: com.histmersion.plugin.Secret.Vault.NameResult
    def resolve(ref: com.histmersion.plugin.Secret.Vault.ResolveRef): Either[com.histmersion.plugin.Secret.Vault.ResolveResultPart1, com.histmersion.plugin.Secret.Vault.ResolveResultPart2]

    /** Several references at once: the host asks for every reference of this vault that the plugins of one config
      * generation (a server start, a `config reload`) hold, in ONE call, before it registers them. The answer has an
      * entry for each reference asked (one missing is reported as unanswered); each `Left`, as in [[resolve]], names
      * what went wrong and never contains a value. The default resolves them one by one; a vault that pays per call — a
      * database to unlock, a hardware key to touch — overrides it to pay once. The values are kept by the host for that
      * generation only.
      */
    def resolveAll(refs: List[com.histmersion.plugin.Secret.Vault.ResolveAllRefs]): Map[com.histmersion.plugin.Secret.Vault.ResolveAllResultPart1, Either[com.histmersion.plugin.Secret.Vault.ResolveAllResultPart2Part1, com.histmersion.plugin.Secret.Vault.ResolveAllResultPart2Part2]] =
      refs.distinct.map(r => r -> resolve(r)).toMap

  object Vault:
    type NameResult = NameResult.T
    object NameResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ResolveResultPart1 = ResolveResultPart1.T
    object ResolveResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ResolveResultPart2 = ResolveResultPart2.T
    object ResolveResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ResolveRef = ResolveRef.T
    object ResolveRef extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ResolveAllResultPart1 = _root_.com.histmersion.plugin.Secret.Vault.ResolveRef
    val ResolveAllResultPart1: _root_.com.histmersion.plugin.Secret.Vault.ResolveRef.type = _root_.com.histmersion.plugin.Secret.Vault.ResolveRef
    type ResolveAllResultPart2Part1 = _root_.com.histmersion.plugin.Secret.Vault.ResolveResultPart1
    val ResolveAllResultPart2Part1: _root_.com.histmersion.plugin.Secret.Vault.ResolveResultPart1.type = _root_.com.histmersion.plugin.Secret.Vault.ResolveResultPart1
    type ResolveAllResultPart2Part2 = _root_.com.histmersion.plugin.Secret.Vault.ResolveResultPart2
    val ResolveAllResultPart2Part2: _root_.com.histmersion.plugin.Secret.Vault.ResolveResultPart2.type = _root_.com.histmersion.plugin.Secret.Vault.ResolveResultPart2
    type ResolveAllRefs = _root_.com.histmersion.plugin.Secret.Vault.ResolveRef
    val ResolveAllRefs: _root_.com.histmersion.plugin.Secret.Vault.ResolveRef.type = _root_.com.histmersion.plugin.Secret.Vault.ResolveRef
