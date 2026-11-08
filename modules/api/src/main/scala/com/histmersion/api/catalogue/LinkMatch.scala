package com.histmersion
package api.catalogue
import com.histmersion.api.given
import io.github.iltotore.iron.constraint.any.Pure
enum LinkMatch derives CanEqual:
  case Exact, Close

  def wire: LinkMatch.Wire = this match
    case Exact => LinkMatch.Wire("exact_match")
    case Close => LinkMatch.Wire("close_match")

object LinkMatch:
  type Wire = Wire.T
  object Wire extends Newtype[String, Pure]

  /** LPF `links[].type`; anything else (`primaryTopicOf`, `subjectOf`, `seeAlso`) is no identity. */

  def ofLpf(t: Wire): Option[LinkMatch] = (t).value.trim.toLowerCase(java.util.Locale.ROOT) match
    case "exactmatch" | "exact_match" | "skos:exactmatch" => Some(Exact)
    case "closematch" | "close_match" | "skos:closematch" => Some(Close)
    case _                                                => None
