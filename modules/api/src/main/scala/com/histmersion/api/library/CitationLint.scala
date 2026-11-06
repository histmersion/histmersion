package com.histmersion.api.library
import com.histmersion.api.checks.Lint.*

/** The citations' lint rules: edges to no stored source, and short citekeys an edge never resolved. */
object CitationLint:

  def danglingEdges(edges: List[Edge], citekeys: Set[com.histmersion.api.library.Citekey], maxSuggestions: com.histmersion.api.checks.Lint.ItemLimit): List[LintItem] =
    edges.distinct
      .filterNot(e => citekeys.map(_.value.toString).contains(e.dst.value.toString))
      .sortBy(e => (if looksLikeCitekey(e.dst) then 0 else 1, e.src.value.toString, (Edge.Kind.wireOf(e.kind).value: String), e.dst.value.toString))
      .map { e =>
        val near = suggest(e.dst, citekeys, maxSuggestions)
        val what =
          if looksLikeCitekey(e.dst) then "no such source"
          else "no such source — looks like a concept/article link, not a citekey"
        val tail = if near.isEmpty then "" else s" — closest: ${near.mkString(", ")}"
        LintItem(e.src, LintItem.Detail(s"${e.kind} → ${e.dst.value.toString} ($what)$tail"))
      }

  def unresolvedShortCitekeys(edges: List[Edge], citekeys: Set[com.histmersion.api.library.Citekey], maxSuggestions: com.histmersion.api.checks.Lint.ItemLimit): List[LintItem] =
    edges.distinct
      .filterNot(e => citekeys.map(_.value.toString).contains(e.dst.value.toString))
      .flatMap { e =>
        val near = citekeys.map(_.value.toString).filter(ck => ck.startsWith(e.dst.value.toString + "_")).toList.sorted
        if near.isEmpty then None
        else
          val shown = near.take((maxSuggestions).value)
          val more = if near.sizeIs > shown.size then s", +${near.size - shown.size} more" else ""
          val verdict =
            if near.sizeIs == 1 then s"resolves to ${near.head} (fixable: lint --fix)"
            else s"ambiguous between ${shown.mkString(", ")}$more"
          Some(LintItem(_root_.com.histmersion.api.library.Citekey(e.src.value.toString), _root_.com.histmersion.api.checks.Lint.LintItem.Detail(s"${e.kind} → ${e.dst.value.toString} $verdict")))
      }
      .sortBy(i => (i.citekey.value: String, i.detail.value: String))
