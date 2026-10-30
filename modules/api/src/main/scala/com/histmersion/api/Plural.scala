package com.histmersion.api

object Plural:

  def noun(n: Long, singular: String, plural: String = ""): String =
    if n == 1 then singular else if plural.nonEmpty then plural else singular + "s"

  def count(n: Long, singular: String, plural: String = ""): String = s"$n ${noun(n, singular, plural)}"
