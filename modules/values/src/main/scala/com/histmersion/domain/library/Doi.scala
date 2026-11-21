package com.histmersion
package domain.library
import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.string.Match

type Doi = Doi.T

object Doi extends Newtype[String, Match["10\\.[0-9]+/\\S+"]]:
  private val Core = """10\.\d{4,9}/[-._;()/:A-Za-z0-9<>\[\]]+""".r

  def normalize(raw: String): Option[Doi] =
    val s = Option(raw)
      .getOrElse("")
      .trim
      .replaceFirst("(?i)^https?://(dx\\.)?doi\\.org/", "")
      .replaceFirst("(?i)^doi:\\s*", "")
      .trim
    Core
      .findFirstIn(s)
      .flatMap(d =>
        Doi.option(
          d.stripSuffix(".")
            .stripSuffix(",")
            .stripSuffix(";")
            .stripSuffix(")")
            .stripSuffix("}")
            .stripSuffix("|")
            .toLowerCase
        )
      )
