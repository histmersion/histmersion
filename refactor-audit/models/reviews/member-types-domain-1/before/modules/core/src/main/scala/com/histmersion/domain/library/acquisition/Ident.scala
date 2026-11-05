package com.histmersion.domain.library.acquisition

import com.histmersion.domain.library.Doi as DoiValue

/** An identifier a person gives for a work: a DOI, an arXiv id, or the citekey of a source in the corpus. */
enum Ident:
  case Doi(value: String)

  case Arxiv(value: String)

  case Citekey(value: String)

object Ident:


  private val ArxivNew = """^(?:arxiv:)?(\d{4}\.\d{4,5})(?:v\d+)?$""".r
  private val ArxivOld = """^(?:arxiv:)?([a-z-]+(?:\.[A-Z]{2})?/\d{7})(?:v\d+)?$""".r

  /** DOIs are case-insensitive, so they are stored lower-case ([[DoiValue.normalize]]). */
  def doi(raw: String): Option[String] = DoiValue.normalize(raw)

  def arxiv(raw: String): Option[String] =
    Option(raw).map(_.trim).filter(_.nonEmpty).flatMap { s =>
      s.toLowerCase match
        case ArxivNew(id) => Some(id)
        case _            =>
          s match
            case ArxivOld(id) => Some(id)
            case _            => None
    }

  def parse(raw: String): Either[String, Ident] =
    val s = Option(raw).getOrElse("").trim
    if s.isEmpty then Left("empty identifier — give a DOI, arxiv:<id>, or a citekey")
    else
      doi(s)
        .map(Ident.Doi.apply)
        .orElse(arxiv(s).map(Ident.Arxiv.apply))
        .orElse(Option.when(isCitekey(s))(Ident.Citekey(s)))
        .toRight(s"'$s' is neither a DOI, an arXiv id, nor a citekey")

  /** Deliberately permissive: the corpus holds hand-written keys and keys of older conventions that the generator would
    * not produce, so the store, not a regular expression, decides whether a citekey exists.
    */
  def isCitekey(s: String): Boolean =
    s.nonEmpty && s.head.isLetter && !s.exists(_.isWhitespace) && !s.contains("/")
