package com.histmersion.api

object SourceDelete:

  def calibreLine(calibreId: Option[String], check: Option[CalibreEntryCheck]): String =
    calibreId match
      case None     => "Calibre: no linked entry."
      case Some(id) =>
        check match
          case Some(CalibreEntryCheck.Present) =>
            s"Calibre: entry $id is linked and is NOT touched — remove it in Calibre if it should go too."
          case Some(CalibreEntryCheck.Missing) =>
            s"Calibre: entry $id no longer exists in the library — nothing to remove there."
          case Some(CalibreEntryCheck.Unchecked(why)) =>
            s"Calibre: the record links entry $id (the library could not be checked: $why); it is NOT touched — " +
              "remove it in Calibre if it is there and should go too."
          case None =>
            s"Calibre: the record links entry $id (not checked against the library); it is NOT touched — " +
              "remove it in Calibre if it is there and should go too."

  def refusal(ck: String, blockers: List[DeleteBlocker]): String =
    val groups = blockers.groupBy(_.kind).toList.sortBy(_._1.ordinal).map { (k, bs) =>
      s"${k.label} ${bs.map(b => b.id + b.title.fold("")(t => s" ($t)")).mkString(", ")}"
    }
    s"'$ck' cannot be deleted — ${groups.mkString("; ")}. Fix those first (remove the citation from " +
      "each article, re-source the events, let the job finish); there is no override"

  def logText(d: SourceDeletion): String =
    def short(p: DeletePart) = p match
      case DeletePart.EdgesIn => "edges in (parked)"
      case other              => other.wire.replace('-', ' ')
    val parts = d.counts.filter(_.rows > 0).map(c => s"${c.rows} ${short(c.part)}")
    val files = if d.files.isEmpty then Nil else List(Plural.count(d.files.size, "stored original"))
    val calibre = d.calibreId.map(id => s"Calibre entry $id kept").toList
    s"deleted ${d.citekey} (${d.title})" +
      (parts ++ files ++ calibre match
        case Nil => ""
        case xs  => ": " + xs.mkString(", "))

  def markdown(d: SourceDeletion, calibre: Option[CalibreEntryCheck] = None): String =
    val head =
      if d.applied then s"# Deleted `${d.citekey}`"
      else if d.refused then s"# Cannot delete `${d.citekey}`"
      else s"# Would delete `${d.citekey}` (dry run)"
    val blockers =
      if d.blockers.isEmpty then Nil
      else
        List("", "**Refused — fix these first (there is no override):**", "") ++
          d.blockers.map(b => s"- ${b.kind.label}: `${b.id}`${b.title.fold("")(t => s" — $t")}")
    val verb = if d.applied then "removed" else "would go"
    val counts =
      if d.counts.isEmpty then List("", s"Nothing besides the record itself $verb.")
      else
        List("", s"**${if d.applied then "Removed" else "Would remove"}** (with the record itself):", "") ++
          d.counts.map(c => s"- ${c.part.label}: ${c.rows}")
    val aliases = if d.aliases.isEmpty then Nil
    else List("", s"Old citekeys that stop resolving: ${d.aliases.map(a => s"`$a`").mkString(", ")}")
    val edgesIn = if d.edgesIn.isEmpty then Nil
    else
      List(
        "",
        s"Edges of other sources to it (${if d.applied then "parked" else "parked, not lost"}: " +
          "they come back if it is ingested again):",
        ""
      ) ++
        d.edgesIn.map(e => s"- `${e.src}` ${e.kind} → `${e.dst}`")
    val coverage = if d.coverage.isEmpty then Nil
    else List("", s"Coverage reasons dropped from: ${d.coverage.map(s => s"`$s`").mkString(", ")}")
    val files =
      if d.files.isEmpty then List("", "Stored original: none on the server.")
      else
        List("", s"Stored original (${if d.applied then "removed" else "removed after the commit"}):", "") ++
          d.files.map(f => s"- `$f`")
    val failed = if d.filesFailed.isEmpty then Nil
    else List("", "**Could not remove** (logged; remove by hand):", "") ++ d.filesFailed.map(f => s"- $f")
    val mentions = if d.mentions.isEmpty then Nil
    else List("", s"Queue items that mention it (left as they are): ${d.mentions.map(i => s"#$i").mkString(", ")}")
    val calibreLines = List("", calibreLine(d.calibreId, calibre))
    val tail =
      if d.applied then
        List(
          "",
          s"Logged as item #${d.logEntry.fold("?")(_.toString)} of the log queue. A tombstone keeps a " +
            s"bundle merge from bringing it back. The exported mirror's `sources/${d.citekey}.md` is not removed by " +
            "`histmersion export` — delete it there by hand."
        )
      else if d.refused then Nil
      else List("", s"_Dry run. Re-run with `--apply` to delete._")
    (List(head, "", s"**${d.title}**") ++ blockers ++ counts ++ aliases ++ edgesIn ++ coverage ++ files ++ failed ++
      mentions ++ calibreLines ++ tail).mkString("\n")
