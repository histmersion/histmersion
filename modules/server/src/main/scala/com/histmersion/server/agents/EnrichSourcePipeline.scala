package com.histmersion.server.agents

import com.histmersion.core.*
import com.histmersion.core.Agents.{Pipeline, Role}
import com.histmersion.core.EnrichSource.*
import com.histmersion.core.Queue.QueueAdd
import com.histmersion.db.AgentRepo
import com.histmersion.server.Handlers
import io.circe.syntax.*
import sttp.ai.core.agent.ResponseSchema

import javax.sql.DataSource

/** `enrich-source <citekey>` (docs/research-embedded-agents.md §4.2): read one source and propose its
  * summary, classification, period, quoted findings and notes; CODE verifies every answer and writes
  * ONE patch. The LLM steps get no write tool — classify may look up the vocabulary (`facets`,
  * `search`), the others get none at all, so text injected into an OCR'd body has nothing to call.
  *
  * Steps (each checkpointed, in this order): load · classify · findings-1..n · geocode · notes ·
  * patch · close. */
final class EnrichSourcePipeline(h: Handlers, ds: DataSource, cfg: AgentsConfig) extends PipelineDef:
  val pipeline: Pipeline = Pipeline.EnrichSource
  private val name = pipeline.wire

  private def prompt(step: String, user: String): Either[Halt, Prompt] =
    Prompt.resource(name, step).left.map(Halt.Fail(_)).map(Prompt(_, user))

  def run(ctx: RunCtx): Either[Halt, String] =
    val ck = ctx.run.target
    for
      loaded <- ctx.code[Loaded]("load")(load(ck))
      classifyPrompt <- prompt("classify", classifyInput(loaded))
      classified <- ctx.llm[Classification, Classified]("classify", Role.Extractor, classifyPrompt,
                      List(ToolName.Facets, ToolName.Search), ResponseSchema.derived[Classification]())(
                      (c, last) => checkClassification(c, cfg.verify.maxAbsYear, last).map(Checked(_, _)))
      verified <- findings(ctx, loaded, classified)
      geocoded <- ctx.code[List[FindingInput]]("geocode")(Right(geocode(ctx, verified)))
      notesPrompt <- prompt("notes", notesInput(loaded, classified, verified))
      notes <- ctx.llm[Notes, String]("notes", Role.Extractor, notesPrompt, Nil, ResponseSchema.derived[Notes]())(
                 (n, _) => checkNotes(n, isCitekey, slug => AgentRepo.slugExists(ds, slug)).map(Checked(_)))
      patched <- ctx.code[Patched]("patch")(write(ck, loaded, classified, geocoded, notes))
      summary <- ctx.code[String]("close")(close(ctx, ck, loaded, patched, verified))
    yield summary

  // ── steps ──────────────────────────────────────────────────────────────────────────────────────

  private def load(ck: String): Either[String, Loaded] =
    h.getSource(ck, body = true).left.map(_.message).flatMap { sw =>
      val s    = sw.source
      val body = s.ocrMarkdown.map(_.trim).getOrElse("")
      if body.isEmpty then Left(s"$ck has no text — ingest or OCR it first")
      else Right(Loaded(
        citekey = s.citekey, title = s.title, authors = s.authors, year = s.year, venue = s.venue,
        status = s.status.wire, hasSummary = s.summary.exists(_.trim.nonEmpty),
        hasNotes = s.notes.exists(_.trim.nonEmpty), hasPeriod = s.periodStart.isDefined,
        period = for a <- s.periodStart; b <- s.periodEnd yield Period(a, b),
        topics = s.topics, regions = s.regions, existingClaims = sw.findings.map(_.claim),
        updatedAt = s.updatedAt, head = body.take(cfg.enrich.headChars),
        windows = windows(body, cfg.enrich.windowChars, cfg.enrich.maxWindows), tags = s.tags))
    }

  private def meta(l: Loaded): String =
    s"citekey: ${l.citekey}\ntitle: ${l.title}\nauthors: ${l.authors.mkString("; ")}\n" +
      s"year: ${l.year.fold("unknown")(_.toString)}\nvenue: ${l.venue.getOrElse("unknown")}"

  private def classifyInput(l: Loaded): String =
    s"Classify this source.\n\n${meta(l)}\n\nThe opening of its text:\n\n${Prompt.data("source-text", l.head)}"

  /** One LLM step per window. Each answer's quotes are checked against THAT window. */
  private def findings(ctx: RunCtx, l: Loaded, c: Classified): Either[Halt, List[VerifiedFinding]] =
    val existing = l.existingClaims.toSet
    l.windows.foldLeft[Either[Halt, List[VerifiedFinding]]](Right(Nil)) { (acc, w) =>
      acc.flatMap { sofar =>
        val user = s"Extract the findings of this stretch of the source.\n\n${meta(l)}\n" +
          w.heading.fold("")(hd => s"section: $hd\n") +
          s"stretch ${w.index} of ${l.windows.size}\n\n${Prompt.data("source-text", w.text)}"
        // the VERIFIED list is what is checkpointed: a resume replays it without re-checking
        prompt("findings", user).flatMap { p =>
          ctx.llm[Findings, VerifiedFindings](s"findings-${w.index}", Role.Extractor, p, Nil,
              ResponseSchema.derived[Findings]()) { (f, last) =>
            checkFindings(f, w, cfg.verify.quoteMinRatio, cfg.verify.maxAbsYear, l.period.orElse(c.period),
              existing ++ sofar.map(_.claim), last).map(Checked(_, _))
          }.map(v => sofar ++ v.findings)
        }
      }
    }

  /** Places through the gazetteer, never the model's coordinates. An unknown place keeps its name
    * (`region`) with no point, and says so. */
  private def geocode(ctx: RunCtx, fs: List[VerifiedFinding]): List[FindingInput] =
    fs.map { f =>
      val hit = f.place.flatMap(p => AgentRepo.geocode(ds, p))
      f.place.filter(_ => hit.isEmpty).foreach(p => ctx.warn(s"geocode: no gazetteer place named '$p' (finding kept without a point)"))
      FindingInput(
        claim = f.claim, region = hit.map(_._1).orElse(f.place), lon = hit.map(_._2), lat = hit.map(_._3),
        periodStart = f.start, periodEnd = f.end, periodName = f.periodName,
        tags = f.tags, confidence = Some(f.confidence))
    }

  private def notesInput(l: Loaded, c: Classified, fs: List[VerifiedFinding]): String =
    s"Write the notes for [[${l.citekey}]].\n\n${meta(l)}\n\nsummary: ${c.summary}\n\nverified findings:\n" +
      fs.map(f => s"- ${f.claim} (quote: \"${f.quote}\")").mkString("\n")

  private def isCitekey(s: String): Boolean = h.getSource(s, body = false).isRight

  /** The single write. On a 412 (someone wrote the source since `load`) the source is re-read and the
    * patch rebuilt from the CHECKPOINTED answers — no LLM call — and sent once more. */
  private def write(ck: String, l: Loaded, c: Classified, fs: List[FindingInput], notes: String): Either[String, Patched] =
    val (p, fields, proposal) = patch(l, c, fs, Option(notes).filter(_.nonEmpty))
    h.patchSource(ck, p) match
      case Right(_) => Right(Patched(fs.size, fields, proposal))
      case Left(e) if e.code.contains(Write.CodePrecondition) =>
        load(ck).flatMap { fresh =>
          val again = fresh.copy(windows = Nil, head = "")
          val existing = fresh.existingClaims.map(AgentVerify.normalise).toSet
          val (p2, fields2, proposal2) = patch(again, c, fs.filterNot(f => existing.contains(AgentVerify.normalise(f.claim))),
            Option(notes).filter(_.nonEmpty))
          h.patchSource(ck, p2).left.map(_.message).map(_ => Patched(p2.findings.fold(0)(_.add.size), fields2, proposal2))
        }
      case Left(e) => Left(e.message)

  /** Bookkeeping, as the skills do it: a `log` line, and a `tasks` item for a status a person must
    * decide. Calibre is untouched: a source not in Calibre is already what `histmersion calibre gaps`
    * reports, host-side. */
  private def close(ctx: RunCtx, ck: String, l: Loaded, p: Patched, fs: List[VerifiedFinding]): Either[String, String] =
    val what = if p.fieldsSet.isEmpty then "nothing new" else p.fieldsSet.mkString(", ")
    val line = s"enrich-source [[${ck}]] (agent run ${ctx.run.id.take(8)}): $what"
    p.statusProposal.foreach { s =>
      val _ = h.ops.queueAdd("tasks", QueueAdd(s"[[${ck}]]: an agent run proposes status $s — check and set it by hand",
        topic = Some("agent proposals")))
      ctx.warn(s"status not applied (needs a person): $s")
    }
    h.ops.queueAdd("log", QueueAdd(line)).left.map(_.message).map(_ => line)
