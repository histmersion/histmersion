package com.histmersion.api.aliases
import com.histmersion.api.Write
import com.histmersion.api.given
import com.histmersion.api.exchange.Bundle
import com.histmersion.api.Api

object LabelRenames:
  private val Shape = "^[a-z0-9][a-z0-9_.-]*$".r

  def validate(kind: Bundle.Kind, from: String, raw: String): Either[Api.Error, String] =
    val to = raw.trim
    val what = kind match
      case Bundle.Kind.Article => "article slug"
      case Bundle.Kind.Event   => "event id"
      case Bundle.Kind.Place   => "place id"
      case _                   => "goal slug"
    if !Shape.matches(to) then
      Left(Api.Error(s"'$to' is not an $what (lower-case letters, digits, '_', '.' and '-')", Some(Write.CodeInvalid)))
    else if to == from.trim then Left(Api.Error(s"'$to' is already that $what", Some(Write.CodeInvalid)))
    else Right(to)
