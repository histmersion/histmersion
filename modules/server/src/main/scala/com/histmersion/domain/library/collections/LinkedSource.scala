package com.histmersion.domain.library.collections

/** A source linked to an item of the Calibre library (`sources.calibre_id`), with what an audit compares. */
final case class LinkedSource(citekey: String, id: String, title: String, authors: List[String], year: Option[Int])
