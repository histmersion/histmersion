package histdb.cli

import cats.syntax.all.*
import com.monovore.decline.*
import io.circe.syntax.*
import histdb.core.*
import histdb.core.Queue.{QueueAdd, QueueImport, QueuePatch}

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

  /** What `histdb queue …` was asked to do. */
  enum QueueCmd:
    case Show(list: String, all: Boolean)
    case Add(list: String, text: String, topic: Option[String])
    case Done(id: Int)
    case Rm(id: Int)
    case Export(dir: String)
    case Import(dir: String)

  object QueueArgs:
    val usage: String =
      "usage: histdb queue <acquire|tasks|log> [--all] | queue <list> add \"<text>\" [--topic t] | " +
        "queue done <id> | queue rm <id> | queue export <dir> | queue import <dir>"

    /** Pure: positional words + flags → a command, or the usage line. */
    def parse(args: List[String], topic: Option[String], all: Boolean): Either[String, QueueCmd] =
      def id(s: String): Either[String, Int] =
        s.toIntOption.toRight(s"'$s' is not an item id — $usage")
      args match
        case "done" :: i :: Nil     => id(i).map(QueueCmd.Done.apply)
        case "rm" :: i :: Nil       => id(i).map(QueueCmd.Rm.apply)
        case "export" :: dir :: Nil => Right(QueueCmd.Export(dir))
        case "import" :: dir :: Nil => Right(QueueCmd.Import(dir))
        case list :: rest if Queue.Kind.parse(list).isDefined =>
          rest match
            case Nil                  => Right(QueueCmd.Show(list, all))
            case "add" :: text :: Nil => Right(QueueCmd.Add(list, text, topic))
            case "add" :: Nil         => Left(s"add needs the item text — $usage")
            case _                    => Left(usage)
        case list :: _ => Left(s"unknown list or verb '$list' — $usage")
        case Nil       => Left(usage)

  // ── the meta files on disk ────────────────────────────────────────────────────────────────────────

  /** Write `ACQUIRE.md` / `TASKS.md` / `LOG.md` into `dir`, rendered by the server from the rows.
    * Used by `queue export` and by `histdb export`, so both produce identical files. */
  def writeMeta(c: Client, dir: Path): Either[CliError, List[String]] =
    val _ = Files.createDirectories(dir)
    Queue.Kind.values.toList.foldLeft[Either[CliError, List[String]]](Right(Nil)) { (acc, kind) =>
      acc.flatMap { done =>
        c.queueExport(kind.label).map { text =>
          val _ = Files.writeString(dir.resolve(kind.file), text)
          done :+ kind.file
        }
      }
    }

  /** Read the three files back. A missing file is skipped, not an error: a workspace may only keep
    * some of them. */
  def readMeta(c: Client, dir: Path, replace: Boolean): Either[CliError, List[String]] =
    if !Files.isDirectory(dir) then Left(CliError(s"not a directory: $dir", exitCode = 1))
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

  private val jsonFlag = Opts.flag("json", "Print raw JSON instead of Markdown").orFalse

  /** `lint --fix` applies the server-side repairs and then loops while sources remain to re-chunk,
    * because a whole-corpus rebuild is far longer than one HTTP request. */
  @annotation.tailrec
  private def fixLoop(c: Client, sources: Int, chunks: Int, edges: Int): Either[CliError, List[String]] =
    c.lintFix match
      case Left(e) => Left(e)
      case Right(r) =>
        val (s, ch, ed) = (sources + r.sourcesReindexed, chunks + r.chunksWritten, edges + r.edgesResolved)
        if r.remaining > 0 then
          Console.err.print(s"\rfixing… $s source(s) reindexed, ${r.remaining} to go")
          fixLoop(c, s, ch, ed)
        else
          if sources > 0 then Console.err.print("\r" + " " * 60 + "\r")
          Right(List(s"resolve-edges: $ed edge target(s) rewritten",
            s"reindex: $s source(s) → $ch chunk(s)"))

  val lint: Opts[Client => Either[CliError, Unit]] =
    Opts.subcommand("lint", s"Health-check the corpus (${Lint.names.size} rules: ${Lint.names.mkString(", ")})") {
      (
        Opts.options[String]("rule", "Run only this rule (repeatable)").orEmpty,
        Opts.flag("fix", "First apply the safe repairs: resolve short edge targets, reindex stale chunks").orFalse,
        jsonFlag
      ).mapN { (rules, fix, json) => (c: Client) =>
        for
          fixed <- if fix then fixLoop(c, 0, 0, 0) else Right(Nil)
          rep   <- c.lint(rules)
        yield
          val full = rep.copy(fixed = fixed)
          println(if json then full.asJson.spaces2 else Lint.markdown(full))
      }
    }

  val query: Opts[Client => Either[CliError, Unit]] =
    Opts.subcommand("query", "Run ONE read-only SQL statement (a write is refused by Postgres itself)") {
      (Opts.argument[String]("sql"), jsonFlag).mapN { (sql, json) => (c: Client) =>
        c.query(sql).map(r => println(if json then r.asJson.spaces2 else Query.markdown(r)))
      }
    }

  val schema: Opts[Client => Either[CliError, Unit]] =
    Opts.subcommand("schema", "List the tables and columns `query` can read (from information_schema)") {
      jsonFlag.map { json => (c: Client) =>
        c.schemaTables.map(s => println(if json then s.asJson.spaces2 else Query.schemaMarkdown(s)))
      }
    }

  val queue: Opts[Client => Either[CliError, Unit]] =
    // NB: no `|` in the description — `tools/gen-cli-docs.sh` pastes it into a Markdown TABLE cell.
    // The full grammar (which needs pipes to read well) is `QueueArgs.usage`, printed on a bad call.
    Opts.subcommand("queue", "Bookkeeping queues: acquire / tasks / log — show, add, done, rm, export, import") {
      (
        Opts.arguments[String]("args").orEmpty,
        Opts.option[String]("topic", "Group the added item under this heading").orNone,
        Opts.flag("all", "Include done items and the file's prose blocks").orFalse,
        jsonFlag
      ).mapN { (args, topic, all, json) => (c: Client) =>
        QueueArgs.parse(args, topic, all) match
          case Left(msg)  => Left(CliError(msg, exitCode = 1))
          case Right(cmd) => run(c, cmd, json)
      }
    }

  private def run(c: Client, cmd: QueueCmd, json: Boolean): Either[CliError, Unit] = cmd match
    case QueueCmd.Show(list, all) =>
      c.queueList(list, all).map { l =>
        if json then println(l.asJson.spaces2)
        else println(Queue.Kind.parse(l.list).map(k => Queue.markdown(k, l.items)).getOrElse(""))
      }
    case QueueCmd.Add(list, text, topic) =>
      c.queueAdd(list, QueueAdd(text, topic)).map { i =>
        if json then println(i.asJson.spaces2)
        else println(s"added #${i.id} to ${i.list}${i.topic.map(t => s" · $t").getOrElse("")}")
      }
    case QueueCmd.Done(id) =>
      c.queuePatch(id, QueuePatch(status = Some(Queue.Done))).map(i => println(s"done #${i.id}: ${i.text}"))
    case QueueCmd.Rm(id) =>
      c.queueDelete(id).map(i => println(s"removed #${i.id}: ${i.text}"))
    case QueueCmd.Export(dir) =>
      writeMeta(c, Path.of(dir)).map(fs => println(s"wrote ${fs.mkString(", ")} to $dir"))
    case QueueCmd.Import(dir) =>
      readMeta(c, Path.of(dir), replace = true).map(_.foreach(l => println(s"  $l")))
