package com.histmersion
package api

import com.histmersion.util.problem.Problem
import io.github.iltotore.iron.constraint.any.Pure

enum Dataset derives CanEqual:
  case Seed, Local
  def wire: Dataset.Wire = Dataset.Wire(this.toString.toLowerCase(java.util.Locale.ROOT))

object Dataset:
  type Wire = Wire.T
  object Wire extends Newtype[String, Pure]

  def parse(s: Wire): Option[Dataset] = values.find(_.wire == Wire((s).value.trim.toLowerCase(java.util.Locale.ROOT)))

  def ofParam(raw: Option[Wire]): Either[Api.Error, Dataset] =
    raw.map((_).value.trim).filter(_.nonEmpty) match
      case None    => Right(Local)
      case Some(s) =>
        parse(Wire(s)).toRight(
          Api.Error(
            Problem.Invalid.Unknown.here(Problem.TypeName.of(Wire), Problem.Raw(s"$s"), values.toVector.map(d => Problem.Raw(s"${d.wire}"))),
            Some(Api.Error.Code(Write.CodeInvalid))
          )
        )
