package com.histmersion.api

import sttp.tapir.*

final case class OffsetPage(total: com.histmersion.api.RowCount, offset: com.histmersion.api.Offset, shown: com.histmersion.api.RowCount):
  def next: Option[com.histmersion.api.Offset] = (Option.when(shown.value > 0 && offset.value + shown.value < total.value)(offset.value + shown.value)).map(com.histmersion.api.Offset.apply)

  def isAll: Boolean = (offset.value == 0 && shown.value >= total.value)

object OffsetPage:
  type FullSetMax = FullSetMaxValue
  type FullSetMaxValue = FullSetMaxValue.T
  object FullSetMaxValue extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MarkdownResult = MarkdownResult.T
  object MarkdownResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  val FullSetMax: com.histmersion.api.OffsetPage.FullSetMax = _root_.com.histmersion.api.OffsetPage.FullSetMaxValue(500)

  def header(p: OffsetPage, noun: String): String =
    if (p.isAll) then (s"### ${math.max(p.total.value, p.shown.value)} $noun")
    else (s"### ${p.shown.value} of ${p.total.value} $noun")

  def footer(p: OffsetPage): String =
    if ((p.isAll) == (true)) || p.shown.value == 0 then ("")
    else
      val next = p.next.fold("")(n => s" — next: offset=$n")
      val full = if p.total.value <= FullSetMax then s" — full set: limit=${p.total.value}" else ""
      (s"\n\n_showing ${p.offset.value + 1}–${p.offset.value + p.shown.value} of ${p.total.value}$next${full}_")

  def markdown[A](p: OffsetPage, noun: String, empty: String, items: List[A])(line: A => String): com.histmersion.api.OffsetPage.MarkdownResult =
    if items.isEmpty then
      p.offset.value match
        case 0 => _root_.com.histmersion.api.OffsetPage.MarkdownResult((empty))
        case o => _root_.com.histmersion.api.OffsetPage.MarkdownResult(s"$empty\n\n_offset=$o is past the end: there are ${p.total.value}_")
    else _root_.com.histmersion.api.OffsetPage.MarkdownResult(s"${header(p, ((noun)))}\n\n${items.map(line).mkString("\n")}${footer(p)}")
