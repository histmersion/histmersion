package com.histmersion
package plugins.vaults

import com.histmersion.plugin.Problem
import scala.annotation.tailrec
import com.histmersion.plugin.*
import com.histmersion.plugin.Plugin.Config.given

import java.nio.file.Path
import scala.concurrent.duration.*
import com.histmersion.plugin.Processes

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

  private def file = Path.of(db.value).getFileName.toString

  def resolve(ref: Secret.Vault.ResolveRef): Either[Problem, Secret.Resolved] =
    request(ref).flatMap { (entry, attr) =>
      if yubikeys.nonEmpty then
        withYubiKey(s"read '$ref'", s"for '$ref'", entry)(yk =>
          Subprocess
            .run(argv(attr, entry, Some(yk)), Map.empty, stdin, yubikeyTimeout)
            .map(r => if r.exit.value == 0 then Right(r) else Left(r))
            .left
            .map(e => Problem(e.toString))
        )
          .flatMap(r => Vaults.answer(name, ref, Right(r), _ => None))
      else Vaults.answer(name, ref, Subprocess.run(argv(attr, entry, None), Map.empty, stdin, timeout), classify(entry))
    }

  /** Every reference through ONE `keepassxc-cli open` session ([[KeePassXcSession]]): one unlock, one YubiKey touch. A
    * single reference (or one whose entry holds a line break) is read as [[resolve]] reads it; a failed unlock is every
    * reference's error, a missing entry only its own.
    */
  override def resolveAll(
      refs: List[Secret.Vault.ResolveRef]
  ): Map[Secret.Vault.ResolveRef, Either[Problem, Secret.Resolved]] =
    val asked = refs.distinct
    val requests = asked.map(r => r -> request(r))
    val batched = requests.collect {
      case (r, Right((e, a))) if KeePassXcSession.sendable(e, a) => r -> (e, a)
    }
    val single = asked.filterNot(r => batched.exists(_._1 == r)).map(r => r -> resolve(r)).toMap
    if batched.size <= 1 then single ++ batched.map((r, _) => r -> resolve(r))
    else single ++ session(batched)

  /** `<entry>#<attribute>` as two arguments that cannot read as options. */
  private def request(
      ref: Secret.Vault.ResolveRef
  ): Either[Problem, (KeePassXcSession.Entry, Vaults.Field)] =
    val (entry, attr) = Vaults.entryAndField(ref, attribute)
    for
      _ <- Subprocess
        .plain(Vaults.Operation("the entry"), Subprocess.Text(entry.value))
        .left
        .map(e => Problem(e.value))
      _ <- Subprocess
        .plain(Vaults.Operation("the attribute"), Subprocess.Text(attr.value))
        .left
        .map(e => Problem(e.value))
    yield (entry, attr)

  private def stdin = password.map(p => Processes.RunStdin(p.value + "\n"))

  private def unlocking(yk: Option[YubiKey.Slot]): List[String] =
    keyFile.toList.flatMap(k => List("-k", k.value)) ++ yk.toList.flatMap(y => List("-y", y.shown.value)) ++
      (if password.isEmpty then List("--no-password") else Nil)

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
  private def open(
      yk: Option[YubiKey.Slot],
      wait: FiniteDuration
  ): Either[Problem, Either[Subprocess.Result, Opened]] =
    Subprocess.Session
      .start(
        (List(exe.value, "open", "-q") ++ unlocking(yk) ++ List(db.value)).map(a => Processes.StartArgv(a)),
        Map.empty
      )
      .left
      .map(e => Problem(e.toString))
      .flatMap { s =>
        password.foreach(pw => s.send(Processes.Session.SendLine(pw.value)))
        val up = s
          .awaitOut(wait)(o => KeePassXcSession.prompt(Processes.Result.Out(o.value)).isDefined)
        KeePassXcSession.prompt(s.out) match
          case Some(p) if up && s.alive => Right(Right(Opened(s, p)))
          case _ if !s.alive            =>
            Right(
              Left(
                Subprocess.Result(
                  Processes.Result.Exit(s.exit(timeout).fold(-1)(_.value)),
                  Processes.Result.Out(""),
                  Processes.Result.Err(s.err.value)
                )
              )
            )
          case _ =>
            val _ = s.close(1.second)
            Left(Problem(s"keepassxc-cli did not answer within ${wait.toSeconds} s"))
      }

  private def session(
      batched: List[(Secret.Vault.ResolveRef, (KeePassXcSession.Entry, Vaults.Field))]
  ): Map[Secret.Vault.ResolveRef, Either[Problem, Secret.Resolved]] =
    val what = s"open $file for ${batched.size} references"
    val opened: Either[Problem, Opened] =
      if yubikeys.nonEmpty then
        withYubiKey(
          what,
          s"for ${batched.size} references (${batched.map(_._1).mkString(", ")})",
          KeePassXcSession.Entry("")
        )(yk => open(Some(yk), yubikeyTimeout))
      else
        open(None, timeout) match
          case Left(why)      => Left(Problem(s"$name: could not $what (${why.value})"))
          case Right(Left(r)) =>
            Left(
              Problem(
                classify(KeePassXcSession.Entry(""))(r.err.value.toLowerCase(java.util.Locale.ROOT))
                  .getOrElse(
                    s"$name: could not $what (exit ${r.exit}: ${YubiKey.problem(Problem(r.err.value))})"
                  )
              )
            )
          case Right(Right(o)) => Right(o)
    opened match
      case Left(e) =>
        batched
          .map((r, _) => r -> (Left(e): Either[Problem, Secret.Resolved]))
          .toMap
      case Right(o) =>
        try readAll(o, batched)
        finally
          val _ = o.session.send(Processes.Session.SendLine("exit"))
          val _ = o.session.close(timeout)

  /** One request after another, each answered before the next is sent; a session that stops answering fails the rest.
    */
  private def readAll(
      o: Opened,
      batched: List[(Secret.Vault.ResolveRef, (KeePassXcSession.Entry, Vaults.Field))]
  ): Map[Secret.Vault.ResolveRef, Either[Problem, Secret.Resolved]] =
    val s = o.session
    @tailrec def read(
        rest: List[(Secret.Vault.ResolveRef, (KeePassXcSession.Entry, Vaults.Field))],
        acc: Map[Secret.Vault.ResolveRef, Either[Problem, Secret.Resolved]],
        mark: KeePassXcSession.SegmentFrom,
        broken: Option[String]
    ): Map[Secret.Vault.ResolveRef, Either[Problem, Secret.Resolved]] =
      (rest, broken) match
        case (Nil, _)                    => acc
        case ((ref, _) :: tail, Some(b)) =>
          read(
            tail,
            acc.updated(ref, Left(Problem(s"$name: could not read '$ref' ($b)"))),
            KeePassXcSession.SegmentFrom(0),
            broken
          )
        case ((ref, (entry, attr)) :: tail, None) =>
          val cmd = KeePassXcSession.command(attr, entry)
          val errMark = s.err.value.length
          val answered = s.send(Processes.Session.SendLine(cmd.value)).left.map(e => e.value).flatMap { _ =>
            val _ = s.awaitOut(timeout)(out =>
              KeePassXcSession.segment(Processes.Result.Out(out.value), mark, o.prompt).isDefined
            )
            val out = s.out
            KeePassXcSession
              .segment(out, mark, o.prompt)
              .map(seg => (seg, KeePassXcSession.SegmentFrom(out.value.length)))
              .toRight(if s.alive then s"keepassxc-cli did not answer within ${timeout.toSeconds} s"
              else s"keepassxc-cli stopped: ${YubiKey.problem(Problem(s.err.value.drop(errMark)))}")
          }
          answered match
            case Left(broken) =>
              read(
                tail,
                acc.updated(ref, Left(Problem(s"$name: could not read '$ref' ($broken)"))),
                mark,
                Some(broken)
              )
            case Right((seg, next)) =>
              val v = KeePassXcSession.answer(seg, cmd)
              val result: Either[Problem, Secret.Resolved] =
                if v.value.nonEmpty then Right(v)
                else
                  s.awaitErr(Processes.Session.AwaitErrLength(errMark), 500.millis)
                  val err = s.err.value.drop(errMark)
                  if err.trim.isEmpty then Left(Problem(s"$name: '$ref' has an empty value"))
                  else
                    Left(
                      Problem(
                        classify(entry)(err.toLowerCase(java.util.Locale.ROOT))
                          .getOrElse(s"$name: could not read '$ref' (${Subprocess.problem(Problem(err))})")
                      )
                    )
              read(tail, acc.updated(ref, result), next, None)
    read(batched, Map.empty, KeePassXcSession.SegmentFrom(s.out.value.length), None)

  /** The connected serials, when `ykman` can tell; `None` = unknown (then every entry is tried). */
  private def connected(): Option[Set[YubiKey.Slot.Serial]] =
    ykman.flatMap { y =>
      Subprocess.run(List(y.value, "list", "--serials").map(a => Processes.RunArgv(a)), Map.empty, None, timeout) match
        case Right(r) if r.exit.value == 0 => Some(YubiKey.serials(YubiKey.SerialsOut(r.out.value)))
        case Right(r)                      =>
          log.info(
            Plugin.Log.Line(
              s"ykman list failed (exit ${r.exit}: ${Subprocess.problem(Problem(r.err.value))}): trying the YubiKeys in order"
            )
          );
          None
        case Left(why) => log.info(Plugin.Log.Line(s"$why: trying the YubiKeys in order")); None
    }

  /** Each planned key in turn until `attempt` opens the database with it (`Right(Right)`); a failure (`Right(Left)`:
    * KeePassXC's answer) that no other key could fix stops at once, and so does a key not touched in time. The error
    * names every entry: tried (and why it failed) or not connected. `what` says what failed ("read '<ref>'"), `purpose`
    * what the log line asks the touch for.
    */
  private def withYubiKey[A](what: String, purpose: String, entry: KeePassXcSession.Entry)(
      attempt: YubiKey.Slot => Either[Problem, Either[Subprocess.Result, A]]
  ): Either[Problem, A] =
    val found = connected()
    val (planned, absent) = YubiKey.plan(yubikeys, found, lastOpened.get)
    val notConnected = absent.map(s => s"${s.shown}: not connected")
    def report(tried: List[String], tail: String) =
      val parts = (tried ++ notConnected).mkString("; ")
      Left(Problem(s"$name: could not $what with a YubiKey — $parts$tail"))
    if planned.isEmpty then
      val seen = found.fold("")(s =>
        if s.isEmpty then " (ykman sees no YubiKey)"
        else s" (ykman sees ${s.toList.sortBy(x => x.value: Long).mkString(", ")})"
      )
      Left(
        Problem(
          s"$name: none of the configured YubiKeys is connected$seen — ${yubikeys.map(_.shown).mkString(", ")}; plug one in"
        )
      )
    else
      @annotation.tailrec
      def go(rest: List[YubiKey.Slot], tried: List[String]): Either[Problem, A] = rest match
        case Nil        => report(tried, " — plug in (or touch) one of them")
        case yk :: more =>
          log.info(
            Plugin.Log.Line(
              s"opening $file with YubiKey ${yk.shown} $purpose — touch your YubiKey if it blinks " +
                s"(waiting up to ${yubikeyTimeout.toSeconds} s)"
            )
          )
          attempt(yk) match
            case Left(why) =>
              val what = if why.value.contains("did not answer") then s"not touched in time ($why)" else why.value
              report(tried :+ s"${yk.shown}: $what", "; not trying further keys")
            case Right(Right(a)) =>
              lastOpened.set(Some(yk))
              Right(a)
            case Right(Left(r)) =>
              val msg = YubiKey.problem(Problem(r.err.value))
              YubiKey.failure(
                Problem(r.err.value.toLowerCase(java.util.Locale.ROOT)),
                YubiKey.Setting.Entries(entry.value)
              ) match
                case YubiKey.Failure.Next(why) => go(more, tried :+ s"${yk.shown}: $why ($msg)")
                case YubiKey.Failure.Stop(why) =>
                  if why.value.startsWith("no entry") then Left(Problem(s"$name: $why"))
                  else report(tried :+ s"${yk.shown}: $why ($msg)", "; not trying further keys")
      go(planned, Nil)

  private def classify(entry: KeePassXcSession.Entry)(err: String): Option[String] =
    if err.contains("invalid credentials") || err.contains("wrong key") || err.contains("unable to unlock") ||
      err.contains("enter password")
    then Some(s"$name: the database is locked — set password = \"env:<NAME>\" (or a key-file) for this vault")
    else if err.contains("could not find entry") || err.contains("not found") then Some(s"$name: no entry '$entry'")
    else None
