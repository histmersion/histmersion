package com.histmersion.plugins.vaults

import com.histmersion.plugin.*
import com.histmersion.plugin.PluginConfig.given
import hearth.kindlings.sconfigderivation.ConfigReader

import java.nio.file.{Files, Path}
import scala.concurrent.duration.*

/** The first-party secret vaults (docs/research-plugins.md §3.7): each a plugin, built into the server
  * and DISABLED until `histmersion.plugins.<id>.enabled = true`. Each one runs the vault's own CLI as a
  * subprocess ([[Subprocess]]) and registers a [[SecretVault]] under its id, so `<id>:<ref>` references
  * in any plugin's config resolve through it. No master password is ever stored: a tool that needs one
  * to unlock gets it from a [[Secret]] (`env:…`), or the vault answers that it is locked. */
object Vaults:
  /** The version these plugins report (they ship with the server, and change with it). */
  val Version = "1.0.0"

  /** The vault's own settings, shared by all three. */
  private[vaults] def timeout(seconds: Int): FiniteDuration = seconds.max(1).seconds

  /** Pure: `<entry>#<field>`, the field defaulting to `dflt` (split at the LAST `#`). */
  def entryAndField(ref: String, dflt: String): (String, String) =
    ref.lastIndexOf('#') match
      case -1 => (ref.trim, dflt)
      case i  => (ref.take(i).trim, Option(ref.drop(i + 1).trim).filter(_.nonEmpty).getOrElse(dflt))

  /** A Secret given in a vault's own settings (its unlock password, its session), resolved through the
    * host; `None` when the setting is `null`. */
  private[vaults] def optional(s: Option[Secret], ctx: PluginContext): Either[String, Option[String]] =
    s.fold(Right(None))(ctx.secret(_).map(Some(_)))

  /** Pure: `~/x` against HOME. */
  private[vaults] def expand(p: String, home: Option[String]): String =
    home.filter(_ => p == "~" || p.startsWith("~/")).fold(p)(h => h + p.drop(1))

  /** Run the tool and turn its answer into the value or an error naming the reference — never the value. */
  private[vaults] def answer(vault: String, ref: String, r: Either[String, Subprocess.Result],
      classify: String => Option[String]): Either[String, String] =
    r.flatMap { res =>
      if res.exit == 0 then
        val v = Subprocess.value(res.out)
        if v.isEmpty then Left(s"$vault: '$ref' has an empty value") else Right(v)
      else
        val low = res.err.toLowerCase(java.util.Locale.ROOT)
        Left(classify(low).getOrElse(s"$vault: could not read '$ref' (exit ${res.exit}: ${Subprocess.problem(res.err)})"))
    }

// ── KeePassXC ───────────────────────────────────────────────────────────────────────────────────

/** `keepassxc:<entry>[#<attribute>]`. `database` is the `.kdbx` file; it is unlocked with `key-file`,
  * `password` (a Secret, e.g. `env:KEEPASSXC_PASSWORD` — never a literal you would not put in the file)
  * and/or a YubiKey's challenge-response (`yubikey`: `"<slot>[:<serial>]"` or a list of them, tried in
  * order — see [[YubiKey]]). With no password, `--no-password` is passed: the key file and/or the
  * YubiKey alone must open it. `ykman` (a name on the PATH or a full path; `""` = none) tells which
  * keys are connected, so only those are tried. */
final case class KeePassXcConfig(cli: String, database: String, keyFile: String, password: Option[Secret],
    attribute: String, timeoutSeconds: Int, yubikey: Option[YubiKey.Setting], yubikeyTimeoutSeconds: Int,
    ykman: String) derives ConfigReader

final class KeePassXcPlugin extends Plugin:
  type Config = KeePassXcConfig
  val info = PluginInfo("keepassxc", Vaults.Version, Spi.Version)

  def register(c: KeePassXcConfig, ctx: PluginContext): Either[String, Unit] =
    val home = ctx.env("HOME")
    for
      db  <- Option(c.database).map(_.trim).filter(_.nonEmpty).map(Vaults.expand(_, home))
               .toRight("set database = \"<path to the .kdbx file>\"")
      _   <- Either.cond(Files.isRegularFile(Path.of(db)), (), s"database $db does not exist")
      key  = Option(c.keyFile).map(_.trim).filter(_.nonEmpty).map(Vaults.expand(_, home))
      _   <- key.fold(Right(()))(k => Either.cond(Files.isRegularFile(Path.of(k)), (), s"key-file $k does not exist"))
      exe <- Subprocess.locate(c.cli, ctx.env("PATH"))
      yks <- YubiKey.parseAll(c.yubikey.fold(Nil)(_.entries))
      det <- if yks.isEmpty then Right(None) else detector(c.ykman, ctx)
      pw  <- Vaults.optional(c.password, ctx)
      _   <- ctx.secretVaults.add(info.id, KeePassXcVault(info.id, exe, db, key, pw, c.attribute,
               Vaults.timeout(c.timeoutSeconds), yks, det, Vaults.timeout(c.yubikeyTimeoutSeconds), ctx.log))
    yield ()

  /** `ykman`, when it can be found: a bare name missing from the PATH only means the keys are tried in
    * order; a path that was given and is wrong is an error. */
  private def detector(ykman: String, ctx: PluginContext): Either[String, Option[String]] =
    val t = Option(ykman).map(_.trim).getOrElse("")
    if t.isEmpty then
      ctx.log.info("YubiKey detection is off (ykman = \"\"): the configured YubiKeys are tried in order")
      Right(None)
    else Subprocess.locate(t, ctx.env("PATH")) match
      case Right(p) => Right(Some(p))
      case Left(_) if !t.contains('/') =>
        ctx.log.info("the configured YubiKeys are tried in order, the one that opened the database last first " +
          "(optional: with ykman on the PATH, only the connected ones are tried)")
        Right(None)
      case Left(e) => Left(s"ykman: $e")

final class KeePassXcVault(val name: String, exe: String, db: String, keyFile: Option[String],
    password: Option[String], attribute: String, timeout: FiniteDuration,
    yubikeys: List[YubiKey.Slot], ykman: Option[String], yubikeyTimeout: FiniteDuration, log: PluginLog)
    extends SecretVault:

  /** The YubiKey that opened the database last, tried first next time (a resolve may run on any thread). */
  private val lastOpened = new java.util.concurrent.atomic.AtomicReference[Option[YubiKey.Slot]](None)

  def resolve(ref: String): Either[String, String] =
    val (entry, attr) = Vaults.entryAndField(ref, attribute)
    for
      e <- Subprocess.plain("the entry", entry)
      a <- Subprocess.plain("the attribute", attr)
      v <- if yubikeys.nonEmpty then withYubiKey(ref, a, e)
           else Vaults.answer(name, ref, Subprocess.run(argv(a, e, None), Map.empty, stdin, timeout), classify(e))
    yield v

  private def stdin = password.map(_ + "\n")

  private def argv(attr: String, entry: String, yk: Option[YubiKey.Slot]): List[String] =
    List(exe, "show", "-q", "-a", attr) ++ keyFile.toList.flatMap(k => List("-k", k)) ++
      yk.toList.flatMap(y => List("-y", y.shown)) ++
      (if password.isEmpty then List("--no-password") else Nil) ++ List(db, entry)

  /** The connected serials, when `ykman` can tell; `None` = unknown (then every entry is tried). */
  private def connected(): Option[Set[Long]] =
    ykman.flatMap { y =>
      Subprocess.run(List(y, "list", "--serials"), Map.empty, None, timeout) match
        case Right(r) if r.exit == 0 => Some(YubiKey.serials(r.out))
        case Right(r) =>
          log.info(s"ykman list failed (exit ${r.exit}: ${Subprocess.problem(r.err)}): trying the YubiKeys in order"); None
        case Left(why) => log.info(s"$why: trying the YubiKeys in order"); None
    }

  /** Each planned key in turn until one opens the database; a failure that no other key could fix
    * stops at once. The error names every entry: tried (and why it failed) or not connected. */
  private def withYubiKey(ref: String, attr: String, entry: String): Either[String, String] =
    val found = connected()
    val (planned, absent) = YubiKey.plan(yubikeys, found, lastOpened.get)
    val notConnected = absent.map(s => s"${s.shown}: not connected")
    def report(tried: List[String], tail: String) =
      val parts = (tried ++ notConnected).mkString("; ")
      Left(s"$name: could not read '$ref' with a YubiKey — $parts$tail")
    if planned.isEmpty then
      val seen = found.fold("")(s => if s.isEmpty then " (ykman sees no YubiKey)" else s" (ykman sees ${s.toList.sorted.mkString(", ")})")
      Left(s"$name: none of the configured YubiKeys is connected$seen — ${yubikeys.map(_.shown).mkString(", ")}; plug one in")
    else
      @annotation.tailrec
      def go(rest: List[YubiKey.Slot], tried: List[String]): Either[String, String] = rest match
        case Nil => report(tried, " — plug in (or touch) one of them")
        case yk :: more =>
          log.info(s"opening ${Path.of(db).getFileName} with YubiKey ${yk.shown} for '$ref' — touch your YubiKey if it blinks " +
            s"(waiting up to ${yubikeyTimeout.toSeconds} s)")
          Subprocess.run(argv(attr, entry, Some(yk)), Map.empty, stdin, yubikeyTimeout) match
            case Left(why) =>
              val what = if why.contains("did not answer") then s"not touched in time ($why)" else why
              report(tried :+ s"${yk.shown}: $what", "; not trying further keys")
            case Right(r) if r.exit == 0 =>
              lastOpened.set(Some(yk))
              Vaults.answer(name, ref, Right(r), _ => None)
            case Right(r) =>
              val msg = YubiKey.problem(r.err)
              YubiKey.failure(r.err.toLowerCase(java.util.Locale.ROOT), entry) match
                case YubiKey.Failure.Next(why) => go(more, tried :+ s"${yk.shown}: $why ($msg)")
                case YubiKey.Failure.Stop(why) =>
                  if why.startsWith("no entry") then Left(s"$name: $why")
                  else report(tried :+ s"${yk.shown}: $why ($msg)", "; not trying further keys")
      go(planned, Nil)

  private def classify(entry: String)(err: String): Option[String] =
    if err.contains("invalid credentials") || err.contains("wrong key") || err.contains("unable to unlock") ||
       err.contains("enter password") then
      Some(s"$name: the database is locked — set password = \"env:<NAME>\" (or a key-file) for this vault")
    else if err.contains("could not find entry") || err.contains("not found") then
      Some(s"$name: no entry '$entry'")
    else None

// ── 1Password ───────────────────────────────────────────────────────────────────────────────────

/** `1password:op://<vault>/<item>/<field>` (the `op://` may be left out). The CLI signs in through the
  * desktop app, or with a service account: `service-account-token` (a Secret) becomes
  * `OP_SERVICE_ACCOUNT_TOKEN` for the `op` process only. */
final case class OnePasswordConfig(cli: String, account: String, serviceAccountToken: Option[Secret],
    timeoutSeconds: Int) derives ConfigReader

final class OnePasswordPlugin extends Plugin:
  type Config = OnePasswordConfig
  val info = PluginInfo("1password", Vaults.Version, Spi.Version)

  def register(c: OnePasswordConfig, ctx: PluginContext): Either[String, Unit] =
    for
      exe   <- Subprocess.locate(c.cli, ctx.env("PATH"))
      token <- Vaults.optional(c.serviceAccountToken, ctx)
      _     <- ctx.secretVaults.add(info.id, OnePasswordVault(info.id, exe, Option(c.account).map(_.trim).filter(_.nonEmpty),
                 token, Vaults.timeout(c.timeoutSeconds)))
    yield ()

final class OnePasswordVault(val name: String, exe: String, account: Option[String], token: Option[String],
    timeout: FiniteDuration) extends SecretVault:
  def resolve(ref: String): Either[String, String] =
    val r = ref.trim
    for
      _ <- Subprocess.plain("the reference", r)
      uri = if r.startsWith("op://") then r else s"op://$r"
      argv = List(exe, "read", uri) ++ account.toList.flatMap(a => List("--account", a))
      env  = token.map(t => Map("OP_SERVICE_ACCOUNT_TOKEN" -> t)).getOrElse(Map.empty)
      v <- Vaults.answer(name, ref, Subprocess.run(argv, env, None, timeout), classify(uri))
    yield v

  private def classify(uri: String)(err: String): Option[String] =
    if err.contains("not currently signed in") || err.contains("sign in") || err.contains("authorization") ||
       err.contains("session expired") || err.contains("locked") then
      Some(s"$name: not signed in — unlock the 1Password app, or set service-account-token = \"env:<NAME>\"")
    else if err.contains("isn't an item") || err.contains("could not find") || err.contains("not found") then
      Some(s"$name: no item at $uri")
    else None

// ── Bitwarden ───────────────────────────────────────────────────────────────────────────────────

/** `bitwarden:<item id or search>[#<field>]` (`field` defaults to `password`). `bw` reads an unlocked
  * vault through `BW_SESSION`: `session` (a Secret, e.g. `env:BW_SESSION`) is passed to the `bw`
  * process only; without it the vault reports locked. */
final case class BitwardenConfig(cli: String, session: Option[Secret], field: String, timeoutSeconds: Int)
    derives ConfigReader

final class BitwardenPlugin extends Plugin:
  type Config = BitwardenConfig
  val info = PluginInfo("bitwarden", Vaults.Version, Spi.Version)

  def register(c: BitwardenConfig, ctx: PluginContext): Either[String, Unit] =
    for
      exe     <- Subprocess.locate(c.cli, ctx.env("PATH"))
      session <- Vaults.optional(c.session, ctx)
      _       <- ctx.secretVaults.add(info.id, BitwardenVault(info.id, exe, session, c.field,
                   Vaults.timeout(c.timeoutSeconds)))
    yield ()

final class BitwardenVault(val name: String, exe: String, session: Option[String], field: String,
    timeout: FiniteDuration) extends SecretVault:
  def resolve(ref: String): Either[String, String] =
    val (item, f) = Vaults.entryAndField(ref, field)
    for
      i <- Subprocess.plain("the item", item)
      g <- Subprocess.plain("the field", f)
      argv = List(exe, "get", g, i, "--nointeraction")
      env  = session.map(s => Map("BW_SESSION" -> s)).getOrElse(Map.empty)
      v <- Vaults.answer(name, ref, Subprocess.run(argv, env, None, timeout), classify(i))
    yield v

  private def classify(item: String)(err: String): Option[String] =
    if err.contains("vault is locked") || err.contains("not logged in") then
      Some(s"$name: the vault is locked — run `bw unlock` and set session = \"env:BW_SESSION\" for this vault")
    else if err.contains("more than one result") then Some(s"$name: '$item' matches more than one item — use its id")
    else if err.contains("not found") then Some(s"$name: no item '$item'")
    else None
