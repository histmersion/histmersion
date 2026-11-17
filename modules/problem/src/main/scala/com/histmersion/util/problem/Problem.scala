package com.histmersion
package util.problem
import com.histmersion.util.nonempty.NonEmptyVector

import scala.reflect.TypeTest

import hearth.typename.TypeName as Named

import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

/** Why an operation did not succeed: one closed list of cases, in eleven groups, each case holding typed data and no
  * sentence. The sentence, the HTTP status, the CLI exit code, the log line and the stored wording are the edges' (one
  * renderer each); code that needs to tell failures apart matches on the case, never on text.
  *
  * What failed is named by its type and the constraint it failed, both written down by the compiler where the value is
  * parsed (`Citekey.parse(raw)`), plus the raw input: no case carries a hand-kept vocabulary of what can fail.
  *
  * Several failures at once are [[Problems]]: one `Problem` or a [[NonEmptyVector]] of them. A case's name is its
  * `productPrefix`, its fields' names its `productElementNames`.
  */
sealed trait Problem extends Product, Serializable

object Problem:

  // ---- what failed: shared by the groups ------------------------------------------------------------------------

  /** The name of the type a value failed to be, without its package (`Citekey`, `Source.Status`): what the wire and new
    * stored texts carry, so moving a type to another package changes neither. A `Wire` type is the wire spelling of the
    * type it is nested in and is named by it (`ProviderKind.Wire` is `ProviderKind`). Texts stored before kept the full
    * name (`com.histmersion.domain.library.Citekey`) and read back with it.
    */
  type TypeName = TypeName.T
  object TypeName extends Newtype[String, Pure]:
    /** The name the newtype module captured (a full name), without its package. */
    def of(name: Newtype.TypeName): TypeName =
      val named = name.text.split('.').toVector.dropWhile(_.headOption.forall(_.isLower))
      TypeName(
        (if named.size > 1 && named.lastOption.contains("Wire") then named.dropRight(1) else named).mkString(".")
      )

    /** The name of a newtype, as Hearth names its `T` where it is asked. */
    def of[A, C](newtype: Newtype[A, C])(using Named[newtype.T]): TypeName = of(newtype.typeName)

    /** The name of any other type (an enum, a record), as Hearth names it where it is asked. */
    def named[A](using named: Named[A]): TypeName = of(Newtype.TypeName.of(named))

  /** An Iron constraint a value failed: its type as written and Iron's description of it. */
  import Constraint.{Description, Name}

  final case class Constraint(name: Name, description: Description)

  object Constraint:
    /** The constraint's type as source code writes it: `Match["[a-z]+"]`, `Interval.Closed[0, 9]`. */
    type Name = Name.T
    object Name extends Newtype[String, Pure]

    /** Iron's own description of the constraint: `Should match [a-z]+`. */
    type Description = Description.T
    object Description extends Newtype[String, Pure]

    /** The constraint the newtype module captured. */
    def of(constraint: Newtype.Constraint): Constraint =
      Constraint(Name(s"${constraint.name}"), Description(s"${constraint.description}"))

  /** An input as it was given, before it became a value of any type: the text a case shows and never branches on. */
  type Raw = Raw.T
  object Raw extends Newtype[String, Pure]:
    /** A number given as input. */
    def of(number: Long): Raw = Raw(s"$number")

    /** A value of any newtype as it was given — a model's answer field, a parsed year — shown back as its base value's
      * text (`Raw.of(answer.citekey)`). The newtype stays a concept up to here: this companion is the one place it is
      * unwrapped. Iron's mirror, which every newtype's companion gives, keeps plain values out.
      */
    def of[T](value: T)(using RefinedType.Mirror[T]): Raw = Raw(s"$value")

  /** The wire name of a request or settings field (`lat`, `edges`, `agents.budgets.daily-usd`). */
  type Field = Field.T
  object Field extends Newtype[String, Pure]

  /** Where in the code or the work a failure happened: the operation's own name (`backup`, `merge apply`). */
  type Where = Where.T
  object Where extends Newtype[String, Pure]:
    /** The step a value of any newtype names, as it is written (`Where.of(answer.action)`): unwrapped here, like
      * [[Raw.of]].
      */
    def of[T](value: T)(using RefinedType.Mirror[T]): Where = Where(s"$value")

  /** A share between 0 and 1 (`0.42`: 42 of every hundred), shown to two decimals. */
  type Share = Share.T
  object Share extends Newtype[Double, Pure]:
    /** The share a value of any newtype over a `Double` holds (`Share.of(quote.ratio)`): unwrapped here, like
      * [[Raw.of]].
      */
    def of[T](ratio: T)(using RefinedType.Mirror[T] { type BaseType = Double }): Share = Share(s"$ratio".toDouble)

    /** A share written in decimal digits. */
    def read(digits: String): Option[Share] = digits.toDoubleOption.map(Share(_))

  /** A count of things. */
  type Count = Count.T
  object Count extends Newtype[Long, Pure]:
    /** The count a value of any newtype over a whole number holds (`Count.of(gap)`): unwrapped here, like [[Raw.of]].
      */
    def of[T](value: T)(using RefinedType.Mirror[T] { type BaseType <: Long | Int }): Count = Count(s"$value".toLong)

    /** A count written in decimal digits. */
    def read(digits: String): Option[Count] = digits.toLongOption.map(Count(_))

  /** A length of time, in milliseconds. */
  type Millis = Millis.T
  object Millis extends Newtype[Long, Pure]:
    /** The length of `duration`. */
    def of(duration: scala.concurrent.duration.FiniteDuration): Millis = Millis(duration.toMillis)

    /** The length a value of any newtype over whole milliseconds holds (`Millis.of(timeoutMs)`): unwrapped here, like
      * [[Raw.of]].
      */
    def of[T](value: T)(using RefinedType.Mirror[T] { type BaseType <: Long | Int }): Millis = Millis(s"$value".toLong)

    /** A length of time written in decimal digits of milliseconds. */
    def read(digits: String): Option[Millis] = digits.toLongOption.map(Millis(_))

    extension (m: Millis)
      /** The length as a duration. */
      def duration: scala.concurrent.duration.FiniteDuration =
        scala.concurrent.duration.FiniteDuration(s"$m".toLong, "ms")

  /** A moment, as milliseconds since 1970-01-01T00:00Z (the same on the JVM and in the browser). */
  type EpochMs = EpochMs.T
  object EpochMs extends Newtype[Long, Pure]

  /** A problem's English sentence, as its renderer wrote it: what a [[Relayed]] problem holds. */
  type Sentence = Sentence.T
  object Sentence extends Newtype[String, Pure]

  /** A setting's key: `histmersion.agents.budgets.daily-usd`. */
  type SettingKey = SettingKey.T
  object SettingKey extends Newtype[String, Pure]

  /** The first word of a case's name: one of the eleven groups (what kind of failure it is), or one of the three cases
    * outside them (`Unclassified`, `Stored`, `Relayed`).
    */
  enum Group derives CanEqual:
    case Invalid, NotFound, Conflict, Refused, Upstream, Io, Config, Plugin, Run, Content, Internal, Unclassified,
      Stored, Relayed

  // ---- the groups ---------------------------------------------------------------------------------------------

  /** The input is not a value the operation takes. `at` is the path to the input within the request (`edges`, `3`),
    * outermost first.
    */
  sealed trait Invalid extends Problem

  object Invalid:

    /** `raw` is no value of the type `typeName`: it fails `constraint`. */
    final case class Malformed(typeName: TypeName, constraint: Constraint, raw: Raw, at: Vector[Field]) extends Invalid

    object Malformed:
      /** The problem of the request as a whole: no path into it. */
      def here(typeName: TypeName, constraint: Constraint, raw: Raw): Malformed =
        Malformed(typeName, constraint, raw, Vector.empty)

    /** `raw` is none of the values of `typeName` the operation knows (`known`, when the list is short). */
    final case class Unknown(typeName: TypeName, raw: Raw, known: Vector[Raw], at: Vector[Field]) extends Invalid

    object Unknown:
      /** The problem of the request as a whole: no path into it. */
      def here(typeName: TypeName, raw: Raw, known: Vector[Raw]): Unknown = Unknown(typeName, raw, known, Vector.empty)

    /** `raw` is written in none of the forms a value of `typeName` is written in; `examples` shows some of them. */
    final case class Unreadable(typeName: TypeName, raw: Raw, examples: Vector[Raw], at: Vector[Field]) extends Invalid

    object Unreadable:
      /** The problem of the request as a whole: no path into it. */
      def here(typeName: TypeName, raw: Raw, examples: Vector[Raw]): Unreadable =
        Unreadable(typeName, raw, examples, Vector.empty)

    /** `field` was given where the request takes none of that name; it belongs under `under`. */
    final case class Misplaced(field: Field, under: Field, at: Vector[Field]) extends Invalid

    object Misplaced:
      /** The problem of the request as a whole: no path into it. */
      def here(field: Field, under: Field): Misplaced = Misplaced(field, under, Vector.empty)

    /** The value `raw` of `typeName` is given more than once where each may be given once. */
    final case class Repeated(typeName: TypeName, raw: Raw, at: Vector[Field]) extends Invalid

    object Repeated:
      /** The problem of the request as a whole: no path into it. */
      def here(typeName: TypeName, raw: Raw): Repeated = Repeated(typeName, raw, Vector.empty)

    /** `raw`, a value of `typeName` that must name one place, matches `count` places: more of it tells them apart. */
    final case class Ambiguous(typeName: TypeName, raw: Raw, count: Count, at: Vector[Field]) extends Invalid

    object Ambiguous:
      /** The problem of the request as a whole: no path into it. */
      def here(typeName: TypeName, raw: Raw, count: Count): Ambiguous = Ambiguous(typeName, raw, count, Vector.empty)

    /** `field` is no field the request takes; `declared` are the ones it takes. */
    final case class Undeclared(field: Field, declared: Vector[Field], at: Vector[Field]) extends Invalid

    object Undeclared:
      /** The problem of the request as a whole: no path into it. */
      def here(field: Field, declared: Vector[Field]): Undeclared = Undeclared(field, declared, Vector.empty)

    /** `raw` is no value of `typeName` but a wrapper around one: the value is its member `member`. */
    final case class Wrapped(typeName: TypeName, raw: Raw, member: Field, at: Vector[Field]) extends Invalid

    object Wrapped:
      /** The problem of the request as a whole: no path into it. */
      def here(typeName: TypeName, raw: Raw, member: Field): Wrapped = Wrapped(typeName, raw, member, Vector.empty)

    /** `item`, a value of `typeName`, is stored on `owner`, not on the value the request writes: it is written there.
      */
    final case class StoredOn(typeName: TypeName, item: Raw, owner: Raw, at: Vector[Field]) extends Invalid

    object StoredOn:
      /** The problem of the request as a whole: no path into it. */
      def here(typeName: TypeName, item: Raw, owner: Raw): StoredOn = StoredOn(typeName, item, owner, Vector.empty)

    /** `field` is required and was not given, or was empty. */
    final case class Missing(field: Field, at: Vector[Field]) extends Invalid

    object Missing:
      /** The problem of the request as a whole: no path into it. */
      def here(field: Field): Missing = Missing(field, Vector.empty)

    /** `raw` is a value of `typeName`, but outside what the operation takes. */
    final case class OutOfRange(typeName: TypeName, raw: Raw, bounds: Bounds, at: Vector[Field]) extends Invalid

    object OutOfRange:
      /** The problem of the request as a whole: no path into it. */
      def here(typeName: TypeName, raw: Raw, bounds: Bounds): OutOfRange =
        OutOfRange(typeName, raw, bounds, Vector.empty)

    /** A period would start in `start`, after it ends in `end`. */
    final case class Inverted(start: Year, end: Year, at: Vector[Field]) extends Invalid

    object Inverted:
      /** The problem of the request as a whole: no path into it. */
      def here(start: Year, end: Year): Inverted = Inverted(start, end, Vector.empty)

    /** `year` is outside the years the corpus can hold, `least` to `greatest`. */
    final case class OutOfTime(year: Year, least: Year, greatest: Year, at: Vector[Field]) extends Invalid

    /** The document a request carried is no document of its `format`: at `where` it has `damage`. A request's own body
      * is the caller's input, so it is refused as invalid; a damaged file of the server's own is [[Io.Corrupt]].
      */
    final case class Unparsable(format: Io.Format, where: Where, damage: Io.Damage, at: Vector[Field]) extends Invalid

    /** A year of deep time, signed: 3300 BCE is `-3300`, 530 CE is `530`. A reader sees it as `3300 BCE`, `530 CE`. */
    type Year = Year.T
    object Year extends Newtype[Long, Pure]:
      /** A year written in decimal digits, signed. */
      def read(digits: String): Option[Year] = digits.toLongOption.map(Year(_))

      /** The year a value of any newtype over a whole number holds (`Year.of(start)`): unwrapped here, like [[Raw.of]].
        */
      def of[T](value: T)(using RefinedType.Mirror[T] { type BaseType <: Long | Int }): Year = Year(s"$value".toLong)

    /** The `fields` given break `rule` together, though each is a value on its own. */
    final case class Inconsistent(fields: NonEmptyVector[Field], rule: Exclusion, at: Vector[Field]) extends Invalid

    object Inconsistent:
      /** The problem of the request as a whole: no path into it. */
      def here(fields: NonEmptyVector[Field], rule: Exclusion): Inconsistent = Inconsistent(fields, rule, Vector.empty)

    /** A secret's reference (`value:<literal>`, `env:<NAME>`, `<vault>:<ref>`) is not one: `form` says how. The input
      * itself is never kept: it may be the secret.
      */
    final case class Reference(form: Form, at: Vector[Field]) extends Invalid

    object Reference:
      /** The problem of the request as a whole: no path into it. */
      def here(form: Form): Reference = Reference(form, Vector.empty)

    /** The input breaks `rule`, a rule of the operation's own: a cron's five fields, a tile inside its grid, two runs
      * of one pipeline.
      */
    final case class Broken(rule: Rule, at: Vector[Field]) extends Invalid

    object Broken:
      /** The problem of the request as a whole: no path into it. */
      def here(rule: Rule): Broken = Broken(rule, Vector.empty)

    /** A field of a cron expression, in its order: minute, hour, day of month, month, day of week. */
    enum CronPart derives CanEqual:
      case Minute, Hour, DayOfMonth, Month, DayOfWeek

    /** How one field of a cron expression is not one. */
    enum CronFlaw derives CanEqual:
      /** `stated` is not a number from `least` to `greatest`. */
      case NotNumber(stated: Raw, least: Count, greatest: Count)

      /** The step `stated` (after a `/`) is not a positive number. */
      case Step(stated: Raw)

      /** `stated` has more than one `/`. */
      case Slashes(stated: Raw)

      /** The range `stated` runs backwards. */
      case Backwards(stated: Raw)

      /** `stated` is not a range (`a-b`). */
      case NotRange(stated: Raw)

    enum Rule derives CanEqual:
      /** `stated` is not a cron expression: it has not five fields and is none of the `shortcuts`. */
      case CronFields(stated: Raw, shortcuts: Vector[Raw])

      /** The cron `stated` has a `part` that is not one: `flaw`. */
      case CronField(stated: Raw, part: CronPart, flaw: CronFlaw)

      /** The cron `stated` never fires. */
      case CronNeverFires(stated: Raw)

      /** The cron `stated` fires every `gap` minutes, more often than every `least`. */
      case CronTooOften(stated: Raw, gap: Count, least: Count)

      /** A map tile's zoom `zoom` is outside 0 to `greatest`. */
      case ZoomWithin(zoom: Count, greatest: Count)

      /** The map tile `x`/`y` is outside the grid of zoom `zoom`. */
      case TileWithin(x: Count, y: Count, zoom: Count)

      /** An identifier is empty: it is a DOI, an arXiv id or a citekey. */
      case IdentifierGiven

      /** `stated` is neither a DOI, an arXiv id nor a citekey. */
      case IdentifierKind(stated: Raw)

      /** `stated` is not a page of the activity list (`<instant>~<key>`). */
      case ActivityPage(stated: Raw)

      /** `stated` is not a page of the activity list: its time is not an instant. */
      case ActivityPageTime(stated: Raw)

      /** `stated` is no event catalogue; `known` are. */
      case EventCatalogue(stated: Raw, known: Vector[Raw])

      /** A rename names `citekey`, which the source already has. */
      case CitekeyUnchanged(citekey: Raw)

      /** A rename names `citekey`, a valid citekey that a `[[link]]` would read as an article's slug. */
      case CitekeyLinkShaped(citekey: Raw)

      /** The asset `asset` is not valid base64: `reported`. */
      case AssetBase64(asset: Raw, reported: External.Reported)

      /** The asset `asset` is empty. */
      case AssetEmpty(asset: Raw)

      /** The asset `asset` has `bytes` bytes, more than the `limit` (`limits.asset-bytes`). */
      case AssetBytes(asset: Raw, bytes: Count, limit: Count)

      /** A comparison is given fewer than two runs. */
      case RunsCompared

      /** A comparison is given one run twice. */
      case RunRepeated

      /** The run `run` is of the pipeline `pipeline`, the first run `first` of `firstPipeline`. */
      case RunsPipeline(run: Raw, pipeline: Raw, first: Raw, firstPipeline: Raw)

      /** The run `run` is on the target `target`, the first run `first` on `firstTarget`. */
      case RunsTarget(run: Raw, target: Raw, first: Raw, firstTarget: Raw)

      /** A passage search is given no words. */
      case QueryGiven

      /** A passage regex ran longer than `limit` over the body. */
      case RegexTime(limit: Millis)

      /** `pattern` is not a valid regex: `description`, at `position` when the regex engine names one. */
      case RegexValid(description: External.Reported, position: Option[Count], pattern: Raw)

      /** `stated` is not a merge's id, nor the first characters of one (hexadecimal digits and dashes). */
      case MergeId(stated: Raw)

      /** The work has no DOI recorded, and `purpose` needs one. */
      case DoiRecorded(purpose: Purpose)

      /** No upstream (OpenAlex, Crossref) has a record of the work: there is nothing to name a new source after. */
      case UpstreamRecord

      /** An upload with no bytes. */
      case UploadGiven

      /** An upload of `bytes`, over the `limit` the server takes (`limits.upload-bytes`). */
      case UploadBytes(bytes: Count, limit: Count)

      /** A document handed inline of `bytes`, over the `limit` the server takes inline (`limits.inline-bytes`). */
      case InlineBytes(bytes: Count, limit: Count)

      /** A document handed inline is not base64: `reported`. */
      case Base64Decodable(reported: External.Reported)

      /** A document is handed as a path the server can see or inline as base64: neither was given. */
      case PathOrBase64

      /** The path names no file the server can see: paths are resolved inside the server. */
      case ServerSees

      /** The source has no stored original to convert again. */
      case OriginalForReocr

      /** The source has no stored original to hand out. */
      case OriginalForDownload

      /** A note to import has no YAML frontmatter. */
      case Frontmatter

      /** A note to import names no citekey, in its frontmatter or beside it. */
      case NoteCitekey

      /** The article records no filter, so it has no generated coverage. */
      case FilterRecorded

      /** The region `x`, `y`, `w`, `h` asked of an image `width` by `height` starts outside it. */
      case RegionWithin(x: Count, y: Count, w: Count, h: Count, width: Count, height: Count)

      /** The review manager is library-wide: it takes no goal. */
      case ReviewLibraryWide

      /** The enrich manager is library-wide: it takes no goal. */
      case EnrichLibraryWide

      /** A seeding manager is for one research area: it needs its goal. */
      case SeedGoal

      /** A watcher is for one research area: it needs its goal. */
      case WatchGoal

      /** The enrich manager has no schedule: the ingests and the Enrich buttons feed it. */
      case EnrichCron

      /** A hint that changes a manager's settings names them. */
      case HintSettings

      /** A hold hint names when the hold ends. */
      case HintUntil

      /** A structured hint names what it acts on. */
      case HintTargets

      /** A schedule is set with its cron. */
      case CronGiven

      /** A role's chain names at least one model. */
      case ChainGiven

      /** A hint does not change settings: a manager's config does. `allowed` are the actions a hint takes. */
      case HintConfig(allowed: Vector[Raw])

      /** A manager's `rounds` (`stated`) is outside `least` to `cap`, the cap `agents.review.rounds-cap` sets. */
      case RoundsWithin(stated: Count, least: Count, cap: Count)

      /** `role` is no role the `owner` named `name` uses (a pipeline, or a manager of that kind): `roles` are the ones
        * it uses, none when it calls no model.
        */
      case RoleUsed(owner: RoleOwner, name: Raw, role: Raw, roles: Vector[Raw])

    /** What uses the agent roles a model is set for. */
    enum RoleOwner derives CanEqual:
      /** An agent pipeline, by its name: `--model-for` sets a model for one of its roles. */
      case Pipeline

      /** A manager, by its kind: its config's `models` sets a model for one of the roles its runs use. */
      case Manager

    /** What a work's DOI is looked up for. */
    enum Purpose derives CanEqual:
      /** Its record upstream. */
      case Lookup

      /** Its deposited reference list. */
      case References

      /** Its retraction and correction notices. */
      case Assessment

      /** The works that cite it. */
      case Citations

    /** The least and the greatest value taken, either open. */
    final case class Bounds(least: Option[Raw], greatest: Option[Raw])

    /** How fields given together must relate. */
    enum Exclusion:
      /** All of them or none. */
      case Together

      /** Exactly one of them. */
      case OneOf

      /** At most one of them. */
      case AtMostOne

      /** At least one of them. */
      case AtLeastOne

      /** Each no greater than the next (a start before its end). */
      case Ordered

      /** All of them the same value. */
      case Same

      /** No two of them the same value. */
      case Distinct

    /** How a text fails to be a secret's reference. */
    enum Form:
      /** It names no scheme: a bare value. */
      case Bare

      /** `value:` with nothing after it. */
      case EmptyValue

      /** `env:` with no environment variable's name after it. */
      case EnvName(name: Raw)

      /** `<vault>:` with no entry after it. */
      case NoEntry(vault: Raw)

      /** A scheme that is no vault's name. */
      case VaultName(name: Raw)

    /** The input a newtype refused: its type, its constraint and the input, as the newtype module recorded them. */
    def malformed(refusal: Newtype.Refusal): Invalid =
      Malformed.here(TypeName.of(refusal.typeName), Constraint.of(refusal.constraint), Raw(s"${refusal.raw}"))

  /** What the operation names does not exist. */
  sealed trait NotFound extends Problem

  object NotFound:

    /** No value of `typeName` has the key `key`. */
    final case class Entity(typeName: TypeName, key: Raw) extends NotFound

    /** No fetcher found a legal open-access copy of `work` (its DOI or arXiv id); `answers` is what each fetcher of the
      * chain answered, in the order they ran (empty when the chain's answers are reported apart).
      */
    final case class Copy(work: Raw, answers: Vector[Answer]) extends NotFound

    /** What one fetcher of the chain answered when it brought no copy ([[Copy]]): it had none, or it refused, and
      * `why`.
      */
    final case class Answer(fetcher: Fetcher, kind: Kind, why: Problem)

    /** A fetcher's id (`unpaywall`, `landing`). */
    type Fetcher = Fetcher.T
    object Fetcher extends Newtype[String, Pure]

    /** How a fetcher came back without a copy. */
    enum Kind:
      /** It looked and found no copy. */
      case Miss

      /** It would not fetch the copy (a challenge, a lending-only item, a licence). */
      case Refused

    /** No value of `typeName` has the key `key`; `listing` is the command that lists the keys there are. */
    final case class Listed(typeName: TypeName, key: Raw, listing: Listing) extends NotFound

    /** A command that lists the values of a kind, named where one is not found. */
    enum Listing:
      /** `histmersion merge list`: the merges, by id. */
      case Merges

    /** No `typeName` `key` is `within` the place it is looked for, and `from` refers to it. */
    final case class Dangling(typeName: TypeName, key: Raw, from: Where, within: Scope) extends NotFound

    /** Where a referred-to record is looked for. */
    enum Scope:
      /** In the bundle that holds the reference. */
      case Bundle

      /** In the bundle or in this collection. */
      case BundleOrHere

      /** In this server's gazetteer. */
      case Gazetteer

  /** The request is valid, but the stored state does not allow it now. */
  sealed trait Conflict extends Problem

  object Conflict:

    /** A value of `typeName` with the key `key` exists already. */
    final case class AlreadyExists(typeName: TypeName, key: Raw) extends Conflict

    /** `asked` was renamed to `now`: write to `now`. */
    final case class Renamed(typeName: TypeName, asked: Raw, now: Raw) extends Conflict

    /** `key` is an old key of `of` and still resolves to it, so it cannot name another value. */
    final case class Aliased(typeName: TypeName, key: Raw, of: Raw) extends Conflict

    /** `asked` is an old key of `now`: `action` is done to the value by its key now, not by an old one. */
    final case class OldKey(typeName: TypeName, asked: Raw, now: Raw, action: Action) extends Conflict

    /** A value of `typeName` with the key `key` exists already, and `by` would take that key too. */
    final case class Collides(typeName: TypeName, key: Raw, by: Raw) extends Conflict

    /** `key`, the key the caller chose for a new value of `typeName`, is not free (`clash`): another must be chosen. */
    final case class Claimed(typeName: TypeName, key: Raw, clash: Clash) extends Conflict

    /** What is done to a value that needs it named by its key now. */
    enum Action:
      case Delete, Rename

    /** Why a key chosen for a new value is not free. */
    enum Clash:
      /** A value has it. */
      case Taken

      /** A value that was removed had it. */
      case Removed

      /** It is an old key of `of`, and still resolves to it. */
      case OldKeyOf(of: Raw)

    /** The value changed since the caller read it: `sent` is the version the caller had, `stored` the one there is. */
    final case class Stale(typeName: TypeName, key: Raw, sent: Stamp, stored: Stamp) extends Conflict

    /** The value is in state `is`; the operation needs one of `wanted`. */
    final case class WrongState(typeName: TypeName, key: Raw, is: State, wanted: Vector[State]) extends Conflict

    /** `activity` is running and must finish first. */
    final case class Busy(activity: Activity) extends Conflict

    /** `count` values of the type `by` refer to it (one problem per type of referrer). */
    final case class Referenced(typeName: TypeName, key: Raw, by: TypeName, count: Count) extends Conflict

    /** A merge does not carry out `subject` (an issue, a record or a resolution, as the plan names it): `hold`. */
    final case class Held(subject: Where, hold: Hold) extends Conflict

    /** A person's pause, cancel or resume of the background job `job`, whose status is `status`, cannot be done:
      * `control` says why, and what to do instead.
      */
    final case class Job(job: Raw, status: State, control: Control) extends Conflict

    /** Why a job cannot be paused, cancelled or resumed. */
    enum Control:
      /** A job of `kind` runs as one piece and ends by itself: it is never paused or cancelled. */
      case OnePiece(kind: Raw)

      /** The status is none this server knows. */
      case Unrecognised

      /** The job has ended. */
      case Ended

      /** The job is paused already. */
      case AlreadyPaused

      /** The job has not started: cancelling it is what stops it. */
      case NotStarted

      /** The job was paused before the server restarted, which lost its way to go on: starting it again goes on where
        * it stopped.
        */
      case PausedBeforeRestart

      /** Only a paused job resumes. */
      case NotPaused

    /** Why a merge does not carry something out. */
    enum Hold:
      /** No decision settles the issue yet. */
      case Open

      /** The record is the same work as `here`, by `basis`: folding the two is `merge apply`'s. */
      case SameWork(here: Raw, basis: Raw)

      /** `gate` decided `decided`: folding the record into `into` is `merge apply`'s. */
      case Folds(gate: Raw, decided: Raw, into: Raw)

      /** The bundle's record and this collection's are merged into one: `merge apply`'s. */
      case Merges

      /** Citekeys given to resolve a collision (`subject` names the decisions): `merge apply`'s. */
      case Renames

      /** A label taken here is numbered anew: `merge apply`'s. */
      case Renumbers

      /** Carrying out the decision `decided` is `merge apply`'s. */
      case Carries(decided: Raw)

      /** The resolution names no issue of this plan: the records changed since it was decided, or it never was one. */
      case Stale

      /** The resolution applies only if `on` is decided `when`; `on` is decided `now`, or does not apply (`None`). */
      case Conditional(on: Raw, when: Raw, now: Option[Raw])

      /** Several records of the bundle map onto the record `subject` here. */
      case Collides

      /** The plan is `subject` now, not `sent`, the one reviewed: the collection, the bundle or the resolutions
        * changed.
        */
      case PlanChanged(sent: Raw)

      /** The decision `decided` needs the merged text, which was not sent. */
      case TextNotSent(decided: Raw)

      /** A `map` resolution names `citekey`, which is a citekey (or an old one) of another source here: the merge goes
        * under the other citekey.
        */
      case CitekeyOfAnother(citekey: Raw)

      /** A rename resolution names `citekey`, which is already a citekey (or an old one) here or in the bundle. */
      case CitekeyTaken(citekey: Raw)

      /** The same file is stored already, as the original of `citekey`. */
      case StoredAs(citekey: Raw)

      /** The autopilot's focus holds it: goal `goal` is not one of the active goals. */
      case Focus(goal: Raw)

    /** A version of a value as the store writes it: a time or a revision, in its wire spelling. */
    type Stamp = Stamp.T
    object Stamp extends Newtype[String, Pure]

    /** A state in its wire spelling (`running`, `settled`). */
    type State = State.T
    object State extends Newtype[String, Pure]:
      /** A state a value of any newtype names, as it is written (`State.of(job.status)`): unwrapped here, like
        * [[Raw.of]].
        */
      def of[T](value: T)(using RefinedType.Mirror[T]): State = State(s"$value")

    /** The work that holds the store or a resource while it runs. */
    enum Activity:
      case Backup, Restore, Vacuum, Reindex, MergeApply, BundleImport, Seed, Migration, CalibreGui

  /** The operation is not allowed, though it is valid and the state allows it. */
  sealed trait Refused extends Problem

  object Refused:

    /** A rule of the service forbids it. */
    final case class Policy(rule: Rule) extends Refused

    /** `limit` is spent: `used` of `cap`, until `until` when that is known. */
    final case class Quota(limit: Limit, used: Amount, cap: Amount, until: Option[EpochMs]) extends Refused

    /** `feature` is switched off: `setting` is `false`. `variable`: the environment variable that switches it on, when
      * the refusal names one; `effect`: what switching it on does, when the refusal says it.
      */
    final case class Disabled(
        feature: Feature,
        setting: SettingKey,
        variable: Option[SettingKey],
        effect: Option[Effect]
    ) extends Refused

    /** What switching a feature on does, as a switched-off refusal says it. */
    enum Effect derives CanEqual:
      /** The server writes to the library the collection reads (Calibre's write-back). */
      case WritesLibrary

      /** The fetcher obtains copies without the rights holder's authorisation: it stays off unless a person allows it.
        */
      case Unauthorised

    /** The rules a request can break. */
    enum Rule:
      /** The request needs a live run token. */
      case RunTokenRequired

      /** The request needs the API key. */
      case ApiKeyRequired

      /** The command may not write in the working directory it was given. */
      case WorkingDirDenied

      /** The read-only query holds a statement that writes. */
      case ReadOnlyQuery

      /** The query holds several statements; it runs exactly one. */
      case OneStatement

      /** The runtime refuses this login while the server listens beyond the local machine. */
      case LoginOutsideLocal

      /** Only legal open-access sources may be fetched. */
      case LegalOnly

      /** The collection is read-only: it takes no new items and rewrites none. */
      case ReadOnly

      /** The item is only lent (borrowed on its site), never downloaded. */
      case LendingOnly

      /** The client reads ranges of a file, never a whole one. */
      case RangesOnly

      /** A plugin adds to the host's registries only from inside its `register`, never after it returned. */
      case RegistrationOver

      /** Bookmarks, notes and read marks belong to a person: an automated run has none. */
      case PersonOnly

    /** The allowances a request can spend. */
    enum Limit:
      case DailySpend, MonthlySpend, RunSpend, ProviderQuota, RateLimit, PlanAllowance

    /** An amount of an allowance: money, calls or tokens, as its limit counts them. */
    type Amount = Amount.T
    object Amount extends Newtype[Double, Pure]:
      /** An amount written as a decimal number. */
      def read(number: String): Option[Amount] = number.toDoubleOption.map(Amount(_))

    /** A feature a setting switches on and off, by its name. */
    type Feature = Feature.T
    object Feature extends Newtype[String, Pure]

  /** A service outside Histmersion did not do what was asked of it. */
  sealed trait Upstream extends Problem

  object Upstream:

    /** `upstream` answered `status` at `url`; `body` is the start of what it said. `at`: the part of what was asked
      * (`object 7`) the request was for, when it was one of several.
      */
    final case class Http(
        upstream: Name,
        url: Url,
        status: Status,
        body: External.ReplyExcerpt,
        at: Vector[Field]
    ) extends Upstream

    object Http:
      /** The problem of the request as a whole: no path into it. */
      def here(upstream: Name, url: Url, status: Status, body: External.ReplyExcerpt): Http =
        Http(upstream, url, status, body, Vector.empty)

    /** `upstream` answered `status` at `url` with a bot-protection challenge, not the page: taken as no answer. */
    final case class Challenged(upstream: Name, url: Url, status: Status, at: Vector[Field]) extends Upstream

    object Challenged:
      /** The problem of the request as a whole: no path into it. */
      def here(upstream: Name, url: Url, status: Status): Challenged = Challenged(upstream, url, status, Vector.empty)

    /** `upstream` (a model's plan) reports its own usage limit reached, until `until` when it said: the next model of
      * the chain is asked meanwhile.
      */
    final case class Limited(upstream: Name, until: Option[EpochMs]) extends Upstream

    /** The service was not asked in this sweep: `why` it may not be asked now (its budget spent, it asked to wait). It
      * is asked again in the next sweep.
      */
    final case class Unasked(why: Problem) extends Upstream

    /** `upstream` could not be reached at `url`. */
    final case class Unreachable(
        upstream: Name,
        url: Url,
        reported: External.Reported,
        at: Vector[Field]
    ) extends Upstream

    object Unreachable:
      /** The problem of the request as a whole: no path into it. */
      def here(upstream: Name, url: Url, reported: External.Reported): Unreachable =
        Unreachable(upstream, url, reported, Vector.empty)

    /** `upstream` did not answer at `url` within `after`. */
    final case class Timeout(upstream: Name, url: Url, after: Millis, at: Vector[Field]) extends Upstream

    object Timeout:
      /** The problem of the request as a whole: no path into it. */
      def here(upstream: Name, url: Url, after: Millis): Timeout = Timeout(upstream, url, after, Vector.empty)

    /** `upstream` answered at `url`, but not with `expected`. */
    final case class BadReply(
        upstream: Name,
        url: Url,
        expected: Shape,
        reported: External.Reported,
        at: Vector[Field]
    ) extends Upstream

    object BadReply:
      /** The problem of the request as a whole: no path into it. */
      def here(upstream: Name, url: Url, expected: Shape, reported: External.Reported): BadReply =
        BadReply(upstream, url, expected, reported, Vector.empty)

    /** The service, by the name its settings and plugin use (`openalex`, `core`, the Histmersion server itself). */
    type Name = Name.T
    object Name extends Newtype[String, Pure]

    /** An address of the service. */
    type Url = Url.T
    object Url extends Newtype[String, Pure]

    /** An HTTP status code. */
    type Status = Status.T
    object Status extends Newtype[Int, Pure]:
      /** A status written in decimal digits. */
      def read(digits: String): Option[Status] = digits.toIntOption.map(Status(_))

      val NotFound: Status = Status(404)
      val Unauthorized: Status = Status(401)
      val Forbidden: Status = Status(403)

      extension (s: Status)
        /** 401 or 403: the upstream wants a key or does not take the client. */
        def refusesAccess: Boolean = s == Unauthorized || s == Forbidden

        /** 404: the upstream has no such page. */
        def isNotFound: Boolean = s == NotFound

    /** What a reply was expected to be. */
    enum Shape:
      case Json, Xml, Html, Text, Image, Document, Archive, Problems, Range

  /** A file, a process or the database failed under the operation. */
  sealed trait Io extends Problem

  object Io:

    /** `op` on `path` failed. */
    final case class File(op: Access, path: Path, reported: External.Reported) extends Io

    /** `path` is not what the operation needs: `expected`. */
    final case class Wrong(path: Path, expected: Kind) extends Io

    /** Data in `format` is damaged at `at`. */
    final case class Corrupt(format: Format, at: Where, damage: Damage) extends Io

    /** `tool` did not finish as it should: how it ended. */
    final case class Process(tool: Tool, ending: Ending) extends Io

    /** The database refused `op`. */
    final case class Database(op: DbOp, reported: External.Reported) extends Io

    /** The entry `entry` of an `archive` file is not as its manifest or its records say: `fault`. */
    final case class Entry(archive: Format, entry: Path, fault: Fault) extends Io

    /** What is wrong with an entry of an archive. */
    enum Fault:
      /** The archive holds no such entry. */
      case Missing

      /** The manifest lists it; the archive does not hold it. */
      case Omitted

      /** The archive holds it; the manifest does not list it. */
      case Unlisted

      /** The archive holds it more than once. */
      case Repeated

      /** It is no entry such an archive holds. */
      case Foreign

      /** It comes first, where `first` belongs. */
      case Ahead(first: Path)

      /** No record of the archive refers to it. */
      case Unreferenced

      /** `uses` records refer to it, and the archive does not hold it. */
      case Dangling(uses: Count)

      /** Its `measure` is `found`, where the manifest lists `listed`. */
      case Differs(measure: Measure, listed: Raw, found: Raw)

    /** What a manifest lists of an entry. */
    enum Measure:
      case Sha256, Bytes, Lines, Entries

    /** A path on a file system, as text: the browser has no file system type. */
    type Path = Path.T
    object Path extends Newtype[String, Pure]

    /** A program run as a process, by its command name (`calibredb`, `pg_dump`). */
    type Tool = Tool.T
    object Tool extends Newtype[String, Pure]

    /** A process's exit status. */
    type ExitCode = ExitCode.T
    object ExitCode extends Newtype[Int, Pure]:
      /** An exit status written in decimal digits. */
      def read(digits: String): Option[ExitCode] = digits.toIntOption.map(ExitCode(_))

    enum Access:
      case Read, Write, Create, Delete, Move, List

    /** What a path should be. `Listed`: named by the manifest it is checked against. `OnPath`: found on the `PATH`. */
    enum Kind:
      case File, Directory, Present, Absent, Executable, Listed, OnPath

      /** Inside `directory`, the one the operation is confined to. */
      case Inside(directory: Path)

    enum Format:
      case Bundle, Tar, Json, Png, PmTiles, Pdf, Image, Text, Hocon, Yaml, Xml, Kit, Zip

    /** What is wrong with the data. */
    enum Damage:
      case Checksum, Truncated, Undecodable

      /** It holds `found` units where `expected` belong. */
      case Length(expected: Count, found: Count)

      /** Its reader refused it; `reported` is what the reader said. */
      case Unreadable(reported: External.Reported)

      /** It is written in format `found`, newer than the format `reads` this version reads. */
      case Newer(found: Raw, reads: Raw)

      /** It comes from database schema `found`, newer than the schema `reads` this version has. */
      case Schema(found: Count, reads: Count)

    /** How a process ended that did not end well. */
    enum Ending:
      /** It exited with `code`; `stderr` is the end of what it wrote to its standard error. */
      case Exited(code: ExitCode, stderr: External.Stderr)

      /** It did not finish within `after`. */
      case TimedOut(after: Millis)

      /** It could not be started. */
      case NotStarted(reported: External.Reported)

    enum DbOp:
      case Connect, Query, Migrate, Backup, Restore

  /** The service's settings do not allow the operation. */
  sealed trait Config extends Problem

  object Config:

    /** `key` is set wrongly: `fault`. */
    final case class Setting(key: SettingKey, fault: Fault) extends Config

    /** This server has no `capability`. */
    final case class Unavailable(capability: Capability) extends Config

    /** Reloading the settings failed at `stage`, in `scope`. */
    final case class Reload(stage: Stage, scope: Scope) extends Config

    /** The parts a reload rebuilds depend on each other in a cycle: `scopes`, the first one again at the end. */
    final case class Cycle(scopes: NonEmptyVector[Scope]) extends Config

    /** `model` has no price in the agents' price table: an unpriced model is refused, never taken as free. `metered`:
      * why a model of a plan's provider is billed by the token here, when that is why it needs a price.
      */
    final case class Unpriced(model: Run.Model, metered: Option[Metered]) extends Config

    /** Why a model of a provider that can run on a plan is billed by the token. */
    enum Metered:
      /** `provider` is set to use an API key, so it bills the API, not the plan. */
      case ApiKey(provider: Upstream.Name)

      /** An opencode model is metered unless it is `openai/…` on a ChatGPT login. */
      case Opencode

    /** What is wrong with a setting. */
    enum Fault:
      /** It has no value, or an empty one. */
      case Empty

      /** The environment variable it reads is not set. */
      case Unset(variable: SettingKey)

      /** Its value is no value of the setting's type. */
      case Malformed(raw: Raw)

      /** Its value is none the setting knows. */
      case Unknown(raw: Raw)

      /** It names `id`, which no enabled plugin registered: that entry is skipped. */
      case Unregistered(id: Raw)

      /** It names `id`, which is no plugin in this binary. */
      case NoPlugin(id: Raw)

      /** It contradicts `other`. */
      case Conflicts(other: SettingKey)

      /** It names a secret no vault holds. */
      case NoSecret

      /** It is a block of settings, not one setting. */
      case Block

      /** Its value is a `found` where an `expected` belongs. */
      case WrongKind(expected: ValueKind, found: ValueKind)

      /** Its reader refused its value; `reported` is what the reader said. */
      case Unreadable(reported: External.Reported)

      /** It is no setting Histmersion knows: it is ignored. */
      case Undeclared

      /** It is no setting of the plugin whose block it is in: the plugin's defaults do not declare it. */
      case NotInDefaults

      /** It is outside the `histmersion` block, where no setting is: it is ignored. */
      case OutsideRoot

      /** It holds a credential itself, which is never written into the settings file; `variable` is the environment
        * variable that holds it instead, when it has one.
        */
      case Credential(variable: Option[SettingKey])

      /** `upstream` refused the key or password it holds: it answered `status` (401, 403). */
      case Rejected(upstream: Upstream.Name, status: Upstream.Status)

      /** It holds `value:` (the credential itself, in the file) and the person did not confirm it. */
      case Literal

      /** The file writes it with a substitution or `+=`, which is kept exactly as it is. */
      case Substituted

      /** It is set in `file`, which the settings file includes. */
      case Included(file: Io.Path)

      /** Written, it did not read back as it was written. */
      case NotReadBack

      /** It holds no key, and the plugin needs one: `needs`, the plugin's words for the key and where it is issued. */
      case KeyNeeded(needs: External.PluginSaid)

    /** The kinds of value a setting holds. */
    enum ValueKind:
      case Text, Number, Flag, List, Block, Null

    /** What a server (or the host a plugin runs in) may lack. */
    enum Capability:
      case Originals, AcquisitionChain, Runtimes, Converter, Gazetteer, Basemap, Agents, Calibre, Processes, LocalFiles,
        MediaImport, EventCatalogues, AiProviders, GazetteerImport, SourceCollections, Network, CitationAlerts

    /** The stages of a settings reload. */
    enum Stage:
      case Parse, Plan, Close, Start

    /** The part of the running service a reload rebuilds, by its name. */
    type Scope = Scope.T
    object Scope extends Newtype[String, Pure]

  /** A plugin did not do its part. */
  sealed trait Plugin extends Problem

  object Plugin:

    /** `plugin` could not be loaded: `fault`. */
    final case class Load(plugin: Id, fault: Fault) extends Plugin

    /** `plugin` did not take `item`: `refusal`. A per-item outcome, accumulated over a batch. */
    final case class Rejected(plugin: Id, item: Raw, refusal: Refusal) extends Plugin

    /** The secret vault `vault` failed: `failure`. */
    final case class Vault(vault: Id, failure: Retrieval) extends Plugin

    /** `plugin` needs `module` at `wanted`, newer than the `shipped` one the server holds: one class loader holds one
      * version, and the server's would win.
      */
    final case class Dependency(plugin: Id, module: Raw, wanted: Raw, shipped: Raw) extends Plugin

    /** `plugin` failed in a way no other case names: `claim` is what kind of failure it reports, `text` its words. */
    final case class Said(plugin: Id, claim: Claim, text: External.PluginSaid) extends Plugin

    /** A plugin's (or a vault's) id. */
    type Id = Id.T
    object Id extends Newtype[String, Pure]

    /** The key a plugin jar is signed with, as the host shows it. */
    type Signer = Signer.T
    object Signer extends Newtype[String, Pure]

    /** Why a plugin did not load. */
    enum Fault:
      case Missing, NullComponent, BadDescriptor, VersionMismatch, Duplicate

      /** Loading it threw. */
      case Threw(reported: External.Reported)

      /** It was built against plugin SPI `built`; this version speaks `speaks`. */
      case SpiMismatch(built: Raw, speaks: Raw)

      /** It was built against a plugin SPI other than `speaks`, the one this version speaks, which cannot be told. */
      case ForeignSpi(speaks: Raw)

      /** It needs the class `name`, which no jar has: its library was neither shipped nor installed with it. */
      case MissingClass(name: Raw)

      /** It was installed signed by `pinned`; this jar is signed by `seen`. */
      case SignerChanged(pinned: Signer, seen: Signer)

      /** The jar names no plugin provider. */
      case NoProvider

      /** Its id is another jar's already: `by`. */
      case Taken(by: Raw)

    /** Why a plugin did not take an item. */
    enum Refusal:
      case NoLicence, NoImage, NoName, NoLocation, NoDate, Duplicate, Restricted

      /** Its licence is of the family `family`, which the collection's allowlist does not hold. */
      case LicenceNotAllowed(family: Raw)

      /** The provider's rights field `field` does not mark it open: it states `stated` (nothing, when empty). */
      case NotOpen(field: Field, stated: Vector[Raw])

      /** None of its images is open: the images' rights field `field` states `stated` (nothing, when empty). */
      case NoOpenImage(field: Field, stated: Vector[Raw])

    /** How a vault failed. */
    enum Retrieval:
      case NoVault, NoEntry, EmptyValue, Locked

      /** It did not answer within `after`. */
      case TimedOut(after: Millis)

      /** Its tool failed; `stderr` is the end of what it wrote. */
      case Failed(stderr: External.Stderr)

      /** `ref` holds an empty value. */
      case Blank(ref: Raw)

      /** The vault holds no `ref`. */
      case Absent(ref: Raw)

      /** Its tool could not read `ref`: it exited with `exit`; `stderr` is the end of what it wrote. */
      case Unread(ref: Raw, exit: Io.ExitCode, stderr: External.Stderr)

      /** None of the `configured` hardware keys is connected; `seen` are the ones that are. */
      case NoKey(configured: Vector[Raw], seen: Vector[Raw])

      /** The hardware key `key` did not open the vault: `attempt`; `stderr` is what the tool said, when it said
        * anything.
        */
      case Key(key: Raw, attempt: Attempt, stderr: Option[External.Stderr])

    /** How one try with a hardware key failed. */
    enum Attempt:
      case NotConnected, NotTouched, WrongCredentials, ChallengeFailed, Refused

    /** The kinds of failure a plugin may report in its own words. */
    enum Claim:
      case Unavailable, Rejected, Misconfigured, Failed

  /** A run of the workflow engine stopped or cannot go on. */
  sealed trait Run extends Problem

  object Run:

    /** The run waits for `reason`, until `until` when that is known. */
    final case class Paused(reason: PauseReason, until: Option[EpochMs]) extends Run

    /** No model of `role`'s chain can run; `tried` are the ones passed over. */
    final case class NoModel(role: Role, tried: Vector[Model]) extends Run

    /** The scheduler holds it back: `block`. */
    final case class Blocked(block: Block) extends Run

    /** A restart cut it short. */
    case object Interrupted extends Run

    /** A restart cut `activity` short. It commits or rolls back as a whole, never half applied: its own listing tells
      * whether it took effect.
      */
    final case class CutShort(activity: Conflict.Activity) extends Run

    /** `activity` finished, but its result was not recorded: its own listing tells what it did. */
    final case class Unrecorded(activity: Conflict.Activity) extends Run

    /** `by` cancelled it. */
    final case class Cancelled(by: Actor) extends Run

    /** The autopilot drains since `since`; it resumes once the drain is lifted. */
    final case class Draining(since: EpochMs) extends Run

    /** It cannot go on at `step`: `cause`. */
    final case class Aborted(step: Step, cause: AbortCause) extends Run

    enum PauseReason:
      case Requested, Allowance, Budget, Provider, Approval, Schedule

    /** An agent role, in its wire spelling. */
    type Role = Role.T
    object Role extends Newtype[String, Pure]

    /** A model, by its id. */
    type Model = Model.T
    object Model extends Newtype[String, Pure]

    /** A step of a run, by its name. */
    type Step = Step.T
    object Step extends Newtype[String, Pure]

    /** A pool of runs, by its name. */
    type Pool = Pool.T
    object Pool extends Newtype[String, Pure]

    /** Why the scheduler holds a run back. */
    enum Block:
      /** `pool` runs as many as it may. */
      case PoolFull(pool: Pool)

      case SpendCap, Draining, OutsideWindow, Waiting

    enum Actor:
      case Person, Automation, Server

    enum AbortCause:
      case Disappeared, CheckpointMismatch, VersionChanged

  /** Content written by a person or a model breaks a rule of the writing. */
  sealed trait Content extends Problem

  object Content:

    /** A review finding about an article: `cause`. */
    final case class Finding(cause: Flaw) extends Content

    /** An agent's proposal breaks `rule` at `at`. */
    final case class Proposal(rule: Requirement, at: Where) extends Content

    /** An agent's answer as a whole breaks `rule` (an empty summary, too few places that resolve): no one part of it.
      */
    final case class Answer(rule: Requirement) extends Content

    /** A place a finding names (`finding`, counted from 1) that no gazetteer place matches. */
    final case class Unplaced(place: Raw, finding: Count)

    enum Flaw:
      case HandWrittenSourcesCited, UncitedParagraph, UnknownCitekey, FormattingIntroduced, MissingSection

    enum Requirement:
      case NoYearZero, CitesNoSource, UnknownCitekey, OutsidePeriod

      /** A stand-in (placeholder, TODO, lorem ipsum) where the text belongs. */
      case StandIn

      /** A probe's answer left the title empty: it is the source's exact title, copied from its record. */
      case ExactTitle

      /** A replace edit gave no new text: it needs the text, or is a delete. */
      case ReplaceText

      /** An edit's anchor is not in the article: it quotes the current body exactly, earlier edits included. */
      case AnchorInArticle

      /** At most `cap` claims per source; it gave `gave`. */
      case TooManyClaims(cap: Count, gave: Count)

      /** An agent's proposal gives no quote: the sentence of the record that shows it, copied exactly. */
      case QuoteGiven

      /** The proposal's quote is in none of `records` (the citekeys whose summary, findings and body were searched). */
      case QuoteInRecords(records: Vector[Raw])

      /** `stated` is no citekey of the corpus. */
      case KnownCitekey(stated: Raw)

      /** `stated`, a DOI a ranking picked, is not one of the candidates it was offered (a model inventing a work). */
      case ListedCandidate(stated: Raw)

      /** The proposal says nothing about why. */
      case EvidenceGiven

      /** A status change names no status. */
      case StatusNamed

      /** `unassessed` is where a source starts, never a change to propose. */
      case NotUnassessed

      /** An edge's target is no citekey of the corpus. */
      case EdgeTarget

      /** An edge from a source to itself. */
      case NoSelfEdge

      /** A BCE year is stated as zero or a negative number (`stated`). */
      case PositiveBce(stated: Raw)

      /** A CE year is stated as zero or a negative number (`stated`): BCE says it. */
      case CeFromOne(stated: Raw)

      /** A raw (uncalibrated) radiocarbon age (`stated`) is no calendar year. */
      case Calibrated(stated: Raw)

      /** A period starts (`start`) after it ends (`end`). */
      case StartBeforeEnd(start: Raw, end: Raw)

      /** A signed year is 0: historical reckoning has none (1 BCE is -1, 1 CE is 1). */
      case HistoricalReckoning

      /** A year lies beyond ±`greatest`, the most distant year the corpus holds. */
      case YearWithin(greatest: Raw)

      /** A period (`start`..`end`) lies outside the source's own period (`ownStart`..`ownEnd`). */
      case WithinSourcePeriod(start: Raw, end: Raw, ownStart: Raw, ownEnd: Raw)

      /** `[[target]]` links nothing: it is neither a citekey nor an article slug. */
      case LinkTarget(target: Raw)

      /** Escaped link brackets (`\[\[…\]\]`) render as text and link nothing. */
      case UnescapedLinks

      /** A config hint gives no settings to change (`key=value`). */
      case SettingsGiven

      /** A hint names items the manager does not hold (`unknown`): they are named exactly as listed. */
      case KnownItems(unknown: Vector[Raw])

      /** A hint names neither items (targets) nor a tag. */
      case ItemsNamed

      /** A hold gives neither `until` (a date) nor `untilSource` (a DOI or citekey). */
      case HoldUntil

      /** `stated` is no date (`2026-11-01`). */
      case DateGiven(stated: Raw)

      /** A priority (`stated`) beyond ±`greatest`. */
      case PriorityWithin(stated: Raw, greatest: Raw)

      /** None of the items a hint names exists. */
      case ExistingItems

      /** A proposed filter selects nothing: it needs a query, topics or tags. */
      case FilterSelects

      /** A filter's rationale is empty: it says why these sources are the article's. */
      case FilterRationale

      /** An article plan's rationale is empty: it says how the articles cover the area. */
      case PlanRationale

      /** A proposed article has no title. */
      case ArticleTitle

      /** A proposed article has no filter: it needs a query, topics or tags. */
      case ArticleFilter

      /** A classification gives no summary. */
      case SummaryGiven

      /** A status other than `unassessed` comes with no evidence: the text's reason for it. */
      case StatusEvidence

      /** The notes are empty. */
      case NotesGiven

      /** A finding's claim is empty. */
      case ClaimGiven

      /** An extraction's claim says nothing. */
      case NoClaim

      /** A claim's quote is not in the record of `citekey`: it is copied exactly. */
      case QuoteInRecord(citekey: Raw)

      /** A record judged irrelevant (or giving no claim) does not say why it is not used. */
      case ReasonGiven

      /** The first citation of `citekey` does not state its status `status` (the writing guide's rule 2). */
      case StatusUnstated(citekey: Raw, status: Raw)

      /** A finding's quote is not in the text it was read from; the best stretch matched `best` of its words. */
      case QuoteInWindow(best: Share)

      /** The year `year` is almost surely the `century`th century stored as a year. */
      case CenturyAsYear(year: Raw, century: Count)

      /** The claim says `phrase`, which only makes sense next to a table: a finding stands alone on the map. */
      case PointerPhrase(phrase: Raw)

      /** Only `resolved` of `total` named places resolve to a gazetteer place, fewer than `least` per cent; `missing`
        * did not.
        */
      case PlacesResolve(resolved: Count, total: Count, least: Count, missing: Vector[Unplaced])

      /** An article plan proposes no article. */
      case ArticlesGiven

      /** At most `cap` articles; the plan gave `gave`. */
      case TooManyArticles(cap: Count, gave: Count)

      /** The filter `line` returns `total` sources, fewer than `least`: it is widened. */
      case FilterTooNarrow(line: Raw, total: Count, least: Count)

      /** The filter `line` returns `total` sources, more than `most`: it is narrowed. */
      case FilterTooBroad(line: Raw, total: Count, most: Count)

      /** The filter `line` returns `returned` of the `cited` sources the article cites (`share` per cent), fewer than
        * `least` per cent: it misses `missed` (the first twelve).
        */
      case FilterMisses(line: Raw, returned: Count, cited: Count, share: Count, least: Count, missed: Vector[Raw])

      /** A draft's text is empty. */
      case TextGiven

      /** An extend names `section`, a heading the article does not have: it names an existing one, or none. */
      case KnownHeading(section: Raw)

      /** A draft writes a Sources cited section, which the page generates. */
      case NoSourcesCited

      /** A paragraph of a draft cites no source; `opening` is the paragraph's start (its first 80 characters show). */
      case ParagraphCited(opening: Raw)

      /** A revised body still holds a paragraph that cites no source; `opening` is its start, as for
        * [[ParagraphCited]].
        */
      case StillUncited(opening: Raw)

      /** A compulsory section is missing; any of `alternatives` would do (the writing guide's rule 4). */
      case SectionPresent(alternatives: Vector[Raw])

      /** The text uses banned filler: `matches`, as the lint lists them. */
      case NoFiller(matches: Raw)

      /** A draft cites `citekey`, none of whose claims is in the claim table. */
      case ClaimTabled(citekey: Raw)

      /** A new article's draft gives no title. */
      case TitleGiven

      /** A new article's draft gives no summary: one or two sentences on what the article answers. */
      case ArticleSummary

      /** A draft writes a Not used section, which code generates from the filter and the stated reasons. */
      case NotUsedGenerated

      /** A draft writes a `_Method_` note, which code generates from the filter. */
      case MethodGenerated

      /** A revised body holds a hand-written Not used section, which the page generates. */
      case NotUsedHandWritten

      /** A revised body holds a hand-written Method note, which the page generates. */
      case MethodHandWritten

      /** A draft gives a reason for `citekey`, which is not a source of the claim table. */
      case ReasonForTabled(citekey: Raw)

      /** A draft gives a reason for `citekey`, which it cites: a reason is only for a source it leaves out. */
      case ReasonForUncited(citekey: Raw)

      /** A draft gives an empty reason for `citekey`. */
      case ReasonText(citekey: Raw)

      /** `citekey` has claims in the claim table, but the draft neither cites it nor gives a reason. */
      case TabledAccounted(citekey: Raw)

      /** A revised body is empty. */
      case BodyGiven

      /** The Markdown lint's `rule` finds an error in the text: `message`, at `excerpt` (empty when it shows none). */
      case FormatClean(rule: Raw, message: Raw, excerpt: Raw)

      /** A sub-goal `slug` of a goal plan has no title. */
      case SubGoalTitle(slug: Raw)

      /** A goal plan gives an empty search query. */
      case QueryGiven

      /** A goal plan's search query `query` is longer than `most` characters (its first 40 show). */
      case QueryWithin(query: Raw, most: Count)

      /** `stated` is no DOI (`10.xxxx/…`). */
      case DoiGiven(stated: Raw)

      /** At most `cap` sub-goals; the plan gave `gave`. */
      case TooManySubGoals(cap: Count, gave: Count)

      /** At most `cap` search queries; the plan gave `gave`. */
      case TooManyQueries(cap: Count, gave: Count)

      /** At most `cap` works; the plan gave `gave`. */
      case TooManyWorks(cap: Count, gave: Count)

      /** A goal plan proposes nothing: no sub-goal, query or work. */
      case PlanGiven

      /** A goal plan's rationale is empty: it says how the plan covers the goal. */
      case GoalPlanRationale

  /** Something that should not happen happened: a bug, or a fault of the platform. */
  sealed trait Internal extends Problem

  object Internal:

    /** `exception` was thrown at `where`; `reported` is its message. The exception is logged under `id`, which the
      * answer names so the log line can be found.
      */
    final case class Unexpected(id: Id, where: Where, exception: ExceptionName, reported: External.Reported)
        extends Internal

    /** A problem's id: the key under which the log holds the exception, named in the answer. */
    type Id = Id.T
    object Id extends Newtype[String, Pure]:
      /** A new id: the first eight hexadecimal digits of a random UUID, enough to find one line in a day's log. */
      def mint(): Id = Id(s"${java.util.UUID.randomUUID()}".take(8))

    /** The class of an exception, by its full name. */
    type ExceptionName = ExceptionName.T
    object ExceptionName extends Newtype[String, Pure]:
      def of(thrown: Throwable): ExceptionName = ExceptionName(s"${thrown.getClass.getName}")

  // ---- outside the groups -------------------------------------------------------------------------------------

  /** A failure not classified yet: the text the code before the closed `Problem` wrote. Only the change that brings
    * every construction site over writes it; each later change replaces some with their cases, until none is left.
    */
  final case class Unclassified(text: Legacy.Text) extends Problem

  object Unclassified:
    /** TEMPORARY (G60 step 1, counted by `G60fromString`): an exception's message as an unclassified problem, as the
      * values module's String `Problem.of` read it; its slice turns the site into [[Problem.from]].
      */
    def of(thrown: Throwable): Unclassified = Unclassified(Legacy.Text.of(thrown))

  /** What a stored column held. */
  sealed trait Stored extends Problem

  object Stored:

    /** A sentence no case writes: text an older version stored, kept as it was. No code builds one but the reader of a
      * stored column.
      */
    final case class Unrecognised(text: Text) extends Stored

    /** The text a column holds, as it holds it. */
    type Text = Text.T
    object Text extends Newtype[String, Pure]

  /** A problem known only by its sentence, as another program wrote it: how the browser reads the server's typed
    * entries, since it shows a problem and never tells cases apart, so it links none of the cases' codecs. It is no
    * case of any group and no code branches on it.
    */
  final case class Relayed(sentence: Sentence) extends Problem

  // ---- the operations -----------------------------------------------------------------------------------------

  /** An exception met at `where`, as a problem under the id `id`: its class and its message, captured once, here. */
  def from(thrown: Throwable, where: Where, id: Internal.Id): Problem =
    Internal.Unexpected(id, where, Internal.ExceptionName.of(thrown), External.Reported.of(thrown))

  extension (problem: Problem)
    /** The group the case belongs to. */
    def group: Group = problem match
      case _: Invalid      => Group.Invalid
      case _: NotFound     => Group.NotFound
      case _: Conflict     => Group.Conflict
      case _: Refused      => Group.Refused
      case _: Upstream     => Group.Upstream
      case _: Io           => Group.Io
      case _: Config       => Group.Config
      case _: Plugin       => Group.Plugin
      case _: Run          => Group.Run
      case _: Content      => Group.Content
      case _: Internal     => Group.Internal
      case _: Unclassified => Group.Unclassified
      case _: Stored       => Group.Stored
      case _: Relayed      => Group.Relayed

    /** TEMPORARY (G60 step 1, counted by `G60fromString`): the words of an [[Unclassified]] problem as its site wrote
      * them, and the [[Text.sentence]] of any other case, for the code that still builds one problem's text into
      * another's until its slice. Goes with `Unclassified`.
      */
    def legacyText: String = problem match
      case Unclassified(text) => s"$text"
      case other              => Text.sentence(other)

    /** Is it a missing entity? */
    def isNotFound: Boolean = problem match
      case _: NotFound => true
      case _           => false

    /** The problem as the caller's input caused it: a damaged document the request itself carried is invalid input
      * ([[Invalid.Unparsable]]), not a damaged file of the server's ([[Io.Corrupt]]); any other problem is as it is.
      */
    def input: Problem = problem match
      case Io.Corrupt(format, where, damage) => Invalid.Unparsable(format, where, damage, Vector.empty)
      case other                             => other

    /** The HTTP status an upstream answered, when it answered one. */
    def answered: Option[Upstream.Status] = problem match
      case Upstream.Http(_, _, status, _, _)                   => Some(status)
      case Upstream.Challenged(_, _, status, _)                => Some(status)
      case Config.Setting(_, Config.Fault.Rejected(_, status)) => Some(status)
      case _                                                   => None

    /** Did an upstream refuse the client (401, 403: a key wanted, a bot challenge)? Asking again is refused alike. */
    def accessRefused: Boolean = answered.exists(_.refusesAccess)

    /** The same problem, found in `field` of the request: an input's path, or the part of a request an upstream failed
      * on, gets `field` in front.
      */
    def within(field: Field): Problem = problem match
      case Invalid.Malformed(t, c, raw, at)   => Invalid.Malformed(t, c, raw, field +: at)
      case Invalid.Unknown(t, raw, known, at) => Invalid.Unknown(t, raw, known, field +: at)
      case Invalid.Unreadable(t, raw, eg, at) => Invalid.Unreadable(t, raw, eg, field +: at)
      case Invalid.Missing(f, at)             => Invalid.Missing(f, field +: at)
      case Invalid.Undeclared(f, ds, at)      => Invalid.Undeclared(f, ds, field +: at)
      case Invalid.Wrapped(t, raw, m, at)     => Invalid.Wrapped(t, raw, m, field +: at)
      case Invalid.StoredOn(t, i, o, at)      => Invalid.StoredOn(t, i, o, field +: at)
      case Invalid.Ambiguous(t, raw, n, at)   => Invalid.Ambiguous(t, raw, n, field +: at)
      case Invalid.Repeated(t, raw, at)       => Invalid.Repeated(t, raw, field +: at)
      case Invalid.Misplaced(f, under, at)    => Invalid.Misplaced(f, under, field +: at)
      case Invalid.OutOfRange(t, raw, b, at)  => Invalid.OutOfRange(t, raw, b, field +: at)
      case Invalid.Inverted(start, end, at)   => Invalid.Inverted(start, end, field +: at)
      case Invalid.OutOfTime(y, least, g, at) => Invalid.OutOfTime(y, least, g, field +: at)
      case Invalid.Unparsable(f, w, d, at)    => Invalid.Unparsable(f, w, d, field +: at)
      case Invalid.Inconsistent(fs, rule, at) => Invalid.Inconsistent(fs, rule, field +: at)
      case Invalid.Reference(form, at)        => Invalid.Reference(form, field +: at)
      case Invalid.Broken(rule, at)           => Invalid.Broken(rule, field +: at)
      case Upstream.Http(n, url, st, b, at)   => Upstream.Http(n, url, st, b, field +: at)
      case Upstream.Unreachable(n, u, r, at)  => Upstream.Unreachable(n, u, r, field +: at)
      case Upstream.Challenged(n, u, st, at)  => Upstream.Challenged(n, u, st, field +: at)
      case Upstream.Timeout(n, u, after, at)  => Upstream.Timeout(n, u, after, field +: at)
      case Upstream.BadReply(n, u, e, r, at)  => Upstream.BadReply(n, u, e, r, field +: at)
      case other                              => other

    /** The same problem, found inside `where` (a finding, a claim of an answer): a proposal's place and an input's path
      * get it in front, so that the sentence starts `where: `.
      */
    def inside(where: Where): Problem = problem match
      case Content.Proposal(rule, at) => Content.Proposal(rule, Where(s"$where: $at"))
      case Content.Answer(rule)       => Content.Proposal(rule, where)
      case other                      => other.within(Field(s"$where"))

  // ---- one of each ------------------------------------------------------------------------------------------

  /** One value of every case, with every field set: what the renderers' tables are checked over. */
  val examples: Vector[Problem] =
    val citekey = TypeName("Citekey")
    val core = Upstream.Name("core")
    val url = Upstream.Url("https://api.core.ac.uk/v3/search/works")
    val reported = External.Reported("java.io.IOException: connection reset")
    Vector(
      Invalid.Malformed(
        citekey,
        Constraint(
          Constraint.Name("Match[\"[a-z][a-z0-9_-]*\"]"),
          Constraint.Description("Should match [a-z][a-z0-9_-]*")
        ),
        Raw("Gimbutas 1956"),
        Vector(Field("edges"), Field("dst"))
      ),
      Invalid.Unknown.here(
        TypeName("Source.Status"),
        Raw("dubious"),
        Vector(Raw("current"), Raw("contested"))
      ),
      Invalid.Unreadable(
        TypeName("Year"),
        Raw("13oo BCE"),
        Vector(Raw("1300 BCE"), Raw("530 CE"), Raw("-1300")),
        Vector(Field("from"))
      ),
      Invalid.Missing.here(Field("rationale")),
      Invalid.Undeclared.here(Field("staus"), Vector(Field("status"), Field("tags"))),
      Invalid.Wrapped(
        TypeName("EventGeometry.Geojson"),
        Raw("Feature"),
        Field("geometry"),
        Vector(Field("geometry"))
      ),
      Invalid.StoredOn(
        TypeName("Edge"),
        Raw("'kristiansen2005' --refutes--> 'gimbutas1956'"),
        Raw("kristiansen2005"),
        Vector(Field("edges"), Field("add"))
      ),
      Invalid.Ambiguous(
        TypeName("Article.Edits.Anchor"),
        Raw("the Hittite texts"),
        Count(2L),
        Vector(Field("edits"), Field("0"))
      ),
      Invalid.Repeated(
        TypeName("Agents.ModelRef"),
        Raw("claude:opus"),
        Vector(Field("chain"))
      ),
      Invalid.Misplaced.here(Field("status"), Field("metadata")),
      Invalid.OutOfRange
        .here(TypeName("Latitude"), Raw("91"), Invalid.Bounds(Some(Raw("-90")), Some(Raw("90")))),
      Invalid.Inconsistent.here(NonEmptyVector.of(Field("lat"), Field("lon")), Invalid.Exclusion.Together),
      Invalid.Inverted(Invalid.Year(-900L), Invalid.Year(-1000L), Vector(Field("goal 'g'"))),
      Invalid.OutOfTime(
        Invalid.Year(6000L),
        Invalid.Year(-5000L),
        Invalid.Year(3000L),
        Vector(Field("source"), Field("period_start"))
      ),
      Invalid.Unparsable(
        Io.Format.Json,
        Where("LPF"),
        Io.Damage.Unreadable(External.Reported("expected '\"' or '}', offset: 0x00000002")),
        Vector.empty
      ),
      Invalid.Reference(Invalid.Form.EnvName(Raw("1PASSWORD")), Vector(Field("plugins"), Field("api-key"))),
      Invalid.Broken(
        Invalid.Rule.CronField(
          Raw("61 * * * *"),
          Invalid.CronPart.Minute,
          Invalid.CronFlaw.NotNumber(Raw("61"), Count(0L), Count(59L))
        ),
        Vector(Field("cron"))
      ),
      NotFound.Entity(citekey, Raw("anthony2007")),
      NotFound.Copy(Raw("doi:10.1017/S0003598X00000001"), Vector.empty),
      NotFound.Listed(TypeName("MergeRepo.MergeIdOrPrefix"), Raw("0193f1"), NotFound.Listing.Merges),
      NotFound.Dangling(
        TypeName("Bundle.Kind.Source"),
        Raw("0193f1c4-7b2a-7c3e-9d41-5e6f7a8b9c0d"),
        Where("finding anthony2007#1"),
        NotFound.Scope.BundleOrHere
      ),
      Conflict.AlreadyExists(TypeName("Place.Id"), Raw("pleiades_579885")),
      Conflict.Renamed(citekey, Raw("anthony2006"), Raw("anthony2007")),
      Conflict.Aliased(TypeName("Place.Id"), Raw("pleiades_579885"), Raw("pleiades_579886")),
      Conflict.OldKey(citekey, Raw("anthony2006"), Raw("anthony2007"), Conflict.Action.Delete),
      Conflict.Collides(TypeName("Finding.Id"), Raw("anthony2007#1"), Raw("anthony2007")),
      Conflict.Claimed(TypeName("Place.Id"), Raw("local_zzn_arkona"), Conflict.Clash.OldKeyOf(Raw("local_arkona"))),
      Conflict.Stale(
        citekey,
        Raw("anthony2007"),
        Conflict.Stamp("2026-11-01T10:00:00Z"),
        Conflict.Stamp("2026-11-02T08:30:00Z")
      ),
      Conflict.WrongState(
        TypeName("Job.Id"),
        Raw("412"),
        Conflict.State("running"),
        Vector(Conflict.State("paused"), Conflict.State("failed"))
      ),
      Conflict.Busy(Conflict.Activity.Backup),
      Conflict.Referenced(
        TypeName("Place.Id"),
        Raw("pleiades_579885"),
        TypeName("Finding"),
        Count(3L)
      ),
      Conflict.Held(Where("source anthony2007"), Conflict.Hold.SameWork(Raw("anthony2006"), Raw("doi"))),
      Conflict.Job(Raw("0193f1c4"), Conflict.State("queued"), Conflict.Control.NotStarted),
      Refused.Policy(Refused.Rule.RunTokenRequired),
      Refused.Quota(Refused.Limit.DailySpend, Refused.Amount(5.25), Refused.Amount(4.5), Some(EpochMs(1762732800000L))),
      Refused.Disabled(Refused.Feature("events-gvp"), SettingKey("histmersion.events.gvp.enabled"), None, None),
      Upstream.Http.here(core, url, Upstream.Status(429), External.ReplyExcerpt("{\"message\":\"rate limited\"}")),
      Upstream.Challenged.here(
        Upstream.Name("archive-org"),
        Upstream.Url("https://archive.org/download/x/x.pdf"),
        Upstream.Status(403)
      ),
      Upstream.Limited(Upstream.Name("claude"), Some(EpochMs(1762732800000L))),
      Upstream.Unasked(Relayed(Sentence("CORE asked to wait until 2025-11-10T00:00:00Z"))),
      Upstream.Unreachable.here(core, url, reported),
      Upstream.Timeout.here(core, url, Millis(30000L)),
      Upstream.BadReply
        .here(core, url, Upstream.Shape.Json, External.Reported("expected json value got '<html>' (line 1)")),
      Io.File(
        Io.Access.Read,
        Io.Path("/tmp/bundle.hmx"),
        External.Reported("java.nio.file.NoSuchFileException: /tmp/bundle.hmx")
      ),
      Io.Wrong(Io.Path("/srv/seed"), Io.Kind.Directory),
      Io.Corrupt(Io.Format.Png, Where("terrarium tile 3/4/2"), Io.Damage.Length(Count(1024L), Count(1000L))),
      Io.Process(
        Io.Tool("calibredb"),
        Io.Ending.Exited(Io.ExitCode(2), External.Stderr("calibredb: library is locked"))
      ),
      Io.Database(
        Io.DbOp.Migrate,
        External.Reported("org.postgresql.util.PSQLException: relation \"jobs\" does not exist")
      ),
      Io.Entry(
        Io.Format.Bundle,
        Io.Path("entities/sources.ndjson"),
        Io.Fault.Differs(Io.Measure.Lines, Raw("4"), Raw("3"))
      ),
      Config.Setting(SettingKey("histmersion.agents.providers.codex.command"), Config.Fault.Empty),
      Config.Unavailable(Config.Capability.Originals),
      Config.Reload(Config.Stage.Close, Config.Scope("plugins")),
      Config.Cycle(NonEmptyVector.of(Config.Scope("db"), Config.Scope("lease"), Config.Scope("db"))),
      Config.Unpriced(Run.Model("claude-opus-5-5"), Some(Config.Metered.ApiKey(Upstream.Name("claude-code")))),
      Plugin.Load(Plugin.Id("media-met"), Plugin.Fault.NullComponent),
      Plugin.Rejected(
        Plugin.Id("media-met"),
        Raw("248284"),
        Plugin.Refusal.NotOpen(Field("isPublicDomain"), Vector(Raw("false")))
      ),
      Plugin.Vault(Plugin.Id("keepassxc"), Plugin.Retrieval.TimedOut(Millis(20000L))),
      Plugin.Dependency(
        Plugin.Id("doi-mirror-0.1.0.jar"),
        Raw("com.github.plokhotnyuk.jsoniter-scala:jsoniter-scala-core_3"),
        Raw("2.42.0"),
        Raw("2.41.5")
      ),
      Plugin.Said(
        Plugin.Id("gazetteer-awmc"),
        Plugin.Claim.Unavailable,
        External.PluginSaid("the AWMC server is in maintenance")
      ),
      Run.Paused(Run.PauseReason.Allowance, Some(EpochMs(1762732800000L))),
      Run.NoModel(Run.Role("reviewer"), Vector(Run.Model("claude-opus-5-5"), Run.Model("gpt-6"))),
      Run.Blocked(Run.Block.PoolFull(Run.Pool("review"))),
      Run.Interrupted,
      Run.CutShort(Conflict.Activity.MergeApply),
      Run.Unrecorded(Conflict.Activity.MergeApply),
      Run.Cancelled(Run.Actor.Person),
      Run.Draining(EpochMs(1762732800000L)),
      Run.Aborted(Run.Step("draft"), Run.AbortCause.CheckpointMismatch),
      Content.Finding(Content.Flaw.UncitedParagraph),
      Content.Proposal(Content.Requirement.TooManyClaims(Count(12L), Count(15L)), Where("claims")),
      Content.Answer(Content.Requirement.SummaryGiven),
      Internal.Unexpected(
        Internal.Id("3f2a9c1e"),
        Where("backup"),
        Internal.ExceptionName("java.lang.IllegalStateException"),
        External.Reported("java.lang.IllegalStateException: pg_dump vanished")
      ),
      Unclassified(Legacy.Text("cannot open the bundle")),
      Stored.Unrecognised(Stored.Text("merge apply crashed: NullPointerException")),
      Relayed(Sentence("the server is reindexing"))
    )

  // ---- Problems: one or many ----------------------------------------------------------------------------------

  /** Problems compare by their cases, one with one and many with many (a one-element list is not the problem). */
  given CanEqual[Problems, Problems] = CanEqual.canEqualAny

  /** The test that tells one problem from many: a problem is never a vector. */
  private val one: TypeTest[Matchable, Problem] = summon[TypeTest[Matchable, Problem]]

  extension (problems: Problems)
    /** One problem or many, each to its function. Never match on the union itself. */
    def fold[B](ifOne: Problem => B)(ifMany: NonEmptyVector[Problem] => B): B =
      NonEmptyVector.split(problems)(using one).fold(ifOne, ifMany)

    /** Every problem, in order: what is rendered and serialised. */
    def toVector: Vector[Problem] = fold(Vector(_))(NonEmptyVector.toVector(_))

    /** Every problem, as a non-empty vector. */
    def toNonEmpty: NonEmptyVector[Problem] = fold(NonEmptyVector.of(_))(identity)

    /** These problems, then `more`. */
    def ++(more: Problems): Problems = toNonEmpty.concat(more.toVector)

    /** These problems, found in `field` of the request: every input's path gets `field` in front. */
    def at(field: Field): Problems = fold(_.within(field))(_.map(_.within(field)))

    /** Does any of them satisfy `p`? */
    def exists(p: Problem => Boolean): Boolean = toVector.exists(p)

  extension [A](result: Either[Problems, A])
    /** Both results, or the problems of both: `that` is evaluated whatever this is, so every failure is reported. */
    def and[B](that: Either[Problems, B]): Either[Problems, (A, B)] = (result, that) match
      case (Right(a), Right(b)) => Right((a, b))
      case (Left(p), Right(_))  => Left(p)
      case (Right(_), Left(q))  => Left(q)
      case (Left(p), Left(q))   => Left(p ++ q)

  extension [A, B](checks: (Either[Problems, A], Either[Problems, B]))
    def allOrProblems: Either[Problems, (A, B)] = checks._1.and(checks._2)

  extension [A, B, C](checks: (Either[Problems, A], Either[Problems, B], Either[Problems, C]))
    def allOrProblems: Either[Problems, (A, B, C)] =
      (checks._1, checks._2).allOrProblems.and(checks._3).map { case ((a, b), c) => (a, b, c) }

  extension [A, B, C, D](checks: (Either[Problems, A], Either[Problems, B], Either[Problems, C], Either[Problems, D]))
    def allOrProblems: Either[Problems, (A, B, C, D)] =
      (checks._1, checks._2, checks._3).allOrProblems.and(checks._4).map { case ((a, b, c), d) => (a, b, c, d) }

  extension [A, B, C, D, E](
      checks: (Either[Problems, A], Either[Problems, B], Either[Problems, C], Either[Problems, D], Either[Problems, E])
  )
    def allOrProblems: Either[Problems, (A, B, C, D, E)] =
      (checks._1, checks._2, checks._3, checks._4).allOrProblems.and(checks._5).map { case ((a, b, c, d), e) =>
        (a, b, c, d, e)
      }

  extension [A, B, C, D, E, F](
      checks: (
          Either[Problems, A],
          Either[Problems, B],
          Either[Problems, C],
          Either[Problems, D],
          Either[Problems, E],
          Either[Problems, F]
      )
  )
    def allOrProblems: Either[Problems, (A, B, C, D, E, F)] =
      (checks._1, checks._2, checks._3, checks._4, checks._5).allOrProblems.and(checks._6).map {
        case ((a, b, c, d, e), f) => (a, b, c, d, e, f)
      }

  extension [A, B, C, D, E, F, G](
      checks: (
          Either[Problems, A],
          Either[Problems, B],
          Either[Problems, C],
          Either[Problems, D],
          Either[Problems, E],
          Either[Problems, F],
          Either[Problems, G]
      )
  )
    def allOrProblems: Either[Problems, (A, B, C, D, E, F, G)] =
      (checks._1, checks._2, checks._3, checks._4, checks._5, checks._6).allOrProblems.and(checks._7).map {
        case ((a, b, c, d, e, f), g) => (a, b, c, d, e, f, g)
      }

  extension [A, B, C, D, E, F, G, H](
      checks: (
          Either[Problems, A],
          Either[Problems, B],
          Either[Problems, C],
          Either[Problems, D],
          Either[Problems, E],
          Either[Problems, F],
          Either[Problems, G],
          Either[Problems, H]
      )
  )
    def allOrProblems: Either[Problems, (A, B, C, D, E, F, G, H)] =
      (checks._1, checks._2, checks._3, checks._4, checks._5, checks._6, checks._7).allOrProblems.and(checks._8).map {
        case ((a, b, c, d, e, f, g), h) => (a, b, c, d, e, f, g, h)
      }
