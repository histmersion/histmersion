package com.histmersion.api.library
import com.histmersion.api.text.WikiLinks
import com.histmersion.api.Write
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.api.Api
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

object Rename:
  type ShortForm = ShortForm.T
  object ShortForm extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]


  def shortForms(ck: com.histmersion.api.library.Citekey): List[com.histmersion.api.library.Rename.ShortForm] =
    ((ck).value.indices.filter(i => i > 0 && (ck).value.charAt(i) == '_').map((ck).value.take(_)).toList).map(refactorValue => _root_.com.histmersion.api.library.Rename.ShortForm(refactorValue))

  def findingId(id: com.histmersion.api.library.Finding.Id, oldCk: com.histmersion.api.library.Citekey, newCk: com.histmersion.api.library.Citekey): Option[com.histmersion.api.library.Finding.Id] =
    Option.when((id).value.startsWith((oldCk).value + "#"))(_root_.com.histmersion.api.library.Finding.Id((newCk).value + (id).value.drop((oldCk).value.length)))

  def replaceToken(text: String, oldCk: com.histmersion.api.library.Citekey, newCk: com.histmersion.api.library.Citekey): String =
    val token = s"(?<![a-z0-9_.-])${java.util.regex.Pattern.quote((oldCk).value)}(?![a-z0-9_.-])".r
    token.replaceAllIn(text, java.util.regex.Matcher.quoteReplacement((newCk).value))

  final case class Count(table: Count.Table, rows: Count.Rows) derives KindlingsCodecAsObject, KindlingsSchema, com.histmersion.api.json.Schema

  object Count:
    type Table = Table.T
    object Table extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Rows = Rows.T
    object Rows extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]


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
    object Findings extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Articles = Articles.T
    object Articles extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Sources = Sources.T
    object Sources extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Links = Links.T
    object Links extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type EdgesPromoted = EdgesPromoted.T
    object EdgesPromoted extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]


  final case class Request(newCitekey: com.histmersion.api.library.Citekey) derives KindlingsCodecAsObject, KindlingsSchema, com.histmersion.api.json.Schema
