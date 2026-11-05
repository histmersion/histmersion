package com.histmersion.config.edit

/** One change to the config file: a setting written, or taken out (back to its default). */
enum SettingEdit(val key: SettingKey):
  case Set(setting: SettingKey, value: SettingValue) extends SettingEdit(setting)
  case Unset(setting: SettingKey) extends SettingEdit(setting)

object SettingEdit:
  /** A change as a person or a client gives it — the key as typed, the value as typed and read as the setting's kind
    * (`None`: take it out). An unknown key is left to [[SettingPolicy]], which refuses it by name.
    */
  type ParseAllLoopRestPart1 = ParseAllLoopRestPart1.T
  object ParseAllLoopRestPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ParseAllLoopRestPart2 = ParseAllLoopRestPart2.T
  object ParseAllLoopRestPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type ParseKey = ParseKey.T
  object ParseKey extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ParseValue = ParseValue.T
  object ParseValue extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ParseAllChangesPart1 = ParseAllChangesPart1.T
  object ParseAllChangesPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ParseAllChangesPart2 = ParseAllChangesPart2.T
  object ParseAllChangesPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def parse(key: com.histmersion.config.edit.SettingEdit.ParseKey, value: Option[com.histmersion.config.edit.SettingEdit.ParseValue], schema: ConfigSchema): Either[SettingError, SettingEdit] =
    SettingKey.parse(key).left.map(SettingError.BadKey(_)).flatMap { k =>
      value match
        case None    => Right(Unset(k))
        case Some(v) =>
          val kind = schema.field(k).map(_.kind).getOrElse(SettingKind.Any)
          if kind == SettingKind.Block then Left(SettingError.NotAValue(k))
          else SettingValue.parse(v, kind).left.map(SettingError.BadValue(k, _)).map(Set(k, _))
    }

  /** Every change of `changes` parsed, the first refusal ending it. */
  def parseAll(changes: List[(com.histmersion.config.edit.SettingEdit.ParseAllChangesPart1, Option[com.histmersion.config.edit.SettingEdit.ParseAllChangesPart2])], schema: ConfigSchema): Either[SettingError, List[SettingEdit]] =
    @scala.annotation.tailrec
    def loop(rest: List[(com.histmersion.config.edit.SettingEdit.ParseAllLoopRestPart1, Option[com.histmersion.config.edit.SettingEdit.ParseAllLoopRestPart2])], acc: List[SettingEdit]): Either[SettingError, List[SettingEdit]] =
      rest match
        case Nil            => Right(acc.reverse)
        case (k, v) :: tail =>
          parse(k, v, schema) match
            case Left(e)  => Left(e)
            case Right(e) => loop(tail, e :: acc)
    if changes.isEmpty then Left(SettingError.BadKey("no change was given")) else loop(changes, Nil)
