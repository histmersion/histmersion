package com.histmersion
package web.library

import com.histmersion.api.research.Article
import com.histmersion.web.ui.Layout
import com.histmersion.lint.{LintRule, tolerated}

/** The start of an article's body as plain reading text: its first paragraph of prose (no heading, list, table or
  * quotation), its Markdown and links reduced to their words, cut at a word near `limit`. Pure.
  */
object ArticleOpening:
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def of(body: Article.Body, limit: Layout.ReadingOpening): Option[String] =
    val paragraphs = body.value.split("\n\\s*\n").toList.map(_.trim).filter(_.nonEmpty)
    paragraphs.find(prose).map(p => cut(plain(p), limit.value)).filter(_.nonEmpty)

  private def prose(p: String): Boolean =
    !p.startsWith("#") && !p.startsWith("|") && !p.startsWith(">") && !p.startsWith("- ") && !p.startsWith("* ") &&
      !p.startsWith("```") && !p.startsWith("<") && !p.matches("""(?s)^\d+\.\s.*""") && !p.startsWith("_")

  /** Links read as their words: `[[ck|words]]` → words, `[[ck]]` → nothing (a citation), `[words](url)` → words;
    * emphasis and code marks dropped; lines joined.
    */
  def plain(s: String): String =
    s.replaceAll("""\[\[[^\]|]+\|([^\]]+)\]\]""", "$1")
      .replaceAll("""\s*\[\[[^\]]+\]\]""", "")
      .replaceAll("""\[([^\]]+)\]\([^)]+\)""", "$1")
      .replaceAll("""[*_`]+""", "")
      .replaceAll("""\s+""", " ")
      .replaceAll(""" ([.,;:])""", "$1")
      .trim

  private def cut(s: String, limit: Int): String =
    if s.length <= limit then s
    else
      val head = s.take(limit)
      val at = head.lastIndexOf(' ')
      (if at > limit / 2 then head.take(at) else head).stripSuffix(",").stripSuffix(";") + "…"
