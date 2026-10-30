package com.histmersion.config.edit

/** One change to the config file: a setting written, or taken out (back to its default). */
enum SettingEdit(val key: SettingKey):
  case Set(setting: SettingKey, value: SettingValue) extends SettingEdit(setting)
  case Unset(setting: SettingKey) extends SettingEdit(setting)

object SettingEdit:
  /** A change as a person or a client gives it — the key as typed, the value as typed and read as the setting's
    * kind (`None`: take it out). An unknown key is left to [[SettingPolicy]], which refuses it by name. */
  def parse(key: String, value: Option[String], schema: ConfigSchema): Either[SettingError, SettingEdit] =
    SettingKey.parse(key).left.map(SettingError.BadKey(_)).flatMap { k =>
      value match
        case None    => Right(Unset(k))
        case Some(v) =>
          val kind = schema.field(k).map(_.kind).getOrElse(SettingKind.Any)
          if kind == SettingKind.Block then Left(SettingError.NotAValue(k))
          else SettingValue.parse(v, kind).left.map(SettingError.BadValue(k, _)).map(Set(k, _))
    }

  /** Every change of `changes` parsed, the first refusal ending it. */
  def parseAll(changes: List[(String, Option[String])], schema: ConfigSchema): Either[SettingError, List[SettingEdit]] =
    @scala.annotation.tailrec
    def loop(rest: List[(String, Option[String])], acc: List[SettingEdit]): Either[SettingError, List[SettingEdit]] =
      rest match
        case Nil => Right(acc.reverse)
        case (k, v) :: tail => parse(k, v, schema) match
          case Left(e)  => Left(e)
          case Right(e) => loop(tail, e :: acc)
    if changes.isEmpty then Left(SettingError.BadKey("no change was given")) else loop(changes, Nil)
