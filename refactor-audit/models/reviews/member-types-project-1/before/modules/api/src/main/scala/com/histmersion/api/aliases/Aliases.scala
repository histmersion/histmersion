package com.histmersion.api.aliases
import com.histmersion.api.given

object Aliases:

  val Rule: String =
    "The old one stays an alias: every read and every write that names it (put, patch, delete, rename, a " +
      "reference) goes to the record under its new one, and the answer names the new one."

  def rule(k: AliasKind): String =
    Rule.replaceFirst("The old one", s"The old ${k.idWord.value}").replace("new one", s"new ${k.idWord.value}")

  def followed(k: AliasKind, asked: String, now: String): Option[String] =
    val a = asked.trim
    Option.when(a.nonEmpty && a != now)(
      s"_`$a` is an old ${k.idWord.value} of this ${k.noun.value} — it went to `$now`._"
    )

  def note(k: AliasKind, asked: String, now: String)(md: String): String =
    followed(k, asked, now).fold(md)(n => s"$n\n\n$md")
