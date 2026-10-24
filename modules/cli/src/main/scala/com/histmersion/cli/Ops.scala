package com.histmersion.cli

import cats.syntax.all.*
import com.monovore.decline.*
import com.histmersion.api.*
import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.api.Queue.{QueueAdd, QueueImport, QueuePatch}

import java.nio.file.{Files, Path}

/** The CLI commands of stream 2.8 — `lint`, `queue`, `query`, `schema` — plus the pure argument
  * parsing they need. Like every other command they are HTTP clients of the server; the host never
  * touches Postgres.
  *
  * `queue` deliberately keeps the grammar the plan specifies (`queue acquire add "…"`,
  * `queue tasks`, `queue done 3`, `queue export <dir>`), which does not fit decline's
  * subcommand-per-verb shape — so the positional arguments are dispatched by [[QueueArgs.parse]],
  * a pure function with its own tests, rather than by a nest of subcommands.
  */
object Ops:

  // ── queue argument grammar (pure) ────────────────────────────────────────────────────────────

  /** What `histmersion queue …` was asked to do. */
  enum QueueCmd:
    case Show(list: String, all: Boolean)
    case Add(list: String, text: String, topic: Option[String], reason: Option[String])
    case Done(id: Int)
    case Edit(id: Int, text: String)
    case Rm(id: Int)
    case Export(dir: String)
    case Import(dir: String)

  object QueueArgs:
    val usage: String =
      "usage: histmersion queue <acquire|tasks|log> [--all] | queue <list> add \"<text>\" [--topic t] | " +
        "queue done <id> | queue edit <id> \"<text>\" | queue rm <id> | queue export <dir> | queue import <dir>"

    /** Pure: positional words + flags → a command, or the usage line. */
    def parse(args: List[String], topic: Option[String], reason: Option[String], all: Boolean): Either[String, QueueCmd] =
      def id(s: String): Either[String, Int] =
        s.toIntOption.toRight(s"'$s' is not an item id — $usage")
      args match
        case "done" :: i :: Nil     => id(i).map(QueueCmd.Done.apply)
        case "edit" :: i :: text :: Nil =>
          if text.trim.isEmpty then Left(s"edit needs the new text — $usage") else id(i).map(QueueCmd.Edit(_, text))
        case "edit" :: _            => Left(s"edit takes an item id and the new text — $usage")
        case "rm" :: i :: Nil       => id(i).map(QueueCmd.Rm.apply)
        case "export" :: dir :: Nil => Right(QueueCmd.Export(dir))
        case "import" :: dir :: Nil => Right(QueueCmd.Import(dir))
        case list :: rest if Queue.Kind.parse(list).isDefined =>
          rest match
            case Nil                  => Right(QueueCmd.Show(list, all))
            case "add" :: text :: Nil => Right(QueueCmd.Add(list, text, topic, reason))
            case "add" :: Nil         => Left(s"add needs the item text — $usage")
            case _                    => Left(usage)
        case list :: _ => Left(s"unknown list or verb '$list' — $usage")
        case Nil       => Left(usage)

  // ── the meta files on disk ────────────────────────────────────────────────────────────────────────

  /** Write `ACQUIRE.md` / `TASKS.md` / `LOG.md` and `GOALS.md` into `dir`, rendered by the server
    * from the rows. Used by `queue export` and by `histmersion export`, so both produce identical files. */
  def writeMeta(c: Client, dir: Path): Either[CliError, List[String]] =
    val _ = Files.createDirectories(dir)
    val files: List[(String, () => Either[CliError, String])] =
      Queue.Kind.values.toList.map(k => k.file -> (() => c.queueExport(k.label))) :+
        (GoalsExport.File -> (() => c.goalsExport))
    files.foldLeft[Either[CliError, List[String]]](Right(Nil)) { case (acc, (file, fetch)) =>
      acc.flatMap { done =>
        fetch().map { text =>
          val _ = Files.writeString(dir.resolve(file), text)
          done :+ file
        }
      }
    }

  /** Read the three queue files back. A missing file is skipped, not an error: a workspace may only
    * keep some of them. `GOALS.md` is never read: it is an export only (see [[GoalsExport]]). */
  def readMeta(c: Client, dir: Path, replace: Boolean): Either[CliError, List[String]] =
    if !Files.isDirectory(dir) then Left(CliError(s"not a directory: $dir", exitCode = ExitCode.Usage))
    else
      Queue.Kind.values.toList.foldLeft[Either[CliError, List[String]]](Right(Nil)) { (acc, kind) =>
        acc.flatMap { done =>
          val f = dir.resolve(kind.file)
          if !Files.isRegularFile(f) then Right(done :+ s"${kind.file}: absent, skipped")
          else
            c.queueImport(kind.label, QueueImport(Files.readString(f), replace))
              .map(r => done :+ s"${kind.file}: ${r.items} item(s), ${r.notes} note block(s), ${r.replaced} replaced")
        }
      }

  // ── commands ─────────────────────────────────────────────────────────────────────────────────


  /** `lint --fix` applies the server-side repairs and then loops while sources remain to re-chunk
    * (or point-only imported areas to remove), because a whole-corpus pass is far longer than one
    * HTTP request. */
  @annotation.tailrec
  private def fixLoop(c: Client, rules: List[String], sources: Int, chunks: Int, edges: Int,
      moved: Option[Int] = None, placed: Option[Int] = None,
      places: Option[List[String]] = None, areas: Int = 0): Either[CliError, List[String]] =
    c.lintFix(rules) match
      case Left(e) => Left(e)
      case Right(r) =>
        val (s, ch, ed) = (sources + r.sourcesReindexed, chunks + r.chunksWritten, edges + r.edgesResolved)
        // the geocode repair is not bounded: the first call made every move there was
        val mv = moved.orElse(Option.when(rules.exists(_.trim.equalsIgnoreCase(Lint.FindingGeocode)))(r.findingsRegeocoded))
        // …and neither is the fill of unplaced findings
        val pl = placed.orElse(Option.when(rules.exists(_.trim.equalsIgnoreCase(Lint.FindingUnplaced)))(r.findingsPlaced))
        // …nor the gazetteer clean-up (place-kind, place-grid-point): its lines are the first call's
        val pc = places.orElse(Some(r.applied.filter(l =>
          l.startsWith(Lint.PlaceKindRule + ":") || l.startsWith(Lint.PlaceGridPoint + ":"))))
        val ar = areas + r.pointAreasRemoved
        val asksAreas = rules.exists(_.trim.equalsIgnoreCase(Lint.PlaceImportedPointArea))
        // a call that removed nothing while some remain is not progress (they are being changed
        // under it): stop rather than loop
        val moreAreas = asksAreas && r.pointAreasRemaining > 0 && r.pointAreasRemoved > 0
        if r.remaining > 0 || moreAreas then
          Console.err.print(
            if moreAreas then s"\rfixing… $ar point-only area(s) removed, ${r.pointAreasRemaining} to go"
            else s"\rfixing… $s source(s) reindexed, ${r.remaining} to go")
          fixLoop(c, rules, s, ch, ed, mv, pl, pc, ar)
        else
          if sources > 0 || ar > 0 then Console.err.print("\r" + " " * 70 + "\r")
          Right(List(s"resolve-edges: $ed edge target(s) rewritten",
            s"reindex: $s source(s) → $ch chunk(s)") ++
            mv.map(n => s"${Lint.FindingGeocode}: $n finding(s) moved to the place the current geocoder chooses").toList ++
            pl.map(n => s"${Lint.FindingUnplaced}: $n finding(s) given the point of the place they name").toList ++
            pc.getOrElse(Nil) ++
            Option.when(asksAreas)(s"${Lint.PlaceImportedPointArea}: $ar point-only imported area(s) removed" +
              (if r.pointAreasRemaining > 0 then s", ${r.pointAreasRemaining} left (named by evidence, or changing — run again)" else "")).toList)

  /** `lint --file x.md`: the server checks the text (it has the renderer); with `--fix` the repaired
    * text is written back over the file. A note file's frontmatter is the note's own and is not sent. */
  private def lintFile(c: Client, p: java.nio.file.Path, fix: Boolean): Either[CliError, FormatReport] =
    if !java.nio.file.Files.isRegularFile(p) then Left(CliError(s"no such file: $p", exitCode = ExitCode.Usage))
    else
      val raw          = java.nio.file.Files.readString(p)
      val (head, body) = FileFormat.splitFrontmatter(raw)
      c.lintMarkdown(FormatCheck(body, FormatField.Body, fix)).map { r =>
        r.text.filter(t => fix && t != body).foreach(t => java.nio.file.Files.writeString(p, head + t))
        r.copy(subject = p.getFileName.toString, text = None)
      }

  val lint: Opts[Client => Either[CliError, Unit]] =
    Opts.subcommand("lint", s"Health-check the corpus (${Lint.all.size} rules: ${Lint.names.mkString(", ")})") {
      (
        Opts.options[String]("rule", "Run only this rule (repeatable)").orEmpty,
        Opts.flag("fix", "First apply the safe repairs: resolve short edge targets, reindex stale chunks; with --rule finding-geocode also move the findings it lists, with --rule finding-unplaced give a point to the findings it lists, with --rule place-kind reclassify and merge the places it lists, with --rule place-grid-point move the places it lists, with --rule place-imported-point-area remove the places it lists (with --article/--source/--file: the formatting repairs)").orFalse,
        Opts.option[String]("article", "Check only this article's formatting (a slug)").orNone,
        Opts.option[String]("source", "Check only this source's summary and notes for formatting problems (a citekey)").orNone,
        Opts.option[String]("file", "Check a local Markdown file's formatting before it is stored (--fix rewrites the file)").orNone,
        Output.jsonFlag
      ).mapN { (rules, fix, article, source, file, json) => (c: Client) =>
        def show(r: FormatReport) = Output.emit(json, r)(FormatMarkdown.report)
        (article, source, file) match
          case (Some(slug), _, _) => (if fix then c.fixArticleFormat(slug) else c.lintArticle(slug)).map(show)
          case (_, Some(ck), _)   => (if fix then c.fixSourceFormat(ck) else c.lintSource(ck)).map(show)
          case (_, _, Some(f))    => lintFile(c, java.nio.file.Path.of(f), fix).map(show)
          case _ =>
            for
              fixed <- if fix then fixLoop(c, rules, 0, 0, 0) else Right(Nil)
              rep   <- c.lint(rules)
            yield
              val full = rep.copy(fixed = fixed)
              Output.emit(json, full)(Lint.markdown)
      }
    }

  val query: Opts[Client => Either[CliError, Unit]] =
    Opts.subcommand("query", "Run ONE read-only SQL statement (a write is refused by Postgres itself)") {
      (Opts.argument[String]("sql"), Output.jsonFlag).mapN { (sql, json) => (c: Client) =>
        c.query(sql).map(r => Output.emit(json, r)(Query.markdown))
      }
    }

  val schema: Opts[Client => Either[CliError, Unit]] =
    Opts.subcommand("schema", "List the tables and columns `query` can read (from information_schema)") {
      Output.jsonFlag.map { json => (c: Client) =>
        c.schemaTables.map(s => Output.emit(json, s)(Query.schemaMarkdown))
      }
    }

  val queue: Opts[Client => Either[CliError, Unit]] =
    // NB: no `|` in the description — `tools/gen-cli-docs.sh` pastes it into a Markdown TABLE cell.
    // The full grammar (which needs pipes to read well) is `QueueArgs.usage`, printed on a bad call.
    Opts.subcommand("queue", "Bookkeeping queues: acquire / tasks / log — show, add, done, edit, rm, export (with the goals as GOALS.md), import") {
      (
        Opts.arguments[String]("args").orEmpty,
        Opts.option[String]("topic", "Group the added item under this heading").orNone,
        Opts.option[String]("reason", "Why this item is on the list / what research direction it serves").orNone,
        Opts.flag("all", "Include done items and the file's prose blocks").orFalse,
        Output.jsonFlag
      ).mapN { (args, topic, reason, all, json) => (c: Client) =>
        QueueArgs.parse(args, topic, reason, all) match
          case Left(msg)  => Left(CliError(msg, exitCode = ExitCode.Usage))
          case Right(cmd) => run(c, cmd, json)
      }
    }

  private def run(c: Client, cmd: QueueCmd, json: Boolean): Either[CliError, Unit] = cmd match
    case QueueCmd.Show(list, all) =>
      c.queueList(list, all).map(l =>
        Output.emit(json, l)(l => Queue.Kind.parse(l.list).map(k => Queue.markdown(k, l.items)).getOrElse("")))
    case QueueCmd.Add(list, text, topic, reason) =>
      c.queueAdd(list, QueueAdd(text, topic, reason = reason)).map(i =>
        Output.emit(json, i)(i => s"added #${i.id} to ${i.list}${i.topic.map(t => s" · $t").getOrElse("")}"))
    // the item as it now stands, with --json like `show` and `add` (it printed only the line before)
    case QueueCmd.Done(id) =>
      c.queuePatch(id, QueuePatch(status = Some(Queue.Done))).map(i => Output.emit(json, i)(i => s"done #${i.id}: ${i.text}"))
    case QueueCmd.Edit(id, text) =>
      c.queuePatch(id, QueuePatch(text = Some(text))).map(i => Output.emit(json, i)(i => s"edited #${i.id}: ${i.text}"))
    case QueueCmd.Rm(id) =>
      c.queueDelete(id).map(i => Output.emit(json, i)(i => s"removed #${i.id}: ${i.text}"))
    case QueueCmd.Export(dir) =>
      writeMeta(c, Path.of(dir)).map(fs => println(s"wrote ${fs.mkString(", ")} to $dir"))
    case QueueCmd.Import(dir) =>
      readMeta(c, Path.of(dir), replace = true).map(_.foreach(l => println(s"  $l")))

/** Pure helpers for `lint --file`. */
object FileFormat:

  /** `(frontmatter, body)`: a note file opens with its OWN `---` YAML block (what `article put`
    * sends as metadata) — that is not pasted frontmatter, so only the body is checked. A file
    * without one is all body. */
  def splitFrontmatter(text: String): (String, String) =
    val lines = text.split("\n", -1).toList
    if lines.headOption.map(_.trim).contains("---") then
      lines.drop(1).indexWhere(_.trim == "---") match
        case -1 => ("", text)
        case i  =>
          val head = lines.take(i + 2).mkString("\n") + "\n"
          (head, text.drop(head.length))
    else ("", text)
