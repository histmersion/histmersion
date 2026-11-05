package com.histmersion.cli

/** A corpus source as the audit sees it: the Calibre entry it names and what the corpus says. */
final case class AuditRecord(citekey: String, calibreId: Int, title: String, authors: List[String], year: Option[Int])
