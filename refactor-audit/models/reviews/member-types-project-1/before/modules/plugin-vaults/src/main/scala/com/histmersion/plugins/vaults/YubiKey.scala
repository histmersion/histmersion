package com.histmersion.plugins.vaults

import hearth.kindlings.sconfigderivation.ConfigReader
import org.ekrich.config.{ConfigValue, ConfigValueType}

/** KeePassXC's YubiKey challenge-response (`keepassxc-cli … -y <slot>[:<serial>]`): which keys the `yubikey` setting
  * lists, which of them are connected, and what a failed attempt means — the next key, or stop. All pure;
  * [[KeePassXcVault]] runs the tools. Slots and serials are not secrets.
  */
object YubiKey:

  /** One `<slot>[:<serial>]`: the challenge-response slot (1 or 2) and, optionally, which key. */
  final case class Slot(slot: Slot.Value, serial: Option[Slot.Serial]):
    /** The form `keepassxc-cli -y` takes, and the one the settings and the errors show. */
    def shown: String = serial.fold(slot.toString)(s => s"$slot:$s")

  object Slot:
    type Value = Value.T
    object Value extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Serial = Serial.T
    object Serial extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** Pure: `"2"` or `"2:12345678"`. */
  def parse(text: String): Either[String, Slot] =
    val t = text.trim
    def bad =
      s"yubikey '$t' is not <slot>[:<serial>] — the slot is 1 or 2, the serial the key's number (`ykman list --serials`)"
    t.split(":", -1).toList match
      case List(s)    => s.toIntOption.filter(Set(1, 2)).map(Slot(_, None)).toRight(bad)
      case List(s, n) =>
        for
          slot <- s.toIntOption.filter(Set(1, 2)).toRight(bad)
          serial <- Option.when(n.nonEmpty && n.forall(_.isDigit))(n).flatMap(_.toLongOption).toRight(bad)
        yield Slot(slot, Some(serial))
      case _ => Left(bad)

  /** Pure: every entry of the setting, in order, without repeats; the first malformed one fails it all. */
  def parseAll(entries: List[String]): Either[String, List[Slot]] =
    entries.map(_.trim).filter(_.nonEmpty).foldLeft[Either[String, List[Slot]]](Right(Nil)) { (acc, e) =>
      acc.flatMap(ok => parse(e).map(s => if ok.contains(s) then ok else ok :+ s))
    }

  /** Pure: the serials `ykman list --serials` printed, one per line (anything else is ignored). */
  def serials(out: String): Set[Long] =
    out.linesIterator.map(_.trim).filter(l => l.nonEmpty && l.forall(_.isDigit)).flatMap(_.toLongOption).toSet

  /** Pure: which entries to try and which are absent. `connected = None` (no way to tell): all of them, in order.
    * Otherwise an entry with a serial only when that key is connected, one without when any key is. `first` (the entry
    * that last opened the database) goes to the front: its touch is the one most likely wanted.
    */
  def plan(entries: List[Slot], connected: Option[Set[Long]], first: Option[Slot] = None): (List[Slot], List[Slot]) =
    val (tryThese, absent) = connected match
      case None      => (entries, Nil)
      case Some(set) => entries.partition(e => e.serial.fold(set.nonEmpty)(set.contains))
    val ordered = first.filter(tryThese.contains).fold(tryThese)(f => f :: tryThese.filterNot(_ == f))
    (ordered, absent)

  /** What one failed attempt means. */
  enum Failure:
    /** This key is not there or did not answer: the next one may. */
    case Next(why: String)

    /** No other key would change it (wrong credentials, no such entry, nobody touched the key). */
    case Stop(why: String)

  /** Pure: KeePassXC's message for a failed attempt (lower-cased stderr) → next or stop. Wrong credentials stop at
    * once: another key cannot fix a wrong password, and with the same secret programmed into every listed key a wrong
    * key is the same answer. Unknown messages stop too, so a failure is never retried blindly.
    */
  def failure(err: String, entry: String): Failure =
    def has(s: String*) = s.exists(err.contains)
    if has("invalid credentials", "wrong key", "unable to unlock") then
      Failure.Stop("invalid credentials (the password, key file or YubiKey is wrong)")
    else if has("could not find entry") then Failure.Stop(s"no entry '$entry'")
    else if has("timed out waiting", "user interaction") then Failure.Stop("the YubiKey was not touched in time")
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
    then Failure.Next("not connected")
    else if has(
        "challenge-response",
        "challenge response",
        "slot is not configured",
        "usb error",
        "locked or timed out",
        "hardware key"
      )
    then Failure.Next("challenge-response failed")
    else Failure.Stop("an error KeePassXC gave")

  /** Pure: KeePassXC's own message for an error text — the first stderr line that is not its prompt ("Please present or
    * touch your YubiKey", "Enter password to unlock").
    */
  def problem(err: String): String =
    Subprocess.problem(
      err.linesIterator
        .filterNot { l =>
          val low = l.toLowerCase(java.util.Locale.ROOT)
          low.contains("touch your yubikey") || low.contains("enter password")
        }
        .mkString("\n")
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
        if v.valueType == ConfigValueType.LIST then summon[ConfigReader[List[String]]].from(v).map(Setting(_))
        else ConfigReader.stringReader.from(v).map(s => Setting(s.split("[,\\s]+").toList.filter(_.nonEmpty)))
