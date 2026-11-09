package com.histmersion
package domain.personal.reading
import com.histmersion.util.problem.{Legacy, Problem}

import io.github.iltotore.iron.constraint.any.Pure

/** Which read marks a list holds: the items marked read, those opened, or every one kept. */
enum ReadingShow(val wire: ReadingShow.Wire) derives CanEqual:
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
          .toRight(Problem.Unclassified(Legacy.Text(s"show '$t': one of ${values.map(_.wire.value).mkString(", ")}")))
      )
