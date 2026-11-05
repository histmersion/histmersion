package com.histmersion.api.catalogue
import com.histmersion.api.given
enum LinkMatch:
  case Exact, Close

  def wire: com.histmersion.api.catalogue.LinkMatch.WireResult = this match
    case Exact => "exact_match"
    case Close => "close_match"

object LinkMatch:
  /** LPF `links[].type`; anything else (`primaryTopicOf`, `subjectOf`, `seeAlso`) is no identity. */
  type WireResult = WireResult.T
  object WireResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OfLpfT = OfLpfT.T
  object OfLpfT extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def ofLpf(t: com.histmersion.api.catalogue.LinkMatch.OfLpfT): Option[LinkMatch] = t.trim.toLowerCase(java.util.Locale.ROOT) match
    case "exactmatch" | "exact_match" | "skos:exactmatch" => Some(Exact)
    case "closematch" | "close_match" | "skos:closematch" => Some(Close)
    case _                                                => None
