package com.histmersion.config.edit

/** Whether a change may be written at all, against the schema: the key is a setting, the value is of its
  * kind, a credential is a reference (`value:` only with `literalConsent`). The warnings say what works but
  * should be known (a `value:` credential). Pure. */
object SettingPolicy:

  /** [[check]] of every edit, the first refusal ending it; the warnings of all of them. */
  def all(edits: List[SettingEdit], schema: ConfigSchema, literalConsent: Boolean): Either[SettingError, List[String]] =
    @scala.annotation.tailrec
    def loop(rest: List[SettingEdit], notes: List[String]): Either[SettingError, List[String]] = rest match
      case Nil => Right(notes)
      case e :: tail => check(e, schema, literalConsent) match
        case Left(err) => Left(err)
        case Right(ns) => loop(tail, notes ++ ns)
    loop(edits, Nil)

  def check(edit: SettingEdit, schema: ConfigSchema, literalConsent: Boolean): Either[SettingError, List[String]] =
    val key = edit.key
    for
      field <- schema.field(key).toRight(
                 if schema.isBlock(key) then SettingError.NotAValue(key) else SettingError.UnknownKey(key, schema.closest(key)))
      _     <- Either.cond(field.kind != SettingKind.Block, (), SettingError.NotAValue(key))
      notes <- edit match
                 case SettingEdit.Unset(_)    => Right(Nil)
                 case SettingEdit.Set(_, v)   => value(field, v, literalConsent)
    yield notes

  private def value(field: SettingField, v: SettingValue, literalConsent: Boolean): Either[SettingError, List[String]] =
    val key = field.key
    val kindOk = field.kind == SettingKind.Any || field.kind == v.kind
    if !kindOk then Left(SettingError.WrongKind(key, field.kind, v.kind))
    else field.secret match
      case SecretRule.NotSecret       => Right(Nil)
      case SecretRule.FromEnvironment => Left(SettingError.CredentialInFile(key, field.envVar))
      case SecretRule.Reference       =>
        v match
          case SettingValue.Text(t) =>
            SecretForm.reference(t).left.map(SettingError.NotAReference(key, _)).flatMap {
              case SecretForm.Literal if !literalConsent => Left(SettingError.LiteralNeedsConsent(key))
              case SecretForm.Literal =>
                Right(List(s"${key.relative} holds the key itself (value:): anyone who can read the settings file can read it."))
              case _ => Right(Nil)
            }
          case other => Left(SettingError.WrongKind(key, SettingKind.Text, other.kind))
