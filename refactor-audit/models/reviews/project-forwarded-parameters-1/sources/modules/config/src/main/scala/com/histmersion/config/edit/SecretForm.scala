package com.histmersion.config.edit

/** What a credential's setting holds, as far as it may be told: whether one is set and how it is referred to — never
  * the value.
  */
enum SecretForm(val wire: SecretForm.Wire, val describe: SecretForm.Describe):
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
  type Wire = Wire.T
  object Wire extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Describe = Describe.T
  object Describe extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type OfText = OfText.T
  object OfText extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ReferenceResultPart1 = ReferenceResultPart1.T
  object ReferenceResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ReferenceText = ReferenceText.T
  object ReferenceText extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ShownResult = ShownResult.T
  object ShownResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ShownText = _root_.com.histmersion.config.edit.SecretForm.OfText
  val ShownText: _root_.com.histmersion.config.edit.SecretForm.OfText.type = _root_.com.histmersion.config.edit.SecretForm.OfText

  val ValueScheme = "value"
  val EnvScheme = "env"
  private val EnvName = "[A-Za-z_][A-Za-z0-9_]*".r
  private val VaultName = "[a-z0-9][a-z0-9-]*".r

  /** The forms a reference takes, for a message. */
  val Forms = "env:<NAME>, <vault>:<entry> (keepassxc:…, 1password:…, bitwarden:…) or value:<the value itself>"

  /** How `text` refers to a credential (`rule`: what its setting is). */
  def of(text: com.histmersion.config.edit.SecretForm.OfText, rule: SecretRule): SecretForm =
    val t = Option(text).getOrElse("").trim
    if t.isEmpty then Unset
    else if rule == SecretRule.FromEnvironment then Plain
    else reference(t).fold(_ => Plain, identity)

  /** The reference's form, or why `text` is none. Never repeats `text` (it may be the value). */
  def reference(text: com.histmersion.config.edit.SecretForm.ReferenceText): Either[com.histmersion.config.edit.SecretForm.ReferenceResultPart1, SecretForm] =
    val t = text.trim
    t.indexOf(':') match
      case i if i <= 0 => Left(s"a credential is written as a reference — $Forms — never as the bare value")
      case i           =>
        val (scheme, rest) = (t.take(i), t.drop(i + 1).trim)
        scheme match
          case ValueScheme => if rest.isEmpty then Left("value: holds nothing") else Right(Literal)
          case EnvScheme   =>
            if EnvName.matches(rest) then Right(Environment)
            else Left(s"env:$rest is not an environment variable's name")
          case VaultName() => if rest.isEmpty then Left(s"$scheme: names no entry") else Right(Vault)
          case _           => Left(s"a credential is written as a reference — $Forms")

  /** The reference as it may be shown: the scheme and name of an environment variable or a vault entry, `value:***` for
    * a literal, nothing for a plain value.
    */
  def shown(text: com.histmersion.config.edit.SecretForm.ShownText, rule: SecretRule): Option[com.histmersion.config.edit.SecretForm.ShownResult] =
    of(text, rule) match
      case Environment | Vault => Some(text.trim)
      case Literal             => Some(s"$ValueScheme:***")
      case _                   => None
