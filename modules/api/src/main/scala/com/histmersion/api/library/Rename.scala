package com.histmersion
package api.library
import com.histmersion.api.text.WikiLinks
import com.histmersion.api.Write
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.api.Api
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import io.github.iltotore.iron.constraint.any.Pure

object Rename:
  type ShortForm = ShortForm.T
  object ShortForm extends Newtype[String, Pure]


  def shortForms(ck: Citekey): List[ShortForm] =
    ((ck).value.indices.filter(i => i > 0 && (ck).value.charAt(i) == '_').map((ck).value.take(_)).toList).map(refactorValue => ShortForm(refactorValue))

  def findingId(id: Finding.Id, oldCk: Citekey, newCk: Citekey): Option[Finding.Id] =
    Option.when((id).value.startsWith((oldCk).value + "#"))(Finding.Id((newCk).value + (id).value.drop((oldCk).value.length)))

  def replaceToken(text: String, oldCk: Citekey, newCk: Citekey): String =
    val token = s"(?<![a-z0-9_.-])${java.util.regex.Pattern.quote((oldCk).value)}(?![a-z0-9_.-])".r
    token.replaceAllIn(text, java.util.regex.Matcher.quoteReplacement((newCk).value))

  final case class Count(table: Count.Table, rows: Count.Rows) derives KindlingsCodecAsObject, KindlingsSchema, com.histmersion.api.json.Schema

  object Count:
    type Table = Table.T
    object Table extends Newtype[String, Pure]
    type Rows = Rows.T
    object Rows extends Newtype[Int, Pure]


  final case class Counts(
      findings: Counts.Findings,
      articles: List[Counts.Articles],
      sources: List[Counts.Sources],
      links: Counts.Links,
      columns: List[Count],
      edgesPromoted: Counts.EdgesPromoted
  ) derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema

  object Counts:
    type Findings = Findings.T
    object Findings extends Newtype[Int, Pure]
    type Articles = Articles.T
    object Articles extends Newtype[String, Pure]
    type Sources = Sources.T
    object Sources extends Newtype[String, Pure]
    type Links = Links.T
    object Links extends Newtype[Int, Pure]
    type EdgesPromoted = EdgesPromoted.T
    object EdgesPromoted extends Newtype[Int, Pure]


  final case class Request(newCitekey: Citekey) derives KindlingsCodecAsObject, KindlingsSchema, com.histmersion.api.json.Schema
