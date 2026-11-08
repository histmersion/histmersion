package com.histmersion
package plugins.vaults

import hearth.kindlings.sconfigderivation.ConfigReader
import org.ekrich.config.{ConfigValue, ConfigValueType}
import io.github.iltotore.iron.constraint.any.Pure

/** KeePassXC's YubiKey challenge-response (`keepassxc-cli … -y <slot>[:<serial>]`): which keys the `yubikey` setting
  * lists, which of them are connected, and what a failed attempt means — the next key, or stop. All pure;
  * [[KeePassXcVault]] runs the tools. Slots and serials are not secrets.
  */
object YubiKey:
  private given CanEqual[ConfigValueType, ConfigValueType] = CanEqual.derived

  type Label = Label.T
  object Label extends Newtype[String, Pure]
  export Subprocess.Problem
  type Text = Text.T
  object Text extends Newtype[String, Pure]

  /** One `<slot>[:<serial>]`: the challenge-response slot (1 or 2) and, optionally, which key. */
  type SerialsOut = SerialsOut.T
  object SerialsOut extends Newtype[String, Pure]

  final case class Slot(slot: Slot.Value, serial: Option[Slot.Serial]) derives CanEqual:
    /** The form `keepassxc-cli -y` takes, and the one the settings and the errors show. */
    def shown: Label = Label(serial.fold(slot.value.toString)(s => s"${slot.value}:${s.value}"))

  object Slot:

    type Value = Value.T
    object Value extends Newtype[Int, Pure]
    type Serial = Serial.T
    object Serial extends Newtype[Long, Pure]

  /** Pure: `"2"` or `"2:12345678"`. */
  def parse(text: Text): Either[YubiKey.Problem, Slot] =
    val t = (text).value.trim
    def bad =
      s"yubikey '$t' is not <slot>[:<serial>] — the slot is 1 or 2, the serial the key's number (`ykman list --serials`)"
    t.split(":", -1).toList match
      case List(s) => s.toIntOption.filter(Set(1, 2)).map(v => Slot(Slot.Value(v), None)).toRight(YubiKey.Problem(bad))
      case List(s, n) =>
        (for
          slot <- s.toIntOption.filter(Set(1, 2)).toRight(bad)
          serial <- Option.when(n.nonEmpty && n.forall(_.isDigit))(n).flatMap(_.toLongOption).toRight(bad)
        yield Slot(Slot.Value(slot), Some(Slot.Serial(serial)))).left.map(refactorError2 =>
          YubiKey.Problem(refactorError2)
        )
      case _ => Left(YubiKey.Problem(bad))

  /** Pure: every entry of the setting, in order, without repeats; the first malformed one fails it all. */
  def parseAll(entries: List[Setting.Entries]): Either[YubiKey.Problem, List[Slot]] =
    (entries
      .map((_).value.trim)
      .filter(_.nonEmpty)
      .foldLeft[Either[String, List[Slot]]](Right(Nil)) { (acc, e) =>
        acc.flatMap(ok =>
          (parse(Text(e))
            .map(s => if ok.contains(s) then ok else ok :+ s))
            .left
            .map(refactorError => (refactorError).value)
        )
      })
      .left
      .map(refactorError3 => YubiKey.Problem(refactorError3))

  /** Pure: the serials `ykman list --serials` printed, one per line (anything else is ignored). */
  def serials(out: SerialsOut): Set[Slot.Serial] =
    ((out).value.linesIterator
      .map(_.trim)
      .filter(l => l.nonEmpty && l.forall(_.isDigit))
      .flatMap(_.toLongOption)
      .toSet)
      .map(refactorValue => Slot.Serial(refactorValue))

  /** Pure: which entries to try and which are absent. `connected = None` (no way to tell): all of them, in order.
    * Otherwise an entry with a serial only when that key is connected, one without when any key is. `first` (the entry
    * that last opened the database) goes to the front: its touch is the one most likely wanted.
    */
  def plan(
      entries: List[Slot],
      connected: Option[Set[Slot.Serial]],
      first: Option[Slot] = None
  ): (List[Slot], List[Slot]) =
    val (tryThese, absent) = connected match
      case None      => (entries, Nil)
      case Some(set) => entries.partition(e => e.serial.fold(set.nonEmpty)(set.contains))
    val ordered = first.filter(tryThese.contains).fold(tryThese)(f => f :: tryThese.filterNot(_ == f))
    (ordered, absent)

  /** What one failed attempt means. */
  enum Failure derives CanEqual:
    /** This key is not there or did not answer: the next one may. */
    case Next(why: Failure.Next.Why)

    /** No other key would change it (wrong credentials, no such entry, nobody touched the key). */
    case Stop(why: Failure.Stop.Why)

  object Failure:
    object Next:
      type Why = Why.T
      object Why extends Newtype[String, Pure]
    object Stop:
      type Why = Why.T
      object Why extends Newtype[String, Pure]

  /** Pure: KeePassXC's message for a failed attempt (lower-cased stderr) → next or stop. Wrong credentials stop at
    * once: another key cannot fix a wrong password, and with the same secret programmed into every listed key a wrong
    * key is the same answer. Unknown messages stop too, so a failure is never retried blindly.
    */
  def failure(err: YubiKey.Problem, entry: Setting.Entries): Failure =
    def has(s: String*) = s.exists((err).value.contains)
    if has("invalid credentials", "wrong key", "unable to unlock") then
      Failure.Stop(Failure.Stop.Why("invalid credentials (the password, key file or YubiKey is wrong)"))
    else if has("could not find entry") then Failure.Stop(Failure.Stop.Why(s"no entry '$entry'"))
    else if has("timed out waiting", "user interaction") then
      Failure.Stop(Failure.Stop.Why("the YubiKey was not touched in time"))
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
    then Failure.Next(Failure.Next.Why("not connected"))
    else if has(
        "challenge-response",
        "challenge response",
        "slot is not configured",
        "usb error",
        "locked or timed out",
        "hardware key"
      )
    then Failure.Next(Failure.Next.Why("challenge-response failed"))
    else Failure.Stop(Failure.Stop.Why("an error KeePassXC gave"))

  /** Pure: KeePassXC's own message for an error text — the first stderr line that is not its prompt ("Please present or
    * touch your YubiKey", "Enter password to unlock").
    */
  def problem(err: YubiKey.Problem): YubiKey.Problem =
    Subprocess.problem(
      Subprocess.Problem(
        (err).value.linesIterator
          .filterNot { l =>
            val low = l.toLowerCase(java.util.Locale.ROOT)
            low.contains("touch your yubikey") || low.contains("enter password")
          }
          .mkString("\n")
      )
    )

  /** The `yubikey` setting: one `"<slot>[:<serial>]"` string (commas or spaces may separate several), or a list of
    * them, tried in that order.
    */
  final case class Setting(entries: List[Setting.Entries])

  object Setting:
    type Entries = Entries.T
    object Entries extends Newtype[String, Pure]

    given ConfigReader[Setting] = new ConfigReader[Setting]:
      def from(v: ConfigValue) =
        if v.valueType == ConfigValueType.LIST then
          summon[ConfigReader[List[String]]].from(v).map(l => Setting(l.map(refactorValue3 => Entries(refactorValue3))))
        else
          ConfigReader.stringReader
            .from(v)
            .map(s =>
              Setting((s.split("[,\\s]+").toList.filter(_.nonEmpty)).map(refactorValue4 => Entries(refactorValue4)))
            )
