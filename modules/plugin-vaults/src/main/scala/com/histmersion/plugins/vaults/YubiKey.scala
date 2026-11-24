package com.histmersion
package plugins.vaults
import com.histmersion.util.problem.{External, Problem}
import Subprocess.firstLine

import hearth.kindlings.sconfigderivation.ConfigReader
import org.ekrich.config.{ConfigValue, ConfigValueType}
import io.github.iltotore.iron.constraint.any.Pure
import io.github.iltotore.iron.constraint.string.Match
import com.histmersion.lint.{LintRule, tolerated}

/** KeePassXC's YubiKey challenge-response (`keepassxc-cli … -y <slot>[:<serial>]`): which keys the `yubikey` setting
  * lists, which of them are connected, and what a failed attempt means — the next key, or stop. All pure;
  * [[KeePassXcVault]] runs the tools. Slots and serials are not secrets.
  */
@tolerated(LintRule.CanEqualHome, "pending G67 (a foreign type's CanEqual in one file; its users share no module yet)")
object YubiKey:
  private given CanEqual[ConfigValueType, ConfigValueType] = CanEqual.derived

  type Label = Label.T
  object Label extends Newtype[String, Pure]
  type Text = Text.T
  object Text extends Newtype[String, Pure]

  /** One `<slot>[:<serial>]`: the challenge-response slot (1 or 2) and, optionally, which key. */
  type SerialsOut = SerialsOut.T
  object SerialsOut extends Newtype[String, Pure]

  final case class Slot(slot: Slot.Value, serial: Option[Slot.Serial]) derives CanEqual:
    /** The form `keepassxc-cli -y` takes, and the one the settings and the errors show. */
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def shown: Label = Label(serial.fold(slot.value.toString)(s => s"${slot.value}:${s.value}"))

  object Slot:

    type Value = Value.T
    object Value extends Newtype[Int, Pure]
    type Serial = Serial.T
    object Serial extends Newtype[Long, Pure]

  /** One entry as written: the challenge-response slot (1 or 2), then optionally `:` and the key's serial (`ykman list
    * --serials`).
    */
  type Written = Written.T
  object Written extends Newtype[String, Match["[12](:[0-9]+)?"]]

  /** Pure: `"2"` or `"2:12345678"`. */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def parse(text: Text): Either[Problem, Slot] =
    val t = (text).value.trim
    val refused = Problem.Invalid.malformed(Written.refused(t)).within(Problem.Field("yubikey"))
    t.split(":", -1).toList match
      case List(s)    => s.toIntOption.filter(Set(1, 2)).map(v => Slot(Slot.Value(v), None)).toRight(refused)
      case List(s, n) =>
        (for
          slot <- s.toIntOption.filter(Set(1, 2))
          serial <- Option.when(n.nonEmpty && n.forall(_.isDigit))(n).flatMap(_.toLongOption)
        yield Slot(Slot.Value(slot), Some(Slot.Serial(serial)))).toRight(refused)
      case _ => Left(refused)

  /** Pure: every entry of the setting, in order, without repeats; the first malformed one fails it all. */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def parseAll(entries: List[Setting.Entries]): Either[Problem, List[Slot]] =
    entries
      .map((_).value.trim)
      .filter(_.nonEmpty)
      .foldLeft[Either[Problem, List[Slot]]](Right(Nil)) { (acc, e) =>
        acc.flatMap(ok => parse(Text(e)).map(s => if ok.contains(s) then ok else ok :+ s))
      }

  /** Pure: the serials `ykman list --serials` printed, one per line (anything else is ignored). */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
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
  enum Failure:
    /** This key is not there or did not answer: the next one may. */
    case Next(attempt: Problem.Plugin.Attempt)

    /** No other key would change it (wrong credentials, nobody touched the key, an error KeePassXC gave). */
    case Stop(attempt: Problem.Plugin.Attempt)

    /** The database has no such entry; no other key would change it. */
    case NoEntry(entry: Setting.Entries)

  /** Why one try with one key gave no answer at all from KeePassXC. */
  enum NoAnswer:
    /** It did not answer within `waited`: nobody touched the key. */
    case TimedOut(waited: scala.concurrent.duration.FiniteDuration)

    /** It could not be run, or its answer could not be read. */
    case Unusable(why: Problem)

    /** What failed, as a problem. */
    def problem: Problem = this match
      case TimedOut(waited) =>
        Problem.Io.Process(
          Problem.Io.Tool("keepassxc-cli"),
          Problem.Io.Ending.TimedOut(Problem.Millis.of(waited))
        )
      case Unusable(why) => why

  /** Pure: what one failed attempt means (KeePassXC's message, [[KeePassXcMessage.parse]]) → next or stop. Wrong
    * credentials stop at once: another key cannot fix a wrong password, and with the same secret programmed into every
    * listed key a wrong key is the same answer. Unknown messages stop too, so a failure is never retried blindly.
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def failure(message: KeePassXcMessage, entry: Setting.Entries): Failure = message match
    case KeePassXcMessage.InvalidCredentials      => Failure.Stop(Problem.Plugin.Attempt.WrongCredentials)
    case KeePassXcMessage.NoEntry                 => Failure.NoEntry(entry)
    case KeePassXcMessage.NotTouched              => Failure.Stop(Problem.Plugin.Attempt.NotTouched)
    case KeePassXcMessage.KeyAbsent               => Failure.Next(Problem.Plugin.Attempt.NotConnected)
    case KeePassXcMessage.ChallengeResponseFailed => Failure.Next(Problem.Plugin.Attempt.ChallengeFailed)
    case KeePassXcMessage.Other                   => Failure.Stop(Problem.Plugin.Attempt.Refused)

  /** Pure: KeePassXC's own message for an error text — the first stderr line that is not its prompt ("Please present or
    * touch your YubiKey", "Enter password to unlock").
    */
  extension (err: Subprocess.Result.Err)
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def unprompted: External.Stderr =
      Subprocess.Result
        .Err(
          s"$err".linesIterator
            .filterNot { l =>
              val low = l.toLowerCase(java.util.Locale.ROOT)
              low.contains("touch your yubikey") || low.contains("enter password")
            }
            .mkString("\n")
        )
        .firstLine

  /** The `yubikey` setting: one `"<slot>[:<serial>]"` string (commas or spaces may separate several), or a list of
    * them, tried in that order.
    */
  final case class Setting(entries: List[Setting.Entries])

  @tolerated(LintRule.Derives, "pending G18 (derives instead of hand-written givens)")
  object Setting:
    type Entries = Entries.T
    object Entries extends Newtype[String, Pure]

    given ConfigReader[Setting] = new ConfigReader[Setting]:
      @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
      def from(v: ConfigValue) =
        if v.valueType == ConfigValueType.LIST then
          summon[ConfigReader[List[String]]].from(v).map(l => Setting(l.map(refactorValue3 => Entries(refactorValue3))))
        else
          ConfigReader.stringReader
            .from(v)
            .map(s =>
              Setting((s.split("[,\\s]+").toList.filter(_.nonEmpty)).map(refactorValue4 => Entries(refactorValue4)))
            )
