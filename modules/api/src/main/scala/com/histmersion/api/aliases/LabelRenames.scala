package com.histmersion
package api.aliases

import com.histmersion.util.problem.Problem

import com.histmersion.api.Write
import com.histmersion.api.given
import com.histmersion.api.exchange.Bundle
import com.histmersion.api.Api
import io.github.iltotore.iron.constraint.any.Pure

object LabelRenames:
  type Label = Label.T
  object Label extends Newtype[String, Pure]

  private val Shape = "^[a-z0-9][a-z0-9_.-]*$".r

  def validate(kind: Bundle.Kind, from: Label, raw: Label): Either[Api.Error, Label] =
    val to = (raw).value.trim
    val what = kind match
      case Bundle.Kind.Article => Problem.Field("article slug")
      case Bundle.Kind.Event   => Problem.Field("event id")
      case Bundle.Kind.Place   => Problem.Field("place id")
      case _                   => Problem.Field("goal slug")
    if !Shape.matches(to) then
      Left(
        Api.Error(
          Problem.Invalid.Unreadable(Problem.TypeName.of(Label), Problem.Raw(s"$to"), Vector(Problem.Raw("sea_peoples")), Vector(what)),
          Some(Api.Error.Code(Write.CodeInvalid))
        )
      )
    else if to == (from).value.trim then
      Left(
        Api.Error(
          Problem.Conflict.AlreadyExists(Problem.TypeName.of(Label), Problem.Raw(s"$to")),
          Some(Api.Error.Code(Write.CodeInvalid))
        )
      )
    else Right(Label(to))
