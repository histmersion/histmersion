package com.histmersion.api.library
import com.histmersion.api.checks.Lint.*

/** The citations' lint rules: edges to no stored source, and short citekeys an edge never resolved. */
object CitationLint:

  type DanglingEdgesMaxSuggestions = DanglingEdgesMaxSuggestions.T
  object DanglingEdgesMaxSuggestions extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type UnresolvedShortCitekeysMaxSuggestions = UnresolvedShortCitekeysMaxSuggestions.T
  object UnresolvedShortCitekeysMaxSuggestions extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def danglingEdges(edges: List[Edge], citekeys: Set[com.histmersion.api.library.Citekey], maxSuggestions: com.histmersion.api.library.CitationLint.DanglingEdgesMaxSuggestions): List[LintItem] =
    edges.distinct
      .filterNot(e => citekeys.map(_.value.toString).contains(e.dst.value.toString))
      .sortBy(e => (if looksLikeCitekey(e.dst.value.toString) then 0 else 1, e.src.value.toString, e.kind, e.dst.value.toString))
      .map { e =>
        val near = suggest(e.dst.value.toString, citekeys.map(_.value.toString), maxSuggestions)
        val what =
          if looksLikeCitekey(e.dst.value.toString) then "no such source"
          else "no such source — looks like a concept/article link, not a citekey"
        val tail = if near.isEmpty then "" else s" — closest: ${near.mkString(", ")}"
        LintItem(e.src.value.toString, s"${e.kind} → ${e.dst.value.toString} ($what)$tail")
      }

  def unresolvedShortCitekeys(edges: List[Edge], citekeys: Set[com.histmersion.api.library.Citekey], maxSuggestions: com.histmersion.api.library.CitationLint.UnresolvedShortCitekeysMaxSuggestions): List[LintItem] =
    edges.distinct
      .filterNot(e => citekeys.map(_.value.toString).contains(e.dst.value.toString))
      .flatMap { e =>
        val near = citekeys.map(_.value.toString).filter(ck => ck.startsWith(e.dst.value.toString + "_")).toList.sorted
        if near.isEmpty then None
        else
          val shown = near.take(maxSuggestions)
          val more = if near.sizeIs > shown.size then s", +${near.size - shown.size} more" else ""
          val verdict =
            if near.sizeIs == 1 then s"resolves to ${near.head} (fixable: lint --fix)"
            else s"ambiguous between ${shown.mkString(", ")}$more"
          Some(LintItem(e.src.value.toString, s"${e.kind} → ${e.dst.value.toString} $verdict"))
      }
      .sortBy(i => (i.citekey, i.detail))
