package com.histmersion.domain.personal.reading

/** Which read marks a list holds: the items marked read, those opened, or every one kept. */
enum ReadingShow(val wire: ReadingShow.Wire):
  case Read extends ReadingShow(com.histmersion.domain.personal.reading.ReadingShow.Wire("read"))
  case Opened extends ReadingShow(com.histmersion.domain.personal.reading.ReadingShow.Wire("opened"))
  case All extends ReadingShow(com.histmersion.domain.personal.reading.ReadingShow.Wire("all"))

object ReadingShow:
  type Wire = Wire.T
  object Wire extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]


  type Problem = Problem.T
  object Problem extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def parse(s: Option[Wire]): Either[Problem, ReadingShow] =
    s.map(_.value.trim.toLowerCase)
      .filter(_.nonEmpty)
      .fold[Either[Problem, ReadingShow]](Right(Read))(t =>
        values
          .find(_.wire.value == t)
          .toRight(Problem(s"show '$t': one of ${values.map(_.wire.value).mkString(", ")}"))
      )
