package com.histmersion
package config.edit

import com.histmersion.util.problem.{Problem, Problems}
import io.github.iltotore.iron.constraint.any.Pure

/** Why a change to the config file was refused, in words a person acts on. The file is never written when one of these
  * is the answer.
  */
enum SettingError derives CanEqual:
  case BadKey(why: Problem)
  case UnknownKey(key: SettingKey, closest: List[SettingKey])
  case NotAValue(key: SettingKey)
  case BadValue(key: SettingKey, why: Problem.Config.Fault)
  case WrongKind(key: SettingKey, want: SettingKind, got: SettingKind)

  /** The credential itself would go into the file: its environment variable is the place. */
  case CredentialInFile(key: SettingKey, envVar: Option[SettingError.CredentialInFile.EnvVar])
  case NotAReference(key: SettingKey, why: Problem)

  /** `value:` was asked for without the person saying they mean it. */
  case LiteralNeedsConsent(key: SettingKey)

  /** The file writes this setting (or a block above it) with a `${…}` substitution or `+=`: Histmersion keeps it
    * exactly as it is.
    */
  case Substitution(key: SettingKey)

  /** The setting's value comes from a file the config file includes. */
  case FromInclude(key: SettingKey, file: SettingError.FromInclude.File)

  /** The file as it stands does not read: it is not rewritten until it is fixed by hand. */
  case Unreadable(why: Problem)

  /** The new file would not load. */
  case Invalid(why: Problems)

  /** The file changed after it was read (an editor, another window): `sent` is the version it was read at, `stored` the
    * one it has.
    */
  case Changed(sent: FileVersion, stored: FileVersion)
  case NoFile(why: Problem)
  case Io(why: Problem)

  def message: Problems = this match
    case BadKey(why)            => why
    case UnknownKey(k, closest) =>
      Problem.Invalid.Unknown.here(
        Problem.TypeName.of(SettingKey),
        Problem.Raw(s"$k"),
        closest.toVector.map(c => Problem.Raw(s"$c"))
      )
    case NotAValue(k)             => setting(k, Problem.Config.Fault.Block)
    case BadValue(k, why)         => setting(k, why)
    case WrongKind(k, want, got)  => setting(k, Problem.Config.Fault.WrongKind(want.valueKind, got.valueKind))
    case CredentialInFile(k, env) =>
      setting(k, Problem.Config.Fault.Credential(env.map(v => Problem.SettingKey(s"$v"))))
    case NotAReference(k, why)  => why.within(Problem.Field(s"$k"))
    case LiteralNeedsConsent(k) => setting(k, Problem.Config.Fault.Literal)
    case Substitution(k)        => setting(k, Problem.Config.Fault.Substituted)
    case FromInclude(k, file)   => setting(k, Problem.Config.Fault.Included(Problem.Io.Path(s"$file")))
    case Unreadable(why)        => why
    case Invalid(why)           => why
    case Changed(sent, stored)  =>
      Problem.Conflict.Stale(
        Problem.TypeName.of(SettingsFile.Path),
        Problem.Raw("the settings file"),
        Problem.Conflict.Stamp(s"$sent"),
        Problem.Conflict.Stamp(s"$stored")
      )
    case NoFile(why) => why
    case Io(why)     => why

  private def setting(key: SettingKey, fault: Problem.Config.Fault): Problem =
    Problem.Config.Setting(Problem.SettingKey(s"$key"), fault)

object SettingError:
  object CredentialInFile:
    export SettingField.EnvVar

  object FromInclude:
    export SettingsFile.Path as File
