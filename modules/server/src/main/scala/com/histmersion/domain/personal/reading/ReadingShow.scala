package com.histmersion.domain.personal.reading

/** Which read marks a list holds: the items marked read, those opened, or every one kept. */
enum ReadingShow(val wire: ReadingShow.Wire):
  case Read extends ReadingShow(com.histmersion.domain.personal.reading.ReadingShow.Wire("read"))
  case Opened extends ReadingShow(com.histmersion.domain.personal.reading.ReadingShow.Wire("opened"))
  case All extends ReadingShow(com.histmersion.domain.personal.reading.ReadingShow.Wire("all"))

object ReadingShow:
  type Wire = Wire.T
  object Wire extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]


  def parse(s: Option[com.histmersion.domain.personal.PersonalControllers.ShowMode]): Either[com.histmersion.domain.personal.PersonalControllers.ShowMode, ReadingShow] =
    s.map(_.trim.toLowerCase)
      .filter(_.nonEmpty)
      .fold(Right(Read))(t =>
        values.find(_.wire == t).toRight(s"show '$t': one of ${values.map(_.wire).mkString(", ")}")
      )
