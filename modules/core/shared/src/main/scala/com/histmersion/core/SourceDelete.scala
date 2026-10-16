package com.histmersion.core

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import io.circe.{Codec, Decoder, Encoder}
import sttp.tapir.Schema

/** Deleting a source (#969): `POST /api/sources/{ck}/delete`, `histmersion delete <ck> [--apply]`.
  *
  * A dry run by default: the answer lists everything that would go and everything that refuses it.
  * With `apply` the delete is ONE transaction (`SourceDeleteRepo.apply`), and the stored original is
  * removed after the commit. Not an MCP tool, like `DELETE /api/articles/{slug}`: dropping a source is
  * a person's decision, not something a model should reach for while tidying.
  *
  * What it decides, once:
  *   - an article that cites the source (`[[ck]]`, an old citekey of it, or a short form unique to
  *     it) REFUSES the delete, with no override: the article has to be fixed first, or it would cite
  *     nothing. So do the events, event relations and participants recorded on its authority, and an
  *     ingest job or agent run still working on it (it would write the source back);
  *   - edges from OTHER sources to it are not lost: they are parked in `unresolved_edges`, exactly as
  *     an edge written to a work not in the corpus is (V026), and come back if it is ingested again;
  *   - everything that is the source's own (findings, outgoing edges, chunks, figures, attestations,
  *     the assessment, old citekeys, identifiers, its per-source event datings) goes with it, and so
  *     do the records that only make sense with it (other sources' attestations of its findings and
  *     edges, figure links to it, goal links, articles' reasons for leaving it out);
  *   - the `sources` row leaves a tombstone (V031), so a bundle merge does not bring it back;
  *   - Calibre is never touched; the linked Calibre id is reported so a person can decide (the CLI
  *     first checks that the entry is still in the library: [[CalibreEntryCheck]]);
  *   - the log queue records the delete. */
final case class SourceDeleteRequest(apply: Boolean = false)
object SourceDeleteRequest:
  given Schema[SourceDeleteRequest] = KindlingsSchema.derived[SourceDeleteRequest].schema
  /** Strict (#813): an unknown key is refused, not dropped — see [[StrictJson]]. */
  given Codec.AsObject[SourceDeleteRequest] = StrictJson.codec(KindlingsCodecAsObject.derived[SourceDeleteRequest])

/** What refuses a delete. */
enum DeleteBlockerKind(val wire: String, val label: String):
  case Article          extends DeleteBlockerKind("article", "cited by article")
  case Event            extends DeleteBlockerKind("event", "primary source of event")
  case EventEdge        extends DeleteBlockerKind("event-edge", "source of event relation")
  case EventParticipant extends DeleteBlockerKind("event-participant", "source of event participant")
  case Job              extends DeleteBlockerKind("job", "still being written by job")

object DeleteBlockerKind:
  given WireEnumValues[DeleteBlockerKind]:
    def all: Array[DeleteBlockerKind] = DeleteBlockerKind.values
    def wire(e: DeleteBlockerKind): String = e.wire
    def fromWire(s: String): Option[DeleteBlockerKind] = DeleteBlockerKind.values.find(_.wire == s.trim.toLowerCase)
  given Encoder[DeleteBlockerKind] = WireEnum.encoder
  given Decoder[DeleteBlockerKind] = WireEnum.decoder
  given Schema[DeleteBlockerKind]  = WireEnum.schema

/** One thing that refuses the delete: an article (slug, title), an event (id, label), a job (id). */
final case class DeleteBlocker(kind: DeleteBlockerKind, id: String, title: Option[String] = None)
    derives KindlingsCodecAsObject
object DeleteBlocker:
  given Schema[DeleteBlocker] = KindlingsSchema.derived[DeleteBlocker].schema

/** A kind of row a delete removes (or, for `EdgesIn`, parks). */
enum DeletePart(val wire: String, val label: String):
  case Findings        extends DeletePart("findings", "findings")
  case FindingPlaces   extends DeletePart("finding-places", "gazetteer assignments of its findings")
  case EdgesOut        extends DeletePart("edges-out", "edges from it")
  case ParkedOut       extends DeletePart("parked-edges-out", "parked edges from it (to works not in the corpus)")
  case EdgesIn         extends DeletePart("edges-in", "edges from other sources to it — parked, not lost")
  case Chunks          extends DeletePart("chunks", "full-text chunks")
  case Assets          extends DeletePart("assets", "figures")
  case AssetLinks      extends DeletePart("asset-links", "links of its figures")
  case AssetLinksTo    extends DeletePart("asset-links-to", "links of other figures to it or its findings")
  case Attestations    extends DeletePart("attestations", "attestations it makes")
  case AttestationsOf  extends DeletePart("attestations-of", "other sources' attestations of its findings, edges and datings")
  case Assessments     extends DeletePart("assessments", "assessment")
  case Aliases         extends DeletePart("aliases", "old citekeys")
  case Identifiers     extends DeletePart("identifiers", "identifiers (DOI, file digest)")
  case EventDatings    extends DeletePart("event-datings", "event datings it gives")
  case GoalLinks       extends DeletePart("goal-links", "goal links to it")
  case CoverageReasons extends DeletePart("coverage-reasons", "articles' reasons for leaving it out")

object DeletePart:
  given WireEnumValues[DeletePart]:
    def all: Array[DeletePart] = DeletePart.values
    def wire(e: DeletePart): String = e.wire
    def fromWire(s: String): Option[DeletePart] = DeletePart.values.find(_.wire == s.trim.toLowerCase)
  given Encoder[DeletePart] = WireEnum.encoder
  given Decoder[DeletePart] = WireEnum.decoder
  given Schema[DeletePart]  = WireEnum.schema

final case class DeleteCount(part: DeletePart, rows: Int) derives KindlingsCodecAsObject
object DeleteCount:
  given Schema[DeleteCount] = KindlingsSchema.derived[DeleteCount].schema

/** The answer of a delete, dry run or applied.
  *
  * @param counts       non-zero row counts, in [[DeletePart]] order
  * @param edgesIn      the edges of other sources that point at it (parked by the delete)
  * @param coverage     articles whose coverage reasons name it (the reason goes, the article stays)
  * @param files        the stored original(s) on the server that go after the commit
  * @param filesFailed  `path: reason` for each file the applied delete could not remove (logged)
  * @param mentions     acquisition/task/log items whose text or reason links it — left as they are
  * @param calibreId    the linked Calibre entry, which is never touched
  * @param logEntry     the log-queue item recording an applied delete */
final case class SourceDeletion(
    citekey: String,
    title: String,
    applied: Boolean,
    blockers: List[DeleteBlocker] = Nil,
    counts: List[DeleteCount] = Nil,
    aliases: List[String] = Nil,
    edgesIn: List[Edge] = Nil,
    coverage: List[String] = Nil,
    files: List[String] = Nil,
    filesFailed: List[String] = Nil,
    mentions: List[Int] = Nil,
    calibreId: Option[String] = None,
    logEntry: Option[Int] = None
) derives KindlingsCodecAsObject:
  def refused: Boolean = blockers.nonEmpty
  def count(p: DeletePart): Int = counts.find(_.part == p).fold(0)(_.rows)

object SourceDeletion:
  given Schema[SourceDeletion] = KindlingsSchema.derived[SourceDeletion].schema

/** What the host's Calibre library says of a deleted source's linked entry (#1776). The server cannot
  * see the library, so the CLI looks it up (read-only, `Calibre.byId`) before it prints the answer:
  * the record's stored `calibreId` may name an entry a person has already removed. */
enum CalibreEntryCheck:
  /** The entry is in the library. */
  case Present
  /** The library was read and has no entry with that id. */
  case Missing
  /** The library could not be read (no library configured, `calibredb` and `sqlite3` both failed). */
  case Unchecked(reason: String)

/** The pure rules of a delete. */
object SourceDelete:

  /** The Calibre line of the answer: the linked entry, as the library says it is (`check`; `None`: not
    * looked up — the line then says so rather than claim the entry exists). */
  def calibreLine(calibreId: Option[String], check: Option[CalibreEntryCheck]): String =
    calibreId match
      case None => "Calibre: no linked entry."
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

  /** Whether an attestation (of any source) loses its target when `ck` is deleted: a finding of it
    * (`<ck>#<n>`), an edge FROM it (`<ck>|<dst>|<kind>`), or a dating it gives (an `event_dating`
    * target naming it as a whole token). An attestation of an edge TO it stays: that edge is parked,
    * not deleted. */
  def attestationGone(targetType: String, targetId: String, ck: String): Boolean =
    targetType match
      case AttestationTarget.Finding     => targetId.startsWith(ck + "#")
      case AttestationTarget.Edge        => targetId.split('|').headOption.contains(ck)
      case AttestationTarget.EventDating => Rename.replaceToken(targetId, ck, "\u0000") != targetId
      case _                             => false

  /** Whether `text` links `ck` with a `[[…]]` wikilink (any label or anchor). */
  def links(text: String, ck: String): Boolean = ArticleLinks.targets(text).exists(_.equalsIgnoreCase(ck))

  /** The `refused` message: every blocker, grouped by kind, and what to do about it. */
  def refusal(ck: String, blockers: List[DeleteBlocker]): String =
    val groups = blockers.groupBy(_.kind).toList.sortBy(_._1.ordinal).map { (k, bs) =>
      s"${k.label} ${bs.map(b => b.id + b.title.fold("")(t => s" ($t)")).mkString(", ")}"
    }
    s"'$ck' cannot be deleted — ${groups.mkString("; ")}. Fix those first (remove the citation from " +
      "each article, re-source the events, let the job finish); there is no override"

  /** The line the log queue records for an applied delete: `deleted ck (Title): 3 findings, …`. */
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

  /** As the CLI and the REST answer render it. */
  def markdown(d: SourceDeletion, calibre: Option[CalibreEntryCheck] = None): String =
    val head =
      if d.applied then s"# Deleted `${d.citekey}`"
      else if d.refused then s"# Cannot delete `${d.citekey}`"
      else s"# Would delete `${d.citekey}` (dry run)"
    val blockers =
      if d.blockers.isEmpty then Nil
      else List("", "**Refused — fix these first (there is no override):**", "") ++
        d.blockers.map(b => s"- ${b.kind.label}: `${b.id}`${b.title.fold("")(t => s" — $t")}")
    val verb = if d.applied then "removed" else "would go"
    val counts =
      if d.counts.isEmpty then List("", s"Nothing besides the record itself $verb.")
      else List("", s"**${if d.applied then "Removed" else "Would remove"}** (with the record itself):", "") ++
        d.counts.map(c => s"- ${c.part.label}: ${c.rows}")
    val aliases = if d.aliases.isEmpty then Nil
                  else List("", s"Old citekeys that stop resolving: ${d.aliases.map(a => s"`$a`").mkString(", ")}")
    val edgesIn = if d.edgesIn.isEmpty then Nil
                  else List("", s"Edges of other sources to it (${if d.applied then "parked" else "parked, not lost"}: " +
                    "they come back if it is ingested again):", "") ++
                    d.edgesIn.map(e => s"- `${e.src}` ${e.kind} → `${e.dst}`")
    val coverage = if d.coverage.isEmpty then Nil
                   else List("", s"Coverage reasons dropped from: ${d.coverage.map(s => s"`$s`").mkString(", ")}")
    val files =
      if d.files.isEmpty then List("", "Stored original: none on the server.")
      else List("", s"Stored original (${if d.applied then "removed" else "removed after the commit"}):", "") ++
        d.files.map(f => s"- `$f`")
    val failed = if d.filesFailed.isEmpty then Nil
                 else List("", "**Could not remove** (logged; remove by hand):", "") ++ d.filesFailed.map(f => s"- $f")
    val mentions = if d.mentions.isEmpty then Nil
                   else List("", s"Queue items that mention it (left as they are): ${d.mentions.map(i => s"#$i").mkString(", ")}")
    val calibreLines = List("", calibreLine(d.calibreId, calibre))
    val tail =
      if d.applied then List("", s"Logged as item #${d.logEntry.fold("?")(_.toString)} of the log queue. A tombstone keeps a " +
        s"bundle merge from bringing it back. The exported mirror's `sources/${d.citekey}.md` is not removed by " +
        "`histmersion export` — delete it there by hand.")
      else if d.refused then Nil
      else List("", s"_Dry run. Re-run with `--apply` to delete._")
    (List(head, "", s"**${d.title}**") ++ blockers ++ counts ++ aliases ++ edgesIn ++ coverage ++ files ++ failed ++
      mentions ++ calibreLines ++ tail).mkString("\n")
