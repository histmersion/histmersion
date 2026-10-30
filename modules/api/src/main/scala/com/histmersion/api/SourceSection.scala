package com.histmersion.api

object SourceSection:
  val Summary = "summary"
  val Findings = "findings"
  val Notes = "notes"
  val Toc = "toc"
  val Body = "body"
  val All = "all"
  val all: List[String] = List(Summary, Findings, Notes, Toc, Body, All)

  def parse(s: String): Option[String] =
    val lower = s.trim.toLowerCase
    all.find(_ == lower)
