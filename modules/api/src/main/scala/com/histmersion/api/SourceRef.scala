package com.histmersion.api

import com.histmersion.values.SourceStatus

/** `idLabel` is the DOI when present, else the citekey: the owner's decision to show DOIs in the UI while keeping the
  * internal naming.
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

  def shortCite: String =
    val auth = SourceRef.shortAuthors(authors)
    val yr = year.map(_.toString).getOrElse("n.d.")
    if auth.isEmpty then yr else s"$auth $yr"

object SourceRef:
  def surname(full: String): String =
    val t = full.trim
    if t.contains(",") then t.takeWhile(_ != ',').trim
    else t.split("\\s+").lastOption.getOrElse(t)

  def shortAuthors(authors: List[String]): String =
    val names = authors.map(_.trim).filter(_.nonEmpty)
    names match
      case Nil           => ""
      case a :: Nil      => surname(a)
      case a :: b :: Nil => surname(a) + " & " + surname(b)
      case a :: _        => surname(a) + " et al."
