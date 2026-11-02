package com.histmersion.api.library
import com.histmersion.api.text.WikiLinks
import com.histmersion.domain.library.Citekey
import com.histmersion.api.Write
import com.histmersion.api.given
import com.histmersion.api.library.of
import com.histmersion.api.Api
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

object Rename:

  def validate(oldCk: String, newRaw: String): Either[Api.Error, Citekey] =
    Citekey.of(newRaw).flatMap { ck =>
      if ck.value == oldCk.trim then
        Left(Api.Error(s"'$ck' is already the citekey of that source", Some(Write.CodeInvalid)))
      else if !WikiLinks.isCitekeyShaped(ck.value) then
        Left(
          Api.Error(
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

  final case class Count(table: String, rows: Int) derives KindlingsCodecAsObject, KindlingsSchema
  object Count:
    given Schema[Count] = summon[KindlingsSchema[Count]].schema

  final case class Counts(
      findings: Int,
      articles: List[String],
      sources: List[String],
      links: Int,
      columns: List[Count],
      edgesPromoted: Int
  ) derives KindlingsCodecAsObject,
        KindlingsSchema
  object Counts:
    given Schema[Counts] = summon[KindlingsSchema[Counts]].schema

  final case class Request(newCitekey: String) derives KindlingsCodecAsObject, KindlingsSchema
  object Request:
    given Schema[Request] = summon[KindlingsSchema[Request]].schema
