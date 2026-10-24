package com.histmersion.api

/** Counted nouns for the Markdown renderers: `1 article`, `2 articles`, `0 events` — never
  * `1 articles`. A regular noun takes `s`; an irregular one passes its plural. Pure. */
object Plural:

  def noun(n: Long, singular: String, plural: String = ""): String =
    if n == 1 then singular else if plural.nonEmpty then plural else singular + "s"

  /** `n` and the noun that agrees with it. */
  def count(n: Long, singular: String, plural: String = ""): String = s"$n ${noun(n, singular, plural)}"
