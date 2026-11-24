package com.histmersion
package api.library

import com.histmersion.util.problem.Text.sentences
import com.histmersion.api.{Paging, Plural}
import com.histmersion.api.automation.ListFilters
import com.histmersion.api.catalogue.{PeriodsMarkdown, SkippedPeriod}
import com.histmersion.api.exchange.InitPlan
import com.histmersion.api.jobs.{JobDetail, JobInfo, JobState}
import com.histmersion.api.maintenance.{Vacuum, VacuumPlan}
import com.histmersion.api.media.{AssetInfo, AssetsMarkdown}
import com.histmersion.api.given
import com.histmersion.api.RowCount
import com.histmersion.lint.{LintRule, tolerated}
@tolerated(LintRule.G9PrefixRole, "pending G9 (namespaces, not prefixes)")
object IngestMarkdown:

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def job(j: JobInfo): String =
    val lines = List(
      Some(s"- **status:** ${j.status}"),
      j.citekey.map(_.value.toString).map(c => s"- **citekey:** $c"),
      j.progress.filter(_.value.nonEmpty).map(p => s"- **progress:** $p"),
      // every problem the job keeps: one on its `error` line, several each on a line of its own under `errors`
      j.problems.map(_.sentences.filter(_.nonEmpty)).filter(_.nonEmpty).map {
        case Vector(one) => s"- **error:** $one"
        case several     => s"- **errors:**\n${several.map(s => s"  - $s").mkString("\n")}"
      },
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
        .filter(d => SkippedPeriod.SkippedCount.of(d).value > 0)
        .map(d =>
          s"- **skipped:** ${Plural.count(Plural.CountN(SkippedPeriod.SkippedCount.of(d).value.toLong), Plural.CountSingular("period definition"))} (not imported)" +
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
    (s"### job ${j.id} · ${j.kind}\n\n${lines.mkString("\n")}")

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def jobs(
      js: Vector[JobInfo],
      page: Paging.Page = Paging.Page.empty,
      status: Option[Ingest.JobStatus] = None,
      older: Option[Ingest.JobAge] = None
  ): String =
    if js.isEmpty then (status.fold("_No jobs._")(s => s"_No $s jobs._"))
    else
      val rows = js.map(j =>
        s"| ${j.id} | ${j.kind} | ${JobDetail.subject(j).getOrElse("")} | ${j.status} | " +
          s"${j.progress.getOrElse("")}${j.problems.fold("")(ps => s" — ${ps.sentences.mkString("; ")}")} |"
      )
      val kinds = ListFilters.facetLine(
        ListFilters.FacetName("By kind"),
        page.facet(Facet.Name("kind")).map((t, n) => (ListFilters.Term(t.value), RowCount(n.value)))
      )
      (List(
        s"### ${Paging.heading(Paging.ShownCount(js.size), page.total, Plural.Noun.Singular("job"), qualifier = status.map(_.value))}",
        ""
      ) ++
        Option.when(kinds.value.nonEmpty)(List(kinds.value, "")).toList.flatten ++
        List("| id | kind | subject | status | detail |", "|---|---|---|---|---|") ++ rows ++ older.toList.flatMap(o =>
          List("", o)
        ))
        .mkString("\n")

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def assets(citekey: Citekey, as: Vector[AssetInfo]): String = AssetsMarkdown.assets(citekey, as)
