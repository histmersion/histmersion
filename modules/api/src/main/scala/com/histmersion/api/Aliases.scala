package com.histmersion.api

/** THE rule for an old identifier, the same for every kind (#1799, decided 2026-10-16: "follow the alias everywhere").
  * A renamed record keeps its old identifier as an alias, and every READ and every WRITE that names the old one — a
  * put, a patch, a delete, a rename, a reference from an attestation or a link — goes to the record under its current
  * identifier; the answer names the current one. A put to an old identifier therefore REPLACES the renamed record; it
  * never creates a second one under the old name. The one exception is IMPORTING a note (a source's or an article's)
  * filed under an old identifier: such a file is a stale copy the export leaves behind, so the import is refused with
  * the current identifier instead of overwriting the renamed record. Pure: the server resolves the identifier
  * ([[AliasKind]] + the alias tables) and these texts word it.
  */
object Aliases:

  /** The one description of what a rename leaves behind — for every rename tool, command and endpoint. */
  val Rule: String =
    "The old one stays an alias: every read and every write that names it (put, patch, delete, rename, a " +
      "reference) goes to the record under its new one, and the answer names the new one."

  /** `Rule` for one kind, with its identifier's name: "The old slug stays an alias: …". */
  def rule(k: AliasKind): String =
    Rule.replaceFirst("The old one", s"The old ${k.idWord}").replace("new one", s"new ${k.idWord}")

  /** What an answer says when a call named an old identifier: `` `old` is an old slug of this goal — it went to `new`.
    * ``; `None` when the identifier asked for is the current one.
    */
  def followed(k: AliasKind, asked: String, now: String): Option[String] =
    val a = asked.trim
    Option.when(a.nonEmpty && a != now)(s"_`$a` is an old ${k.idWord} of this ${k.noun} — it went to `$now`._")

  /** `md` with the [[followed]] line on top, when there is one. */
  def note(k: AliasKind, asked: String, now: String)(md: String): String =
    followed(k, asked, now).fold(md)(n => s"$n\n\n$md")
