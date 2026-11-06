package com.histmersion.api.catalogue
import com.histmersion.api.given
enum LinkMatch:
  case Exact, Close

  def wire: com.histmersion.api.catalogue.LinkMatch.Wire = this match
    case Exact => _root_.com.histmersion.api.catalogue.LinkMatch.Wire("exact_match")
    case Close => _root_.com.histmersion.api.catalogue.LinkMatch.Wire("close_match")

object LinkMatch:
  type Wire = Wire.T
  object Wire extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  /** LPF `links[].type`; anything else (`primaryTopicOf`, `subjectOf`, `seeAlso`) is no identity. */

  def ofLpf(t: com.histmersion.api.catalogue.LinkMatch.Wire): Option[LinkMatch] = (t).value.trim.toLowerCase(java.util.Locale.ROOT) match
    case "exactmatch" | "exact_match" | "skos:exactmatch" => Some(Exact)
    case "closematch" | "close_match" | "skos:closematch" => Some(Close)
    case _                                                => None
