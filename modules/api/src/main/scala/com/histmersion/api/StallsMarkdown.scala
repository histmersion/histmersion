package com.histmersion.api

/** The stall report as Markdown — every surface prints this. Pure. */
object StallsMarkdown:
  import Stalls.*

  def report(r: StallReport): String =
    val head = s"# Stalled goals: ${r.stalled.size} of ${r.goals} with no progress in ${r.days} day(s)"
    val reasons =
      if r.byReason.isEmpty then "" else "\n\n" + r.byReason.map(x => s"${x.code} ${x.count}").mkString(" · ")
    val rows = if r.stalled.isEmpty then "\n\n_Every unfinished goal progressed._"
    else
      "\n\n" + r.stalled
        .map { s =>
          s"- **${s.slug}** _(${s.status})_ — ${s.idleDays.fold("never progressed")(d => s"idle $d day(s)")}: _${s.code}_ — ${s.why}\n" +
            s"  - unblock: `${s.action}`"
        }
        .mkString("\n")
    head + reasons + rows
