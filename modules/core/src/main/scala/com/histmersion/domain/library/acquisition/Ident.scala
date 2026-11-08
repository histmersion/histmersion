package com.histmersion
package domain.library.acquisition

import com.histmersion.domain.Problem
import com.histmersion.domain.library.{ArxivId, Doi as DoiValue}
import io.github.iltotore.iron.constraint.any.Pure

/** An identifier a person gives for a work: a DOI, an arXiv id, or the citekey of a source in the corpus. */
enum Ident derives CanEqual:
  case Doi(value: DoiValue)

  case Arxiv(value: ArxivId)

  case Citekey(value: Ident.Citekey.Value)

object Ident:

  object Citekey:
    type Value = Value.T
    object Value extends Newtype[String, Pure]

  /** An identifier as a person wrote it. */
  type Written = Written.T
  object Written extends Newtype[String, Pure]

  private val ArxivNew = """^(?:arxiv:)?(\d{4}\.\d{4,5})(?:v\d+)?$""".r
  private val ArxivOld = """^(?:arxiv:)?([a-z-]+(?:\.[A-Z]{2})?/\d{7})(?:v\d+)?$""".r

  /** DOIs are case-insensitive, so they are stored lower-case ([[DoiValue.normalize]]). */
  def doi(raw: Written): Option[com.histmersion.domain.library.Doi] = DoiValue.normalize(raw.value)

  def arxiv(raw: Written): Option[ArxivId] =
    Option(raw.value).map(_.trim).filter(_.nonEmpty).flatMap { s =>
      s.toLowerCase match
        case ArxivNew(id) => Some(ArxivId(id))
        case _            =>
          s match
            case ArxivOld(id) => Some(ArxivId(id))
            case _            => None
    }

  def parse(raw: Written): Either[Problem, Ident] =
    val s = Option(raw.value).getOrElse("").trim
    if s.isEmpty then Left(Problem("empty identifier — give a DOI, arxiv:<id>, or a citekey"))
    else
      doi(Written(s))
        .map(Doi(_))
        .orElse(arxiv(Written(s)).map(Arxiv(_)))
        .orElse(Option.when(isCitekey(Citekey.Value(s)))(Citekey(Citekey.Value(s))))
        .toRight(Problem(s"'$s' is neither a DOI, an arXiv id, nor a citekey"))

  /** Deliberately permissive: the corpus holds hand-written keys and keys of older conventions that the generator would
    * not produce, so the store, not a regular expression, decides whether a citekey exists.
    */
  def isCitekey(s: Citekey.Value): Boolean =
    s.value.nonEmpty && s.value.head.isLetter && !s.value.exists(_.isWhitespace) && !s.value.contains("/")
