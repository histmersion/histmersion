package com.histmersion
package api

import sttp.tapir.*
import io.github.iltotore.iron.constraint.any.Pure

final case class OffsetPage(total: RowCount, offset: Offset, shown: RowCount):
  def next: Option[Offset] = (Option
    .when(shown.value > 0 && offset.value + shown.value < total.value)(offset.value + shown.value))
    .map(Offset.apply)

  def isAll: Boolean = (offset.value == 0 && shown >= total)

object OffsetPage:
  type FullSetMax = FullSetMaxValue
  type FullSetMaxValue = FullSetMaxValue.T
  object FullSetMaxValue extends Newtype[Int, Pure]
  type MarkdownResult = MarkdownResult.T
  object MarkdownResult extends Newtype[String, Pure]

  val FullSetMax: FullSetMax = FullSetMaxValue(500)

  def header(p: OffsetPage, noun: String): String =
    if p.isAll then (s"### ${math.max(p.total.value, p.shown.value)} $noun")
    else (s"### ${p.shown.value} of ${p.total.value} $noun")

  def footer(p: OffsetPage): String =
    if ((p.isAll) == (true)) || p.shown.value == 0 then ("")
    else
      val next = p.next.fold("")(n => s" — next: offset=$n")
      val full = if p.total.value <= FullSetMax.value then s" — full set: limit=${p.total.value}" else ""
      (s"\n\n_showing ${p.offset.value + 1}–${p.offset.value + p.shown.value} of ${p.total.value}$next${full}_")

  def markdown[A](p: OffsetPage, noun: String, empty: String, items: List[A])(line: A => String): MarkdownResult =
    if items.isEmpty then
      p.offset.value match
        case 0 => MarkdownResult((empty))
        case o => MarkdownResult(s"$empty\n\n_offset=$o is past the end: there are ${p.total.value}_")
    else MarkdownResult(s"${header(p, ((noun)))}\n\n${items.map(line).mkString("\n")}${footer(p)}")
