package com.histmersion.api.library

import com.histmersion.api.Plural
import com.histmersion.api.given
object PassagesMarkdown:
  def render(r: PassageResult): String =
    val what = if ((r.regex) == _root_.com.histmersion.api.library.PassageResult.Regex(true)) then s"/${r.query}/" else s"\"${r.query}\""
    if r.passages.isEmpty then (s"_No match for $what in `${r.citekey}` (${r.bodyChars} chars searched)._")
    else
      val counted =
        if ((r.capped) == _root_.com.histmersion.api.library.PassageResult.Capped(true)) then s"${r.matches}+ matches (counting stopped)" else Plural.count(Plural.Count(r.matches.value.toLong), _root_.com.histmersion.api.Plural.CountSingular("match"), _root_.com.histmersion.api.Plural.CountPlural("matches"))
      val head = s"## Passages: $what in `${r.citekey}`\n\n" +
        s"_$counted in ${Plural.count(Plural.Count(r.total.value.toLong), _root_.com.histmersion.api.Plural.CountSingular("passage"))} · showing ${r.passages.size} · " +
        "matches in **bold** · `get_source` with a passage's `offset` reads on from it_"
      val items = r.passages.zipWithIndex.map { (p, i) =>
        val where = p.heading.map(h => s" · § $h").getOrElse("")
        s"### ${i + 1} · offset ${p.offset}$where\n\n${marked(p)}"
      }
      val more = (r.total).value - r.passages.size
      val tail = if more > 0 then List(s"_$more more — raise limit or narrow the query._") else Nil
      ((head :: items ++ tail).mkString("\n\n"))

  def marked(p: PassageHit): String =
    val text = p.text.value
    val b = new StringBuilder(text.length + p.matches.size * 4)
    val end = p.matches.sortBy(_.start).foldLeft(0) { (at, m) =>
      val s = (m.start).value.max(at).min(text.length)
      val e = (m.end).value.max(s).min(text.length)
      b ++= text.substring(at, s)
      if e > s then b ++= "**" ++= text.substring(s, e) ++= "**"
      e
    }
    b ++= text.substring(end)
    b.result()
