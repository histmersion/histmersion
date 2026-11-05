package com.histmersion.plugins.vaults

import scala.annotation.tailrec
import com.histmersion.plugin.*
import com.histmersion.plugin.Plugin.Config.given

import java.nio.file.Path
import scala.concurrent.duration.*

final class KeePassXcVault(
    val name: KeePassXcVault.Name,
    exe: KeePassXcVault.Exe,
    db: KeePassXcVault.Db,
    keyFile: Option[KeePassXcVault.KeyFile],
    password: Option[KeePassXcVault.Password],
    attribute: KeePassXcVault.Attribute,
    timeout: FiniteDuration,
    yubikeys: List[YubiKey.Slot],
    ykman: Option[KeePassXcVault.Ykman],
    yubikeyTimeout: FiniteDuration,
    log: Plugin.Log
)(using processes: com.histmersion.plugin.Processes) extends Secret.Vault:

  /** The YubiKey that opened the database last, tried first next time (a resolve may run on any thread). */
  private val lastOpened = new java.util.concurrent.atomic.AtomicReference[Option[YubiKey.Slot]](None)

  private def file = Path.of(db).getFileName.toString

  def resolve(ref: com.histmersion.plugins.vaults.KeePassXcVault.ResolveRef): Either[com.histmersion.plugins.vaults.KeePassXcVault.ResolveResultPart1, com.histmersion.plugins.vaults.KeePassXcVault.ResolveResultPart2] =
    request(ref).flatMap { (entry, attr) =>
      if yubikeys.nonEmpty then
        withYubiKey(s"read '$ref'", s"for '$ref'", entry)(yk =>
          Subprocess
            .run(argv(attr, entry, Some(yk)), Map.empty, stdin, yubikeyTimeout)
            .map(r => if r.exit == 0 then Right(r) else Left(r))
        )
          .flatMap(r => Vaults.answer(name, ref, Right(r), _ => None))
      else Vaults.answer(name, ref, Subprocess.run(argv(attr, entry, None), Map.empty, stdin, timeout), classify(entry))
    }

  /** Every reference through ONE `keepassxc-cli open` session ([[KeePassXcSession]]): one unlock, one YubiKey touch. A
    * single reference (or one whose entry holds a line break) is read as [[resolve]] reads it; a failed unlock is every
    * reference's error, a missing entry only its own.
    */
  override def resolveAll(refs: List[String]): Map[String, Either[String, String]] =
    val asked = refs.distinct
    val requests = asked.map(r => r -> request(r))
    val batched = requests.collect {
      case (r, Right((e, a))) if KeePassXcSession.sendable(e) && KeePassXcSession.sendable(a) => r -> (e, a)
    }
    val single = asked.filterNot(r => batched.exists(_._1 == r)).map(r => r -> resolve(r)).toMap
    if batched.size <= 1 then single ++ batched.map((r, _) => r -> resolve(r))
    else single ++ session(batched)

  /** `<entry>#<attribute>` as two arguments that cannot read as options. */
  private def request(ref: com.histmersion.plugins.vaults.KeePassXcVault.RequestRef): Either[com.histmersion.plugins.vaults.KeePassXcVault.RequestResultPart1, (com.histmersion.plugins.vaults.KeePassXcVault.RequestResultPart2Part1, com.histmersion.plugins.vaults.KeePassXcVault.RequestResultPart2Part2)] =
    val (entry, attr) = Vaults.entryAndField(ref, attribute)
    for
      e <- Subprocess.plain("the entry", entry)
      a <- Subprocess.plain("the attribute", attr)
    yield (e, a)

  private def stdin = password.map(_ + "\n")

  private def unlocking(yk: Option[YubiKey.Slot]): List[com.histmersion.plugins.vaults.KeePassXcVault.UnlockingResult] =
    keyFile.toList.flatMap(k => List("-k", k)) ++ yk.toList.flatMap(y => List("-y", y.shown)) ++
      (if password.isEmpty then List("--no-password") else Nil)

  private def argv(attr: com.histmersion.plugins.vaults.KeePassXcVault.ArgvAttr, entry: com.histmersion.plugins.vaults.KeePassXcVault.ArgvEntry, yk: Option[YubiKey.Slot]): List[com.histmersion.plugins.vaults.KeePassXcVault.ArgvResult] =
    List(exe, "show", "-q", "-a", attr) ++ unlocking(yk) ++ List(db, entry)

  final private case class Opened(session: Subprocess.Session, prompt: Opened.Prompt)

  object Opened:
    type Prompt = Prompt.T
    object Prompt extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** Start `keepassxc-cli open` and wait for its prompt: `Right(Right)` = open, `Right(Left)` = it exited (its answer),
    * `Left` = it could not start or did not answer within `wait` (killed). The password line is written alone, before
    * any request: the unlock reads stdin ahead of the prompt.
    */
  private def open(yk: Option[YubiKey.Slot], wait: FiniteDuration): Either[com.histmersion.plugins.vaults.KeePassXcVault.OpenResultPart1, Either[Subprocess.Result, Opened]] =
    Subprocess.Session.start(List(exe, "open", "-q") ++ unlocking(yk) ++ List(db), Map.empty).flatMap { s =>
      password.foreach(pw => s.send(pw))
      val up = s.awaitOut(wait)(o => KeePassXcSession.prompt(o).isDefined)
      KeePassXcSession.prompt(s.out) match
        case Some(p) if up && s.alive => Right(Right(Opened(s, p)))
        case _ if !s.alive            =>
          Right(Left(Subprocess.Result(s.exit(timeout).getOrElse(-1), "", s.err)))
        case _ =>
          val _ = s.close(1.second)
          Left(s"keepassxc-cli did not answer within ${wait.toSeconds} s")
    }

  private def session(batched: List[(com.histmersion.plugins.vaults.KeePassXcVault.SessionBatchedPart1, (com.histmersion.plugins.vaults.KeePassXcVault.SessionBatchedPart2Part1, com.histmersion.plugins.vaults.KeePassXcVault.SessionBatchedPart2Part2))]): Map[com.histmersion.plugins.vaults.KeePassXcVault.SessionResultPart1, Either[com.histmersion.plugins.vaults.KeePassXcVault.SessionResultPart2Part1, com.histmersion.plugins.vaults.KeePassXcVault.SessionResultPart2Part2]] =
    val what = s"open $file for ${batched.size} references"
    val opened: Either[com.histmersion.plugins.vaults.KeePassXcVault.SessionOpenedPart1, Opened] =
      if yubikeys.nonEmpty then
        withYubiKey(what, s"for ${batched.size} references (${batched.map(_._1).mkString(", ")})", "")(yk =>
          open(Some(yk), yubikeyTimeout)
        )
      else
        open(None, timeout) match
          case Left(why)      => Left(s"$name: could not $what ($why)")
          case Right(Left(r)) =>
            Left(
              classify("")(r.err.toLowerCase(java.util.Locale.ROOT))
                .getOrElse(s"$name: could not $what (exit ${r.exit}: ${YubiKey.problem(r.err)})")
            )
          case Right(Right(o)) => Right(o)
    opened match
      case Left(e)  => batched.map((r, _) => r -> Left(e)).toMap
      case Right(o) =>
        try readAll(o, batched)
        finally
          val _ = o.session.send("exit")
          val _ = o.session.close(timeout)

  /** One request after another, each answered before the next is sent; a session that stops answering fails the rest.
    */
  private def readAll(o: Opened, batched: List[(com.histmersion.plugins.vaults.KeePassXcVault.ReadAllBatchedPart1, (com.histmersion.plugins.vaults.KeePassXcVault.ReadAllBatchedPart2Part1, com.histmersion.plugins.vaults.KeePassXcVault.ReadAllBatchedPart2Part2))]): Map[com.histmersion.plugins.vaults.KeePassXcVault.ReadAllResultPart1, Either[com.histmersion.plugins.vaults.KeePassXcVault.ReadAllResultPart2Part1, com.histmersion.plugins.vaults.KeePassXcVault.ReadAllResultPart2Part2]] =
    val s = o.session
    @tailrec def read(
        rest: List[(com.histmersion.plugins.vaults.KeePassXcVault.ReadAllReadRestPart1, (com.histmersion.plugins.vaults.KeePassXcVault.ReadAllReadRestPart2Part1, com.histmersion.plugins.vaults.KeePassXcVault.ReadAllReadRestPart2Part2))],
        acc: Map[com.histmersion.plugins.vaults.KeePassXcVault.ReadAllReadAccPart1, Either[com.histmersion.plugins.vaults.KeePassXcVault.ReadAllReadAccPart2Part1, com.histmersion.plugins.vaults.KeePassXcVault.ReadAllReadAccPart2Part2]],
        mark: com.histmersion.plugins.vaults.KeePassXcVault.ReadAllReadMark,
        broken: Option[com.histmersion.plugins.vaults.KeePassXcVault.ReadAllReadBroken]
    ): Map[com.histmersion.plugins.vaults.KeePassXcVault.ReadAllReadResultPart1, Either[com.histmersion.plugins.vaults.KeePassXcVault.ReadAllReadResultPart2Part1, com.histmersion.plugins.vaults.KeePassXcVault.ReadAllReadResultPart2Part2]] =
      (rest, broken) match
        case (Nil, _)                    => acc
        case ((ref, _) :: tail, Some(b)) =>
          read(tail, acc.updated(ref, Left(s"$name: could not read '$ref' ($b)")), 0, broken)
        case ((ref, (entry, attr)) :: tail, None) =>
          val cmd = KeePassXcSession.command(attr, entry)
          val errMark = s.err.length
          val answered = s.send(cmd).flatMap { _ =>
            val _ = s.awaitOut(timeout)(out => KeePassXcSession.segment(out, mark, o.prompt).isDefined)
            val out = s.out
            KeePassXcSession
              .segment(out, mark, o.prompt)
              .map(seg => (seg, out.length))
              .toRight(if s.alive then s"keepassxc-cli did not answer within ${timeout.toSeconds} s"
              else s"keepassxc-cli stopped: ${YubiKey.problem(s.err.drop(errMark))}")
          }
          answered match
            case Left(broken) =>
              read(tail, acc.updated(ref, Left(s"$name: could not read '$ref' ($broken)")), mark, Some(broken))
            case Right((seg, next)) =>
              val v = KeePassXcSession.answer(seg, cmd)
              val result =
                if v.nonEmpty then Right(v)
                else
                  s.awaitErr(errMark, 500.millis)
                  val err = s.err.drop(errMark)
                  if err.trim.isEmpty then Left(s"$name: '$ref' has an empty value")
                  else
                    Left(
                      classify(entry)(err.toLowerCase(java.util.Locale.ROOT))
                        .getOrElse(s"$name: could not read '$ref' (${Subprocess.problem(err)})")
                    )
              read(tail, acc.updated(ref, result), next, None)
    read(batched, Map.empty, s.out.length, None)

  /** The connected serials, when `ykman` can tell; `None` = unknown (then every entry is tried). */
  private def connected(): Option[Set[com.histmersion.plugins.vaults.KeePassXcVault.ConnectedResult]] =
    ykman.flatMap { y =>
      Subprocess.run(List(y, "list", "--serials"), Map.empty, None, timeout) match
        case Right(r) if r.exit == 0 => Some(YubiKey.serials(r.out))
        case Right(r)                =>
          log.info(s"ykman list failed (exit ${r.exit}: ${Subprocess.problem(r.err)}): trying the YubiKeys in order");
          None
        case Left(why) => log.info(s"$why: trying the YubiKeys in order"); None
    }

  /** Each planned key in turn until `attempt` opens the database with it (`Right(Right)`); a failure (`Right(Left)`:
    * KeePassXC's answer) that no other key could fix stops at once, and so does a key not touched in time. The error
    * names every entry: tried (and why it failed) or not connected. `what` says what failed ("read '<ref>'"), `purpose`
    * what the log line asks the touch for.
    */
  private def withYubiKey[A](what: com.histmersion.plugins.vaults.KeePassXcVault.WithYubiKeyWhat, purpose: com.histmersion.plugins.vaults.KeePassXcVault.WithYubiKeyPurpose, entry: com.histmersion.plugins.vaults.KeePassXcVault.WithYubiKeyEntry)(
      attempt: YubiKey.Slot => Either[com.histmersion.plugins.vaults.KeePassXcVault.WithYubiKeyAttemptResultPart1, Either[Subprocess.Result, A]]
  ): Either[com.histmersion.plugins.vaults.KeePassXcVault.WithYubiKeyResultPart1, A] =
    val found = connected()
    val (planned, absent) = YubiKey.plan(yubikeys, found, lastOpened.get)
    val notConnected = absent.map(s => s"${s.shown}: not connected")
    def report(tried: List[com.histmersion.plugins.vaults.KeePassXcVault.WithYubiKeyReportTried], tail: com.histmersion.plugins.vaults.KeePassXcVault.WithYubiKeyReportTail) =
      val parts = (tried ++ notConnected).mkString("; ")
      Left(s"$name: could not $what with a YubiKey — $parts$tail")
    if planned.isEmpty then
      val seen = found.fold("")(s =>
        if s.isEmpty then " (ykman sees no YubiKey)" else s" (ykman sees ${s.toList.sorted.mkString(", ")})"
      )
      Left(
        s"$name: none of the configured YubiKeys is connected$seen — ${yubikeys.map(_.shown).mkString(", ")}; plug one in"
      )
    else
      @annotation.tailrec
      def go(rest: List[YubiKey.Slot], tried: List[com.histmersion.plugins.vaults.KeePassXcVault.WithYubiKeyGoTried]): Either[com.histmersion.plugins.vaults.KeePassXcVault.WithYubiKeyGoResultPart1, A] = rest match
        case Nil        => report(tried, " — plug in (or touch) one of them")
        case yk :: more =>
          log.info(
            s"opening $file with YubiKey ${yk.shown} $purpose — touch your YubiKey if it blinks " +
              s"(waiting up to ${yubikeyTimeout.toSeconds} s)"
          )
          attempt(yk) match
            case Left(why) =>
              val what = if why.contains("did not answer") then s"not touched in time ($why)" else why
              report(tried :+ s"${yk.shown}: $what", "; not trying further keys")
            case Right(Right(a)) =>
              lastOpened.set(Some(yk))
              Right(a)
            case Right(Left(r)) =>
              val msg = YubiKey.problem(r.err)
              YubiKey.failure(r.err.toLowerCase(java.util.Locale.ROOT), entry) match
                case YubiKey.Failure.Next(why) => go(more, tried :+ s"${yk.shown}: $why ($msg)")
                case YubiKey.Failure.Stop(why) =>
                  if why.startsWith("no entry") then Left(s"$name: $why")
                  else report(tried :+ s"${yk.shown}: $why ($msg)", "; not trying further keys")
      go(planned, Nil)

  private def classify(entry: com.histmersion.plugins.vaults.KeePassXcVault.ClassifyEntry)(err: com.histmersion.plugins.vaults.KeePassXcVault.ClassifyErr): Option[com.histmersion.plugins.vaults.KeePassXcVault.ClassifyResult] =
    if err.contains("invalid credentials") || err.contains("wrong key") || err.contains("unable to unlock") ||
      err.contains("enter password")
    then Some(s"$name: the database is locked — set password = \"env:<NAME>\" (or a key-file) for this vault")
    else if err.contains("could not find entry") || err.contains("not found") then Some(s"$name: no entry '$entry'")
    else None

object KeePassXcVault:
  type SessionOpenedPart1 = SessionOpenedPart1.T
  object SessionOpenedPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ReadAllReadResultPart1 = ReadAllReadResultPart1.T
  object ReadAllReadResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ReadAllReadResultPart2Part1 = ReadAllReadResultPart2Part1.T
  object ReadAllReadResultPart2Part1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ReadAllReadResultPart2Part2 = ReadAllReadResultPart2Part2.T
  object ReadAllReadResultPart2Part2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ReadAllReadRestPart1 = ReadAllReadRestPart1.T
  object ReadAllReadRestPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ReadAllReadRestPart2Part1 = ReadAllReadRestPart2Part1.T
  object ReadAllReadRestPart2Part1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ReadAllReadRestPart2Part2 = ReadAllReadRestPart2Part2.T
  object ReadAllReadRestPart2Part2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ReadAllReadAccPart1 = ReadAllReadAccPart1.T
  object ReadAllReadAccPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ReadAllReadAccPart2Part1 = ReadAllReadAccPart2Part1.T
  object ReadAllReadAccPart2Part1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ReadAllReadAccPart2Part2 = ReadAllReadAccPart2Part2.T
  object ReadAllReadAccPart2Part2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ReadAllReadMark = ReadAllReadMark.T
  object ReadAllReadMark extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ReadAllReadBroken = ReadAllReadBroken.T
  object ReadAllReadBroken extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type WithYubiKeyReportTried = WithYubiKeyReportTried.T
  object WithYubiKeyReportTried extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type WithYubiKeyReportTail = WithYubiKeyReportTail.T
  object WithYubiKeyReportTail extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type WithYubiKeyGoResultPart1 = WithYubiKeyGoResultPart1.T
  object WithYubiKeyGoResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type WithYubiKeyGoTried = WithYubiKeyGoTried.T
  object WithYubiKeyGoTried extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type Name = Name.T
  object Name extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Exe = Exe.T
  object Exe extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Db = Db.T
  object Db extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type KeyFile = KeyFile.T
  object KeyFile extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Password = Password.T
  object Password extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Attribute = Attribute.T
  object Attribute extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Ykman = Ykman.T
  object Ykman extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type ResolveResultPart1 = ResolveResultPart1.T
  object ResolveResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ResolveResultPart2 = ResolveResultPart2.T
  object ResolveResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ResolveRef = ResolveRef.T
  object ResolveRef extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RequestResultPart1 = RequestResultPart1.T
  object RequestResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RequestResultPart2Part1 = RequestResultPart2Part1.T
  object RequestResultPart2Part1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RequestResultPart2Part2 = RequestResultPart2Part2.T
  object RequestResultPart2Part2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RequestRef = RequestRef.T
  object RequestRef extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type UnlockingResult = UnlockingResult.T
  object UnlockingResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ArgvResult = ArgvResult.T
  object ArgvResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ArgvAttr = ArgvAttr.T
  object ArgvAttr extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ArgvEntry = ArgvEntry.T
  object ArgvEntry extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OpenResultPart1 = OpenResultPart1.T
  object OpenResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SessionResultPart1 = SessionResultPart1.T
  object SessionResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SessionResultPart2Part1 = SessionResultPart2Part1.T
  object SessionResultPart2Part1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SessionResultPart2Part2 = SessionResultPart2Part2.T
  object SessionResultPart2Part2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SessionBatchedPart1 = SessionBatchedPart1.T
  object SessionBatchedPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SessionBatchedPart2Part1 = SessionBatchedPart2Part1.T
  object SessionBatchedPart2Part1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SessionBatchedPart2Part2 = SessionBatchedPart2Part2.T
  object SessionBatchedPart2Part2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ReadAllResultPart1 = ReadAllResultPart1.T
  object ReadAllResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ReadAllResultPart2Part1 = ReadAllResultPart2Part1.T
  object ReadAllResultPart2Part1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ReadAllResultPart2Part2 = ReadAllResultPart2Part2.T
  object ReadAllResultPart2Part2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ReadAllBatchedPart1 = ReadAllBatchedPart1.T
  object ReadAllBatchedPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ReadAllBatchedPart2Part1 = ReadAllBatchedPart2Part1.T
  object ReadAllBatchedPart2Part1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ReadAllBatchedPart2Part2 = ReadAllBatchedPart2Part2.T
  object ReadAllBatchedPart2Part2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ConnectedResult = ConnectedResult.T
  object ConnectedResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type WithYubiKeyResultPart1 = WithYubiKeyResultPart1.T
  object WithYubiKeyResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type WithYubiKeyWhat = WithYubiKeyWhat.T
  object WithYubiKeyWhat extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type WithYubiKeyPurpose = WithYubiKeyPurpose.T
  object WithYubiKeyPurpose extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type WithYubiKeyEntry = WithYubiKeyEntry.T
  object WithYubiKeyEntry extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type WithYubiKeyAttemptResultPart1 = WithYubiKeyAttemptResultPart1.T
  object WithYubiKeyAttemptResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ClassifyResult = ClassifyResult.T
  object ClassifyResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ClassifyEntry = ClassifyEntry.T
  object ClassifyEntry extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ClassifyErr = ClassifyErr.T
  object ClassifyErr extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
