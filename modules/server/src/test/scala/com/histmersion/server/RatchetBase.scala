package com.histmersion
package server

import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path}
import java.time.LocalDate
import scala.jdk.CollectionConverters.*
import scala.util.Try

/** The limits of the ratchet files (B1): each file as committed at the merge base with the pilot branch, never the file
  * in the working tree. The suites hold the code exact to the working tree's file; this holds that file no higher than
  * the base's, so a branch cannot raise a limit in the change that raises the count.
  *
  * Two shapes of file, both read as a count per key:
  *   - a counted file (`guidelines-ratchet.tsv`, `contracts.tsv`, `exceptions.tsv`): the first columns are the key, the
  *     last one the count. A count above the base's, or a key the base does not list (it counts zero there), is a rise;
  *     a lower count, or a row gone, is a fall and passes;
  *   - a reviewed file (`total-order.tsv`, `boundaries.tsv`): every row is reviewed text, the whole row its key and the
  *     number of copies its count. A row the base does not have (new, or changed: a changed row is a new one) is a
  *     rise; a row removed passes.
  *
  * A rise passes only when `architecture/accepted-rises.tsv` has rows for its key that the base does not have: the
  * owner's accept script writes each (file, key, delta, reason, date) with the rise. A row approves a rise of at most
  * `delta` over the base's count, whatever that count is, so an approval survives a rebase onto a pilot whose count
  * moved; the rows the branch adds for one key sum. A row of the first form (file, key, from, to, reason, date) reads
  * as the delta `to - from`. Which rows count as the owner's is [[Approvals]] — the seam for a stronger proof (a signed
  * commit, a hook) when the owner chooses one.
  *
  * The base is the merge base of `HEAD` with the ref in `HISTMERSION_RATCHET_BASE`, else with `contracts3-pilot`, else
  * with `origin/contracts3-pilot`. Without a git repository, or with none of those refs, the check fails and says so:
  * there is no limit to compare against, and passing would accept any count.
  */
object RatchetBase:

  val BaseVariable: String = "HISTMERSION_RATCHET_BASE"
  val DefaultBases: List[String] = List("contracts3-pilot", "origin/contracts3-pilot")
  val AcceptedFile: String = "architecture/accepted-rises.tsv"

  val RiseNeedsOwner: String =
    s"A rise, or a new or changed reviewed row, needs the owner's accept script (notes/accept-*.sh), which commits the " +
      s"row with its reason to $AcceptedFile; lower the count instead, or ask the owner."

  /** A ratchet file: its path in the repository and how its rows are keyed. */
  final case class Spec(path: String, shape: Shape):

    /** The file's rows as a count per key; a malformed row is an error, never a row skipped. */
    def counts(lines: List[String]): Either[String, Map[Key, Int]] =
      val rows = lines.filterNot(l => l.startsWith("#") || l.isBlank)
      shape match
        case Shape.Counted(n) =>
          val parsed = rows.map { l =>
            val cols = l.split("\t", -1).toList
            cols
              .lift(n)
              .flatMap(_.trim.toIntOption)
              .filter(_ => cols.length == n + 1)
              .map(cols.take(n) -> _)
              .toRight(s"$path: malformed row ($n key cells and a count): $l")
          }
          parsed.collectFirst { case Left(e) => e }.toLeft(parsed.collect { case Right(kv) => kv }.toMap)
        case Shape.Reviewed(header) =>
          Right(rows.filterNot(l => header.contains(l)).groupMapReduce(l => l.split("\t", -1).toList)(_ => 1)(_ + _))

    /** Holds `lines` (the branch's rows of this file) no higher than the base's file: the accepted rows used, or why
      * not.
      */
    def check(
        lines: List[String],
        git: Git,
        env: Map[String, String],
        approvals: Git => Approvals = Approvals.AddedInBranch.apply,
        inventories: List[TreeInventory] = Nil
    ): Either[String, List[Accepted]] =
      for
        b <- git.base(env)
        now <- counts(lines)
        before <- counts(git.show(b.commit, path).getOrElse(Nil))
        accepted <- approvals(git).granted(b)
        risen = rises(path, before, now)
        measured <- BaseMeasure.of(inventories.filter(_.file == path), risen, git, b.commit)
        left = unaccepted(risen.filterNot(measured.proves), accepted)
        _ <- Either.cond(
          left.isEmpty,
          (),
          (s"$path is above its limit, the file at ${b.describe}:" ::
            left.map(r => measured.refusal(r).getOrElse(r.describe(shape, accepted))) ++ List(RiseNeedsOwner))
            .mkString("\n")
        )
      yield accepted.filter(a => risen.exists(a.covers))

    /** Holds the working tree's file no higher than the base's; a missing or unreadable file fails (it is never
      * regenerated from the code).
      */
    def checkFile(
        git: Git = Git(Path.of(".")),
        env: Map[String, String] = sys.env,
        approvals: Git => Approvals = Approvals.AddedInBranch.apply,
        inventories: List[TreeInventory] = Nil
    ): Either[String, List[Accepted]] =
      val file = git.repo.resolve(path)
      for
        _ <- Either.cond(
          Files.isRegularFile(file),
          (),
          s"$path is missing: a ratchet file is restored from git (git checkout -- $path), never regenerated, since " +
            "a regenerated file would accept any count"
        )
        lines <- read(file)
        used <- check(lines, git, env, approvals, inventories)
      yield used

  object Spec:
    def counted(path: String, keyColumns: Int): Spec = Spec(path, Shape.Counted(keyColumns))
    def reviewed(path: String, header: Option[String] = None): Spec = Spec(path, Shape.Reviewed(header))

    val GuidelineRatchet: Spec = counted("architecture/guidelines-ratchet.tsv", 3)
    val Boundaries: Spec = reviewed("architecture/boundaries.tsv", Some("path\tkind\treason"))
    val Contracts: Spec = counted("modules/server/src/test/resources/architecture/contracts.tsv", 3)
    val Exceptions: Spec = counted("modules/server/src/test/resources/architecture/exceptions.tsv", 3)
    val TotalOrder: Spec = reviewed("modules/server/src/test/resources/architecture/total-order.tsv")

  enum Shape derives CanEqual:
    /** `keyColumns` key cells, then the count. */
    case Counted(keyColumns: Int)

    /** The whole row is the key; a row equal to `header` (a header without `#`) is none. */
    case Reviewed(header: Option[String])

  /** A row's key: its key cells. */
  type Key = List[String]

  trait TreeInventory:
    def file: String
    def owns(key: Key): Boolean
    def measure(git: Git, commit: String): Either[String, Map[Key, Int]]

  final case class BaseMeasure(commit: String, owned: Key => Boolean, counts: Map[Key, Int]):
    def eligible(rise: Rise): Boolean = rise.from == 0 && owned(rise.key)

    def proves(rise: Rise): Boolean = eligible(rise) && rise.to <= counts.getOrElse(rise.key, 0)

    def refusal(rise: Rise): Option[String] =
      Option.when(eligible(rise))(
        s"  ${rise.key.mkString("\t")}: not at the base -> ${rise.to} here, but the merge-base tree " +
          s"(${commit.take(9)}) measures ${counts.getOrElse(rise.key, 0)}: a new rule's baseline may not exceed what " +
          "the base already had; fix the new findings"
      )

  object BaseMeasure:
    def of(inventories: List[TreeInventory], risen: List[Rise], git: Git, commit: String): Either[String, BaseMeasure] =
      val asked = inventories.filter(i => risen.exists(r => r.from == 0 && i.owns(r.key)))
      val measured = asked.map(_.measure(git, commit))
      measured
        .collectFirst { case Left(why) => why }
        .toLeft(
          BaseMeasure(commit, k => asked.exists(_.owns(k)), measured.collect { case Right(m) => m }.flatten.toMap)
        )

  /** A count above the base's: the file, the key, the base's count (zero when the base has no such row) and the new. */
  final case class Rise(file: String, key: Key, from: Int, to: Int) derives CanEqual:

    /** How far the count rose over the base's. */
    def by: Int = to - from

    /** How far the accepted rows let this key rise over the base: their deltas summed (none: zero). */
    def allowance(accepted: List[Accepted]): Int = accepted.filter(_.covers(this)).map(_.delta).sum

    /** The rise's line with what the accepted rows allow, when they allow anything. */
    def describe(shape: Shape, accepted: List[Accepted]): String =
      val n = allowance(accepted)
      describe(shape) + (if n == 0 then "" else s" (+$by; the accepted rows allow +$n)")

    def describe(shape: Shape): String = shape match
      case Shape.Counted(_) =>
        val at = if from == 0 then "not at the base" else s"$from at the base"
        s"  ${key.mkString("\t")}: $at -> $to here"
      case Shape.Reviewed(_) =>
        val what = if from == 0 then "a row not at the base (new, or changed)" else s"$from copies at the base -> $to"
        s"  $what: ${key.mkString("\t")}"

  /** A row of the accepted-rises file: the owner allows `key` of `file` to rise by at most `delta` (positive) over its
    * count at the merge base, whatever that count is when the suites run.
    */
  final case class Accepted(file: String, key: Key, delta: Int, reason: String, date: LocalDate) derives CanEqual:
    import Accepted.{encodeKey, escape}

    /** The row is about the risen key (it adds `delta` to what the rise may be). */
    def covers(rise: Rise): Boolean = file == rise.file && key == rise.key
    def row: String = List(file, encodeKey(key), delta.toString, escape(reason), date.toString).mkString("\t")

  object Accepted:
    val Header: String =
      "# file\tkey\tdelta\treason\tdate — rises of the ratchet files the owner accepted, each by at most delta over " +
        "the count at the merge base (written only by the owner's notes/accept-*.sh; a row counts on the branch that " +
        "adds it, and a branch's rows for one key sum); key cells joined by \\t, \\ as \\\\"

    /** The file in the delta form: [[Header]], then each row as [[parse]] reads it (a first-form row from, to as its
      * delta `to - from`), in the file's order. The rows read back equal, so no branch gains an approval by rewriting.
      */
    def rewrite(lines: List[String]): Either[String, List[String]] = parse(lines).map(rows => Header :: rows.map(_.row))

    /** A cell's tab and backslash as `\t` and `\\`, so a key of several cells fits one cell. */
    def escape(text: String): String = text.replace("\\", "\\\\").replace("\t", "\\t")

    def unescape(cell: String): String =
      val out = new StringBuilder
      var i = 0
      while i < cell.length do
        if cell.charAt(i) == '\\' && i + 1 < cell.length then
          out.append(if cell.charAt(i + 1) == 't' then '\t' else cell.charAt(i + 1))
          i += 2
        else
          out.append(cell.charAt(i))
          i += 1
      out.toString

    def encodeKey(key: Key): String = escape(key.mkString("\t"))
    def decodeKey(cell: String): Key = unescape(cell).split("\t", -1).toList

    /** The rows: (file, key, delta, reason, date), or the first form (file, key, from, to, reason, date) as the delta
      * `to - from`. A delta that is not positive, a missing reason or a date that is not ISO is an error, never a row
      * skipped.
      */
    def parse(lines: List[String]): Either[String, List[Accepted]] =
      def row(l: String, file: String, key: String, delta: Option[Int], reason: String, date: String) =
        (delta, Try(LocalDate.parse(date)).toOption) match
          case (Some(n), Some(d)) if n > 0 && reason.nonEmpty =>
            Right(Accepted(file, decodeKey(key), n, unescape(reason), d))
          case _ => Left(s"$AcceptedFile: malformed row (a positive delta, a reason and an ISO date): $l")
      val rows = lines.filterNot(l => l.startsWith("#") || l.isBlank).map { l =>
        l.split("\t", -1).toList match
          case file :: key :: delta :: reason :: date :: Nil      => row(l, file, key, delta.toIntOption, reason, date)
          case file :: key :: from :: to :: reason :: date :: Nil =>
            row(l, file, key, from.toIntOption.zip(to.toIntOption).map((f, t) => t - f), reason, date)
          case _ => Left(s"$AcceptedFile: malformed row (file, key, delta, reason, date): $l")
      }
      rows.collectFirst { case Left(e) => e }.toLeft(rows.collect { case Right(a) => a })

  /** The keys whose count in `now` is above the base's (a key the base lacks counts zero there). */
  def rises(file: String, base: Map[Key, Int], now: Map[Key, Int]): List[Rise] =
    now.toList
      .collect { case (k, n) if n > base.getOrElse(k, 0) => Rise(file, k, base.getOrElse(k, 0), n) }
      .sortBy(_.key.mkString("\t"))

  /** The rises above what the accepted rows allow. */
  def unaccepted(rises: List[Rise], accepted: List[Accepted]): List[Rise] =
    rises.filter(r => r.by > r.allowance(accepted))

  /** The merge base, and the ref it was taken with. */
  final case class Base(commit: String, ref: String):
    def short: String = commit.take(9)
    def describe: String = s"the merge base $short of HEAD and $ref"

  /** git in one repository, read-only; `env` is added to the process's (the tests' isolated git configuration). */
  final case class Git(repo: Path, env: Map[String, String] = Map.empty):
    /** Exit code and standard output; the error output is dropped (a missing object is told by the code). */
    def run(args: String*): (Int, String) =
      val builder = new ProcessBuilder(("git" :: "-C" :: repo.toString :: args.toList).asJava)
        .redirectError(ProcessBuilder.Redirect.DISCARD)
      env.foreach((k, v) => builder.environment.put(k, v))
      Try {
        val process = builder.start()
        val out = new String(process.getInputStream.readAllBytes(), UTF_8)
        (process.waitFor(), out)
      }.getOrElse((-1, ""))

    def ok(args: String*): Option[String] =
      val (code, out) = run(args*)
      Option.when(code == 0)(out)

    def isRepository: Boolean = ok("rev-parse", "--is-inside-work-tree").exists(_.trim == "true")

    /** The file at a commit: `None` when the commit has no such file. */
    def show(commit: String, path: String): Option[List[String]] =
      ok("cat-file", "-e", s"$commit:$path").flatMap(_ => ok("show", s"$commit:$path")).map(_.linesIterator.toList)

    def blobs(commit: String, paths: List[String]): Map[String, String] =
      val requests = Files.createTempFile("ratchet-blobs", ".txt")
      try
        val _ = Files.writeString(requests, paths.map(p => s"$commit:$p\n").mkString, UTF_8)
        val builder = new ProcessBuilder(List("git", "-C", repo.toString, "cat-file", "--batch").asJava)
          .redirectInput(requests.toFile)
          .redirectError(ProcessBuilder.Redirect.DISCARD)
        env.foreach((k, v) => builder.environment.put(k, v))
        Try {
          val process = builder.start()
          val bytes = process.getInputStream.readAllBytes()
          val _ = process.waitFor()
          bytes
        }.toOption.fold(Map.empty)(batch(paths, _))
      finally
        val _ = Files.deleteIfExists(requests)

    private def batch(paths: List[String], bytes: Array[Byte]): Map[String, String] =
      var at = 0
      val out = Map.newBuilder[String, String]
      paths.foreach { path =>
        var eol = at
        while eol < bytes.length && bytes(eol) != '\n'.toByte do eol += 1
        val header = new String(bytes, at, eol - at, UTF_8)
        at = eol + 1
        header.split(' ').toList match
          case _ :: "blob" :: size :: Nil =>
            val n = size.toInt
            out += path -> new String(bytes, at, n, UTF_8)
            at += n + 1
          case _ :: _ :: size :: Nil => at += size.toInt + 1
          case _                     => ()
      }
      out.result()

    /** The base: the merge base of `HEAD` with the variable's ref, else with the first default ref that has one. */
    def base(env: Map[String, String]): Either[String, Base] =
      if !isRepository then
        Left(
          s"not a git repository (${repo.toAbsolutePath.normalize}): the ratchet limits are the files committed at " +
            s"the merge base with ${DefaultBases.head}, and without git there is none to compare against — run the " +
            "suites from a clone that has that branch"
        )
      else
        def mergeBase(ref: String): Option[Base] =
          ok("merge-base", "HEAD", ref).map(_.trim).filter(_.nonEmpty).map(Base(_, ref))
        env.get(BaseVariable).filter(_.nonEmpty) match
          case Some(ref) =>
            mergeBase(ref).toRight(
              s"$BaseVariable=$ref: no merge base of HEAD and $ref (an unknown ref, or no history)"
            )
          case None =>
            DefaultBases.iterator
              .flatMap(mergeBase)
              .nextOption()
              .toRight(
                s"no merge base of HEAD and ${DefaultBases.mkString(" or ")} (a shallow clone, or the branch not " +
                  s"fetched): fetch it, or name the base in $BaseVariable"
              )

  /** Which rows of the accepted-rises file are the owner's: the seam for a stronger proof. */
  trait Approvals:
    def granted(base: Base): Either[String, List[Accepted]]

  object Approvals:
    /** The rows of the working tree's accepted-rises file that the base's does not have, compared as read (a row
      * rewritten from the first form to the delta form is the same row). A row already at the base grants nothing: the
      * file is a ledger, and only the branch that adds a row is approved by it.
      */
    final case class AddedInBranch(git: Git) extends Approvals:
      def granted(base: Base): Either[String, List[Accepted]] =
        val file = git.repo.resolve(AcceptedFile)
        for
          now <- if Files.isRegularFile(file) then read(file).flatMap(Accepted.parse) else Right(Nil)
          before <- Accepted.parse(git.show(base.commit, AcceptedFile).getOrElse(Nil))
        yield now.diff(before)

  private def read(file: Path): Either[String, List[String]] =
    Try(Files.readAllLines(file, UTF_8).asScala.toList).toEither.left.map(e => s"$file is unreadable: ${e.getMessage}")

  /** The line a run prints for the accepted rises it used, so the hand-back names them. */
  def announce(used: List[Accepted]): Option[String] =
    Option.when(used.nonEmpty)(
      (s"*** RISES ACCEPTED through $AcceptedFile (name them in the hand-back):" :: used.map(a =>
        s"***   ${a.file}: ${a.key.mkString("\t")} +${a.delta} over the base (${a.date}: ${a.reason})"
      )).mkString("\n")
    )
