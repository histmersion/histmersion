package com.histmersion.api

import sttp.tapir.*

final case class OffsetPage(total: com.histmersion.api.RowCount, offset: com.histmersion.api.Offset, shown: com.histmersion.api.RowCount):
  def next: Option[com.histmersion.api.Offset] = (Option.when(shown.value > 0 && offset.value + shown.value < total.value)(offset.value + shown.value)).map(com.histmersion.api.Offset.apply)

  def isAll: com.histmersion.api.OffsetPage.IsAllResult = _root_.com.histmersion.api.OffsetPage.IsAllResult(offset.value == 0 && shown.value >= total.value)

object OffsetPage:
  type IsAllResult = IsAllResult.T
  object IsAllResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FullSetMax = FullSetMax.T
  object FullSetMax extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type HeaderResult = HeaderResult.T
  object HeaderResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type HeaderNoun = HeaderNoun.T
  object HeaderNoun extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FooterResult = FooterResult.T
  object FooterResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MarkdownResult = MarkdownResult.T
  object MarkdownResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MarkdownNoun = MarkdownNoun.T
  object MarkdownNoun extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MarkdownEmpty = MarkdownEmpty.T
  object MarkdownEmpty extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MarkdownLineResult = MarkdownLineResult.T
  object MarkdownLineResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  val FullSetMax: com.histmersion.api.OffsetPage.FullSetMax = _root_.com.histmersion.api.OffsetPage.FullSetMax(500)

  def header(p: OffsetPage, noun: com.histmersion.api.OffsetPage.HeaderNoun): com.histmersion.api.OffsetPage.HeaderResult =
    if (p.isAll).value then _root_.com.histmersion.api.OffsetPage.HeaderResult(s"### ${math.max(p.total.value, p.shown.value)} $noun")
    else _root_.com.histmersion.api.OffsetPage.HeaderResult(s"### ${p.shown.value} of ${p.total.value} $noun")

  def footer(p: OffsetPage): com.histmersion.api.OffsetPage.FooterResult =
    if p.isAll || p.shown.value == 0 then _root_.com.histmersion.api.OffsetPage.FooterResult("")
    else
      val next = p.next.fold("")(n => s" — next: offset=$n")
      val full = if p.total.value <= FullSetMax then s" — full set: limit=${p.total.value}" else ""
      _root_.com.histmersion.api.OffsetPage.FooterResult(s"\n\n_showing ${p.offset.value + 1}–${p.offset.value + p.shown.value} of ${p.total.value}$next${full}_")

  def markdown[A](p: OffsetPage, noun: com.histmersion.api.OffsetPage.MarkdownNoun, empty: com.histmersion.api.OffsetPage.MarkdownEmpty, items: List[A])(line: A => com.histmersion.api.OffsetPage.MarkdownLineResult): com.histmersion.api.OffsetPage.MarkdownResult =
    if items.isEmpty then
      p.offset.value match
        case 0 => empty
        case o => _root_.com.histmersion.api.OffsetPage.MarkdownResult(s"$empty\n\n_offset=$o is past the end: there are ${p.total.value}_")
    else s"${header(p, noun)}\n\n${items.map(line).mkString("\n")}${footer(p)}"
