package com.histmersion.api.aliases
import com.histmersion.api.Write
import com.histmersion.api.given
import com.histmersion.api.exchange.Bundle
import com.histmersion.api.Api

object LabelRenames:
  type Label = Label.T
  object Label extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  private val Shape = "^[a-z0-9][a-z0-9_.-]*$".r

  def validate(kind: Bundle.Kind, from: com.histmersion.api.aliases.LabelRenames.Label, raw: com.histmersion.api.aliases.LabelRenames.Label): Either[Api.Error, com.histmersion.api.aliases.LabelRenames.Label] =
    val to = (raw).value.trim
    val what = kind match
      case Bundle.Kind.Article => "article slug"
      case Bundle.Kind.Event   => "event id"
      case Bundle.Kind.Place   => "place id"
      case _                   => "goal slug"
    if !Shape.matches(to) then
      Left(Api.Error(_root_.com.histmersion.api.Api.Error.Message(s"'$to' is not an $what (lower-case letters, digits, '_', '.' and '-')"), Some(_root_.com.histmersion.api.Api.Error.Code(Write.CodeInvalid))))
    else if to == (from).value.trim then Left(Api.Error(_root_.com.histmersion.api.Api.Error.Message(s"'$to' is already that $what"), Some(_root_.com.histmersion.api.Api.Error.Code(Write.CodeInvalid))))
    else Right(_root_.com.histmersion.api.aliases.LabelRenames.Label(to))
