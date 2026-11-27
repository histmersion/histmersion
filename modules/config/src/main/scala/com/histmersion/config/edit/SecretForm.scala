package com.histmersion
package config.edit

import com.histmersion.util.problem.Problem
import com.histmersion.lint.{movesTo, FeaturePlace}
import com.histmersion.lint.{tolerated, LintRule}

/** What a credential's setting holds, as far as it may be told: whether one is set and how it is referred to — never
  * the value.
  */
@movesTo(FeaturePlace.UtilSettings)
enum SecretForm(val wire: String, val describe: String):
  case Unset extends SecretForm("unset", "not set")

  /** `env:<NAME>`: the server's environment variable. */
  case Environment extends SecretForm("env", "an environment variable")

  /** `<vault>:<entry>`: a password manager's entry. */
  case Vault extends SecretForm("vault", "a password manager's entry")

  /** `value:<literal>`: the value itself, in the file. */
  case Literal extends SecretForm("value", "the value itself, written in the file")

  /** A plain value where a reference belongs (a setting that holds the credential itself). */
  case Plain extends SecretForm("plain", "set")

object SecretForm:
  val ValueScheme = "value"
  val EnvScheme = "env"
  private val EnvName = "[A-Za-z_][A-Za-z0-9_]*".r
  private val VaultName = "[a-z0-9][a-z0-9-]*".r

  /** How `text` refers to a credential (`rule`: what its setting is). */
  def of(text: String, rule: SecretRule): SecretForm =
    val t = Option(text).getOrElse("").trim
    if t.isEmpty then Unset
    else if rule == SecretRule.FromEnvironment then Plain
    else reference(t).fold(_ => Plain, identity)

  /** The reference's form, or why `text` is none. Never repeats `text` (it may be the value). */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def reference(text: String): Either[Problem, SecretForm] =
    val t = text.trim
    def not(form: Problem.Invalid.Form) = Left(Problem.Invalid.Reference.here(form))
    t.indexOf(':') match
      case i if i <= 0 => not(Problem.Invalid.Form.Bare)
      case i           =>
        val (scheme, rest) = (t.take(i), t.drop(i + 1).trim)
        scheme match
          case ValueScheme => if rest.isEmpty then not(Problem.Invalid.Form.EmptyValue) else Right(Literal)
          case EnvScheme   =>
            if EnvName.matches(rest) then Right(Environment)
            else not(Problem.Invalid.Form.EnvName(Problem.Raw(s"$rest")))
          case VaultName() =>
            if rest.isEmpty then not(Problem.Invalid.Form.NoEntry(Problem.Raw(s"$scheme"))) else Right(Vault)
          case _ => not(Problem.Invalid.Form.VaultName(Problem.Raw(s"$scheme")))

  /** The reference as it may be shown: the scheme and name of an environment variable or a vault entry, `value:***` for
    * a literal, nothing for a plain value.
    */
  def shown(text: String, rule: SecretRule): Option[String] =
    of(text, rule) match
      case Environment | Vault => Some(text.trim)
      case Literal             => Some(s"$ValueScheme:***")
      case _                   => None
