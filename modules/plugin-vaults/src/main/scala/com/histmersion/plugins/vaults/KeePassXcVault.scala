package com.histmersion
package plugins.vaults
import Vaults.{failed, said}
import Subprocess.{code, firstLine}
import YubiKey.unprompted
import com.histmersion.util.problem.{Problem, Problems}
import com.histmersion.util.problem.Text.sentence

import scala.annotation.tailrec
import com.histmersion.plugin.*
import com.histmersion.plugin.Plugin.Config.given

import java.nio.file.Path
import scala.concurrent.duration.*
import com.histmersion.plugin.Processes
import com.histmersion.lint.{LintRule, tolerated}

final class KeePassXcVault(
    val name: Secret.Vault.Name,
    exe: Vaults.Path,
    db: Vaults.Path,
    keyFile: Option[Vaults.Path],
    password: Option[Secret.Resolved],
    attribute: Vaults.Field,
    timeout: FiniteDuration,
    yubikeys: List[YubiKey.Slot],
    ykman: Option[Vaults.Path],
    yubikeyTimeout: FiniteDuration,
    log: Plugin.Log
)(using processes: Processes)
    extends Secret.Vault:

  /** The YubiKey that opened the database last, tried first next time (a resolve may run on any thread). */
  private val lastOpened = new java.util.concurrent.atomic.AtomicReference[Option[YubiKey.Slot]](None)

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def file = Path.of(db.value).getFileName.toString

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def resolve(ref: Secret.Vault.ResolveRef): Either[Problems, Secret.Resolved] =
    request(ref).flatMap { (entry, attr) =>
      if yubikeys.nonEmpty then
        withYubiKey(s"for '$ref'", entry)(yk =>
          Subprocess
            .run(argv(attr, entry, Some(yk)), Map.empty, stdin, yubikeyTimeout)
            .map(r => if r.exit.value == 0 then Right(r) else Left(r))
            .left
            .map(noAnswer)
        )
          .flatMap(r => Vaults.answer(name, ref, Right(r), _ => None))
      else Vaults.answer(name, ref, Subprocess.run(argv(attr, entry, None), Map.empty, stdin, timeout), classify(entry))
    }

  /** Why the host's process runner gave no answer, as a try with a YubiKey sees it. */
  private def noAnswer(failure: Problem): YubiKey.NoAnswer = failure match
    case Problem.Io.Process(_, Problem.Io.Ending.TimedOut(after)) => YubiKey.NoAnswer.TimedOut(after.duration)
    case why                                                      => YubiKey.NoAnswer.Unusable(why)

  /** Every reference through ONE `keepassxc-cli open` session ([[KeePassXcSession]]): one unlock, one YubiKey touch. A
    * single reference (or one whose entry holds a line break) is read as [[resolve]] reads it; a failed unlock is every
    * reference's error, a missing entry only its own.
    */
  override def resolveAll(
      refs: List[Secret.Vault.ResolveRef]
  ): Map[Secret.Vault.ResolveRef, Either[Problems, Secret.Resolved]] =
    val asked = refs.distinct
    val requests = asked.map(r => r -> request(r))
    val batched = requests.collect {
      case (r, Right((e, a))) if KeePassXcSession.sendable(e, a) => r -> (e, a)
    }
    val single = asked.filterNot(r => batched.exists(_._1 == r)).map(r => r -> resolve(r)).toMap
    if batched.size <= 1 then single ++ batched.map((r, _) => r -> resolve(r))
    else single ++ session(batched)

  /** `<entry>#<attribute>` as two arguments that cannot read as options. */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def request(
      ref: Secret.Vault.ResolveRef
  ): Either[Problem, (KeePassXcSession.Entry, Vaults.Field)] =
    val (entry, attr) = Vaults.entryAndField(ref, attribute)
    for
      _ <- Subprocess
        .plain(Vaults.Operation("the entry"), Subprocess.Text(entry.value))
      _ <- Subprocess
        .plain(Vaults.Operation("the attribute"), Subprocess.Text(attr.value))
    yield (entry, attr)

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def stdin = password.map(p => Processes.RunStdin(p.value + "\n"))

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def unlocking(yk: Option[YubiKey.Slot]): List[String] =
    keyFile.toList.flatMap(k => List("-k", k.value)) ++ yk.toList.flatMap(y => List("-y", y.shown.value)) ++
      (if password.isEmpty then List("--no-password") else Nil)

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def argv(
      attr: Vaults.Field,
      entry: KeePassXcSession.Entry,
      yk: Option[YubiKey.Slot]
  ): List[Processes.RunArgv] =
    (List(exe.value, "show", "-q", "-a", attr.value) ++ unlocking(yk) ++ List(db.value, entry.value)).map(a =>
      Processes.RunArgv(a)
    )

  final private case class Opened(session: Subprocess.Session, prompt: KeePassXcSession.Prompt)

  /** Start `keepassxc-cli open` and wait for its prompt: `Right(Right)` = open, `Right(Left)` = it exited (its answer),
    * `Left` = it could not start or did not answer within `wait` (killed). The password line is written alone, before
    * any request: the unlock reads stdin ahead of the prompt.
    */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def open(
      yk: Option[YubiKey.Slot],
      wait: FiniteDuration
  ): Either[YubiKey.NoAnswer, Either[Subprocess.Result, Opened]] =
    Subprocess.Session
      .start(
        (List(exe.value, "open", "-q") ++ unlocking(yk) ++ List(db.value)).map(a => Processes.StartArgv(a)),
        Map.empty
      )
      .left
      .map(e => YubiKey.NoAnswer.Unusable(e))
      .flatMap { s =>
        password.foreach(pw => s.send(Processes.Session.SendLine(pw.value)))
        val up = s
          .awaitOut(wait)(o => KeePassXcSession.prompt(o).isDefined)
        KeePassXcSession.prompt(s.out) match
          case Some(p) if up && s.alive => Right(Right(Opened(s, p)))
          case _ if !s.alive            =>
            Right(
              Left(
                Subprocess.Result(
                  Processes.Result.Exit(s.exit(timeout).fold(-1)(_.value)),
                  Processes.Result.Out(""),
                  s.err
                )
              )
            )
          case _ =>
            val _ = s.close(1.second)
            Left(YubiKey.NoAnswer.TimedOut(wait))
      }

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def session(
      batched: List[(Secret.Vault.ResolveRef, (KeePassXcSession.Entry, Vaults.Field))]
  ): Map[Secret.Vault.ResolveRef, Either[Problems, Secret.Resolved]] =
    val opened: Either[Problems, Opened] =
      if yubikeys.nonEmpty then
        withYubiKey(
          s"for ${batched.size} references (${batched.map(_._1).mkString(", ")})",
          KeePassXcSession.Entry("")
        )(yk => open(Some(yk), yubikeyTimeout))
      else
        open(None, timeout) match
          case Left(why)      => Left(why.problem)
          case Right(Left(r)) =>
            Left(
              classify(KeePassXcSession.Entry(""))(r.err.value.toLowerCase(java.util.Locale.ROOT))
                .getOrElse(
                  name.failed(
                    Problem.Plugin.Retrieval
                      .Unread(Problem.Raw(s"$file"), r.exit.code, r.err.unprompted)
                  )
                )
            )
          case Right(Right(o)) => Right(o)
    opened match
      case Left(e) =>
        batched
          .map((r, _) => r -> (Left(e): Either[Problems, Secret.Resolved]))
          .toMap
      case Right(o) =>
        try readAll(o, batched)
        finally
          val _ = o.session.send(Processes.Session.SendLine("exit"))
          val _ = o.session.close(timeout)

  /** One request after another, each answered before the next is sent; a session that stops answering fails the rest.
    */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def readAll(
      o: Opened,
      batched: List[(Secret.Vault.ResolveRef, (KeePassXcSession.Entry, Vaults.Field))]
  ): Map[Secret.Vault.ResolveRef, Either[Problems, Secret.Resolved]] =
    val s = o.session
    @tailrec def read(
        rest: List[(Secret.Vault.ResolveRef, (KeePassXcSession.Entry, Vaults.Field))],
        acc: Map[Secret.Vault.ResolveRef, Either[Problems, Secret.Resolved]],
        mark: KeePassXcSession.Segment.From,
        broken: Option[Problem]
    ): Map[Secret.Vault.ResolveRef, Either[Problems, Secret.Resolved]] =
      (rest, broken) match
        case (Nil, _)                    => acc
        case ((ref, _) :: tail, Some(b)) =>
          read(tail, acc.updated(ref, Left(b)), KeePassXcSession.Segment.From(0), broken)
        case ((ref, (entry, attr)) :: tail, None) =>
          val cmd = KeePassXcSession.command(attr, entry)
          val errMark = s.err.value.length
          val answered = s.send(Processes.Session.SendLine(cmd.value)).flatMap { _ =>
            val _ = s.awaitOut(timeout)(out => KeePassXcSession.segment(out, mark, o.prompt).isDefined)
            val out = s.out
            KeePassXcSession
              .segment(out, mark, o.prompt)
              .map(seg => (seg, KeePassXcSession.Segment.From(out.value.length)))
              .toRight(
                if s.alive then
                  Problem.Io.Process(
                    Problem.Io.Tool("keepassxc-cli"),
                    Problem.Io.Ending.TimedOut(Problem.Millis.of(timeout))
                  )
                else
                  name
                    .failed(Problem.Plugin.Retrieval.Failed(Processes.Result.Err(s.err.value.drop(errMark)).unprompted))
              )
          }
          answered match
            case Left(broken)       => read(tail, acc.updated(ref, Left(broken)), mark, Some(broken))
            case Right((seg, next)) =>
              val v = KeePassXcSession.answer(seg, cmd)
              val result: Either[Problems, Secret.Resolved] =
                if v.value.nonEmpty then Right(v)
                else
                  s.awaitErr(Processes.Session.AwaitErrLength(errMark), 500.millis)
                  val err = s.err.value.drop(errMark)
                  if err.trim.isEmpty then Left(name.failed(Problem.Plugin.Retrieval.Blank(Problem.Raw(s"$ref"))))
                  else
                    Left(
                      classify(entry)(err.toLowerCase(java.util.Locale.ROOT)).getOrElse(
                        name.failed(Problem.Plugin.Retrieval.Failed(Processes.Result.Err(err).firstLine))
                      )
                    )
              read(tail, acc.updated(ref, result), next, None)
    read(batched, Map.empty, KeePassXcSession.Segment.From(s.out.value.length), None)

  /** The connected serials, when `ykman` can tell; `None` = unknown (then every entry is tried). */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def connected(): Option[Set[YubiKey.Slot.Serial]] =
    ykman.flatMap { y =>
      Subprocess.run(List(y.value, "list", "--serials").map(a => Processes.RunArgv(a)), Map.empty, None, timeout) match
        case Right(r) if r.exit.value == 0 => Some(YubiKey.serials(YubiKey.SerialsOut(r.out.value)))
        case Right(r)                      =>
          val failed = Problem.Io.Process(
            Problem.Io.Tool("ykman"),
            Problem.Io.Ending.Exited(r.exit.code, r.err.firstLine)
          )
          log.info(Plugin.Log.Line(s"${failed.sentence}: trying the YubiKeys in order"));
          None
        case Left(why) => log.info(Plugin.Log.Line(s"${why.sentence}: trying the YubiKeys in order")); None
    }

  /** Each planned key in turn until `attempt` opens the database with it (`Right(Right)`); a failure (`Right(Left)`:
    * KeePassXC's answer) that no other key could fix stops at once, and so does a key not touched in time. Every key
    * that failed is logged; the answer is every key's failure, in order (or that none of them is connected). `purpose`
    * says what the log line asks the touch for.
    */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def withYubiKey[A](purpose: String, entry: KeePassXcSession.Entry)(
      attempt: YubiKey.Slot => Either[YubiKey.NoAnswer, Either[Subprocess.Result, A]]
  ): Either[Problems, A] =
    val found = connected()
    val (planned, absent) = YubiKey.plan(yubikeys, found, lastOpened.get)
    def key(yk: YubiKey.Slot, attempt: Problem.Plugin.Attempt, stderr: Option[Subprocess.Result.Err]): Problem =
      name.failed(Problem.Plugin.Retrieval.Key(Problem.Raw(s"${yk.shown}"), attempt, stderr.map(_.unprompted)))
    absent.foreach(yk => log.info(Plugin.Log.Line(key(yk, Problem.Plugin.Attempt.NotConnected, None).sentence)))
    if planned.isEmpty then
      Left(
        name.failed(
          Problem.Plugin.Retrieval.NoKey(
            yubikeys.toVector.map(yk => Problem.Raw(s"${yk.shown}")),
            found.toVector.flatMap(_.toVector.sortBy(x => x.value: Long).map(s => Problem.Raw(s"$s")))
          )
        )
      )
    else
      @annotation.tailrec
      def go(rest: List[YubiKey.Slot], failed: Vector[Problem]): Either[Problems, A] =
        def and(last: Problem): Problems = Problems.from(failed :+ last).getOrElse(last)
        rest match
          case Nil =>
            Left(
              Problems.from(failed).getOrElse(name.failed(Problem.Plugin.Retrieval.Locked))
            )
          case yk :: more =>
            log.info(
              Plugin.Log.Line(
                s"opening $file with YubiKey ${yk.shown} $purpose — touch your YubiKey if it blinks " +
                  s"(waiting up to ${yubikeyTimeout.toSeconds} s)"
              )
            )
            attempt(yk) match
              case Left(YubiKey.NoAnswer.TimedOut(_))  => Left(and(key(yk, Problem.Plugin.Attempt.NotTouched, None)))
              case Left(YubiKey.NoAnswer.Unusable(of)) => Left(and(of))
              case Right(Right(a))                     =>
                lastOpened.set(Some(yk))
                Right(a)
              case Right(Left(r)) =>
                YubiKey.failure(
                  KeePassXcMessage.parse(KeePassXcMessage.Stderr(r.err.value)),
                  YubiKey.Setting.Entries(entry.value)
                ) match
                  case YubiKey.Failure.Next(why) =>
                    val refused = key(yk, why, Some(r.err))
                    log.info(Plugin.Log.Line(refused.sentence))
                    go(more, failed :+ refused)
                  case YubiKey.Failure.NoEntry(missing) =>
                    Left(name.failed(Problem.Plugin.Retrieval.Absent(Problem.Raw(s"$missing"))))
                  case YubiKey.Failure.Stop(why) => Left(and(key(yk, why, Some(r.err))))
      go(planned, Vector.empty)

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def classify(entry: KeePassXcSession.Entry)(err: String): Option[Problem] =
    if err.contains("invalid credentials") || err.contains("wrong key") || err.contains("unable to unlock") ||
      err.contains("enter password")
    then
      Some(
        name.said(
          Problem.Plugin.Claim.Misconfigured,
          "the database is locked — set password = \"env:<NAME>\" (or a key-file) for this vault"
        )
      )
    else if err.contains("could not find entry") || err.contains("not found") then
      Some(name.failed(Problem.Plugin.Retrieval.Absent(Problem.Raw(s"$entry"))))
    else None
