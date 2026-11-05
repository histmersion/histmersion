package com.histmersion.api

import sttp.tapir.*

final case class OffsetPage(total: com.histmersion.api.RowCount, offset: com.histmersion.api.Offset, shown: com.histmersion.api.RowCount):
  def next: Option[com.histmersion.api.Offset] = (Option.when(shown > 0 && offset + shown < total)(offset + shown)).map(com.histmersion.api.Offset.apply)

  def isAll: Boolean = offset == 0 && shown >= total

object OffsetPage:
  val FullSetMax: Int = 500

  def header(p: OffsetPage, noun: String): String =
    if p.isAll then s"### ${math.max(p.total, p.shown)} $noun"
    else s"### ${p.shown} of ${p.total} $noun"

  def footer(p: OffsetPage): String =
    if p.isAll || p.shown == 0 then ""
    else
      val next = p.next.fold("")(n => s" — next: offset=$n")
      val full = if p.total <= FullSetMax then s" — full set: limit=${p.total}" else ""
      s"\n\n_showing ${p.offset + 1}–${p.offset + p.shown} of ${p.total}$next${full}_"

  def markdown[A](p: OffsetPage, noun: String, empty: String, items: List[A])(line: A => String): String =
    if items.isEmpty then
      p.offset match
        case 0 => empty
        case o => s"$empty\n\n_offset=$o is past the end: there are ${p.total}_"
    else s"${header(p, noun)}\n\n${items.map(line).mkString("\n")}${footer(p)}"
