package com.histmersion.config.edit

/** Why a change to the config file was refused, in words a person acts on. The file is never written when one of these
  * is the answer.
  */
enum SettingError:
  case BadKey(why: SettingError.BadKey.Why)
  case UnknownKey(key: SettingKey, closest: List[SettingKey])
  case NotAValue(key: SettingKey)
  case BadValue(key: SettingKey, why: SettingError.BadValue.Why)
  case WrongKind(key: SettingKey, want: SettingKind, got: SettingKind)

  /** The credential itself would go into the file: its environment variable is the place. */
  case CredentialInFile(key: SettingKey, envVar: Option[SettingError.CredentialInFile.EnvVar])
  case NotAReference(key: SettingKey, why: SettingError.NotAReference.Why)

  /** `value:` was asked for without the person saying they mean it. */
  case LiteralNeedsConsent(key: SettingKey)

  /** The file writes this setting (or a block above it) with a `${…}` substitution or `+=`: Histmersion keeps it
    * exactly as it is.
    */
  case Substitution(key: SettingKey)

  /** The setting's value comes from a file the config file includes. */
  case FromInclude(key: SettingKey, file: SettingError.FromInclude.File)

  /** The file as it stands does not read: it is not rewritten until it is fixed by hand. */
  case Unreadable(why: SettingError.Unreadable.Why)

  /** The new file would not load. */
  case Invalid(why: SettingError.Invalid.Why)

  /** The file changed after it was read (an editor, another window). */
  case Changed
  case NoFile(why: SettingError.NoFile.Why)
  case Io(why: SettingError.Io.Why)

  def message: com.histmersion.config.edit.SettingError.MessageResult = this match
    case BadKey(why)            => why
    case UnknownKey(k, closest) =>
      s"There is no setting called ${k.relative}." +
        (if closest.isEmpty then "" else s" Did you mean ${closest.map(_.relative).mkString(" or ")}?")
    case NotAValue(k) => s"${k.relative} is a group of settings, not one setting: set one of the settings inside it."
    case BadValue(k, why)         => s"${k.relative}: $why."
    case WrongKind(k, want, got)  => s"${k.relative} takes ${want.describe}, not ${got.describe}."
    case CredentialInFile(k, env) =>
      s"${k.relative} holds a key or password itself, so it is not written into the settings file. " +
        env.fold("Set it in the server's environment instead.")(v =>
          s"Set the environment variable $v for the server instead."
        )
    case NotAReference(k, why)  => s"${k.relative}: $why."
    case LiteralNeedsConsent(k) =>
      s"${k.relative}: value: writes the key itself into the settings file, where anyone who can read the file can read it. " +
        "Use env:<NAME> or a password manager's entry, or confirm that you want value:."
    case Substitution(k) =>
      s"${k.relative} is written in the settings file with a substitution ($${…}) or +=, which is kept exactly as it is. " +
        "Change it in the file itself."
    case FromInclude(k, file) => s"${k.relative} is set in $file, which the settings file includes. Change it there."
    case Unreadable(why)      =>
      s"The settings file does not read, so it is not changed: $why. Fix it in a text editor first."
    case Invalid(why) => s"That change would leave settings the server cannot start with, so it was not made: $why"
    case Changed      => "The settings file changed since it was read. Look at it again, then make the change."
    case NoFile(why)  => why
    case Io(why)      => s"The settings file could not be written: $why"

object SettingError:
  object BadKey:
    type Why = Why.T
    object Why extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  object BadValue:
    type Why = Why.T
    object Why extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  object CredentialInFile:
    type EnvVar = EnvVar.T
    object EnvVar extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  object NotAReference:
    type Why = Why.T
    object Why extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  object FromInclude:
    type File = File.T
    object File extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  object Unreadable:
    type Why = Why.T
    object Why extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  object Invalid:
    type Why = Why.T
    object Why extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  object NoFile:
    type Why = Why.T
    object Why extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  object Io:
    type Why = Why.T
    object Why extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type MessageResult = MessageResult.T
  object MessageResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
