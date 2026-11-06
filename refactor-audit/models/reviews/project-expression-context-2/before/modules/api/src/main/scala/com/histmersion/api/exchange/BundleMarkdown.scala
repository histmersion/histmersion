package com.histmersion.api.exchange

import com.histmersion.api.Plural
import com.histmersion.api.aliases.InstanceInfo
import com.histmersion.api.given
object BundleMarkdown:

  type InstanceResult = InstanceResult.T
  object InstanceResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type WhoResult = WhoResult.T
  object WhoResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BytesResult = BytesResult.T
  object BytesResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BytesN = BytesN.T
  object BytesN extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ManifestResult = ManifestResult.T
  object ManifestResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type VerifyResult = VerifyResult.T
  object VerifyResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ImportResultResult = ImportResultResult.T
  object ImportResultResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ProblemsResult = ProblemsResult.T
  object ProblemsResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ProblemsTitle = ProblemsTitle.T
  object ProblemsTitle extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ProblemsPs = ProblemsPs.T
  object ProblemsPs extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def instance(i: InstanceInfo): com.histmersion.api.exchange.BundleMarkdown.InstanceResult =
    _root_.com.histmersion.api.exchange.BundleMarkdown.InstanceResult(s"**Instance** ${i.id}" + i.label
      .map(l => s" · $l")
      .getOrElse(" · _(no label — `histmersion bundle instance --label <name>`)_") +
      s"  \n_created ${i.createdAt}_")

  private def who(i: Bundle.Instance): com.histmersion.api.exchange.BundleMarkdown.WhoResult = _root_.com.histmersion.api.exchange.BundleMarkdown.WhoResult(i.label.fold(i.id.toString)(l => s"$l (${i.id})"))

  private def bytes(n: com.histmersion.api.exchange.BundleMarkdown.BytesN): com.histmersion.api.exchange.BundleMarkdown.BytesResult =
    if (n).value < 1024 then _root_.com.histmersion.api.exchange.BundleMarkdown.BytesResult(s"$n B")
    else if (n).value < 1024L * 1024 then _root_.com.histmersion.api.exchange.BundleMarkdown.BytesResult(s"${(n).value / 1024} KB")
    else if (n).value < 1024L * 1024 * 1024 then _root_.com.histmersion.api.exchange.BundleMarkdown.BytesResult(s"${(n).value / (1024L * 1024)} MB")
    else _root_.com.histmersion.api.exchange.BundleMarkdown.BytesResult(s"${(n).value / (1024L * 1024 * 1024)} GB")

  def manifest(m: Bundle.Manifest): com.histmersion.api.exchange.BundleMarkdown.ManifestResult =
    val files = m.files
      .filter(_.lines > 0)
      .map(f => s"| ${f.path.stripPrefix("entities/").stripSuffix(".ndjson")} | ${f.lines} |")
    val scope = m.since.fold("everything")(s => s"changes after $s")
    List(
      s"**Bundle** ${m.bundleId} · from ${who(m.instance)} · format ${m.formatVersion} · schema V${m.schemaVersion}",
      s"$scope · as of ${m.asOf.getOrElse("—")} · ${Plural.count(m.blobs.count, "blob")} (${bytes(m.blobs.bytes)})",
      if files.isEmpty then "_(no records)_" else ("| records | lines |" :: "|---|---:|" :: files).mkString("\n")
    ).mkString("\n\n")

  def verify(r: Bundle.VerifyResult): com.histmersion.api.exchange.BundleMarkdown.VerifyResult =
    val head = if ((r.ok) == _root_.com.histmersion.api.exchange.Bundle.VerifyResult.Ok(true)) then s"**OK** — every checksum, record and ${Plural.count(r.blobsChecked, _root_.com.histmersion.api.Plural.CountSingular("blob"))} verified"
    else s"**NOT OK** — ${Plural.count(_root_.com.histmersion.api.Plural.CountN((r.problems.size).toLong), _root_.com.histmersion.api.Plural.CountSingular("problem"))}"
    (head :: r.manifest.map(manifest).toList ++ problems("Problems", r.problems)).mkString("\n\n")

  def importResult(r: Bundle.ImportResult): com.histmersion.api.exchange.BundleMarkdown.ImportResultResult =
    val verb =
      if r.problems.nonEmpty then "**Refused** — nothing was written"
      else if ((r.dryRun) == _root_.com.histmersion.api.exchange.Bundle.ImportResult.DryRun(true)) then "**Dry run** — nothing was written; this is what an import would do"
      else if r.changed == _root_.com.histmersion.api.exchange.Bundle.ImportResult.ChangedResult(0) then "**Nothing to change** — this instance already has everything in the bundle"
      else
        s"**Imported** — ${Plural.count(r.changed, _root_.com.histmersion.api.Plural.CountSingular("row"))} changed" + r.mergeId.map(m => s" (merge $m)").getOrElse("")
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
      problems("Problems (fix these, then import again)", r.problems) ++ issues ++
      problems("Conflicts (changed on both sides; decided by a resolution)", r.conflicts) ++
      problems("Notes", r.notes)).mkString("\n\n")

  private def problems(title: com.histmersion.api.exchange.BundleMarkdown.ProblemsTitle, ps: List[com.histmersion.api.exchange.BundleMarkdown.ProblemsPs]): List[com.histmersion.api.exchange.BundleMarkdown.ProblemsResult] =
    if ps.isEmpty then Nil else List(_root_.com.histmersion.api.exchange.BundleMarkdown.ProblemsResult(s"**$title**\n" + ps.map(p => s"- $p").mkString("\n")))
