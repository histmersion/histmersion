package com.histmersion.upstreams.identifiers
object Identifiers:
  type ArxivText = ArxivText.T
  object ArxivText extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DoiText = DoiText.T
  object DoiText extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]


  private val DoiCore = """10\.\d{4,9}/[-._;()/:A-Za-z0-9<>\[\]]+""".r
  private val ArxivNew = """^(?:arxiv:)?(\d{4}\.\d{4,5})(?:v\d+)?$""".r
  private val ArxivOld = """^(?:arxiv:)?([a-z-]+(?:\.[A-Z]{2})?/\d{7})(?:v\d+)?$""".r

  /** DOIs are case-insensitive, so they are kept lower-case. */
  def doi(raw: com.histmersion.upstreams.identifiers.Identifiers.DoiText): Option[com.histmersion.plugin.Doi] =
    val s = Option(raw)
      .map(_.value)
      .getOrElse("")
      .trim
      .replaceFirst("(?i)^https?://(dx\\.)?doi\\.org/", "")
      .replaceFirst("(?i)^doi:\\s*", "")
      .trim
    DoiCore
      .findFirstIn(s)
      .map(d =>
        _root_.com.histmersion.plugin.Doi(
          d.stripSuffix(".")
            .stripSuffix(",")
            .stripSuffix(";")
            .stripSuffix(")")
            .stripSuffix("}")
            .stripSuffix("|")
            .toLowerCase
        )
      )

  def arxiv(raw: com.histmersion.upstreams.identifiers.Identifiers.ArxivText): Option[com.histmersion.upstreams.ArxivId] =
    Option(raw).map((_).value.trim).filter(_.nonEmpty).flatMap { s =>
      s.toLowerCase match
        case ArxivNew(id) => Some(_root_.com.histmersion.upstreams.ArxivId(id))
        case _            =>
          s match
            case ArxivOld(id) => Some(_root_.com.histmersion.upstreams.ArxivId(id))
            case _            => None
    }
