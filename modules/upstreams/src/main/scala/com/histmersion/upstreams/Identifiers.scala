package com.histmersion.upstreams

/** The identifiers an upstream's record names, as Histmersion keeps them: a DOI lower-case and bare (no `doi.org/`,
  * no trailing punctuation a citation dragged in), an arXiv id without its version. The host's own copy of these two
  * rules (the wire's `Acquire.normalizeDoi`/`normalizeArxiv`, which the Scala.js page shares) is kept equal to these by
  * a test over the same inputs.
  */
object Identifiers:

  private val DoiCore = """10\.\d{4,9}/[-._;()/:A-Za-z0-9<>\[\]]+""".r
  private val ArxivNew = """^(?:arxiv:)?(\d{4}\.\d{4,5})(?:v\d+)?$""".r
  private val ArxivOld = """^(?:arxiv:)?([a-z-]+(?:\.[A-Z]{2})?/\d{7})(?:v\d+)?$""".r

  /** DOIs are case-insensitive, so they are kept lower-case. */
  def doi(raw: String): Option[String] =
    val s = Option(raw)
      .getOrElse("")
      .trim
      .replaceFirst("(?i)^https?://(dx\\.)?doi\\.org/", "")
      .replaceFirst("(?i)^doi:\\s*", "")
      .trim
    DoiCore
      .findFirstIn(s)
      .map(
        _.stripSuffix(".")
          .stripSuffix(",")
          .stripSuffix(";")
          .stripSuffix(")")
          .stripSuffix("}")
          .stripSuffix("|")
          .toLowerCase
      )

  def arxiv(raw: String): Option[String] =
    Option(raw).map(_.trim).filter(_.nonEmpty).flatMap { s =>
      s.toLowerCase match
        case ArxivNew(id) => Some(id)
        case _            =>
          s match
            case ArxivOld(id) => Some(id)
            case _            => None
    }
