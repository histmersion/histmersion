package com.histmersion.api.catalogue
import com.histmersion.api.{OffsetPage, TimeSpan, YearFmt}
import com.histmersion.api.Markdown.*

object EventsMarkdown:

  type TimeSpanResult = TimeSpanResult.T
  object TimeSpanResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EventLineResult = EventLineResult.T
  object EventLineResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EventListResult = EventListResult.T
  object EventListResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EventScaleResult = EventScaleResult.T
  object EventScaleResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EventResult = EventResult.T
  object EventResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EventPictures = EventPictures.T
  object EventPictures extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  private[api] def timeSpan(ts: TimeSpan): com.histmersion.api.catalogue.EventsMarkdown.TimeSpanResult = YearFmt.spanLong(ts)

  private[api] def eventLine(e: EventSummary): com.histmersion.api.catalogue.EventsMarkdown.EventLineResult =
    val ts = timeSpan(e.timeSpan)
    val ck = e.citekey.map(c => s" [[$c]]").getOrElse("")
    val prec = EventPrecision.label(e.precision).fold("")(l => s" · $l")
    _root_.com.histmersion.api.catalogue.EventsMarkdown.EventLineResult(s"- **${e.label}** `${e.id}` _(${e.kind} · ${e.scale} · ${e.historicity} · $ts$prec)_$ck")

  def eventList(result: EventList): com.histmersion.api.catalogue.EventsMarkdown.EventListResult =
    OffsetPage.markdown(result.page, _root_.com.histmersion.api.OffsetPage.MarkdownNoun("event(s)"), _root_.com.histmersion.api.OffsetPage.MarkdownEmpty("_No matching events._"), result.events)(eventLine)

  private[api] def eventScale(e: EventSummary): com.histmersion.api.catalogue.EventsMarkdown.EventScaleResult =
    s"${e.scale} (${if e.scaleStated then "stated" else "derived"})"

  /** An event's page; `pictures` are its pictures' lines, as the page that shows them renders them. */
  def event(d: EventCard, pictures: List[com.histmersion.api.catalogue.EventsMarkdown.EventPictures] = Nil): com.histmersion.api.catalogue.EventsMarkdown.EventResult =
    val e = d.event
    val header = s"# ${e.label}\n\n_`${e.id}` · ${e.kind} · ${e.historicity} · ${timeSpan(e.timeSpan)}_"
    val desc = e.description.map(d => s"\n\n$d").getOrElse("")
    val ck = e.citekey.map(c => s"  \n**Primary source:** [[$c]]").getOrElse("")
    val place = e.placeId.map(p => s"  \n**Place:** `$p`").getOrElse("")
    val scale = s"  \n**Scale:** ${eventScale(e)}"
    val dating = (e.datingNote.toList ++ EventPrecision
      .label(e.precision)
      .map(l => if e.precision == EventPrecision.Imprecise then s"$l — listed, not drawn on the map at a year" else l))
      .mkString(" · ")
    val datingLine = if dating.isEmpty then "" else s"  \n**Dating:** $dating"
    val corrections = if d.corrections.isEmpty then ""
    else
      "\n\n**Corrected by hand** (kept over the catalogue on a re-import)\n" +
        d.corrections.map(c => s"- ${Corrections.describe(c)}").mkString("\n")
    val geom = d.geometry
      .map(g => s"  \n**Geometry:** ${g.take(40).takeWhile(_ != ',')}… (GeoJSON)")
      .getOrElse("")
    val parent = d.parent.map(p => s"\n\n**Part of:** ${p.label} `${p.id}`").getOrElse("")
    val children = if d.children.isEmpty then "" else "\n\n**Sub-events**\n" + d.children.map(eventLine).mkString("\n")
    val datings = if d.datings.isEmpty then ""
    else
      "\n\n**Datings**\n" + d.datings
        .map { dt =>
          val method = dt.method.map(m => s" [$m]").getOrElse("")
          val note = dt.note.map(n => s" — $n").getOrElse("")
          s"- [[${dt.citekey}]]$method: ${timeSpan(dt.timeSpan)}$note"
        }
        .mkString("\n")
    val edges = if d.edges.isEmpty then ""
    else
      "\n\n**Event edges**\n" + d.edges
        .map { eg =>
          val note = eg.note.map(n => s" — $n").getOrElse("")
          s"- `${eg.srcId}` →_${eg.kind}_ `${eg.dstId}` [[${eg.citekey}]]$note"
        }
        .mkString("\n")
    val parts = if d.participants.isEmpty then ""
    else
      "\n\n**Participants**\n" + d.participants
        .map { p =>
          val ck = p.citekey.map(c => s" [[$c]]").getOrElse("")
          s"- ${p.actor} _(${p.role})_$ck"
        }
        .mkString("\n")
    val wd = d.wikidata.map(q => s"  \n**Wikidata:** $q").getOrElse("")
    val media = if pictures.isEmpty then "" else "\n\n**Pictures**\n" + pictures.mkString("\n")
    _root_.com.histmersion.api.catalogue.EventsMarkdown.EventResult(header + desc + ck + place + scale + datingLine + wd + geom + parent + children + datings + edges + parts + media +
      corrections +
      EventLinksMarkdown.context(d))
