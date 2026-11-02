package com.histmersion.api.library.citations

import com.histmersion.api.*
import com.histmersion.api.Lint.*

/** The citations' lint rules: edges to no stored source, and short citekeys an edge never resolved. */
object CitationLint:

  def danglingEdges(edges: List[Edge], citekeys: Set[String], maxSuggestions: Int): List[LintItem] =
    edges.distinct
      .filterNot(e => citekeys.contains(e.dst))
      .sortBy(e => (if looksLikeCitekey(e.dst) then 0 else 1, e.src, e.kind, e.dst))
      .map { e =>
        val near = suggest(e.dst, citekeys, maxSuggestions)
        val what =
          if looksLikeCitekey(e.dst) then "no such source"
          else "no such source — looks like a concept/article link, not a citekey"
        val tail = if near.isEmpty then "" else s" — closest: ${near.mkString(", ")}"
        LintItem(e.src, s"${e.kind} → ${e.dst} ($what)$tail")
      }

  def unresolvedShortCitekeys(edges: List[Edge], citekeys: Set[String], maxSuggestions: Int): List[LintItem] =
    edges.distinct
      .filterNot(e => citekeys.contains(e.dst))
      .flatMap { e =>
        val near = citekeys.filter(ck => ck.startsWith(e.dst + "_")).toList.sorted
        if near.isEmpty then None
        else
          val shown = near.take(maxSuggestions)
          val more = if near.sizeIs > shown.size then s", +${near.size - shown.size} more" else ""
          val verdict =
            if near.sizeIs == 1 then s"resolves to ${near.head} (fixable: lint --fix)"
            else s"ambiguous between ${shown.mkString(", ")}$more"
          Some(LintItem(e.src, s"${e.kind} → ${e.dst} $verdict"))
      }
      .sortBy(i => (i.citekey, i.detail))
