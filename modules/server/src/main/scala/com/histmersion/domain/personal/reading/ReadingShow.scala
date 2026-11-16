package com.histmersion
package domain.personal.reading
import com.histmersion.util.problem.Problem

import io.github.iltotore.iron.constraint.any.Pure

/** Which read marks a list holds: the items marked read, those opened, or every one kept. */
enum ReadingShow(val wire: ReadingShow.Wire):
  case Read extends ReadingShow(ReadingShow.Wire("read"))
  case Opened extends ReadingShow(ReadingShow.Wire("opened"))
  case All extends ReadingShow(ReadingShow.Wire("all"))

object ReadingShow:
  type Wire = Wire.T
  object Wire extends Newtype[String, Pure]

  def parse(s: Option[Wire]): Either[Problem, ReadingShow] =
    s.map(_.value.trim.toLowerCase)
      .filter(_.nonEmpty)
      .fold[Either[Problem, ReadingShow]](Right(Read))(t =>
        values
          .find(_.wire.value == t)
          .toRight(
            Problem.Invalid.Unknown(
              Problem.TypeName.of(Wire),
              Problem.Raw(t),
              values.toVector.map(v => Problem.Raw.of(v.wire)),
              Vector(Problem.Field("show"))
            )
          )
      )
