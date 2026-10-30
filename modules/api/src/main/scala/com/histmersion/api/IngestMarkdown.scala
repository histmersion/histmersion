package com.histmersion.api

/** Rendering of the ingest surface — kept out of [[Markdown]] so concurrent streams do not all append to the same file.
  * Same contract: every surface answers Markdown.
  */
object IngestMarkdown:

  def job(j: JobInfo): String =
    val lines = List(
      Some(s"- **status:** ${j.status}"),
      j.citekey.map(c => s"- **citekey:** $c"),
      j.progress.filter(_.nonEmpty).map(p => s"- **progress:** $p"),
      j.error.filter(_.nonEmpty).map(e => s"- **error:** $e"),
      j.sha256.map(s => s"- **sha256:** ${s.take(12)}…"),
      j.acquireItem.map(i =>
        s"- **acquisition item:** $i" + (j.state match
          case JobState.Done   => "" // the progress line says what happened
          case JobState.Failed => " (left open: the job failed)"
          case _               => " (ticked off when the job succeeds)")
      ),
      j.detail.flatMap(_.init).map(p => s"- **imports:** ${InitPlan.describe(p)}"),
      j.detail.flatMap(_.vacuum).map(p => s"- **vacuums:** ${Vacuum.describe(p)}"),
      j.detail.flatMap(_.about).map(a => s"- **does:** $a"),
      j.detail
        .filter(_.skippedPeriodCount > 0)
        .map(d =>
          s"- **skipped:** ${Plural.count(d.skippedPeriodCount, "period definition")} (not imported)" +
            (if d.skippedPeriods.isEmpty then ""
             else "\n" + PeriodsMarkdown.skippedList(d.skippedPeriods).linesIterator.map("  " + _).mkString("\n"))
        ),
      Some(s"- **updated:** ${j.updatedAt}")
    ).flatten
    s"### job ${j.id} · ${j.kind}\n\n${lines.mkString("\n")}"

  /** A page of jobs ([[Paging]]): headed `37 jobs`, or `Latest 20 of 2,431 failed jobs` when the page is not the whole
    * list, with the `older` line (how to ask for the next page) under the table.
    */
  def jobs(
      js: Vector[JobInfo],
      page: Paging.Page = Paging.Page.empty,
      status: Option[String] = None,
      older: Option[String] = None
  ): String =
    if js.isEmpty then status.fold("_No jobs._")(s => s"_No $s jobs._")
    else
      val rows = js.map(j =>
        s"| ${j.id} | ${j.kind} | ${JobDetail.subject(j).getOrElse("")} | ${j.status} | " +
          s"${j.progress.getOrElse("")}${j.error.map(e => s" — $e").getOrElse("")} |"
      )
      val kinds = ListFilters.facetLine("By kind", page.facet("kind"))
      (List(s"### ${Paging.heading(js.size, page.total, "job", qualifier = status)}", "") ++
        Option.when(kinds.nonEmpty)(List(kinds, "")).toList.flatten ++
        List("| id | kind | subject | status | detail |", "|---|---|---|---|---|") ++ rows ++ older.toList.flatMap(o =>
          List("", o)
        ))
        .mkString("\n")

  def assets(citekey: String, as: Vector[AssetInfo]): String =
    if as.isEmpty then s"_No assets stored for **$citekey**._"
    else
      val rows = as.map { a =>
        val size = (a.width, a.height) match
          case (Some(w), Some(h)) => s"$w×$h"
          case _                  => "—"
        s"| ${a.name} | ${a.mime} | $size | ${a.bytes} | `${AssetLinks.url(citekey, a.name)}` |"
      }
      (List(
        s"### ${as.size} asset(s) · $citekey",
        "",
        "| name | mime | size | bytes | url |",
        "|---|---|---|---:|---|"
      ) ++ rows).mkString("\n")
