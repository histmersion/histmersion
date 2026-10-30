package com.histmersion.plugin

/** Where a secret's value comes from. The config holds only this reference, never the value (except the discouraged
  * `value:` form, which is masked wherever it is shown).
  */
enum SecretRef:
  /** `value:<literal>` — the value itself, in the config file. Allowed, discouraged: the host warns. */
  case Value(literal: String)

  /** `env:<NAME>` — a variable of the server's environment. */
  case Env(name: String)

  /** `<vault>:<ref>` — resolved by the [[SecretVault]] registered under `vault`. */
  case Vault(vault: String, ref: String)

  /** The reference as it may be shown: a literal is masked, everything else is shown as written. */
  def shown: String = this match
    case Value(_)    => s"${SecretRef.ValueScheme}:${SecretRef.Mask}"
    case Env(n)      => s"${SecretRef.EnvScheme}:$n"
    case Vault(v, r) => s"$v:$r"

object SecretRef:
  val ValueScheme = "value"
  val EnvScheme = "env"

  /** What a literal value is shown as. */
  val Mask = "***"

  private val EnvName = "[A-Za-z_][A-Za-z0-9_]*".r
  private val VaultName = "[a-z0-9][a-z0-9-]*".r

  val Forms = "value:<literal>, env:<NAME> or <vault>:<ref>"

  /** Pure: a reference from its text. Never echoes the text back in an error (it may be a value). */
  def parse(text: String): Either[String, SecretRef] =
    val t = Option(text).getOrElse("").trim
    t.indexOf(':') match
      case i if i <= 0 => Left(s"a secret is a reference ($Forms), not a bare value")
      case i           =>
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
