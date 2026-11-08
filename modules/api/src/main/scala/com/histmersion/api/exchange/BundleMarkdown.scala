package com.histmersion
package api.exchange

import com.histmersion.api.Plural
import com.histmersion.api.aliases.InstanceInfo
import com.histmersion.api.given
import com.histmersion.api.ByteCount
object BundleMarkdown:


  def instance(i: InstanceInfo): String =
    (s"**Instance** ${i.id}" + i.label
      .map(l => s" · $l")
      .getOrElse(" · _(no label — `histmersion bundle instance --label <name>`)_") +
      s"  \n_created ${i.createdAt}_")

  private def who(i: Bundle.Instance): String = (i.label.fold(i.id.toString)(l => s"$l (${i.id})"))

  private def bytes(n: ByteCount): String =
    if (n).value < 1024 then (s"$n B")
    else if (n).value < 1024L * 1024 then (s"${(n).value / 1024} KB")
    else if (n).value < 1024L * 1024 * 1024 then (s"${(n).value / (1024L * 1024)} MB")
    else (s"${(n).value / (1024L * 1024 * 1024)} GB")

  def manifest(m: Bundle.Manifest): String =
    val files = m.files
      .filter(_.lines.value > 0)
      .map(f => s"| ${f.path.value.stripPrefix("entities/").stripSuffix(".ndjson")} | ${f.lines} |")
    val scope = m.since.fold("everything")(s => s"changes after $s")
    List(
      s"**Bundle** ${m.bundleId} · from ${who(m.instance)} · format ${m.formatVersion} · schema V${m.schemaVersion}",
      s"$scope · as of ${m.asOf.getOrElse("—")} · ${Plural.count(Plural.CountN(m.blobs.count.value.toLong), Plural.Noun.Singular("blob"))} (${bytes(m.blobs.bytes)})",
      if files.isEmpty then "_(no records)_" else ("| records | lines |" :: "|---|---:|" :: files).mkString("\n")
    ).mkString("\n\n")

  def verify(r: Bundle.VerifyResult): String =
    val head = if ((r.ok) == Bundle.VerifyResult.Ok(true)) then s"**OK** — every checksum, record and ${Plural.count(Plural.CountN(r.blobsChecked.value.toLong), Plural.CountSingular("blob"))} verified"
    else s"**NOT OK** — ${Plural.count(Plural.CountN((r.problems.size).toLong), Plural.CountSingular("problem"))}"
    (head :: r.manifest.map(manifest).toList ++ problems(("Problems"), r.problems.map(_.value))).mkString("\n\n")

  def importResult(r: Bundle.ImportResult): String =
    val verb =
      if r.problems.nonEmpty then "**Refused** — nothing was written"
      else if ((r.dryRun) == Bundle.ImportResult.DryRun(true)) then "**Dry run** — nothing was written; this is what an import would do"
      else if r.changed == Bundle.ChangedCount(0) then "**Nothing to change** — this instance already has everything in the bundle"
      else
        s"**Imported** — ${Plural.count(Plural.CountN(r.changed.value.toLong), Plural.CountSingular("row"))} changed" + r.mergeId.map(m => s" (merge $m)").getOrElse("")
    val rows = r.counts.map(c =>
      s"| ${c.kind.wire} | ${c.inserted} | ${c.updated} | ${c.deleted} | ${c.unchanged} | ${c.keptLocal} | ${c.conflicts} | ${c.skippedDeleted} | ${c.held} |"
    )
    val table =
      if rows.isEmpty then Nil
      else
        List(
          ("| kind | new | updated | deleted | unchanged | kept here | conflicts | deleted here | held |" ::
            "|---|---:|---:|---:|---:|---:|---:|---:|---:|" :: rows).mkString("\n")
        )
    val issues =
      if r.issues.isEmpty then Nil
      else
        List(
          s"**Issues holding it up** (plan ${r.planHash.getOrElse("—")}; decide them in a resolution file — " +
            "`histmersion merge plan <bundle> --write-template resolutions.yaml`)\n" + r.issues
              .map(MergeMarkdown.issue)
              .mkString("\n")
        )
    (s"$verb · bundle ${r.bundleId} from ${who(r.from)}" :: table ++
      problems(("Problems (fix these, then import again)"), r.problems.map(_.value)) ++ issues ++
      problems("Conflicts (changed on both sides; decided by a resolution)", r.conflicts.map(_.value)) ++
      problems("Notes", r.notes.map(_.value))).mkString("\n\n")

  private def problems(title: String, ps: List[String]): List[String] =
    if ps.isEmpty then Nil else List((s"**$title**\n" + ps.map(p => s"- $p").mkString("\n")))
