package com.histmersion.domain.personal.reading

/** Which read marks a list holds: the items marked read, those opened, or every one kept. */
enum ReadingShow(val wire: String):
  case Read extends ReadingShow("read")
  case Opened extends ReadingShow("opened")
  case All extends ReadingShow("all")

object ReadingShow:
  type ParseResultPart1 = ParseResultPart1.T
  object ParseResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ParseS = ParseS.T
  object ParseS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def parse(s: Option[com.histmersion.domain.personal.reading.ReadingShow.ParseS]): Either[com.histmersion.domain.personal.reading.ReadingShow.ParseResultPart1, ReadingShow] =
    s.map(_.trim.toLowerCase)
      .filter(_.nonEmpty)
      .fold(Right(Read))(t =>
        values.find(_.wire == t).toRight(s"show '$t': one of ${values.map(_.wire).mkString(", ")}")
      )
