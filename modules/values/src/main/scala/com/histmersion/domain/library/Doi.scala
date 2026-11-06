package com.histmersion.domain.library
import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.string.Match

/** A DOI as the registry writes it after `https://doi.org/`: `10.<registrant>/<suffix>`. */
type Doi = Doi.T

object Doi extends RefinedType[String, Match["10\\.[0-9]+/\\S+"]]:
  private val Core = """10\.\d{4,9}/[-._;()/:A-Za-z0-9<>\[\]]+""".r

  /** The DOI a text gives (bare, `doi:`, or as a resolver URL), lower case — DOIs are case-insensitive — and without
    * the punctuation a sentence leaves after it; `None` when it gives none.
    */
  def normalize(raw: String): Option[Doi] =
    val s = Option(raw)
      .getOrElse("")
      .trim
      .replaceFirst("(?i)^https?://(dx\\.)?doi\\.org/", "")
      .replaceFirst("(?i)^doi:\\s*", "")
      .trim
    Core
      .findFirstIn(s)
      .flatMap(
        d => Doi.option(d.stripSuffix(".")
          .stripSuffix(",")
          .stripSuffix(";")
          .stripSuffix(")")
          .stripSuffix("}")
          .stripSuffix("|")
          .toLowerCase)
      )
