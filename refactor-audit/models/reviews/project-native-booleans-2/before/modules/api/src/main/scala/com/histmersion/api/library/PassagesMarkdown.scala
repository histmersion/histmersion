package com.histmersion.api.library

import com.histmersion.api.Plural
import com.histmersion.api.given
object PassagesMarkdown:

  type RenderResult = RenderResult.T
  object RenderResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MarkedResult = MarkedResult.T
  object MarkedResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def render(r: PassageResult): com.histmersion.api.library.PassagesMarkdown.RenderResult =
    val what = if r.regex then s"/${r.query}/" else s"\"${r.query}\""
    if r.passages.isEmpty then _root_.com.histmersion.api.library.PassagesMarkdown.RenderResult(s"_No match for $what in `${r.citekey}` (${r.bodyChars} chars searched)._")
    else
      val counted =
        if r.capped then s"${r.matches}+ matches (counting stopped)" else Plural.count(r.matches, _root_.com.histmersion.api.Plural.CountSingular("match"), _root_.com.histmersion.api.Plural.CountPlural("matches"))
      val head = s"## Passages: $what in `${r.citekey}`\n\n" +
        s"_$counted in ${Plural.count(r.total, _root_.com.histmersion.api.Plural.CountSingular("passage"))} · showing ${r.passages.size} · " +
        "matches in **bold** · `get_source` with a passage's `offset` reads on from it_"
      val items = r.passages.zipWithIndex.map { (p, i) =>
        val where = p.heading.map(h => s" · § $h").getOrElse("")
        s"### ${i + 1} · offset ${p.offset}$where\n\n${marked(p)}"
      }
      val more = r.total - r.passages.size
      val tail = if more > 0 then List(s"_$more more — raise limit or narrow the query._") else Nil
      _root_.com.histmersion.api.library.PassagesMarkdown.RenderResult((head :: items ++ tail).mkString("\n\n"))

  def marked(p: PassageHit): com.histmersion.api.library.PassagesMarkdown.MarkedResult =
    val b = new StringBuilder(p.text.length + p.matches.size * 4)
    val end = p.matches.sortBy(_.start).foldLeft(0) { (at, m) =>
      val s = m.start.max(at).min(p.text.length)
      val e = m.end.max(s).min(p.text.length)
      b ++= p.text.substring(at, s)
      if e > s then b ++= "**" ++= p.text.substring(s, e) ++= "**"
      e
    }
    b ++= p.text.substring(end)
    b.result()
