package com.histmersion.domain.personal.reading

/** Which read marks a list holds: the items marked read, those opened, or every one kept. */
enum ReadingShow(val wire: String):
  case Read extends ReadingShow("read")
  case Opened extends ReadingShow("opened")
  case All extends ReadingShow("all")

object ReadingShow:
  def parse(s: Option[String]): Either[String, ReadingShow] =
    s.map(_.trim.toLowerCase)
      .filter(_.nonEmpty)
      .fold(Right(Read))(t =>
        values.find(_.wire == t).toRight(s"show '$t': one of ${values.map(_.wire).mkString(", ")}")
      )
