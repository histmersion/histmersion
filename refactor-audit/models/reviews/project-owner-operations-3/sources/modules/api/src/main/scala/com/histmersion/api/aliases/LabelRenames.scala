package com.histmersion.api.aliases
import com.histmersion.api.Write
import com.histmersion.api.given
import com.histmersion.api.exchange.Bundle
import com.histmersion.api.Api

object LabelRenames:
  type ValidateResultPart2 = ValidateResultPart2.T
  object ValidateResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ValidateFrom = ValidateFrom.T
  object ValidateFrom extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ValidateRaw = ValidateRaw.T
  object ValidateRaw extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  private val Shape = "^[a-z0-9][a-z0-9_.-]*$".r

  def validate(kind: Bundle.Kind, from: com.histmersion.api.aliases.LabelRenames.ValidateFrom, raw: com.histmersion.api.aliases.LabelRenames.ValidateRaw): Either[Api.Error, com.histmersion.api.aliases.LabelRenames.ValidateResultPart2] =
    val to = (raw).value.trim
    val what = kind match
      case Bundle.Kind.Article => "article slug"
      case Bundle.Kind.Event   => "event id"
      case Bundle.Kind.Place   => "place id"
      case _                   => "goal slug"
    if !Shape.matches(to) then
      Left(Api.Error(_root_.com.histmersion.api.Api.Error.Message(s"'$to' is not an $what (lower-case letters, digits, '_', '.' and '-')"), Some(_root_.com.histmersion.api.Api.Error.Code(Write.CodeInvalid))))
    else if to == from.trim then Left(Api.Error(_root_.com.histmersion.api.Api.Error.Message(s"'$to' is already that $what"), Some(_root_.com.histmersion.api.Api.Error.Code(Write.CodeInvalid))))
    else Right(to)
