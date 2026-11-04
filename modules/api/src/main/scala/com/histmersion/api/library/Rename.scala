package com.histmersion.api.library
import com.histmersion.api.text.WikiLinks
import com.histmersion.api.Write
import com.histmersion.api.given
import com.histmersion.api.Api
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

object Rename:

  def shortForms(ck: String): List[String] =
    ck.indices.filter(i => i > 0 && ck.charAt(i) == '_').map(ck.take(_)).toList

  def findingId(id: String, oldCk: String, newCk: String): Option[String] =
    Option.when(id.startsWith(oldCk + "#"))(newCk + id.drop(oldCk.length))

  def replaceToken(text: String, oldCk: String, newCk: String): String =
    val token = s"(?<![a-z0-9_.-])${java.util.regex.Pattern.quote(oldCk)}(?![a-z0-9_.-])".r
    token.replaceAllIn(text, java.util.regex.Matcher.quoteReplacement(newCk))

  final case class Count(table: String, rows: Int) derives KindlingsCodecAsObject, KindlingsSchema, com.histmersion.api.json.Schema


  final case class Counts(
      findings: Int,
      articles: List[String],
      sources: List[String],
      links: Int,
      columns: List[Count],
      edgesPromoted: Int
  ) derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema


  final case class Request(newCitekey: String) derives KindlingsCodecAsObject, KindlingsSchema, com.histmersion.api.json.Schema
