package com.histmersion.api.library

import com.histmersion.api.{Paging, Plural}
import com.histmersion.api.automation.ListFilters
import com.histmersion.api.catalogue.{PeriodsMarkdown, SkippedPeriod}
import com.histmersion.api.exchange.InitPlan
import com.histmersion.api.jobs.{JobDetail, JobInfo, JobState}
import com.histmersion.api.maintenance.{Vacuum, VacuumPlan}
import com.histmersion.api.media.{AssetInfo, AssetsMarkdown}
import com.histmersion.api.given
object IngestMarkdown:

  type JobResult = JobResult.T
  object JobResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type JobsResult = JobsResult.T
  object JobsResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type JobsStatus = JobsStatus.T
  object JobsStatus extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type JobsOlder = JobsOlder.T
  object JobsOlder extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type AssetsResult = AssetsResult.T
  object AssetsResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type AssetsCitekey = AssetsCitekey.T
  object AssetsCitekey extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def job(j: JobInfo): com.histmersion.api.library.IngestMarkdown.JobResult =
    val lines = List(
      Some(s"- **status:** ${j.status}"),
      j.citekey.map(_.value.toString).map(c => s"- **citekey:** $c"),
      j.progress.filter(_.nonEmpty).map(p => s"- **progress:** $p"),
      j.error.filter(_.nonEmpty).map(e => s"- **error:** $e"),
      j.sha256.map(_.value).map(s => s"- **sha256:** ${s.take(12)}…"),
      j.acquireItem.map(i =>
        s"- **acquisition item:** $i" + (j.state match
          case JobState.Done   => ""
          case JobState.Failed => " (left open: the job failed)"
          case _               => " (ticked off when the job succeeds)")
      ),
      j.detail.flatMap(InitPlan.Detail.of).map(p => s"- **imports:** ${InitPlan.describe(p)}"),
      j.detail.flatMap(VacuumPlan.Detail.of).map(p => s"- **vacuums:** ${Vacuum.describe(p)}"),
      j.detail.flatMap(JobDetail.Purpose.of).map(a => s"- **does:** $a"),
      j.detail
        .filter(SkippedPeriod.SkippedCount.of(_) > 0)
        .map(d =>
          s"- **skipped:** ${Plural.count(SkippedPeriod.SkippedCount.of(d), "period definition")} (not imported)" +
            (if SkippedPeriod.Skipped.of(d).isEmpty then ""
             else
               "\n" + PeriodsMarkdown
                 .skippedList(SkippedPeriod.Skipped.of(d))
                 .linesIterator
                 .map("  " + _)
                 .mkString("\n"))
        ),
      Some(s"- **updated:** ${j.updatedAt}")
    ).flatten
    s"### job ${j.id} · ${j.kind}\n\n${lines.mkString("\n")}"

  def jobs(
      js: Vector[JobInfo],
      page: Paging.Page = Paging.Page.empty,
      status: Option[com.histmersion.api.library.IngestMarkdown.JobsStatus] = None,
      older: Option[com.histmersion.api.library.IngestMarkdown.JobsOlder] = None
  ): com.histmersion.api.library.IngestMarkdown.JobsResult =
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

  def assets(citekey: com.histmersion.api.library.IngestMarkdown.AssetsCitekey, as: Vector[AssetInfo]): com.histmersion.api.library.IngestMarkdown.AssetsResult = AssetsMarkdown.assets(citekey, as)
