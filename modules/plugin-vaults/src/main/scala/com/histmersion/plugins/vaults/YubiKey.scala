package com.histmersion.plugins.vaults

import hearth.kindlings.sconfigderivation.ConfigReader
import org.ekrich.config.{ConfigValue, ConfigValueType}

/** KeePassXC's YubiKey challenge-response (`keepassxc-cli … -y <slot>[:<serial>]`): which keys the `yubikey` setting
  * lists, which of them are connected, and what a failed attempt means — the next key, or stop. All pure;
  * [[KeePassXcVault]] runs the tools. Slots and serials are not secrets.
  */
object YubiKey:

  /** One `<slot>[:<serial>]`: the challenge-response slot (1 or 2) and, optionally, which key. */
  type ParseResultPart1 = ParseResultPart1.T
  object ParseResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ParseText = ParseText.T
  object ParseText extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ParseAllResultPart1 = ParseAllResultPart1.T
  object ParseAllResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ParseAllEntries = ParseAllEntries.T
  object ParseAllEntries extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SerialsResult = SerialsResult.T
  object SerialsResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SerialsOut = SerialsOut.T
  object SerialsOut extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PlanConnected = PlanConnected.T
  object PlanConnected extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FailureErr = FailureErr.T
  object FailureErr extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FailureEntry = FailureEntry.T
  object FailureEntry extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ProblemResult = ProblemResult.T
  object ProblemResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ProblemErr = ProblemErr.T
  object ProblemErr extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  final case class Slot(slot: Slot.Value, serial: Option[Slot.Serial]):
    /** The form `keepassxc-cli -y` takes, and the one the settings and the errors show. */
    def shown: com.histmersion.plugins.vaults.YubiKey.Slot.ShownResult = _root_.com.histmersion.plugins.vaults.YubiKey.Slot.ShownResult(serial.fold(slot.toString)(s => s"$slot:$s"))

  object Slot:
    type ShownResult = ShownResult.T
    object ShownResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    type Value = Value.T
    object Value extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Serial = Serial.T
    object Serial extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** Pure: `"2"` or `"2:12345678"`. */
  def parse(text: com.histmersion.plugins.vaults.YubiKey.ParseText): Either[com.histmersion.plugins.vaults.YubiKey.ParseResultPart1, Slot] =
    val t = (text).value.trim
    def bad =
      s"yubikey '$t' is not <slot>[:<serial>] — the slot is 1 or 2, the serial the key's number (`ykman list --serials`)"
    t.split(":", -1).toList match
      case List(s)    => s.toIntOption.filter(Set(1, 2)).map(Slot(_root_.com.histmersion.plugins.vaults.YubiKey.Slot.Value(_), None)).toRight(_root_.com.histmersion.plugins.vaults.YubiKey.ParseResultPart1(bad))
      case List(s, n) =>
        (for
          slot <- s.toIntOption.filter(Set(1, 2)).toRight(bad)
          serial <- Option.when(n.nonEmpty && n.forall(_.isDigit))(n).flatMap(_.toLongOption).toRight(bad)
        yield Slot(_root_.com.histmersion.plugins.vaults.YubiKey.Slot.Value(slot), Some(_root_.com.histmersion.plugins.vaults.YubiKey.Slot.Serial(serial)))).left.map(refactorError2 => _root_.com.histmersion.plugins.vaults.YubiKey.ParseResultPart1(refactorError2))
      case _ => Left(_root_.com.histmersion.plugins.vaults.YubiKey.ParseResultPart1(bad))

  /** Pure: every entry of the setting, in order, without repeats; the first malformed one fails it all. */
  def parseAll(entries: List[com.histmersion.plugins.vaults.YubiKey.ParseAllEntries]): Either[com.histmersion.plugins.vaults.YubiKey.ParseAllResultPart1, List[Slot]] =
    entries.map((_).value.trim).filter(_.nonEmpty).foldLeft[Either[String, List[Slot]]](Right(Nil)) { (acc, e) =>
      acc.flatMap(ok => (parse(_root_.com.histmersion.plugins.vaults.YubiKey.ParseText(e)).map(s => if ok.contains(s) then ok else ok :+ s)).left.map(refactorError => (refactorError).value))
    }

  /** Pure: the serials `ykman list --serials` printed, one per line (anything else is ignored). */
  def serials(out: com.histmersion.plugins.vaults.YubiKey.SerialsOut): Set[com.histmersion.plugins.vaults.YubiKey.SerialsResult] =
    ((out).value.linesIterator.map(_.trim).filter(l => l.nonEmpty && l.forall(_.isDigit)).flatMap(_.toLongOption).toSet).map(refactorValue => _root_.com.histmersion.plugins.vaults.YubiKey.SerialsResult(refactorValue))

  /** Pure: which entries to try and which are absent. `connected = None` (no way to tell): all of them, in order.
    * Otherwise an entry with a serial only when that key is connected, one without when any key is. `first` (the entry
    * that last opened the database) goes to the front: its touch is the one most likely wanted.
    */
  def plan(entries: List[Slot], connected: Option[Set[com.histmersion.plugins.vaults.YubiKey.PlanConnected]], first: Option[Slot] = None): (List[Slot], List[Slot]) =
    val (tryThese, absent) = connected match
      case None      => (entries, Nil)
      case Some(set) => entries.partition(e => e.serial.fold(set.nonEmpty)(set.contains))
    val ordered = first.filter(tryThese.contains).fold(tryThese)(f => f :: tryThese.filterNot(_ == f))
    (ordered, absent)

  /** What one failed attempt means. */
  enum Failure:
    /** This key is not there or did not answer: the next one may. */
    case Next(why: Failure.Next.Why)

    /** No other key would change it (wrong credentials, no such entry, nobody touched the key). */
    case Stop(why: Failure.Stop.Why)

  object Failure:
    object Next:
      type Why = Why.T
      object Why extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    object Stop:
      type Why = Why.T
      object Why extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** Pure: KeePassXC's message for a failed attempt (lower-cased stderr) → next or stop. Wrong credentials stop at
    * once: another key cannot fix a wrong password, and with the same secret programmed into every listed key a wrong
    * key is the same answer. Unknown messages stop too, so a failure is never retried blindly.
    */
  def failure(err: com.histmersion.plugins.vaults.YubiKey.FailureErr, entry: com.histmersion.plugins.vaults.YubiKey.FailureEntry): Failure =
    def has(s: String*) = s.exists((err).value.contains)
    if has("invalid credentials", "wrong key", "unable to unlock") then
      Failure.Stop(_root_.com.histmersion.plugins.vaults.YubiKey.Failure.Stop.Why("invalid credentials (the password, key file or YubiKey is wrong)"))
    else if has("could not find entry") then Failure.Stop(_root_.com.histmersion.plugins.vaults.YubiKey.Failure.Stop.Why(s"no entry '$entry'"))
    else if has("timed out waiting", "user interaction") then Failure.Stop(_root_.com.histmersion.plugins.vaults.YubiKey.Failure.Stop.Why("the YubiKey was not touched in time"))
    else if has(
        "could not find hardware key",
        "could not find interface",
        "please plug it in",
        "please connect it",
        "no yubikey",
        "yubikey not found",
        "no hardware key",
        "no challenge-response key"
      )
    then Failure.Next(_root_.com.histmersion.plugins.vaults.YubiKey.Failure.Next.Why("not connected"))
    else if has(
        "challenge-response",
        "challenge response",
        "slot is not configured",
        "usb error",
        "locked or timed out",
        "hardware key"
      )
    then Failure.Next(_root_.com.histmersion.plugins.vaults.YubiKey.Failure.Next.Why("challenge-response failed"))
    else Failure.Stop(_root_.com.histmersion.plugins.vaults.YubiKey.Failure.Stop.Why("an error KeePassXC gave"))

  /** Pure: KeePassXC's own message for an error text — the first stderr line that is not its prompt ("Please present or
    * touch your YubiKey", "Enter password to unlock").
    */
  def problem(err: com.histmersion.plugins.vaults.YubiKey.ProblemErr): com.histmersion.plugins.vaults.YubiKey.ProblemResult =
    Subprocess.problem(
      _root_.com.histmersion.plugins.vaults.Subprocess.ProblemErr((err).value.linesIterator
        .filterNot { l =>
          val low = l.toLowerCase(java.util.Locale.ROOT)
          low.contains("touch your yubikey") || low.contains("enter password")
        }
        .mkString("\n"))
    )

  /** The `yubikey` setting: one `"<slot>[:<serial>]"` string (commas or spaces may separate several), or a list of
    * them, tried in that order.
    */
  final case class Setting(entries: List[Setting.Entries])

  object Setting:
    type Entries = Entries.T
    object Entries extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    given ConfigReader[Setting] = new ConfigReader[Setting]:
      def from(v: ConfigValue) =
        if v.valueType == ConfigValueType.LIST then summon[ConfigReader[List[String]]].from(v).map(Setting((_).map(refactorValue3 => _root_.com.histmersion.plugins.vaults.YubiKey.Setting.Entries(refactorValue3))))
        else ConfigReader.stringReader.from(v).map(s => Setting((s.split("[,\\s]+").toList.filter(_.nonEmpty)).map(refactorValue4 => _root_.com.histmersion.plugins.vaults.YubiKey.Setting.Entries(refactorValue4))))
