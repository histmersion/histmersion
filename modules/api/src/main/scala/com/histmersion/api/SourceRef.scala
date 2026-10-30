package com.histmersion.api

import com.histmersion.values.SourceStatus

// ── reference types ───────────────────────────────────────────────────────────────────────────────

/** A lightweight reference to a source, carrying just enough to render a citation link. `idLabel` is the DOI when
  * present, else the citekey — the owner's rule: "keep our own internal naming system BUT present DOI in UI when it is
  * available, falling back on our item".
  */
final case class SourceRef(
    citekey: String,
    doi: Option[String],
    authors: List[String],
    year: Option[Int],
    title: String,
    status: SourceStatus
):
  def idLabel: String = doi.getOrElse(citekey)

  /** `"Lastname & Lastname 2015"` or `"Lastname et al. 2015"`. */
  def shortCite: String =
    val auth = SourceRef.shortAuthors(authors)
    val yr = year.map(_.toString).getOrElse("n.d.")
    if auth.isEmpty then yr else s"$auth $yr"

object SourceRef:
  /** Extract a surname from an author string, handling both `"Firstname Lastname"` and `"Lastname, Firstname"` forms.
    */
  def surname(full: String): String =
    val t = full.trim
    if t.contains(",") then t.takeWhile(_ != ',').trim
    else t.split("\\s+").lastOption.getOrElse(t)

  /** `"Lastname & Lastname"` or `"Lastname et al."` from a properly typed author list. */
  def shortAuthors(authors: List[String]): String =
    val names = authors.map(_.trim).filter(_.nonEmpty)
    names match
      case Nil           => ""
      case a :: Nil      => surname(a)
      case a :: b :: Nil => surname(a) + " & " + surname(b)
      case a :: _        => surname(a) + " et al."
