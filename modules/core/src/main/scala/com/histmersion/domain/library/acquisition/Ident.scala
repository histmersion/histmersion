package com.histmersion.domain.library.acquisition

import com.histmersion.domain.library.Doi as DoiValue

/** An identifier a person gives for a work: a DOI, an arXiv id, or the citekey of a source in the corpus. */
enum Ident:
  case Doi(value: Ident.Doi.Value)

  case Arxiv(value: Ident.Arxiv.Value)

  case Citekey(value: Ident.Citekey.Value)

object Ident:


  object Doi:
    type Value = Value.T
    object Value extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  object Arxiv:
    type Value = Value.T
    object Value extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  object Citekey:
    type Value = Value.T
    object Value extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type DoiRaw = DoiRaw.T
  object DoiRaw extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ArxivRaw = ArxivRaw.T
  object ArxivRaw extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ParseResultPart1 = ParseResultPart1.T
  object ParseResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ParseRaw = ParseRaw.T
  object ParseRaw extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type IsCitekeyS = IsCitekeyS.T
  object IsCitekeyS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  private val ArxivNew = """^(?:arxiv:)?(\d{4}\.\d{4,5})(?:v\d+)?$""".r
  private val ArxivOld = """^(?:arxiv:)?([a-z-]+(?:\.[A-Z]{2})?/\d{7})(?:v\d+)?$""".r

  /** DOIs are case-insensitive, so they are stored lower-case ([[DoiValue.normalize]]). */
  def doi(raw: com.histmersion.domain.library.acquisition.Ident.DoiRaw): Option[com.histmersion.domain.library.Doi] = DoiValue.normalize(raw)

  def arxiv(raw: com.histmersion.domain.library.acquisition.Ident.ArxivRaw): Option[com.histmersion.domain.library.ArxivId] =
    Option(raw).map(_.trim).filter(_.nonEmpty).flatMap { s =>
      s.toLowerCase match
        case ArxivNew(id) => Some(id)
        case _            =>
          s match
            case ArxivOld(id) => Some(id)
            case _            => None
    }

  def parse(raw: com.histmersion.domain.library.acquisition.Ident.ParseRaw): Either[com.histmersion.domain.library.acquisition.Ident.ParseResultPart1, Ident] =
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
  def isCitekey(s: com.histmersion.domain.library.acquisition.Ident.IsCitekeyS): Boolean =
    s.nonEmpty && s.head.isLetter && !s.exists(_.isWhitespace) && !s.contains("/")
