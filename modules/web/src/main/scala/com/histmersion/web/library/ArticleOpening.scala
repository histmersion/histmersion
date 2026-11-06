package com.histmersion.web.library

/** The start of an article's body as plain reading text: its first paragraph of prose (no heading, list, table or
  * quotation), its Markdown and links reduced to their words, cut at a word near `limit`. Pure.
  */
object ArticleOpening:

  type OfResult = OfResult.T
  object OfResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OfBody = OfBody.T
  object OfBody extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OfLimit = _root_.com.histmersion.web.library.ArticleOpening.CutLimit
  val OfLimit: _root_.com.histmersion.web.library.ArticleOpening.CutLimit.type = _root_.com.histmersion.web.library.ArticleOpening.CutLimit
  type ProseResult = ProseResult.T
  object ProseResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ProseP = ProseP.T
  object ProseP extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PlainResult = PlainResult.T
  object PlainResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PlainS = PlainS.T
  object PlainS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CutResult = CutResult.T
  object CutResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CutS = CutS.T
  object CutS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CutLimit = CutLimit.T
  object CutLimit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def of(body: com.histmersion.web.library.ArticleOpening.OfBody, limit: com.histmersion.web.library.ArticleOpening.OfLimit): Option[com.histmersion.web.library.ArticleOpening.OfResult] =
    val paragraphs = body.split("\n\\s*\n").toList.map(_.trim).filter(_.nonEmpty)
    paragraphs.find(prose).map(p => cut(plain(p), limit)).filter(_.nonEmpty)

  private def prose(p: com.histmersion.web.library.ArticleOpening.ProseP): com.histmersion.web.library.ArticleOpening.ProseResult =
    !p.startsWith("#") && !p.startsWith("|") && !p.startsWith(">") && !p.startsWith("- ") && !p.startsWith("* ") &&
      !p.startsWith("```") && !p.startsWith("<") && !p.matches("""(?s)^\d+\.\s.*""") && !p.startsWith("_")

  /** Links read as their words: `[[ck|words]]` → words, `[[ck]]` → nothing (a citation), `[words](url)` → words;
    * emphasis and code marks dropped; lines joined.
    */
  def plain(s: com.histmersion.web.library.ArticleOpening.PlainS): com.histmersion.web.library.ArticleOpening.PlainResult =
    s.replaceAll("""\[\[[^\]|]+\|([^\]]+)\]\]""", "$1")
      .replaceAll("""\s*\[\[[^\]]+\]\]""", "")
      .replaceAll("""\[([^\]]+)\]\([^)]+\)""", "$1")
      .replaceAll("""[*_`]+""", "")
      .replaceAll("""\s+""", " ")
      .replaceAll(""" ([.,;:])""", "$1")
      .trim

  private def cut(s: com.histmersion.web.library.ArticleOpening.CutS, limit: com.histmersion.web.library.ArticleOpening.CutLimit): com.histmersion.web.library.ArticleOpening.CutResult =
    if s.length <= limit then s
    else
      val head = s.take(limit)
      val at = head.lastIndexOf(' ')
      (if at > limit / 2 then head.take(at) else head).stripSuffix(",").stripSuffix(";") + "…"
