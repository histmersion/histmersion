package com.histmersion.values

import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.string.Match

/** Lower-case, starts with a letter, then letters, digits, `_`, `.` or `-`, 2 to 121 characters, and never `..`. A
  * citekey is a file name on the server (the ingest spool keeps `<spool>/<citekey>`) and a URL segment, so it is
  * bounded and can never climb out of a directory. The corpus convention is `author2023_word`; `-` and `.` are admitted
  * because real citekeys use them (`smith2019_a-b`, `x2020_v1.2`).
  */
type CitekeyRule = Match["(?!.*\\.\\.)[a-z][a-z0-9_.-]{1,120}"]

type Citekey = Citekey.T

object Citekey extends RefinedType[String, CitekeyRule]:

  /** A citekey from what a person or a client typed (trimmed), or why it is none. */
  def parse(raw: String): Either[String, Citekey] =
    val s = Option(raw).map(_.trim).getOrElse("")
    if s.isEmpty then Left("citekey is required")
    else if s.contains("..") then Left(s"invalid citekey '$raw' — it may not contain '..'")
    else
      either(s).left.map(_ =>
        s"invalid citekey '$raw' — lower-case, start with a letter, then letters, digits, " +
          "'_', '.' or '-' (2-121 characters), e.g. anthony2007_horse"
      )

  /** Is `raw` a citekey as it stands — for the places that classify rather than reject (a `[[link]]` target). */
  def valid(raw: String): Boolean = parse(raw).isRight
