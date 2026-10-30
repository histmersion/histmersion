package com.histmersion.api

import com.histmersion.values.Citekey

object Rename:

  def validate(oldCk: String, newRaw: String): Either[ApiError, Citekey] =
    Citekey.of(newRaw).flatMap { ck =>
      if ck.value == oldCk.trim then
        Left(ApiError(s"'$ck' is already the citekey of that source", Some(Write.CodeInvalid)))
      else if !ArticleLinks.isCitekeyShaped(ck.value) then
        Left(
          ApiError(
            s"'$ck' is a valid citekey but not a LINK-shaped one — `[[$ck]]` would resolve to an " +
              "article, not the source. Use lower-case letters, digits and '_' with a four-digit year or an " +
              "underscore (e.g. page1959_history)",
            Some(Write.CodeInvalid)
          )
        )
      else Right(ck)
    }

  def shortForms(ck: String): List[String] =
    ck.indices.filter(i => i > 0 && ck.charAt(i) == '_').map(ck.take(_)).toList

  def findingId(id: String, oldCk: String, newCk: String): Option[String] =
    Option.when(id.startsWith(oldCk + "#"))(newCk + id.drop(oldCk.length))

  def replaceToken(text: String, oldCk: String, newCk: String): String =
    val token = s"(?<![a-z0-9_.-])${java.util.regex.Pattern.quote(oldCk)}(?![a-z0-9_.-])".r
    token.replaceAllIn(text, java.util.regex.Matcher.quoteReplacement(newCk))
