package com.histmersion.api

/** LPF's `exactMatch` (the same entity) or `closeMatch` (near enough to join on). */
enum LinkMatch:
  case Exact, Close

  def wire: String = this match
    case Exact => "exact_match"
    case Close => "close_match"

object LinkMatch:
  /** LPF `links[].type`; anything else (`primaryTopicOf`, `subjectOf`, `seeAlso`) is no identity. */
  def ofLpf(t: String): Option[LinkMatch] = t.trim.toLowerCase(java.util.Locale.ROOT) match
    case "exactmatch" | "exact_match" | "skos:exactmatch" => Some(Exact)
    case "closematch" | "close_match" | "skos:closematch" => Some(Close)
    case _                                                => None
