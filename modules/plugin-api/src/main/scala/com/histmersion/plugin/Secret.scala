package com.histmersion.plugin

import hearth.kindlings.sconfigderivation.{ConfigDecodingError, ConfigReader}

/** Where a secret's value comes from (docs/research-plugins.md §3.7). The config holds only this
  * reference, never the value (except the discouraged `value:` form, which is masked wherever it is
  * shown). */
enum SecretRef:
  /** `value:<literal>` — the value itself, in the config file. Allowed, discouraged: the host warns. */
  case Value(literal: String)
  /** `env:<NAME>` — a variable of the server's environment. */
  case Env(name: String)
  /** `<vault>:<ref>` — resolved by the [[SecretVault]] registered under `vault`. */
  case Vault(vault: String, ref: String)

  /** The reference as it may be shown: a literal is masked, everything else is shown as written. */
  def shown: String = this match
    case Value(_)        => s"${SecretRef.ValueScheme}:${SecretRef.Mask}"
    case Env(n)          => s"${SecretRef.EnvScheme}:$n"
    case Vault(v, r)     => s"$v:$r"

object SecretRef:
  val ValueScheme = "value"
  val EnvScheme   = "env"
  /** What a literal value is shown as. */
  val Mask = "***"

  private val EnvName   = "[A-Za-z_][A-Za-z0-9_]*".r
  private val VaultName = "[a-z0-9][a-z0-9-]*".r

  val Forms = "value:<literal>, env:<NAME> or <vault>:<ref>"

  /** Pure: a reference from its text. Never echoes the text back in an error (it may be a value). */
  def parse(text: String): Either[String, SecretRef] =
    val t = Option(text).getOrElse("").trim
    t.indexOf(':') match
      case i if i <= 0 => Left(s"a secret is a reference ($Forms), not a bare value")
      case i =>
        val (scheme, rest) = (t.take(i), t.drop(i + 1))
        scheme match
          case ValueScheme =>
            if rest.isEmpty then Left("value: has no value") else Right(Value(rest))
          case EnvScheme =>
            rest.trim match
              case EnvName() => Right(Env(rest.trim))
              case _         => Left(s"env:${rest.trim} is not an environment variable name")
          case VaultName() =>
            if rest.trim.isEmpty then Left(s"$scheme: names no entry") else Right(Vault(scheme, rest.trim))
          case _ => Left(s"a secret is a reference ($Forms); '$scheme' is not a vault name ([a-z0-9][a-z0-9-]*)")

/** A credential in a config: a REFERENCE to the value, parsed when the config is read and resolved by
  * the host when the plugin is registered (through [[PluginContext.secret]]). `toString` shows the
  * reference — `Secret(env:PORTAL_TOKEN)`, `Secret(value:***)` — never the value. */
final class Secret private (val ref: SecretRef):
  override def toString: String = s"Secret(${ref.shown})"
  override def equals(o: Any): Boolean = o.isInstanceOf[Secret] && o.asInstanceOf[Secret].ref == ref
  override def hashCode: Int = ref.hashCode

object Secret:
  def apply(ref: SecretRef): Secret = new Secret(ref)

  def parse(text: String): Either[String, Secret] = SecretRef.parse(text).map(Secret(_))

  /** The HOCON reader: a string holding a reference. It parses the reference only — nothing is
    * resolved while the config is read. */
  given ConfigReader[Secret] = new ConfigReader[Secret]:
    def from(v: org.ekrich.config.ConfigValue) =
      ConfigReader.stringReader.from(v).flatMap(s =>
        parse(s).left.map(e => ConfigDecodingError.CannotConvert(Nil, e, None)))

/** A secret backend (KeePassXC, 1Password, Bitwarden, …), registered by a plugin under its [[name]]:
  * the host hands it the part of a `<name>:<ref>` reference after the colon. `Left` names what went
  * wrong (locked, no such entry, the tool is missing) and NEVER contains the value. */
trait SecretVault:
  def name: String
  def resolve(ref: String): Either[String, String]
