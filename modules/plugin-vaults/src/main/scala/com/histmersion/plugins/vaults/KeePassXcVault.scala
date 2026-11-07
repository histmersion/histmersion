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

  private def file = Path.of((db).value).getFileName.toString

  def resolve(ref: com.histmersion.plugin.Secret.Vault.ResolveRef): Either[com.histmersion.plugins.vaults.KeePassXcVault.ResolveResultPart1, com.histmersion.plugins.vaults.KeePassXcVault.ResolveResultPart2] =
    request(_root_.com.histmersion.plugin.Secret.Vault.ResolveRef((ref).value)).flatMap { (entry, attr) =>
      if yubikeys.nonEmpty then
        withYubiKey(_root_.com.histmersion.plugins.vaults.Vaults.Operation(s"read '$ref'"), _root_.com.histmersion.plugins.vaults.KeePassXcVault.Purpose(s"for '$ref'"), _root_.com.histmersion.plugins.vaults.KeePassXcSession.Entry((entry).value))(yk =>
          Subprocess
            .run(argv(_root_.com.histmersion.plugins.vaults.KeePassXcVault.Attribute((attr).value), _root_.com.histmersion.plugins.vaults.KeePassXcSession.Entry((entry).value), Some(yk)), Map.empty, (stdin).map(refactorValue4 => _root_.com.histmersion.plugin.Processes.RunStdin(refactorValue4)), yubikeyTimeout)
            .map(r => if r.exit == 0 then Right(r) else Left(r))
        )
          .flatMap(r => Vaults.answer(name, ref, Right(r), _ => None))
      else Vaults.answer(name, ref, Subprocess.run(argv(_root_.com.histmersion.plugins.vaults.KeePassXcVault.Attribute((attr).value), _root_.com.histmersion.plugins.vaults.KeePassXcSession.Entry((entry).value), None), Map.empty, (stdin).map(refactorValue3 => _root_.com.histmersion.plugin.Processes.RunStdin(refactorValue3)), timeout), classify(_root_.com.histmersion.plugins.vaults.KeePassXcSession.Entry((entry).value)))
    }

  /** Every reference through ONE `keepassxc-cli open` session ([[KeePassXcSession]]): one unlock, one YubiKey touch. A
    * single reference (or one whose entry holds a line break) is read as [[resolve]] reads it; a failed unlock is every
    * reference's error, a missing entry only its own.
    */
  override def resolveAll(refs: List[String]): Map[String, Either[String, String]] =
    val asked = refs.distinct
    val requests = asked.map(r => r -> request(_root_.com.histmersion.plugin.Secret.Vault.ResolveRef(r)))
    val batched = requests.collect {
      case (r, Right((e, a))) if KeePassXcSession.sendable(e) && KeePassXcSession.sendable(a) => r -> (e, a)
    }
    val single = asked.filterNot(r => batched.exists(_._1 == r)).map(r => r -> resolve(_root_.com.histmersion.plugin.Secret.Vault.ResolveRef(r))).toMap
    if batched.size <= 1 then (single ++ batched.map((r, _) => r -> resolve(_root_.com.histmersion.plugin.Secret.Vault.ResolveRef(r)))).iterator.map { case (refactorKey, refactorItem) => (refactorKey, ((refactorItem).left.map(refactorError => (refactorError).value)).map(refactorValue => (refactorValue).value)) }.toMap
    else single ++ session((batched).map(refactorValue2 => { val refactorTuple = refactorValue2; (_root_.com.histmersion.plugin.Secret.Vault.ResolveRef(refactorTuple._1), { val refactorTuple2 = refactorTuple._2; (_root_.com.histmersion.plugins.vaults.KeePassXcSession.Entry((refactorTuple2._1).value), _root_.com.histmersion.plugins.vaults.KeePassXcVault.Attribute((refactorTuple2._2).value)) }) }))

  /** `<entry>#<attribute>` as two arguments that cannot read as options. */
  private def request(ref: com.histmersion.plugin.Secret.Vault.ResolveRef): Either[com.histmersion.plugins.vaults.KeePassXcVault.Problem, (com.histmersion.plugins.vaults.KeePassXcSession.Entry, com.histmersion.plugins.vaults.KeePassXcVault.Attribute)] =
    val (entry, attr) = Vaults.entryAndField(ref, attribute)
    for
      e <- Subprocess.plain(_root_.com.histmersion.plugins.vaults.Vaults.Operation("the entry"), entry)
      a <- Subprocess.plain(_root_.com.histmersion.plugins.vaults.Vaults.Operation("the attribute"), attr)
    yield (e, a)

  private def stdin = password.map((_).value + "\n")

  private def unlocking(yk: Option[YubiKey.Slot]): List[com.histmersion.plugins.vaults.KeePassXcVault.Unlock] =
    keyFile.toList.flatMap(k => List("-k", k)) ++ yk.toList.flatMap(y => List("-y", y.shown)) ++
      (if password.isEmpty then List("--no-password") else Nil)

  private def argv(attr: com.histmersion.plugins.vaults.KeePassXcVault.Attribute, entry: com.histmersion.plugins.vaults.KeePassXcSession.Entry, yk: Option[YubiKey.Slot]): List[com.histmersion.plugins.vaults.KeePassXcSession.Argument] =
    List(exe, "show", "-q", "-a", attr) ++ unlocking(yk) ++ List(db, entry)

  final private case class Opened(session: Subprocess.Session, prompt: Opened.Prompt)

  object Opened:
    type Prompt = Prompt.T
    object Prompt extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** Start `keepassxc-cli open` and wait for its prompt: `Right(Right)` = open, `Right(Left)` = it exited (its answer),
    * `Left` = it could not start or did not answer within `wait` (killed). The password line is written alone, before
    * any request: the unlock reads stdin ahead of the prompt.
    */
  private def open(yk: Option[YubiKey.Slot], wait: FiniteDuration): Either[com.histmersion.plugins.vaults.KeePassXcVault.Problem, Either[Subprocess.Result, Opened]] =
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

  private def session(batched: List[(com.histmersion.plugin.Secret.Vault.ResolveRef, (com.histmersion.plugins.vaults.KeePassXcSession.Entry, com.histmersion.plugins.vaults.KeePassXcVault.Attribute))]): Map[com.histmersion.plugins.vaults.KeePassXcVault.Problem, Either[com.histmersion.plugin.Secret.Vault.ResolveRef, com.histmersion.plugins.vaults.KeePassXcVault.SessionResultPart2Part2]] =
    val what = s"open $file for ${batched.size} references"
    val opened: Either[com.histmersion.plugins.vaults.KeePassXcVault.Problem, Opened] =
      if yubikeys.nonEmpty then
        withYubiKey(_root_.com.histmersion.plugins.vaults.Vaults.Operation(what), _root_.com.histmersion.plugins.vaults.KeePassXcVault.Purpose(s"for ${batched.size} references (${batched.map(_._1).mkString(", ")})"), _root_.com.histmersion.plugins.vaults.KeePassXcSession.Entry(""))(yk =>
          (open(Some(yk), yubikeyTimeout)).left.map(refactorError3 => _root_.com.histmersion.plugins.vaults.KeePassXcVault.Problem((refactorError3).value))
        )
      else
        open(None, timeout) match
          case Left(why)      => Left(_root_.com.histmersion.plugins.vaults.KeePassXcVault.Problem(s"$name: could not $what ($why)"))
          case Right(Left(r)) =>
            Left(
              classify(_root_.com.histmersion.plugins.vaults.KeePassXcSession.Entry(""))(r.err.toLowerCase(java.util.Locale.ROOT))
                .getOrElse(s"$name: could not $what (exit ${r.exit}: ${YubiKey.problem(r.err)})")
            )
          case Right(Right(o)) => Right(o)
    opened match
      case Left(e)  => batched.map((r, _) => r -> Left(e)).toMap
      case Right(o) =>
        try (readAll(o, (batched).map(refactorValue9 => { val refactorTuple3 = refactorValue9; (_root_.com.histmersion.plugin.Secret.Vault.ResolveRef((refactorTuple3._1).value), { val refactorTuple4 = refactorTuple3._2; (_root_.com.histmersion.plugins.vaults.KeePassXcSession.Entry((refactorTuple4._1).value), _root_.com.histmersion.plugins.vaults.KeePassXcVault.Attribute((refactorTuple4._2).value)) }) }))).iterator.map { case (refactorKey2, refactorItem2) => (_root_.com.histmersion.plugins.vaults.KeePassXcVault.Problem((refactorKey2).value), ((refactorItem2).left.map(refactorError5 => _root_.com.histmersion.plugin.Secret.Vault.ResolveRef((refactorError5).value))).map(refactorValue10 => _root_.com.histmersion.plugins.vaults.KeePassXcVault.SessionResultPart2Part2((refactorValue10).value))) }.toMap
        finally
          val _ = o.session.send(_root_.com.histmersion.plugin.Processes.Session.SendLine("exit"))
          val _ = o.session.close(timeout)

  /** One request after another, each answered before the next is sent; a session that stops answering fails the rest.
    */
  private def readAll(o: Opened, batched: List[(com.histmersion.plugin.Secret.Vault.ResolveRef, (com.histmersion.plugins.vaults.KeePassXcSession.Entry, com.histmersion.plugins.vaults.KeePassXcVault.Attribute))]): Map[com.histmersion.plugins.vaults.KeePassXcVault.Problem, Either[com.histmersion.plugin.Secret.Vault.ResolveRef, com.histmersion.plugins.vaults.KeePassXcVault.ReadAllResultPart2Part2]] =
    val s = o.session
    @tailrec def read(
        rest: List[(com.histmersion.plugin.Secret.Vault.ResolveRef, (com.histmersion.plugins.vaults.KeePassXcSession.Entry, com.histmersion.plugins.vaults.KeePassXcVault.Attribute))],
        acc: Map[com.histmersion.plugin.Secret.Vault.ResolveRef, Either[com.histmersion.plugins.vaults.KeePassXcSession.Entry, com.histmersion.plugins.vaults.KeePassXcVault.Attribute]],
        mark: com.histmersion.plugins.vaults.KeePassXcVault.ReadAllReadMark,
        broken: Option[com.histmersion.plugins.vaults.KeePassXcVault.Problem]
    ): Map[com.histmersion.plugins.vaults.KeePassXcVault.Problem, Either[com.histmersion.plugins.vaults.KeePassXcVault.ReadAllReadResultPart2Part1, com.histmersion.plugins.vaults.KeePassXcVault.ReadAllReadResultPart2Part2]] =
      (rest, broken) match
        case (Nil, _)                    => acc
        case ((ref, _) :: tail, Some(b)) =>
          read(tail, acc.updated(ref, Left(_root_.com.histmersion.plugins.vaults.KeePassXcSession.Entry(s"$name: could not read '$ref' ($b)"))), _root_.com.histmersion.plugins.vaults.KeePassXcVault.ReadAllReadMark(0), broken)
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
              read(tail, acc.updated(ref, Left(_root_.com.histmersion.plugins.vaults.KeePassXcSession.Entry(s"$name: could not read '$ref' ($broken)"))), mark, Some(broken))
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
  private def connected(): Option[Set[com.histmersion.plugins.vaults.KeePassXcVault.YubiKeySerial]] =
    ykman.flatMap { y =>
      Subprocess.run(List(y, _root_.com.histmersion.plugin.Processes.RunArgv("list"), _root_.com.histmersion.plugin.Processes.RunArgv("--serials")), Map.empty, None, timeout) match
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
  private def withYubiKey[A](what: com.histmersion.plugins.vaults.Vaults.Operation, purpose: com.histmersion.plugins.vaults.KeePassXcVault.Purpose, entry: com.histmersion.plugins.vaults.KeePassXcSession.Entry)(
      attempt: YubiKey.Slot => Either[com.histmersion.plugins.vaults.KeePassXcVault.Problem, Either[Subprocess.Result, A]]
  ): Either[com.histmersion.plugins.vaults.KeePassXcVault.Problem, A] =
    val found = connected()
    val (planned, absent) = YubiKey.plan(yubikeys, found, lastOpened.get)
    val notConnected = absent.map(s => s"${s.shown}: not connected")
    def report(tried: List[com.histmersion.plugins.vaults.KeePassXcVault.Attempt], tail: com.histmersion.plugins.vaults.KeePassXcVault.Hint) =
      val parts = (tried ++ notConnected).mkString("; ")
      Left(s"$name: could not $what with a YubiKey — $parts$tail")
    if planned.isEmpty then
      val seen = found.fold("")(s =>
        if s.isEmpty then " (ykman sees no YubiKey)" else s" (ykman sees ${s.toList.sorted.mkString(", ")})"
      )
      Left(
        _root_.com.histmersion.plugins.vaults.KeePassXcVault.Problem(s"$name: none of the configured YubiKeys is connected$seen — ${yubikeys.map(_.shown).mkString(", ")}; plug one in")
      )
    else
      @annotation.tailrec
      def go(rest: List[YubiKey.Slot], tried: List[com.histmersion.plugins.vaults.KeePassXcVault.Attempt]): Either[com.histmersion.plugins.vaults.KeePassXcVault.Problem, A] = rest match
        case Nil        => report(tried, _root_.com.histmersion.plugins.vaults.KeePassXcVault.Hint(" — plug in (or touch) one of them"))
        case yk :: more =>
          log.info(
            _root_.com.histmersion.plugin.Plugin.Log.InfoMessage(s"opening $file with YubiKey ${yk.shown} $purpose — touch your YubiKey if it blinks " +
              s"(waiting up to ${yubikeyTimeout.toSeconds} s)")
          )
          attempt(yk) match
            case Left(why) =>
              val what = if (why).value.contains("did not answer") then s"not touched in time ($why)" else why
              report(tried :+ s"${yk.shown}: $what", _root_.com.histmersion.plugins.vaults.KeePassXcVault.Hint("; not trying further keys"))
            case Right(Right(a)) =>
              lastOpened.set(Some(yk))
              Right(a)
            case Right(Left(r)) =>
              val msg = YubiKey.problem(r.err)
              YubiKey.failure(r.err.toLowerCase(java.util.Locale.ROOT), entry) match
                case YubiKey.Failure.Next(why) => go(more, tried :+ s"${yk.shown}: $why ($msg)")
                case YubiKey.Failure.Stop(why) =>
                  if why.startsWith("no entry") then Left(s"$name: $why")
                  else report(tried :+ s"${yk.shown}: $why ($msg)", _root_.com.histmersion.plugins.vaults.KeePassXcVault.Hint("; not trying further keys"))
      go(planned, Nil)

  private def classify(entry: com.histmersion.plugins.vaults.KeePassXcSession.Entry)(err: com.histmersion.plugins.vaults.KeePassXcVault.Problem): Option[com.histmersion.plugins.vaults.KeePassXcVault.Problem] =
    if (err).value.contains("invalid credentials") || (err).value.contains("wrong key") || (err).value.contains("unable to unlock") ||
      (err).value.contains("enter password")
    then Some(_root_.com.histmersion.plugins.vaults.KeePassXcVault.Problem(s"$name: the database is locked — set password = \"env:<NAME>\" (or a key-file) for this vault"))
    else if (err).value.contains("could not find entry") || (err).value.contains("not found") then Some(_root_.com.histmersion.plugins.vaults.KeePassXcVault.Problem(s"$name: no entry '$entry'"))
    else None

object KeePassXcVault:
  type Attempt = Attempt.T
  object Attempt extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Hint = Hint.T
  object Hint extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Problem = Problem.T
  object Problem extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Purpose = Purpose.T
  object Purpose extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Unlock = Unlock.T
  object Unlock extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type YubiKeySerial = YubiKeySerial.T
  object YubiKeySerial extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ReadAllReadResultPart2Part1 = ReadAllReadResultPart2Part1.T
  object ReadAllReadResultPart2Part1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ReadAllReadResultPart2Part2 = ReadAllReadResultPart2Part2.T
  object ReadAllReadResultPart2Part2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ReadAllReadMark = ReadAllReadMark.T
  object ReadAllReadMark extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

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
  type SessionResultPart2Part2 = SessionResultPart2Part2.T
  object SessionResultPart2Part2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ReadAllResultPart2Part2 = ReadAllResultPart2Part2.T
  object ReadAllResultPart2Part2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ConnectedResult = ConnectedResult.T
  object ConnectedResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]:
    given _root_.scala.math.Ordering[T] = _root_.scala.math.Ordering.by[T, _root_.scala.Long](_.value)
