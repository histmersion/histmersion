package com.histmersion
package util.problem
import com.histmersion.util.nonempty.NonEmptyVector

import scala.reflect.TypeTest

import hearth.typename.TypeName as Named

import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{tolerated, LintRule}

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
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
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

    def of(value: scala.reflect.Enum): Raw = Raw(value.toString)

  /** The wire name of a request or settings field (`lat`, `edges`, `agents.budgets.daily-usd`). */
  type Field = Field.T
  object Field extends Newtype[String, Pure]:
    /** The field a value of any newtype names (`Field.of(file)`, an entry of a list read field by field): unwrapped
      * here, like [[Raw.of]].
      */
    def of[T](value: T)(using RefinedType.Mirror[T]): Field = Field(s"$value")

  /** Where in the code or the work a failure happened: the operation's own name (`backup`, `merge apply`). */
  type Where = Where.T
  object Where extends Newtype[String, Pure]:
    /** The step a value of any newtype names, as it is written (`Where.of(answer.action)`): unwrapped here, like
      * [[Raw.of]].
      */
    def of[T](value: T)(using RefinedType.Mirror[T]): Where = Where(s"$value")

    extension (at: Where)
      /** The same place, found inside `outer` (a part of a file, inside that file): `feature #3 in seed/regions.json`.
        */
      def in(outer: Where): Where = Where(s"$at in $outer")

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
  object Sentence extends Newtype[String, Pure]:
    extension (sentence: Sentence)
      @tolerated(LintRule.G60Unrecognised, "pending G60 (only the stored column's reader builds Unrecognised)")
      def kept: Problem = Stored.Unrecognised(Stored.Text(sentence.value))

  /** A setting's key: `histmersion.agents.budgets.daily-usd`. */
  type SettingKey = SettingKey.T
  object SettingKey extends Newtype[String, Pure]:
    /** The key a value of any newtype over text names (`SettingKey.of(parameter.key)`): unwrapped here, like
      * [[Raw.of]].
      */
    def of[T](value: T)(using RefinedType.Mirror[T] { type BaseType = String }): SettingKey = SettingKey(s"$value")

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

      case LogPage(stated: Raw)

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

      /** A point set by hand moves only a place known by a point: `place` (`label`) has a shape. */
      case PointOnly(place: Raw, label: Raw)

      /** `name` is a name the gazetteer seed or an imported source gives `place` (`label`), not one a person added:
        * removing it would last only until the next import.
        */
      case NameByHand(name: Raw, place: Raw, label: Raw)

      /** `name` is no focus region `basemap.terrain.focus-regions` names (`known` are): its `--bbox` is given. */
      case FocusRegion(name: Raw, known: Vector[Raw])

      /** A focus region holds the zooms deeper than the extract's, `extract`: more than that is asked for. */
      case FocusDeeper(extract: Count)

      /** The physical names are not read with range requests: `labels` fetches them. */
      case LabelsUnranged

      /** The sea floor is not read with range requests: `bathymetry` fetches it. */
      case SeaFloorUnranged

      /** The model reference `model` was handed to the client of `provider`, which runs only its own models. */
      case ProviderOf(model: Raw, provider: Raw)

      /** An opencode model `model` is not `<provider>/<model>`. */
      case OpencodeModelShape(model: Raw)

      /** `provider` is Histmersion's own: the host runs it, the plugin SPI's turn is never called for it. */
      case HostProvider(provider: Raw)

      /** `option` is a run option of the pipeline `owner`, not of `pipeline`, the one asked for. */
      case OptionOf(option: Raw, owner: Raw, pipeline: Raw)

      /** `target` is a new article: compiling it needs a filter (query, topics or tags). */
      case NewArticleFilter(target: Raw)

      /** `target` is a new article: compiling it needs its title. */
      case NewArticleTitle(target: Raw)

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
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
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
    final case class Answer(fetcher: Fetcher, said: Said, why: Problem)

    /** A fetcher's id (`unpaywall`, `landing`). */
    type Fetcher = Fetcher.T
    object Fetcher extends Newtype[String, Pure]

    enum Said derives CanEqual:
      case Miss(absence: Absence)
      case Refused(refusal: Refusal)

    enum Absence derives CanEqual:
      case NoIdentifier, NotHeld, Unanswered, RateLimited, Unconfigured

    enum Refusal derives CanEqual:
      case BotChallenge, LendingOnly, NoEntitlement, LicenceForbids

    /** No value of `typeName` has the key `key`; `listing` is the command that lists the keys there are. */
    final case class Listed(typeName: TypeName, key: Raw, listing: Listing) extends NotFound

    /** A command that lists the values of a kind, named where one is not found. */
    enum Listing:
      /** `histmersion merge list`: the merges, by id. */
      case Merges

    /** The place `place` (`label`) has no name `name` in `lang` (in any language, when empty); `has` are the languages
      * it has that name in by a person's hand (an empty one: no language), which the caller may have meant.
      */
    final case class Name(place: Raw, label: Raw, name: Raw, lang: Raw, has: Vector[Raw]) extends NotFound

    /** The base map's `layer` is not downloaded: `fetch` downloads it. */
    final case class Undownloaded(layer: Raw, fetch: Raw) extends NotFound

    /** The place `place` (`label`) has no kind, period or point a person set by hand. */
    final case class SetByHand(place: Raw, label: Raw) extends NotFound

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

    /** Another server, `holder` (its host, process and connection time as the database names its session), holds the
      * database: one server per database.
      */
    final case class ServerHeld(holder: Raw) extends Conflict

    /** A restore did not start: `busy` was still at work after `waited` seconds, and nothing was changed. */
    final case class Undrained(busy: NonEmptyVector[Raw], waited: Count) extends Conflict

    /** `count` values of the type `by` refer to it (one problem per type of referrer). */
    final case class Referenced(typeName: TypeName, key: Raw, by: TypeName, count: Count) extends Conflict

    /** The place `place` (`label`) is not removed: `refs` refer to it. Merging it into the place it is the same as
      * moves them; so does moving them first.
      */
    final case class Referred(place: Raw, label: Raw, refs: NonEmptyVector[Reference]) extends Conflict

    /** `count` records of the kind `by` refer to a place. */
    final case class Reference(count: Count, by: Referrer)

    /** What refers to a place, keeping it from being removed. */
    enum Referrer derives CanEqual:
      case Events, Attestations, AssetLinks, ChildPlaces, ManualNames

      /** Findings it is the only place of. */
      case OnlyPlaceOf

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
      case Restore, MergeApply, CalibreGui, TileRequests

      /** A Codex run, which uses the runs' Codex home. */
      case CodexRun

  /** The operation is not allowed, though it is valid and the state allows it. */
  sealed trait Refused extends Problem

  object Refused:

    /** A rule of the service forbids it. */
    final case class Policy(rule: Rule) extends Refused

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

    /** `work` needs `needs` bytes on `on`, where `free` are free: it would leave less than `least`
      * (`basemap.min-free-gb`).
      */
    final case class Space(work: Work, needs: Count, on: Io.Path, free: Count, least: Count) extends Refused

    /** The request came from a network that is not trusted and carried no valid `credential`. */
    final case class Unauthenticated(credential: Credential) extends Refused

    /** The request named `host` in its `Host` header, which is none of the names this server answers to: a page whose
      * name was pointed at this server (DNS rebinding) must not reach it.
      */
    final case class Misdirected(host: Raw) extends Refused

    /** What a request must carry to be let in from a network that is not trusted. */
    enum Credential derives CanEqual:
      /** The API key, as `Authorization: Bearer <key>`. */
      case ApiKey

      /** A live run token of an agent run. */
      case RunToken

    /** What a refusal for want of disk space was for, each with its own way out. */
    enum Work derives CanEqual:
      /** The base map's layer `title`: a smaller region, fewer zooms or another directory. */
      case Layer(title: Raw)

      /** The ETOPO1 sea floor. */
      case SeaFloor

      /** The GEBCO sea floor: a smaller region, or the ETOPO1 sea floor. */
      case Gebco

      /** The physical names. */
      case Labels

    /** The rules a request can break. */
    enum Rule:
      /** The query holds several statements; it runs exactly one. */
      case OneStatement

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

      /** The setting decides what agents may do: only a person changes it, never an agent's tool call. */
      case PersonHeldSetting

      /** The run token is another agent's than the one whose tools were asked for. */
      case TokenOtherAgent

      /** A provider that spends the person's own plan is refused while the server listens beyond loopback: another
        * client could spend it.
        */
      case PlanOutsideLocal

      /** claude-code is refused while the server listens beyond loopback: another client could spend the Claude plan.
        */
      case ClaudeCodeOutsideLocal

      /** codex is refused while the server listens beyond loopback: another client could spend the ChatGPT plan. */
      case CodexOutsideLocal

      /** opencode on a ChatGPT login is refused while the server listens beyond loopback. */
      case OpencodeChatgptOutsideLocal

      /** The ChatGPT plan does not allow ordinary usage now (`ordinaryUsageAllowed: false`). */
      case OrdinaryUsageDenied

      /** A change sent by a page of another site (its `Origin`, or its `Referer`, is none of this server's own). */
      case CrossSite

      /** The request came from a network the settings list neither as trusted, as authenticated nor as refused. */
      case NetworkNotAccepted

      /** The request came from a network the settings list as refused (`auth.refused-networks`). */
      case NetworkRefused

      /** The request says a proxy forwarded it (`Forwarded`, `X-Forwarded-For`, `X-Forwarded-Host`,
        * `X-Forwarded-Proto`, `X-Real-IP` or `Via`), and it comes from no network the settings declare a proxy in
        * (`auth.proxy-networks`; empty, the default, declares none).
        */
      case UndeclaredProxy

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

    /** `upstream` could not be reached a moment ago: it is not asked again before `until`, when that is known. */
    final case class Offline(upstream: Name, until: Option[EpochMs]) extends Upstream

    /** `upstream`'s file changed under a read (another ETag or size). */
    final case class Changed(upstream: Name) extends Upstream

    /** The file at `url` changed during a download (its size or ETag differ): the fetch is run again for the new build.
      */
    final case class Rebuilt(url: Url) extends Upstream

    /** The file at `url` is `size` bytes, but the provider lists `listed` for the build `build`. */
    final case class Mislisted(url: Url, size: Count, listed: Count, build: Raw) extends Upstream

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

    final case class Unanswered(upstream: Name, url: Url, reported: External.Reported) extends Upstream

    /** `upstream` answered at `url`, but not with `expected`. */
    final case class BadReply(
        upstream: Name,
        url: Url,
        expected: Shape,
        reported: External.Reported,
        at: Vector[Field]
    ) extends Upstream

    /** A call to the model `upstream` failed: `why`, as its provider described it. */
    final case class CallFailed(upstream: Name, why: Problem) extends Upstream

    /** A call to the model `upstream` threw: `reported`, the exception's class and message. */
    final case class CallThrew(upstream: Name, reported: External.Reported) extends Upstream

    /** `upstream` (a command-line runtime, or the request it answered) reported a failure in its own words: `said`. */
    final case class Replied(upstream: Name, said: External.Reported) extends Upstream

    /** The agent MCP server a command-line runtime started for a step is not connected: `status`, as it reported it. */
    final case class McpServer(status: Raw) extends Upstream

    object BadReply:
      /** The problem of the request as a whole: no path into it. */
      def here(upstream: Name, url: Url, expected: Shape, reported: External.Reported): BadReply =
        BadReply(upstream, url, expected, reported, Vector.empty)

    /** The service, by the name its settings and plugin use (`openalex`, `core`, the Histmersion server itself). */
    type Name = Name.T
    object Name extends Newtype[String, Pure]:
      /** The service a value of any newtype names (`Name.of(model.wire)`): unwrapped here, like [[Raw.of]]. */
      def of[T](value: T)(using RefinedType.Mirror[T]): Name = Name(s"$value")

    /** An address of the service. */
    type Url = Url.T
    object Url extends Newtype[String, Pure]:
      /** The address a value of any newtype holds (`Url.of(source.url)`): unwrapped here, like [[Raw.of]]. */
      def of[T](value: T)(using RefinedType.Mirror[T]): Url = Url(s"$value")

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

        def wantsPayment: Boolean = s == PaymentRequired

        def asksToWait: Boolean = s == TooManyRequests

        def isServerError: Boolean = s.value >= 500 && s.value < 600

      val PaymentRequired: Status = Status(402)
      val TooManyRequests: Status = Status(429)

    /** What a reply was expected to be. */
    enum Shape:
      case Json, Document, Range

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

    /** No `held` is at `path`. `setting`: the setting that moves it, when the problem names one. */
    final case class Absent(held: Held, path: Path, setting: Option[SettingKey]) extends Io

    /** The content stored at `path` hashes to `found`, not to `expected`, the hash it is stored under. */
    final case class Hashed(path: Path, expected: Raw, found: Raw) extends Io

    /** What a path should hold. */
    enum Held derives CanEqual:
      /** The bundled gazetteer seed (a directory of Linked Places files). */
      case GazetteerSeed

      /** The manifest of the gazetteer's pinned extracts. */
      case GazetteerManifest

      /** World Historical Gazetteer downloads (Linked Places files), in the drop folder. */
      case LpfFiles

      /** The JSON files of the gazetteer seed. */
      case SeedFiles

      /** The Codex home the agent runs use. */
      case CodexHome

    /** The gazetteer's pinned extract `extract` cannot be read: `pinning`. */
    final case class Extract(extract: Raw, pinning: Pinning) extends Io

    /** Why a pinned extract cannot be read, and what to do about it. */
    enum Pinning derives CanEqual:
      /** The manifest `manifest` pins no extract of that name. */
      case Unlisted(manifest: Path)

      /** The manifest `manifest` names no built file or sha256 for it: it is built and pinned first. */
      case NotBuilt(manifest: Path)

      /** The file at `path` has sha256 `found`, not `pinned`, the one version `version` is pinned at: refused. */
      case Mismatch(version: Raw, path: Path, found: Raw, pinned: Raw)

      /** Version `version`'s `file` is not at `path`, and its pin has no download url (the extracts are not hosted): it
        * is put there, `bytes` bytes with sha256 `sha256`. The folder is `setting` (the environment variable
        * `variable`).
        */
      case Unhosted(
          version: Raw,
          file: Raw,
          path: Path,
          sha256: Raw,
          bytes: Count,
          setting: SettingKey,
          variable: SettingKey
      )

    /** `path`, a document read whole, is `megabytes` MB, over the limit `setting` sets: one record per line, it
      * streams.
      */
    final case class Oversize(path: Path, megabytes: Count, setting: SettingKey) extends Io

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
    object Path extends Newtype[String, Pure]:
      /** The path a value of any newtype holds (`Path.of(store.where(key))`): unwrapped here, like [[Raw.of]]. */
      def of[T](value: T)(using RefinedType.Mirror[T]): Path = Path(s"$value")

    /** A program run as a process, by its command name (`calibredb`, `pg_dump`). */
    type Tool = Tool.T
    object Tool extends Newtype[String, Pure]

    /** A process's exit status. */
    type ExitCode = ExitCode.T
    object ExitCode extends Newtype[Int, Pure]:
      /** An exit status written in decimal digits. */
      def read(digits: String): Option[ExitCode] = digits.toIntOption.map(ExitCode(_))

    enum Access:
      case Read, Write, Create, Delete, Move

    /** What a path should be. `Listed`: named by the manifest it is checked against. `OnPath`: found on the `PATH`. */
    enum Kind:
      case File, Directory, Present, Executable, Listed, OnPath

      /** On disk: a file another program can be handed (an in-memory store has none). */
      case OnDisk

      /** Inside `directory`, the one the operation is confined to. */
      case Inside(directory: Path)

    enum Format:
      case Bundle, Tar, Json, Png, PmTiles, Pdf, Image, Text, Hocon, Yaml, Xml, Kit, Zip, GeoJson, WebP, Gzip, Mvt,
        GeoTiff

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

      /** Its layout breaks its format: `flaw`. */
      case Structure(flaw: Flaw)

      /** It is not data of its format at all: its magic number is not there. */
      case Foreign

      /** It holds `found` bytes where `expected` belong. */
      case Bytes(expected: Count, found: Count)

      /** Its compressed data does not inflate: `reported` is what the inflater said. */
      case Inflate(reported: External.Reported)

      /** It is a kind of its format this version does not read: `variant`. */
      case Unsupported(variant: Variant)

      /** This damage, to the data in `format` at `at`. */
      def in(format: Format, at: Where): Corrupt = Corrupt(format, at, this)

    /** How data breaks the layout of its format. */
    enum Flaw derives CanEqual:
      /** A GeoJSON document that is no FeatureCollection. */
      case NotFeatureCollection

      /** Natural Earth's land download holds no polygon. */
      case NoLandPolygons

      /** A JSON document that is no array, where a list belongs. */
      case NotArray

      /** A provider's list of builds that lists none. */
      case NoBuild

      /** A PNG with no image header (IHDR) before its data. */
      case NoImageHeader

      /** A header holds a negative `quantity` at byte `byte`. */
      case NegativeAt(quantity: Quantity, byte: Count)

      /** A directory says it holds `size` entries, more than any can. */
      case DirectorySize(size: Count)

      /** The directory's entry `entry` has a negative offset. */
      case NegativeEntry(entry: Count)

      /** An entry's offset `offset` lies outside its file. */
      case OffsetOutside(offset: Count)

      /** An entry's length `length` is negative. */
      case NegativeLength(length: Count)

      /** Its directories nest deeper than the format allows. */
      case TooDeep

      /** Its root directory lies outside the first 16 KiB, where a reader looks for it. */
      case RootOutside

      /** Its metadata is over 16 MiB. */
      case MetadataOver

      /** A prefix code longer than 15 bits. */
      case PrefixTooLong

      /** An incomplete or over-subscribed prefix code. */
      case PrefixIncomplete

      /** A prefix code that matches no symbol. */
      case PrefixInvalid

      /** A chunk that runs past the end of the file. */
      case ChunkTruncated

      /** A stream that ends before its image does. */
      case StreamTruncated

      /** A stream without the VP8L signature. */
      case NotVp8l

      /** A transform used twice. */
      case TransformTwice

      /** A colour cache of a size outside 1 to 11 bits. */
      case CacheSize

      /** A symbol outside its alphabet. */
      case SymbolOutside

      /** More code lengths than symbols. */
      case CodeLengthsOver

      /** Code lengths that run past the alphabet. */
      case CodeLengthsPast

      /** A backward reference outside the image. */
      case ReferenceOutside

      /** A colour-cache code where no cache is. */
      case CacheWithout

      /** A colour-cache index outside the cache. */
      case CacheIndexOutside

      /** A range request answered `got` bytes for a range of `asked`. */
      case RangeBytes(got: Count, asked: Count)

      /** A TIFF that is not little-endian (or no TIFF). */
      case NotLittleEndian

      /** An image file directory without the tag `tag`. */
      case NoTag(tag: Count)

      /** A TIFF without strip offsets. */
      case NoStripOffsets

      /** A TIFF without a pixel scale: no GeoTIFF. */
      case NoPixelScale

      /** A TIFF without a tie point: no GeoTIFF. */
      case NoTiePoint

      /** A TIFF whose strips hold no row `row`. */
      case NoStripForRow(row: Count)

      /** This flaw of the layout, in the data in `format` at `at`. */
      def in(format: Format, at: Where): Corrupt = Damage.Structure(this).in(format, at)

    /** What a header holds a value of. */
    enum Quantity derives CanEqual:
      case Offset, Length

    /** A kind of a format a reader does not read, and what it reads instead. */
    enum Variant derives CanEqual:
      /** A PNG of colour type `colour`, bit depth `depth` and interlace method `interlace`: only 8-bit RGB(A)
        * non-interlaced ones are read.
        */
      case PngLayout(colour: Count, depth: Count, interlace: Count)

      /** A PNG row filtered with `filter`: the filters are 0 to 4. */
      case PngFilter(filter: Count)

      /** A lossy (VP8) WebP: only lossless WebP is read. */
      case LossyWebP

      /** A WebP with no VP8L chunk: only lossless WebP is read. */
      case NoLossless

      /** A VP8L stream of a version other than 0. */
      case Vp8lVersion

      /** Spec version `found`; this version reads `reads` only. */
      case SpecVersion(found: Count, reads: Count)

      /** `found` compression; this version reads `reads` only. */
      case Compression(found: Raw, reads: Raw)

      /** A BigTIFF: only classic TIFF is read. */
      case BigTiff

      /** Samples of `bits` bits: only 16-bit ones are read. */
      case SampleBits(bits: Count)

      /** TIFF compression `code`: only uncompressed grids are read. */
      case TiffCompression(code: Count)

      /** `count` bands: only one is read. */
      case Bands(count: Count)

      /** Planar configuration `configuration`: only 1 (chunky) is read. */
      case Planar(configuration: Count)

      /** Sample format `format`: only signed integers are read. */
      case SampleFormat(format: Count)

      /** A tiled TIFF: only strips are read. */
      case Tiled

      /** Data in `format` at `at` of this kind, which this version does not read. */
      def in(format: Format, at: Where): Corrupt = Damage.Unsupported(this).in(format, at)

    /** How a process ended that did not end well. */
    enum Ending:
      /** It exited with `code`; `stderr` is the end of what it wrote to its standard error. */
      case Exited(code: ExitCode, stderr: External.Stderr)

      /** It did not finish within `after`. */
      case TimedOut(after: Millis)

      /** It could not be started. */
      case NotStarted(reported: External.Reported)

      /** It ended without answering `request`. */
      case Unanswered(request: Raw)

    enum DbOp:
      case Connect, Query, Migrate

  /** The service's settings do not allow the operation. */
  sealed trait Config extends Problem

  object Config:

    /** `key` is set wrongly: `fault`. */
    final case class Setting(key: SettingKey, fault: Fault) extends Config

    /** This server has no `capability`. */
    final case class Unavailable(capability: Capability) extends Config

    /** The part `scope` of the running service cannot be built: `why`, each problem. */
    final case class Unbuilt(scope: Scope, why: NonEmptyVector[Problem]) extends Config

    /** A settings reload published nothing: `why`. The running parts stay as they were. */
    final case class Unchanged(why: Problem) extends Config

    /** The running part `component` (its name, generation and users, as the reload names it) was retired: `drain` says
      * how its closing went.
      */
    final case class Drained(component: Raw, drain: Drain) extends Config

    /** Claude Code is logged in with an API key (`method`), not with a Claude plan. */
    final case class ApiKeyLogin(method: Raw) extends Config

    /** A plan pool's floors do not parse (`from` says whose), so it floors nothing: `why`, each problem. */
    final case class FloorsUnread(from: FloorsFrom, why: NonEmptyVector[Problem]) extends Config

    /** Where a plan pool's floors come from. */
    enum FloorsFrom derives CanEqual:
      /** The floors saved for the pool. */
      case Saved

      /** The setting `key`. */
      case Setting(key: SettingKey)

    /** The settings `file`, read at `at` (the file itself, or the copy staged before it replaces the file), does not
      * load: `why`, each problem. They name the path read; shown, they name the settings file.
      */
    final case class ReadAt(file: Io.Path, at: Io.Path, why: NonEmptyVector[Problem]) extends Config

    /** How a retired part's closing went. */
    enum Drain derives CanEqual:
      /** It is still in use `seconds` after the reload: it closes when its last user lets go. */
      case InUse(seconds: Count)

      /** The server shuts down: it is closed while still in use. */
      case ClosedInUse

      /** Its scope did not close within `seconds`. */
      case Unclosed(seconds: Count)

    /** The parts a reload rebuilds depend on each other in a cycle: `scopes`, the first one again at the end. */
    final case class Cycle(scopes: NonEmptyVector[Scope]) extends Config

    /** `model` has no price in the agents' price table: an unpriced model is refused, never taken as free. `metered`:
      * why a model of a plan's provider is billed by the token here, when that is why it needs a price.
      */
    final case class Unpriced(model: Run.Model, metered: Option[Metered]) extends Config

    /** The agent definition, skill or shared fragment `file` (as the prompts name it: `agents/x.md`) is wrong: `flaw`.
      */
    final case class Definition(file: Raw, flaw: Flaw) extends Config

    /** What is wrong with an agent definition, a skill or a shared fragment. */
    enum Flaw derives CanEqual:
      /** It does not start with a YAML header (`--- … ---`). */
      case NoHeader

      /** Its YAML header is not closed. */
      case HeaderUnclosed

      /** Its YAML header is not a mapping. */
      case HeaderNotMapping

      /** Its YAML header does not parse: `reported`, the parser's words. */
      case HeaderUnparsable(reported: External.Reported)

      /** `field` holds `value`, not a list. */
      case NotList(field: Field, value: Raw)

      /** Its header names no agent. */
      case NoName

      /** Its header names `named`, not the file's own name. */
      case NameNotFile(named: Raw)

      /** Its header has no description. */
      case NoDescription

      /** Its `model-role` is none of `roles`. */
      case RoleUnknown(roles: Vector[Raw])

      /** Its role `role` is no contributed workflow's: one of `roles` is. */
      case RoleUnused(role: Raw, roles: Vector[Raw])

      /** `tools.histmersion` names `tools`, which an agent cannot be offered: one of `known` can. */
      case ToolsUnoffered(tools: Vector[Raw], known: Vector[Raw])

      /** Its `tools.web` is none of none, discover, search or fetch. */
      case WebUnknown

      /** Its `writes` is none of none, propose or direct. */
      case WritesUnknown

      /** Its `max-turns` is not a positive number. */
      case TurnsNotPositive

      /** It names runtimes no runtime is. */
      case RuntimesUnknown(runtimes: Vector[Raw])

      /** It runs interactively without `interactive: {model, tools}`. */
      case InteractiveUnset

      /** A skill's header names another skill than `name`, the skill's own. */
      case SkillNotNamed(name: Raw)

      /** It includes `include`, which is not among the shared fragments. */
      case IncludeMissing(include: Raw)

      /** It lists skill `skill`, which is not among the skills. */
      case SkillMissing(skill: Raw)

      /** An audience marker names `audience`, which is no audience. */
      case AudienceUnknown(audience: Raw)

      /** The marker for `audience` is never closed. */
      case MarkerUnclosed(audience: Raw)

      /** The marker for `audience` opens inside the one for `outer`. */
      case MarkerNested(audience: Raw, outer: Raw)

      /** The closing marker for `audience` closes nothing open. */
      case MarkerUnopened(audience: Raw)

      /** It names `{{tool:tool}}`, which is no MCP tool. */
      case ToolUnknown(tool: Raw)

      /** A skill's description is over 1,024 characters, which opencode refuses. */
      case DescriptionLong

    /** The server would listen on `bind`, which `reach` can connect to, and nothing would stop a stranger there:
      * `exposure` says why.
      */
    final case class Exposed(bind: Raw, reach: Raw, exposure: Exposure) extends Config

    /** Why an address the server listens on would be open to whoever reaches it. */
    enum Exposure derives CanEqual:
      /** No setting lists the network: neither as trusted, as authenticated nor as refused. */
      case Unlisted

      /** The network is listed as authenticated, but there is no API key to authenticate with. */
      case NoKey

    /** The public URL `publicUrl` is HTTPS, which this server does not serve itself, so a reverse proxy forwards to it,
      * and loopback, where a proxy on this machine connects from, is trusted while no proxy network
      * (`auth.proxy-networks`) covers it: a proxy that does not mark what it forwards would hand every client the
      * owner's trust.
      */
    final case class ProxyUndeclared(publicUrl: Raw) extends Config

    /** `server.host` lists the wildcard `wildcard` (`0.0.0.0` or `::`; the JDK binds either as the wildcard of both
      * families) and `address` besides, on the one port: the wildcard already listens on every address, so the second
      * listener fails to bind (Linux) or takes its address's connections from the first (macOS).
      */
    final case class WildcardOverlap(wildcard: Raw, address: Raw) extends Config

    /** `server.host` lists one address twice: as `address`, and again as `again` (the same text, or another spelling of
      * it — `::1` and `[::1]`, a name in another case). Each is kept as written.
      */
    final case class BindRepeated(address: Raw, again: Raw) extends Config

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

      /** Its value `raw` is no size Postgres reads: a whole number of kB, MB, GB or TB, `auto`, or empty. */
      case NotSize(raw: Raw)

      case NotPositiveSeconds(raw: Raw, variable: SettingKey)

      /** It sizes the database pool at `pool` connections, more than the bundled Postgres' `connections` (the setting
        * `limit`) leave room for: at most `most`. A reload holds the old pools beside the new ones until they are idle,
        * each application pool beside its read-only query pool, and `reserved` connections stay free for a backup, the
        * bundle's own administration and a person's session.
        */
      case PoolOverConnections(pool: Count, limit: SettingKey, connections: Count, most: Count, reserved: Count)

      /** It names `id`, which no enabled plugin registered: that entry is skipped. */
      case Unregistered(id: Raw)

      /** Its value `raw` is none of `known`: it is treated as `used`. */
      case Defaulted(raw: Raw, known: Vector[Raw], used: Raw)

      /** It names `id`, which is no plugin in this binary. */
      case NoPlugin(id: Raw)

      /** It contradicts `other`. */
      case Conflicts(other: SettingKey)

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
      case Originals, AcquisitionChain, Converter, Agents, Processes, LocalFiles, MediaImport, EventCatalogues,
        AiProviders, GazetteerImport, SourceCollections, Network, CitationAlerts

      /** A service that starts the server again when it stops: without one, the server cannot restart itself. */
      case Supervisor

      /** A settings file the server was started with: without one, Settings cannot change anything. */
      case SettingsFile

      /** The graph of the parts a reload rebuilds: closed once the server shuts down. */
      case HotGraph

      /** A Claude Code login: without one, the Claude plan cannot be used. */
      case ClaudeLogin

      /** A read of the ChatGPT plan through `codex app-server`: none where nothing is started. */
      case AppServerRead

      /** A `claude -p` probe of the Claude plan: none where nothing is started. */
      case PlanProbe

      /** A check of a plan's login: none where nothing is started. */
      case LoginCheck

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
      case Missing, NullComponent, BadDescriptor, Duplicate

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
      case NoLicence, NoImage, NoDate

      /** Its licence is of the family `family`, which the collection's allowlist does not hold. */
      case LicenceNotAllowed(family: Raw)

      /** The provider's rights field `field` does not mark it open: it states `stated` (nothing, when empty). */
      case NotOpen(field: Field, stated: Vector[Raw])

      /** None of its images is open: the images' rights field `field` states `stated` (nothing, when empty). */
      case NoOpenImage(field: Field, stated: Vector[Raw])

    /** How a vault failed. */
    enum Retrieval:
      case NoVault, NoEntry, Locked

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

    /** A run stopped for `cause` and waits for a person, an allowance or a clock: `why`, the problem it stopped on. A
      * job's row keeps it, so the row says what the run waits for.
      */
    final case class Halted(cause: PauseCause, why: Problem) extends Run

    /** A queued run was paused before it started. */
    case object Unstarted extends Run

    /** A queued upload's file was held by the process that a restart ended: its job cannot run. */
    case object UploadUnheld extends Run

    /** No model of `role`'s chain can run, for want of `shortfall`: each of its models, passed over and why. */
    final case class NoModel(role: Role, shortfall: Shortfall, passed: Vector[PassedOver]) extends Run

    enum Shortfall derives CanEqual:
      case Unusable
      case Exhausted(floor: Floor, frees: Frees)
      case Unassigned(asked: Asked)

    final case class Skipped(skip: Skip) extends Run

    enum Skip derives CanEqual:
      case Cooling(until: EpochMs)
      case BelowFloor(pool: Raw, reading: Raw)
      case BelowAllowance(limits: NonEmptyVector[Spent])
      case AllowanceUnread(broken: NonEmptyVector[Unread])
      case PlanOnly(role: Role)
      case AllowanceReached(model: Model)

    /** `model` of a chain is passed over: `why` (each reason it cannot run now). */
    final case class PassedOver(model: Model, why: NonEmptyVector[Problem]) extends Run

    /** Run `run` is `status`, in which `control` does not apply. */
    final case class NotNow(run: Raw, status: Raw, control: Control) extends Run

    /** Step `step` stopped, or an attempt of it was turned down: `failure`, how. */
    final case class AtStep(step: Step, failure: Failure) extends Run

    /** How a step stopped, or how an attempt of it was turned down. */
    enum Failure derives CanEqual:
      case Failed(why: NonEmptyVector[Problem])
      case Unsettled(attempts: Count)
      case ProviderDown(why: NonEmptyVector[Problem])
      case BudgetSpent
      case NoValidAnswer(attempts: Count, problems: Vector[Problem])
      case OutputCapped(model: Model, cap: Count, sent: Count, setting: Ceiling)
      case Declined(problems: Vector[Problem])

    /** Model `model` cannot be used now: `why`, each problem (its client does not build, its plan said no). */
    final case class AtModel(model: Model, why: NonEmptyVector[Problem]) extends Run

    /** An attempt of a step, or a run, failed: `why`, each problem (as the attempt's record keeps them). */
    final case class Failed(why: NonEmptyVector[Problem]) extends Run

    /** The model's plan reported its usage limit (`why`, as its runtime said it): the model is skipped until `until`.
      */
    final case class LimitReached(why: Problem, until: EpochMs) extends Run

    /** A restart cut it short. */
    case object Interrupted extends Run

    /** A restore replaced the database while it ran: what it would write next belongs to a database that is gone. */
    case object Restored extends Run

    /** A restart cut `activity` short. It commits or rolls back as a whole, never half applied: its own listing tells
      * whether it took effect.
      */
    final case class CutShort(activity: Conflict.Activity) extends Run

    /** `by` cancelled it. */
    final case class Cancelled(by: Actor) extends Run

    /** The autopilot drains (since `since`, when it is known); it resumes once the drain is lifted. */
    final case class Draining(since: Option[EpochMs]) extends Run

    final case class Stopped(stop: Stop) extends Run

    enum Stop derives CanEqual:
      case Stalled(step: Count, minutes: Count, times: Count)
      case Silent(step: Count, minutes: Count, stall: Count, of: Count)
      case NoPipeline(pipeline: Raw)
      case PipelineChanged
      case Reordered(seq: Count, recorded: Step, now: Step)
      case CheckpointUnread(seq: Count, step: Step, reported: External.Reported)
      case Vanished
      case Over(budget: Spending)
      case MovedOn(run: Raw)
      case Withdrawn
      case Guard(why: Raw)
      case Unrecorded(activity: Conflict.Activity)

    final case class Needs(need: Need) extends Run

    enum Need derives CanEqual:
      case Text(source: Raw)
      case Body(article: Raw)
      case Login(why: Raw)
      case Probe(exit: Io.ExitCode, said: Option[External.Reported])

    enum Spending derives CanEqual:
      case Usd(spent: Raw, budget: Raw)
      case Tokens(used: Count, budget: Count, weight: Raw)
      case Capped(cap: Cap, amount: Raw)

    enum PauseReason:
      case Requested

    /** What a run stopped for (the agents' pause reasons): the word a stopped run's sentence starts with. */
    enum PauseCause derives CanEqual:
      case Budget, Interrupted, Requested, Provider, Allowance, PlanQuota, Stalled

    /** An agent role, in its wire spelling. */
    type Role = Role.T
    object Role extends Newtype[String, Pure]:
      /** The role a value of any newtype spells (`Role.of(role.wire)`): unwrapped here, like [[Raw.of]]. */
      def of[T](value: T)(using RefinedType.Mirror[T]): Role = Role(s"$value")

    /** A model, by its id. */
    type Model = Model.T
    object Model extends Newtype[String, Pure]:
      /** The model a value of any newtype names (`Model.of(ref.wire)`): unwrapped here, like [[Raw.of]]. */
      def of[T](value: T)(using RefinedType.Mirror[T]): Model = Model(s"$value")

    /** A step of a run, by its name. */
    type Step = Step.T
    object Step extends Newtype[String, Pure]:
      /** The step a value of any newtype names (`Step.of(name)`): unwrapped here, like [[Raw.of]]. */
      def of[T](value: T)(using RefinedType.Mirror[T]): Step = Step(s"$value")

    enum Actor:
      case Person

    /** The agents' spend caps: a day's and a month's. */
    enum Cap derives CanEqual:
      case Daily, Monthly

    /** The setting that caps a step's answer. */
    enum Ceiling derives CanEqual:
      /** `agents.step.max-tokens`, every step's cap. */
      case StepMaxTokens

      /** The step's own cap: the fixer's or the writer's. */
      case StepOwn

    /** What a person does to a run. */
    enum Control derives CanEqual:
      case Pause, Resume, Cancel

    /** What a chain whose models are all out of headroom is below. */
    enum Floor derives CanEqual:
      /** A plan pool's floor, or an allowance. */
      case PlanOrAllowance

      /** An allowance. */
      case Allowance

    /** When a chain whose models are all out of headroom gets one back. */
    enum Frees derives CanEqual:
      /** `model`'s window resets first, at `at`: the run resumes by itself then. `held`: the pools a person holds. */
      case At(at: EpochMs, model: Model, held: Vector[HeldPool])

      /** No window frees by itself; the pools a person holds free once released. */
      case Released(held: NonEmptyVector[HeldPool])

      /** No window frees by itself: the allowance is raised or reset. */
      case Never

    /** A plan pool a person holds: its title, and its name to release it by. */
    final case class HeldPool(title: Raw, pool: Raw)

    /** A limit of an allowance without headroom: the limit as written, what is used and the reserve kept. */
    final case class Spent(limit: Raw, used: Raw, reserve: Raw)

    /** An allowance's `target` that cannot be read: `why`. */
    final case class Unread(target: Raw, why: NonEmptyVector[Problem])

    /** Where the question for a role's model came from. */
    enum Asked derives CanEqual:
      /** A call that needs a model now: the setting, its variable, the saved chain or a model of the run's own. */
      case Call

      /** The listing of the chains (`agent models`). */
      case Listing

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

    /** An article a plan proposed and the checks dropped: `problem`, why. */
    final case class NotProposed(problem: Problem) extends Content

    /** A model's answer is not the step's typed answer: `why`; `finish`, how the call ended when that says more;
      * `began`, the start of the answer.
      */
    final case class Undecoded(why: Decoding, finish: Option[External.Reported], began: Option[External.ModelSaid])
        extends Content

    /** Why an answer does not decode. */
    enum Decoding:
      /** It is JSON, but not the schema's at `path`: `reported`, the decoder's words. */
      case Mismatch(path: Raw, reported: External.Reported)

      /** It is not JSON: `reported`, the parser's words. */
      case NotJson(reported: External.Reported)

      /** The decoder failed otherwise: `reported`. */
      case Unreadable(reported: External.Reported)

      /** No answer the schema asks for came back. */
      case NotSchema

    /** A place a finding names (`finding`, counted from 1) that no gazetteer place matches. */
    final case class Unplaced(place: Raw, finding: Count)

    enum Flaw:
      case HandWrittenSourcesCited

    enum Requirement:
      case CitesNoSource

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

      /** A review panel's answer (a reviewer's or the fixer's) breaks `rule`. */
      case Panel(rule: PanelRule)

      /** The article `slug` is proposed twice. */
      case ProposedOnce(slug: Raw)

      /** An article with the proposed slug exists, or did. */
      case SlugFree

      /** The proposed filter `line` returns `total` sources, fewer than `least`. */
      case ArticleSources(line: Raw, total: Count, least: Count)

      /** `share` of the proposed article's sources are `other`'s already, not less than `most`. */
      case ArticleOverlap(share: Share, other: Raw, most: Share)

      /** No proposed article passed the checks. */
      case ArticlePassed

    /** What the review panel's answers are held to (ReviewArticle): the reviewers' findings, verdicts, spot-checks,
      * rulings and checks, and the fixer's rejections, set-asides and reasons. Its own vocabulary, apart from
      * [[Requirement]], so that each stays a size the codec derivation handles.
      */
    enum PanelRule:

      /** A reviewer that reads no sources (the cold reader) gives evidence records. */
      case RecordsCited

      /** An evidence record's quote (`quote`; its first 80 characters show) is not in the record of `citekey`. */
      case QuoteInSourceRecord(citekey: Raw, quote: Raw)

      /** A finding's severity (`stated`) is neither major nor minor. */
      case SeverityKnown(stated: Raw)

      /** A finding states no problem. */
      case ProblemStated

      /** A finding states no `where`: the section heading or the passage's first words. */
      case WhereStated

      /** An answer gives no verdict. */
      case VerdictGiven

      /** The stated verdict (`stated`) is not PASS, MINOR or MAJOR. */
      case VerdictKnown(stated: Raw)

      /** The stated verdict `verdict` is worse than any finding: a finding that makes it so is left out. */
      case VerdictShown(verdict: Raw)

      /** An entry names no citekey. */
      case CitekeyGiven

      /** A spot-check is of a source the article does not cite. */
      case SpotCited

      /** A spot-check's verdict (`stated`) is none of `known`. */
      case SpotVerdict(stated: Raw, known: Vector[Raw])

      /** A spot-check's claim is not in the article: it copies the article's words. */
      case ClaimInArticle

      /** A spot-check rests on no get_source or passages call of the step for its source. */
      case RecordRead

      /** Only `verified` of the `required` spot-checks are verified on different cited sources; the step made `calls`
        * get_source/passages calls.
        */
      case SpotChecksVerified(verified: Count, required: Count, calls: Count)

      /** An empty explain-back slot (`stated`) is none of `known`. */
      case SlotKnown(stated: Raw, known: Vector[Raw])

      /** The cold reader gives no explain-back. */
      case ExplainBackGiven

      /** A missing-critique MAJOR's quote is not in the article: it quotes the sentence that states the criticised
        * claim.
        */
      case CriticisedClaimQuoted

      /** The missing-critique MAJOR `finding` names no pair of the critique checklist (its `pair` is empty). */
      case PairNamed(finding: Raw)

      /** A ruling names no pair of the critique checklist. */
      case ChecklistPair

      /** A ruling names a pair that is already `state` (not open). */
      case PairOpen(state: Raw)

      /** A not-material ruling names a pair a missing-critique MAJOR names too: the finding stands. */
      case FindingStands

      /** A ruling gives no reason. */
      case ReasonStated

      /** A reason is `length` characters, more than `most`. */
      case ReasonWithin(length: Count, most: Count)

      /** Open pairs of the critique checklist (`unruled`) have no ruling. */
      case PairsRuled(unruled: Vector[Raw])

      /** A check gives no id: it is one of `asked`. */
      case CheckId(asked: Vector[Raw])

      /** A check's id is not one of the findings asked about (`asked`). */
      case AskedFinding(asked: Vector[Raw])

      /** A check's quote (`quote`; its first 80 characters show) is not in the current article. */
      case QuoteInArticle(quote: Raw)

      /** A check gives no quote of the current text that shows what it says (`shows`). */
      case CheckQuoted(shows: Shows)

      /** A check that a finding is not fixed gives no note. */
      case CheckNoted

      /** A finding asked about has no check. */
      case CheckGiven

      /** The fixer names `id`, which is no finding of the round. */
      case KnownFinding(id: Raw)

      /** A rejection names no finding of the round. */
      case FindingListed

      /** The fixer rejects `id`, which cannot be rejected (`why`). */
      case Rejectable(id: Raw, why: Unrejectable)

      /** The rejection of `id` gives no reason. */
      case RejectionReason(id: Raw)

      /** The rejection of `id` names `stated`, which is no citekey. */
      case RejectionCitekey(id: Raw, stated: Raw)

      /** A rejection's quote is not in the record of `citekey`. */
      case RejectionQuote(citekey: Raw)

      /** A won't-fix gives no id. */
      case WontFixId

      /** An entry names a finding there is none of. */
      case SuchFinding

      /** The fixer declines `id`, a MAJOR: only a minor finding can be declined. */
      case OnlyMinorDeclined(id: Raw)

      /** An entry gives no reason. */
      case GiveReason

      /** `id` is listed as applied, but its pair (`src`, `dst`) is not cited in one paragraph. */
      case PairAnswered(id: Raw, src: Raw, dst: Raw)

      /** The edits cite `cited`, whose recorded critiques (`pairs`) no paragraph discusses. */
      case CritiquesDiscussed(cited: Vector[Raw], pairs: Vector[Discussed])

      /** The edits reopen a closed finding (`line`, as the ledger shows it). */
      case ClosedKept(line: Raw)

      /** The major finding `id` is not decided; `decision` says how it may be. */
      case MajorDecided(id: Raw, decision: Decision)

      /** The fixer lists `applied` as applied, but no edit changed the article. */
      case EditMade(applied: Vector[Raw])

      /** A set-aside names `pair`, which is no pair of the article's critique checklist; `remedy` says what else to do.
        */
      case ChecklistEdge(pair: Raw, remedy: Remedy)

      /** A set-aside names neither a finding nor a pair. */
      case SetAsideNamed

      /** A set-aside names a finding that is not a missing critique. */
      case MissingCritiqueOnly

      /** A set-aside's finding names no pair, and the set-aside gives none. */
      case FindingPair

      /** The fixer gives coverage reasons for an article that records no filter. */
      case ReasonsUnfiltered

      /** An empty reason for a source that has no stored reason to remove. */
      case StoredReason

      /** A reason for a source the filter does not return. */
      case FilterReturns

      /** A reason for a source the article cites. */
      case LeftOut

      /** The unplaced-sources findings `findings` are listed as applied, but `left` are still neither cited nor given a
        * reason (the first twenty show).
        */
      case StillUnplaced(findings: Vector[Raw], left: Vector[Raw])

    /** What a check's quote shows: the finding fixed, or still there. */
    enum Shows:
      case Fixed, StillThere

    /** Why a finding cannot be rejected: a mechanical check is certain, a reader's confusion is a fact. */
    enum Unrejectable:
      case MechanicalCheck, ReadersConfusion

    /** How an undecided MAJOR may be decided: applied or rejected; or, a coverage finding on an article that records no
      * filter, declined (no edit records the filter).
      */
    enum Decision:
      case ApplyOrReject, DeclineUnfiltered

    /** What a set-aside that names no checklist pair may do instead: nothing more (a pair it names alone), or apply or
      * reject its finding.
      */
    enum Remedy:
      case Unstated, ApplyOrReject

    /** A recorded critique (`pair`, `src>dst`) of a work the edits cite, with its edge kinds. */
    final case class Discussed(pair: Raw, kinds: Vector[Raw])

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
  @tolerated(LintRule.ProblemField, "pending G60 (a problem's fields are newtypes or enums)")
  final case class Unclassified(text: Legacy.Text) extends Problem

  object Unclassified:
    /** TEMPORARY (G60 step 1, counted by `G60fromString`): an exception's message as an unclassified problem, as the
      * values module's String `Problem.of` read it; its slice turns the site into [[Problem.from]].
      */
    @tolerated(LintRule.G60FromString, "pending G60 (problems built from cases, not text)")
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

  /** A problem known only by its sentence, as another program wrote it: how the browser reads outside text, a bare
    * string where a typed entry would be. It is no case of any group and no code branches on it.
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
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
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

    /** The same problem, found inside `where` (a finding, a claim of an answer, a file): a proposal's place and an
      * input's path get it in front, so that the sentence starts `where: `; damaged data is placed inside it.
      */
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def inside(where: Where): Problem = problem match
      case Content.Proposal(rule, at) => Content.Proposal(rule, Where(s"$where: $at"))
      case Content.Answer(rule)       => Content.Proposal(rule, where)
      case Io.Corrupt(format, at, d)  => Io.Corrupt(format, at.in(where), d)
      case other                      => other.within(Field(s"$where"))

  // ---- one of each ------------------------------------------------------------------------------------------

  /** One value of every case, with every field set: what the renderers' tables are checked over. */
  @tolerated(LintRule.G60FromString, "pending G60 (problems built from cases, not text)")
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
      NotFound.Name(Raw("pleiades_579885"), Raw("Knossos"), Raw("Cnossus"), Raw("la"), Vector(Raw("en"), Raw(""))),
      NotFound.SetByHand(Raw("pleiades_579885"), Raw("Knossos")),
      NotFound.Undownloaded(Raw("vector"), Raw("histmersion basemap fetch")),
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
      Conflict.Busy(Conflict.Activity.Restore),
      Conflict.ServerHeld(Raw("histmersion-server mac.local:4242, connected since 2026-11-19T08:00:00Z")),
      Conflict.Undrained(NonEmptyVector.of(Raw("2 writes in flight"), Raw("agent runs: 1 running")), Count(60L)),
      Conflict.Referenced(
        TypeName("Place.Id"),
        Raw("pleiades_579885"),
        TypeName("Finding"),
        Count(3L)
      ),
      Conflict.Held(Where("source anthony2007"), Conflict.Hold.SameWork(Raw("anthony2006"), Raw("doi"))),
      Conflict.Referred(
        Raw("local_arkona"),
        Raw("Arkona"),
        NonEmptyVector.of(
          Conflict.Reference(Count(3L), Conflict.Referrer.Events),
          Conflict.Reference(Count(1L), Conflict.Referrer.ChildPlaces)
        )
      ),
      Conflict.Job(Raw("0193f1c4"), Conflict.State("queued"), Conflict.Control.NotStarted),
      Refused.Policy(Refused.Rule.OneStatement),
      Refused.Unauthenticated(Refused.Credential.ApiKey),
      Refused.Misdirected(Raw("rebound.example")),
      Refused.Disabled(Refused.Feature("events-gvp"), SettingKey("histmersion.events.gvp.enabled"), None, None),
      Refused.Space(
        Refused.Work.Gebco,
        Count(4_200_000_000L),
        Io.Path("/srv/histmersion/basemap"),
        Count(5_000_000_000L),
        Count(2_000_000_000L)
      ),
      Upstream.Http.here(core, url, Upstream.Status(429), External.ReplyExcerpt("{\"message\":\"rate limited\"}")),
      Upstream.Challenged.here(
        Upstream.Name("archive-org"),
        Upstream.Url("https://archive.org/download/x/x.pdf"),
        Upstream.Status(403)
      ),
      Upstream.Limited(Upstream.Name("claude"), Some(EpochMs(1762732800000L))),
      Upstream.Unasked(Relayed(Sentence("CORE asked to wait until 2025-11-10T00:00:00Z"))),
      Upstream.Unreachable.here(core, url, reported),
      Upstream.Offline(Upstream.Name("the provider"), Some(EpochMs(1762732800000L))),
      Upstream.Changed(Upstream.Name("the provider")),
      Upstream.Rebuilt(Upstream.Url("https://build.protomaps.com/20261101.pmtiles")),
      Upstream.Mislisted(
        Upstream.Url("https://build.protomaps.com/20261101.pmtiles"),
        Count(120_000_000_000L),
        Count(121_000_000_000L),
        Raw("20261101.pmtiles")
      ),
      Upstream.Timeout.here(core, url, Millis(30000L)),
      Upstream.Unanswered(
        Upstream.Name("Histmersion"),
        Upstream.Url("https://histmersion.example/api/queue/tasks"),
        External.Reported("java.io.IOException: connection reset")
      ),
      Upstream.CallFailed(
        Upstream.Name("anthropic:claude-opus"),
        Plugin.Said(Plugin.Id("hello-ai"), Plugin.Claim.Failed, External.PluginSaid("Claude API error: Overloaded"))
      ),
      Upstream.CallThrew(Upstream.Name("anthropic:claude-opus"), External.Reported("IOException: connection reset")),
      Upstream.Replied(Upstream.Name("codex exec"), External.Reported("stream disconnected before completion")),
      Upstream.McpServer(Raw("failed")),
      Upstream.BadReply
        .here(core, url, Upstream.Shape.Json, External.Reported("expected json value got '<html>' (line 1)")),
      Io.File(
        Io.Access.Read,
        Io.Path("/tmp/bundle.hmx"),
        External.Reported("java.nio.file.NoSuchFileException: /tmp/bundle.hmx")
      ),
      Io.Wrong(Io.Path("/srv/seed"), Io.Kind.Directory),
      Io.Absent(Io.Held.GazetteerSeed, Io.Path("/srv/histmersion/gazetteer"), Some(SettingKey("init.data-dir"))),
      Io.Extract(
        Raw("geonames"),
        Io.Pinning.Mismatch(
          Raw("2026-09"),
          Io.Path("/home/x/.cache/histmersion/gazetteer/geonames.lpf.gz"),
          Raw("ab12"),
          Raw("cd34")
        )
      ),
      Io.Oversize(Io.Path("whg-crete.json"), Count(612L), SettingKey("places.sources.max-lpf-mb")),
      Io.Hashed(Io.Path("/srv/originals/ab12.pdf"), Raw("ab12"), Raw("cd34")),
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
      Config.Cycle(NonEmptyVector.of(Config.Scope("db"), Config.Scope("lease"), Config.Scope("db"))),
      Config.Unbuilt(Config.Scope("pool"), NonEmptyVector.of(Config.Unavailable(Config.Capability.HotGraph))),
      Config.Unchanged(
        Config.Unbuilt(Config.Scope("pool"), NonEmptyVector.of(Config.Cycle(NonEmptyVector.of(Config.Scope("db")))))
      ),
      Config.Drained(Raw("pool (generation 3, 2 pinned)"), Config.Drain.InUse(Count(10L))),
      Config.ApiKeyLogin(Raw("api_key_helper")),
      Config.Exposed(Raw("0.0.0.0"), Raw("0.0.0.0/0"), Config.Exposure.Unlisted),
      Config.ProxyUndeclared(Raw("https://histmersion.example.edu")),
      Config.WildcardOverlap(Raw("0.0.0.0"), Raw("127.0.0.1")),
      Config.BindRepeated(Raw("::1"), Raw("[::1]")),
      Config.FloorsUnread(
        Config.FloorsFrom.Setting(SettingKey("agents.quota.claude-plan.floors")),
        NonEmptyVector.of(Invalid.Missing(Field("five_hour"), Vector.empty))
      ),
      Config.ReadAt(
        Io.Path("~/.config/histmersion/histmersion.conf"),
        Io.Path("/tmp/histmersion.conf.1234"),
        NonEmptyVector.of(Io.Wrong(Io.Path("/tmp/histmersion.conf.1234"), Io.Kind.File))
      ),
      Config.Unpriced(Run.Model("claude-opus-5-5"), Some(Config.Metered.ApiKey(Upstream.Name("claude-code")))),
      Config.Definition(
        Raw("agents/skeptic.md"),
        Config.Flaw.ToolsUnoffered(Vector(Raw("patch_source")), Vector(Raw("search"), Raw("get_source")))
      ),
      Plugin.Load(Plugin.Id("media-met"), Plugin.Fault.NullComponent),
      Plugin.Rejected(
        Plugin.Id("media-met"),
        Raw("248284"),
        Plugin.Refusal.NotOpen(Field("isPublicDomain"), Vector(Raw("false")))
      ),
      Plugin.Vault(Plugin.Id("keepassxc"), Plugin.Retrieval.Locked),
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
      Run.Paused(Run.PauseReason.Requested, Some(EpochMs(1762732800000L))),
      Run.Halted(Run.PauseCause.PlanQuota, Run.Skipped(Run.Skip.Cooling(EpochMs(1762732800000L)))),
      Run.Unstarted,
      Run.UploadUnheld,
      Run.NoModel(
        Run.Role("reviewer"),
        Run.Shortfall.Unusable,
        Vector(
          Run.PassedOver(
            Run.Model("claude-code:opus"),
            NonEmptyVector.of(Config.Unpriced(Run.Model("claude-code:opus"), None))
          ),
          Run.PassedOver(
            Run.Model("codex:gpt-6"),
            NonEmptyVector.of(Run.Skipped(Run.Skip.PlanOnly(Run.Role("reviewer"))))
          )
        )
      ),
      Run
        .PassedOver(Run.Model("local:qwen"), NonEmptyVector.of(Run.Skipped(Run.Skip.Cooling(EpochMs(1762732800000L))))),
      Run.Skipped(Run.Skip.Cooling(EpochMs(1762732800000L))),
      Run.Stopped(Run.Stop.Stalled(Count(4L), Count(31L), Count(3L))),
      Run.NotNow(Raw("0193f1c4"), Raw("done"), Run.Control.Resume),
      Run.AtStep(
        Run.Step("enrich"),
        Run.Failure.Failed(
          NonEmptyVector.of(Run.NoModel(Run.Role("enricher"), Run.Shortfall.Unassigned(Run.Asked.Call), Vector.empty))
        )
      ),
      Run.AtModel(
        Run.Model("hello-ai:echo"),
        NonEmptyVector.of(Refused.Policy(Refused.Rule.PlanOutsideLocal))
      ),
      Run.Failed(NonEmptyVector.of(NotFound.Entity(TypeName("com.histmersion.domain.library.Citekey"), Raw("x")))),
      Run.LimitReached(
        Upstream.Limited(Upstream.Name("claude-code"), Some(EpochMs(1762732800000L))),
        EpochMs(1762732800000L)
      ),
      Run.Needs(Run.Need.Text(Raw("anthony2007"))),
      Run.Interrupted,
      Run.Restored,
      Run.CutShort(Conflict.Activity.MergeApply),
      Run.Cancelled(Run.Actor.Person),
      Run.Draining(Some(EpochMs(1762732800000L))),
      Content.Finding(Content.Flaw.HandWrittenSourcesCited),
      Content.Proposal(Content.Requirement.TooManyClaims(Count(12L), Count(15L)), Where("claims")),
      Content.Answer(Content.Requirement.SummaryGiven),
      Content.NotProposed(Content.Answer(Content.Requirement.ProposedOnce(Raw("tin-trade")))),
      Content.Undecoded(
        Content.Decoding.Mismatch(Raw(".notes"), External.Reported("Got value '5' with wrong type, expecting string")),
        Some(External.Reported("iteration limit")),
        Some(External.ModelSaid("{\"notes\": 5}"))
      ),
      Internal.Unexpected(
        Internal.Id("3f2a9c1e"),
        Where("backup"),
        Internal.ExceptionName("java.lang.IllegalStateException"),
        External.Reported("java.lang.IllegalStateException: pg_dump vanished")
      ),
      Unclassified(Legacy.Text("cannot open the bundle")),
      Sentence("merge apply crashed: NullPointerException").kept,
      Relayed(Sentence("the server is reindexing"))
    )

  val variants: Vector[Problem] = Vector(
    Run.Stopped(Run.Stop.Silent(Count(4L), Count(31L), Count(1L), Count(3L))),
    Run.Stopped(Run.Stop.NoPipeline(Raw("enrich-everything"))),
    Run.Stopped(Run.Stop.PipelineChanged),
    Run.Stopped(Run.Stop.Reordered(Count(2L), Run.Step("extract"), Run.Step("enrich"))),
    Run.Stopped(
      Run.Stop.CheckpointUnread(Count(2L), Run.Step("extract"), External.Reported("missing required field 'claims'"))
    ),
    Run.Stopped(Run.Stop.Vanished),
    Run.Stopped(Run.Stop.Over(Run.Spending.Usd(Raw("$1.2000"), Raw("$1.00")))),
    Run.Stopped(Run.Stop.Over(Run.Spending.Tokens(Count(600001L), Count(600000L), Raw("0.1")))),
    Run.Stopped(Run.Stop.Over(Run.Spending.Capped(Run.Cap.Daily, Raw("5.0")))),
    Run.Stopped(Run.Stop.Over(Run.Spending.Capped(Run.Cap.Monthly, Raw("50.0")))),
    Run.Stopped(Run.Stop.MovedOn(Raw("0193f1c4"))),
    Run.Stopped(Run.Stop.Withdrawn),
    Run.Stopped(Run.Stop.Guard(Raw("the quota-guard mod ended the turn"))),
    Run.Stopped(Run.Stop.Unrecorded(Conflict.Activity.MergeApply)),
    Run.Needs(Run.Need.Body(Raw("yamnaya"))),
    Run.Needs(Run.Need.Login(Raw("'claude auth status' says not logged in"))),
    Run.Needs(Run.Need.Probe(Io.ExitCode(1), Some(External.Reported("Invalid API key")))),
    Run.AtStep(Run.Step("enrich"), Run.Failure.Unsettled(Count(16L))),
    Run.AtStep(
      Run.Step("enrich"),
      Run.Failure.ProviderDown(
        NonEmptyVector.of(
          Upstream.Timeout(
            Upstream.Name("anthropic"),
            Upstream.Url("https://api.anthropic.com/v1/messages"),
            Millis(600000L),
            Vector.empty
          )
        )
      )
    ),
    Run.AtStep(Run.Step("enrich"), Run.Failure.BudgetSpent),
    Run.AtStep(
      Run.Step("extract"),
      Run.Failure.NoValidAnswer(Count(3L), Vector(Content.Answer(Content.Requirement.StandIn)))
    ),
    Run.AtStep(
      Run.Step("extract"),
      Run.Failure.Declined(
        Vector(Content.Answer(Content.Requirement.StandIn), Content.Answer(Content.Requirement.SummaryGiven))
      )
    ),
    Run.AtStep(
      Run.Step("cold-reader-1"),
      Run.Failure
        .OutputCapped(Run.Model("claude-code:haiku"), Count(4096L), Count(4096L), Run.Ceiling.StepMaxTokens)
    ),
    Run.NoModel(
      Run.Role("enricher"),
      Run.Shortfall.Exhausted(
        Run.Floor.PlanOrAllowance,
        Run.Frees.At(
          EpochMs(1762732800000L),
          Run.Model("claude-code:sonnet"),
          Vector(Run.HeldPool(Raw("Claude plan"), Raw("claude-plan")))
        )
      ),
      Vector(
        Run.PassedOver(
          Run.Model("claude-code:sonnet"),
          NonEmptyVector.of(
            Run.Skipped(Run.Skip.BelowFloor(Raw("Claude plan"), Raw("5-hour window 3% left, floor 10%")))
          )
        )
      )
    ),
    Run.NoModel(Run.Role("skeptic"), Run.Shortfall.Unassigned(Run.Asked.Call), Vector.empty),
    Run.Skipped(Run.Skip.BelowFloor(Raw("ChatGPT plan"), Raw("weekly window 2% left, floor 5%"))),
    Run.Skipped(
      Run.Skip.BelowAllowance(
        NonEmptyVector.of(
          Run.Spent(Raw("5 usd/day"), Raw("4.9 USD"), Raw("0.5 USD")),
          Run.Spent(Raw("2M tokens/week"), Raw("1.9M"), Raw("0"))
        )
      )
    ),
    Run.Skipped(
      Run.Skip.AllowanceUnread(
        NonEmptyVector.of(
          Run.Unread(
            Raw("claude-code:*"),
            NonEmptyVector.of(
              Invalid.Unreadable.here(TypeName("ModelChains.Limit.Spec"), Raw("5 usd/fortnight"), Vector.empty)
            )
          )
        )
      )
    ),
    Run.Skipped(Run.Skip.PlanOnly(Run.Role("writer"))),
    Run.Skipped(Run.Skip.AllowanceReached(Run.Model("anthropic:claude-opus")))
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
