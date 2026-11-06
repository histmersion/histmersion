package com.histmersion.config.edit

/** Whether a change may be written at all, against the schema: the key is a setting, the value is of its kind, a
  * credential is a reference (`value:` only with `literalConsent`). The warnings say what works but should be known (a
  * `value:` credential). Pure.
  */
object SettingPolicy:

  /** [[check]] of every edit, the first refusal ending it; the warnings of all of them. */
  type AllLoopResultPart2 = AllLoopResultPart2.T
  object AllLoopResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type AllLoopNotes = AllLoopNotes.T
  object AllLoopNotes extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type AllResultPart2 = AllResultPart2.T
  object AllResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type AllLiteralConsent = AllLiteralConsent.T
  object AllLiteralConsent extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CheckResultPart2 = CheckResultPart2.T
  object CheckResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CheckLiteralConsent = _root_.com.histmersion.config.edit.SettingPolicy.ValueLiteralConsent
  val CheckLiteralConsent: _root_.com.histmersion.config.edit.SettingPolicy.ValueLiteralConsent.type = _root_.com.histmersion.config.edit.SettingPolicy.ValueLiteralConsent
  type ValueResultPart2 = ValueResultPart2.T
  object ValueResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ValueLiteralConsent = ValueLiteralConsent.T
  object ValueLiteralConsent extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def all(edits: List[SettingEdit], schema: ConfigSchema, literalConsent: com.histmersion.config.edit.SettingPolicy.AllLiteralConsent): Either[SettingError, List[com.histmersion.config.edit.SettingPolicy.AllResultPart2]] =
    @scala.annotation.tailrec
    def loop(rest: List[SettingEdit], notes: List[com.histmersion.config.edit.SettingPolicy.AllLoopNotes]): Either[SettingError, List[com.histmersion.config.edit.SettingPolicy.AllLoopResultPart2]] = rest match
      case Nil       => Right(notes)
      case e :: tail =>
        check(e, schema, literalConsent) match
          case Left(err) => Left(err)
          case Right(ns) => loop(tail, notes ++ ns)
    loop(edits, Nil)

  def check(edit: SettingEdit, schema: ConfigSchema, literalConsent: com.histmersion.config.edit.SettingPolicy.CheckLiteralConsent): Either[SettingError, List[com.histmersion.config.edit.SettingPolicy.CheckResultPart2]] =
    val key = edit.key
    for
      field <- schema
        .field(key)
        .toRight(if schema.isBlock(key) then SettingError.NotAValue(key)
        else SettingError.UnknownKey(key, schema.closest(key)))
      _ <- Either.cond(field.kind != SettingKind.Block, (), SettingError.NotAValue(key))
      notes <- edit match
        case SettingEdit.Unset(_)  => Right(Nil)
        case SettingEdit.Set(_, v) => value(field, v, literalConsent)
    yield notes

  private def value(field: SettingField, v: SettingValue, literalConsent: com.histmersion.config.edit.SettingPolicy.ValueLiteralConsent): Either[SettingError, List[com.histmersion.config.edit.SettingPolicy.ValueResultPart2]] =
    val key = field.key
    val kindOk = field.kind == SettingKind.Any || field.kind == v.kind
    if !kindOk then Left(SettingError.WrongKind(key, field.kind, v.kind))
    else
      field.secret match
        case SecretRule.NotSecret       => Right(Nil)
        case SecretRule.FromEnvironment => Left(SettingError.CredentialInFile(key, field.envVar))
        case SecretRule.Reference       =>
          v match
            case SettingValue.Text(t) =>
              SecretForm.reference(t).left.map(SettingError.NotAReference(key, _)).flatMap {
                case SecretForm.Literal if !literalConsent => Left(SettingError.LiteralNeedsConsent(key))
                case SecretForm.Literal                    =>
                  Right(
                    List(
                      s"${key.relative} holds the key itself (value:): anyone who can read the settings file can read it."
                    )
                  )
                case _ => Right(Nil)
              }
            case other => Left(SettingError.WrongKind(key, SettingKind.Text, other.kind))
