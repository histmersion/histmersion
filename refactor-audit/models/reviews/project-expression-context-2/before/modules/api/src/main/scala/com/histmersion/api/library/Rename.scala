package com.histmersion.api.library
import com.histmersion.api.text.WikiLinks
import com.histmersion.api.Write
import com.histmersion.api.given
import com.histmersion.api.Api
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

object Rename:

  type ShortFormsResult = ShortFormsResult.T
  object ShortFormsResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ShortFormsCk = ShortFormsCk.T
  object ShortFormsCk extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FindingIdResult = FindingIdResult.T
  object FindingIdResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FindingIdId = FindingIdId.T
  object FindingIdId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FindingIdOldCk = FindingIdOldCk.T
  object FindingIdOldCk extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FindingIdNewCk = FindingIdNewCk.T
  object FindingIdNewCk extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ReplaceTokenResult = ReplaceTokenResult.T
  object ReplaceTokenResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ReplaceTokenText = ReplaceTokenText.T
  object ReplaceTokenText extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ReplaceTokenOldCk = ReplaceTokenOldCk.T
  object ReplaceTokenOldCk extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ReplaceTokenNewCk = ReplaceTokenNewCk.T
  object ReplaceTokenNewCk extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def shortForms(ck: com.histmersion.api.library.Rename.ShortFormsCk): List[com.histmersion.api.library.Rename.ShortFormsResult] =
    ((ck).value.indices.filter(i => i > 0 && (ck).value.charAt(i) == '_').map((ck).value.take(_)).toList).map(refactorValue => _root_.com.histmersion.api.library.Rename.ShortFormsResult(refactorValue))

  def findingId(id: com.histmersion.api.library.Rename.FindingIdId, oldCk: com.histmersion.api.library.Rename.FindingIdOldCk, newCk: com.histmersion.api.library.Rename.FindingIdNewCk): Option[com.histmersion.api.library.Rename.FindingIdResult] =
    Option.when((id).value.startsWith((oldCk).value + "#"))(_root_.com.histmersion.api.library.Rename.FindingIdResult((newCk).value + (id).value.drop((oldCk).value.length)))

  def replaceToken(text: com.histmersion.api.library.Rename.ReplaceTokenText, oldCk: com.histmersion.api.library.Rename.ReplaceTokenOldCk, newCk: com.histmersion.api.library.Rename.ReplaceTokenNewCk): com.histmersion.api.library.Rename.ReplaceTokenResult =
    val token = s"(?<![a-z0-9_.-])${java.util.regex.Pattern.quote(oldCk)}(?![a-z0-9_.-])".r
    token.replaceAllIn(text, java.util.regex.Matcher.quoteReplacement(newCk))

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
