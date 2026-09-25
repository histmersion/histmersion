package com.histmersion.core

/** A validated citekey.
  *
  * A citekey is not just a database key: it is a PATH SEGMENT on the server's disk. The ingest spool
  * resolves `<spool-dir>/<citekey>` and the acquisition spool `<dir>/<citekey>/paper.pdf`, so a
  * citekey of `../../../../tmp/x` wrote outside the spool — and nothing validated one anywhere. It
  * is also a URL segment (`/source/{citekey}`) and an FTS/link target.
  *
  * So: validate ONCE, at every boundary that accepts one, and pass the validated value around
  * (AGENTS.md, "smart constructors where invariants exist"). Pure, so the rule is one unit test.
  */
opaque type Citekey <: String = String

object Citekey:

  /** Lower-case, starts with a letter, then letters/digits/`_`/`.`/`-`. Bounded, because it becomes
    * a filename. The corpus convention is `author2023_word`, which this admits; `-` and `.` are
    * admitted because real citekeys use them (`smith2019_a-b`, `x2020_v1.2`). */
  private val Shape = """^[a-z][a-z0-9_.-]{1,120}$""".r

  def parse(raw: String): Either[String, Citekey] =
    val s = Option(raw).map(_.trim).getOrElse("")
    if s.isEmpty then Left("citekey is required")
    else if s.contains("..") then
      // belt and braces: `..` cannot match `Shape` anyway (a leading `.` is refused and the rest is
      // bounded), but a path-traversal guard should not depend on reading a regex correctly.
      Left(s"invalid citekey '$raw' — it may not contain '..'")
    else if !Shape.matches(s) then
      Left(s"invalid citekey '$raw' — lower-case, start with a letter, then letters, digits, " +
        "'_', '.' or '-' (2-121 characters), e.g. anthony2007_horse")
    else Right(s)

  /** The same rule as an `ApiError`, which is what every handler actually needs. */
  def of(raw: String): Either[ApiError, Citekey] =
    parse(raw).left.map(m => ApiError(m, Some(Write.CodeInvalid)))

  /** True when `raw` is already a valid citekey — for the places that classify rather than reject
    * (a `[[link]]` target, say). */
  def valid(raw: String): Boolean = parse(raw).isRight
