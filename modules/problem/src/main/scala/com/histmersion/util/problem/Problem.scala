package com.histmersion
package util.problem

import scala.reflect.TypeTest

import hearth.typename.TypeName as Named

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
sealed trait Problem extends Product, Serializable derives CanEqual

object Problem:

  // ---- what failed: shared by the groups ------------------------------------------------------------------------

  /** The full name of the type a value failed to be, as the compiler wrote it (`com.histmersion.domain.Citekey`). */
  type TypeName = TypeName.T
  object TypeName extends Newtype[String, Pure]:
    /** The name the newtype module captured. */
    def of(name: Newtype.TypeName): TypeName = TypeName(s"${name.text}")

    /** The name of a newtype, as Hearth names its `T` where it is asked. */
    def of[A, C](newtype: Newtype[A, C])(using Named[newtype.T]): TypeName = of(newtype.typeName)

  /** An Iron constraint a value failed: its type as written and Iron's description of it. */
  import Constraint.{Description, Name}

  final case class Constraint(name: Name, description: Description) derives CanEqual

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

  /** The wire name of a request or settings field (`lat`, `edges`, `agents.budgets.daily-usd`). */
  type Field = Field.T
  object Field extends Newtype[String, Pure]

  /** Where in the code or the work a failure happened: the operation's own name (`backup`, `merge apply`). */
  type Where = Where.T
  object Where extends Newtype[String, Pure]

  /** A count of things. */
  type Count = Count.T
  object Count extends Newtype[Long, Pure]:
    /** A count written in decimal digits. */
    def read(digits: String): Option[Count] = digits.toLongOption.map(Count(_))

  /** A length of time, in milliseconds. */
  type Millis = Millis.T
  object Millis extends Newtype[Long, Pure]:
    /** A length of time written in decimal digits of milliseconds. */
    def read(digits: String): Option[Millis] = digits.toLongOption.map(Millis(_))

  /** A moment, as milliseconds since 1970-01-01T00:00Z (the same on the JVM and in the browser). */
  type EpochMs = EpochMs.T
  object EpochMs extends Newtype[Long, Pure]:
    /** The moment `millis` milliseconds after 1970-01-01T00:00Z. */
    def of(millis: Long): EpochMs = EpochMs(millis)

  /** A setting's key: `histmersion.agents.budgets.daily-usd`. */
  type SettingKey = SettingKey.T
  object SettingKey extends Newtype[String, Pure]

  /** The first word of a case's name: one of the eleven groups (what kind of failure it is), or one of the two cases
    * outside them (`Unclassified`, `Stored`).
    */
  enum Group derives CanEqual:
    case Invalid, NotFound, Conflict, Refused, Upstream, Io, Config, Plugin, Run, Content, Internal, Unclassified,
      Stored

  // ---- the groups ---------------------------------------------------------------------------------------------

  /** The input is not a value the operation takes. `at` is the path to the input within the request (`edges`, `3`),
    * outermost first.
    */
  sealed trait Invalid extends Problem

  object Invalid:

    /** `raw` is no value of the type `typeName`: it fails `constraint`. */
    final case class Malformed(typeName: TypeName, constraint: Constraint, raw: Raw, at: Vector[Field] = Vector.empty)
        extends Invalid

    /** `raw` is none of the values of `typeName` the operation knows (`known`, when the list is short). */
    final case class Unknown(typeName: TypeName, raw: Raw, known: Vector[Raw], at: Vector[Field] = Vector.empty)
        extends Invalid

    /** `field` is required and was not given, or was empty. */
    final case class Missing(field: Field, at: Vector[Field] = Vector.empty) extends Invalid

    /** `raw` is a value of `typeName`, but outside what the operation takes. */
    final case class OutOfRange(typeName: TypeName, raw: Raw, bounds: Bounds, at: Vector[Field] = Vector.empty)
        extends Invalid

    /** The `fields` given break `rule` together, though each is a value on its own. */
    final case class Inconsistent(fields: NonEmptyVector[Field], rule: Exclusion, at: Vector[Field] = Vector.empty)
        extends Invalid

    /** The least and the greatest value taken, either open. */
    final case class Bounds(least: Option[Raw], greatest: Option[Raw]) derives CanEqual

    /** How fields given together must relate. */
    enum Exclusion derives CanEqual:
      /** All of them or none. */
      case Together

      /** Exactly one of them. */
      case OneOf

      /** At most one of them. */
      case AtMostOne

      /** Each no greater than the next (a start before its end). */
      case Ordered

    /** The input a newtype refused: its type, its constraint and the input, as the newtype module recorded them. */
    def malformed(refusal: Newtype.Refusal): Invalid =
      Malformed(TypeName.of(refusal.typeName), Constraint.of(refusal.constraint), Raw(s"${refusal.raw}"))

  /** What the operation names does not exist. */
  sealed trait NotFound extends Problem

  object NotFound:

    /** No value of `typeName` has the key `key`. */
    final case class Entity(typeName: TypeName, key: Raw) extends NotFound

  /** The request is valid, but the stored state does not allow it now. */
  sealed trait Conflict extends Problem

  object Conflict:

    /** A value of `typeName` with the key `key` exists already. */
    final case class AlreadyExists(typeName: TypeName, key: Raw) extends Conflict

    /** `asked` was renamed to `now`: write to `now`. */
    final case class Renamed(typeName: TypeName, asked: Raw, now: Raw) extends Conflict

    /** The value changed since the caller read it: `sent` is the version the caller had, `stored` the one there is. */
    final case class Stale(typeName: TypeName, key: Raw, sent: Stamp, stored: Stamp) extends Conflict

    /** The value is in state `is`; the operation needs one of `wanted`. */
    final case class WrongState(typeName: TypeName, key: Raw, is: State, wanted: Vector[State]) extends Conflict

    /** `activity` is running and must finish first. */
    final case class Busy(activity: Activity) extends Conflict

    /** `count` values of the type `by` refer to it (one problem per type of referrer). */
    final case class Referenced(typeName: TypeName, key: Raw, by: TypeName, count: Count) extends Conflict

    /** A version of a value as the store writes it: a time or a revision, in its wire spelling. */
    type Stamp = Stamp.T
    object Stamp extends Newtype[String, Pure]

    /** A state in its wire spelling (`running`, `settled`). */
    type State = State.T
    object State extends Newtype[String, Pure]

    /** The work that holds the store or a resource while it runs. */
    enum Activity derives CanEqual:
      case Backup, Restore, Vacuum, Reindex, MergeApply, BundleImport, Seed, Migration, CalibreGui

  /** The operation is not allowed, though it is valid and the state allows it. */
  sealed trait Refused extends Problem

  object Refused:

    /** A rule of the service forbids it. */
    final case class Policy(rule: Rule) extends Refused

    /** `limit` is spent: `used` of `cap`, until `until` when that is known. */
    final case class Quota(limit: Limit, used: Amount, cap: Amount, until: Option[EpochMs]) extends Refused

    /** `feature` is switched off by `setting`. */
    final case class Disabled(feature: Feature, setting: SettingKey) extends Refused

    /** The rules a request can break. */
    enum Rule derives CanEqual:
      /** The request needs a live run token. */
      case RunTokenRequired

      /** The request needs the API key. */
      case ApiKeyRequired

      /** The command may not write in the working directory it was given. */
      case WorkingDirDenied

      /** The read-only query holds a statement that writes. */
      case ReadOnlyQuery

      /** The runtime refuses this login while the server listens beyond the local machine. */
      case LoginOutsideLocal

      /** Only legal open-access sources may be fetched. */
      case LegalOnly

    /** The allowances a request can spend. */
    enum Limit derives CanEqual:
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

    /** `upstream` answered `status` at `url`; `body` is the start of what it said. */
    final case class Http(upstream: Name, url: Url, status: Status, body: External.ReplyExcerpt) extends Upstream

    /** `upstream` could not be reached at `url`. */
    final case class Unreachable(upstream: Name, url: Url, reported: External.Reported) extends Upstream

    /** `upstream` did not answer at `url` within `after`. */
    final case class Timeout(upstream: Name, url: Url, after: Millis) extends Upstream

    /** `upstream` answered at `url`, but not with `expected`. */
    final case class BadReply(upstream: Name, url: Url, expected: Shape, reported: External.Reported) extends Upstream

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

    /** What a reply was expected to be. */
    enum Shape derives CanEqual:
      case Json, Xml, Html, Text, Image, Document, Archive, Problems

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

    enum Access derives CanEqual:
      case Read, Write, Create, Delete, Move, List

    enum Kind derives CanEqual:
      case File, Directory, Present, Absent, Executable

    enum Format derives CanEqual:
      case Bundle, Tar, Json, Png, PmTiles, Pdf, Image, Text

    /** What is wrong with the data. */
    enum Damage derives CanEqual:
      case Checksum, Truncated, Undecodable

      /** It holds `found` units where `expected` belong. */
      case Length(expected: Count, found: Count)

    /** How a process ended that did not end well. */
    enum Ending derives CanEqual:
      /** It exited with `code`; `stderr` is the end of what it wrote to its standard error. */
      case Exited(code: ExitCode, stderr: External.Stderr)

      /** It did not finish within `after`. */
      case TimedOut(after: Millis)

      /** It could not be started. */
      case NotStarted(reported: External.Reported)

    enum DbOp derives CanEqual:
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

    /** What is wrong with a setting. */
    enum Fault derives CanEqual:
      /** It has no value, or an empty one. */
      case Empty

      /** The environment variable it reads is not set. */
      case Unset(variable: SettingKey)

      /** Its value is no value of the setting's type. */
      case Malformed(raw: Raw)

      /** Its value is none the setting knows. */
      case Unknown(raw: Raw)

      /** It contradicts `other`. */
      case Conflicts(other: SettingKey)

      /** It names a secret no vault holds. */
      case NoSecret

    /** What a server may lack. */
    enum Capability derives CanEqual:
      case Originals, AcquisitionChain, Runtimes, Converter, Gazetteer, Basemap, Agents, Calibre

    /** The stages of a settings reload. */
    enum Stage derives CanEqual:
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

    /** `plugin` failed in a way no other case names: `claim` is what kind of failure it reports, `text` its words. */
    final case class Said(plugin: Id, claim: Claim, text: External.PluginSaid) extends Plugin

    /** A plugin's (or a vault's) id. */
    type Id = Id.T
    object Id extends Newtype[String, Pure]

    /** Why a plugin did not load. */
    enum Fault derives CanEqual:
      case Missing, NullComponent, BadDescriptor, VersionMismatch, Duplicate

      /** Loading it threw. */
      case Threw(reported: External.Reported)

    /** Why a plugin did not take an item. */
    enum Refusal derives CanEqual:
      case NoLicence, NoImage, NoName, NoLocation, NoDate, Duplicate, Restricted

    /** How a vault failed. */
    enum Retrieval derives CanEqual:
      case NoVault, NoEntry, EmptyValue, Locked

      /** It did not answer within `after`. */
      case TimedOut(after: Millis)

      /** Its tool failed; `stderr` is the end of what it wrote. */
      case Failed(stderr: External.Stderr)

    /** The kinds of failure a plugin may report in its own words. */
    enum Claim derives CanEqual:
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

    /** `by` cancelled it. */
    final case class Cancelled(by: Actor) extends Run

    /** The autopilot drains since `since`; it resumes once the drain is lifted. */
    final case class Draining(since: EpochMs) extends Run

    /** It cannot go on at `step`: `cause`. */
    final case class Aborted(step: Step, cause: AbortCause) extends Run

    enum PauseReason derives CanEqual:
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
    enum Block derives CanEqual:
      /** `pool` runs as many as it may. */
      case PoolFull(pool: Pool)

      case SpendCap, Draining, OutsideWindow, Waiting

    enum Actor derives CanEqual:
      case Person, Automation, Server

    enum AbortCause derives CanEqual:
      case Disappeared, CheckpointMismatch, VersionChanged

  /** Content written by a person or a model breaks a rule of the writing. */
  sealed trait Content extends Problem

  object Content:

    /** A review finding about an article: `cause`. */
    final case class Finding(cause: Flaw) extends Content

    /** An agent's proposal breaks `rule` at `at`. */
    final case class Proposal(rule: Requirement, at: Where) extends Content

    enum Flaw derives CanEqual:
      case HandWrittenSourcesCited, UncitedParagraph, UnknownCitekey, FormattingIntroduced, MissingSection

    enum Requirement derives CanEqual:
      case NoYearZero, CitesNoSource, UnknownCitekey, OutsidePeriod

      /** At most `cap` claims per source; it gave `gave`. */
      case TooManyClaims(cap: Count, gave: Count)

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

    /** TEMPORARY (G60 step 1, counted by `G60fromString`): the words of an [[Unclassified]] problem as its site wrote
      * them, for the modules that cannot see the api's renderer `api.problem.Text` (plugin-api, upstreams, the plugins,
      * plugin-vaults, plugin-resolver, values, config). Every problem those modules meet is unclassified until their
      * slice moves them to cases and renders them at an edge; any other case shows as Scala prints it. Goes with
      * `Unclassified`.
      */
    def legacyText: String = problem match
      case Unclassified(text) => s"$text"
      case other              => s"$other"

    /** Is it a missing entity? */
    def isNotFound: Boolean = problem match
      case _: NotFound => true
      case _           => false

    /** The same problem, found in `field` of the request: an input's path gets `field` in front. */
    def within(field: Field): Problem = problem match
      case Invalid.Malformed(t, c, raw, at)   => Invalid.Malformed(t, c, raw, field +: at)
      case Invalid.Unknown(t, raw, known, at) => Invalid.Unknown(t, raw, known, field +: at)
      case Invalid.Missing(f, at)             => Invalid.Missing(f, field +: at)
      case Invalid.OutOfRange(t, raw, b, at)  => Invalid.OutOfRange(t, raw, b, field +: at)
      case Invalid.Inconsistent(fs, rule, at) => Invalid.Inconsistent(fs, rule, field +: at)
      case other                              => other

  // ---- one of each ------------------------------------------------------------------------------------------

  /** One value of every case, with every field set: what the renderers' tables are checked over. */
  val examples: Vector[Problem] =
    val citekey = TypeName("com.histmersion.domain.library.Citekey")
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
      Invalid.Unknown(
        TypeName("com.histmersion.domain.library.Source.Status"),
        Raw("dubious"),
        Vector(Raw("current"), Raw("contested"))
      ),
      Invalid.Missing(Field("rationale")),
      Invalid
        .OutOfRange(TypeName("com.histmersion.Latitude"), Raw("91"), Invalid.Bounds(Some(Raw("-90")), Some(Raw("90")))),
      Invalid.Inconsistent(NonEmptyVector.of(Field("lat"), Field("lon")), Invalid.Exclusion.Together),
      NotFound.Entity(citekey, Raw("anthony2007")),
      Conflict.AlreadyExists(TypeName("com.histmersion.domain.catalogue.Place.Id"), Raw("pleiades_579885")),
      Conflict.Renamed(citekey, Raw("anthony2006"), Raw("anthony2007")),
      Conflict.Stale(
        citekey,
        Raw("anthony2007"),
        Conflict.Stamp("2026-11-01T10:00:00Z"),
        Conflict.Stamp("2026-11-02T08:30:00Z")
      ),
      Conflict.WrongState(
        TypeName("com.histmersion.util.jobs.Job.Id"),
        Raw("412"),
        Conflict.State("running"),
        Vector(Conflict.State("paused"), Conflict.State("failed"))
      ),
      Conflict.Busy(Conflict.Activity.Backup),
      Conflict.Referenced(
        TypeName("com.histmersion.domain.catalogue.Place.Id"),
        Raw("pleiades_579885"),
        TypeName("com.histmersion.domain.library.Finding"),
        Count(3L)
      ),
      Refused.Policy(Refused.Rule.RunTokenRequired),
      Refused.Quota(Refused.Limit.DailySpend, Refused.Amount(5.25), Refused.Amount(4.5), Some(EpochMs(1762732800000L))),
      Refused.Disabled(Refused.Feature("events-gvp"), SettingKey("histmersion.events.gvp.enabled")),
      Upstream.Http(core, url, Upstream.Status(429), External.ReplyExcerpt("{\"message\":\"rate limited\"}")),
      Upstream.Unreachable(core, url, reported),
      Upstream.Timeout(core, url, Millis(30000L)),
      Upstream.BadReply(core, url, Upstream.Shape.Json, External.Reported("expected json value got '<html>' (line 1)")),
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
      Config.Setting(SettingKey("histmersion.agents.providers.codex.command"), Config.Fault.Empty),
      Config.Unavailable(Config.Capability.Originals),
      Config.Reload(Config.Stage.Close, Config.Scope("plugins")),
      Plugin.Load(Plugin.Id("media-met"), Plugin.Fault.NullComponent),
      Plugin.Rejected(Plugin.Id("media-smithsonian"), Raw("edanmdm:nmnhanthro_8371234"), Plugin.Refusal.NoImage),
      Plugin.Vault(Plugin.Id("keepassxc"), Plugin.Retrieval.TimedOut(Millis(20000L))),
      Plugin.Said(
        Plugin.Id("gazetteer-awmc"),
        Plugin.Claim.Unavailable,
        External.PluginSaid("the AWMC server is in maintenance")
      ),
      Run.Paused(Run.PauseReason.Allowance, Some(EpochMs(1762732800000L))),
      Run.NoModel(Run.Role("reviewer"), Vector(Run.Model("claude-opus-5-5"), Run.Model("gpt-6"))),
      Run.Blocked(Run.Block.PoolFull(Run.Pool("review"))),
      Run.Interrupted,
      Run.Cancelled(Run.Actor.Person),
      Run.Draining(EpochMs(1762732800000L)),
      Run.Aborted(Run.Step("draft"), Run.AbortCause.CheckpointMismatch),
      Content.Finding(Content.Flaw.UncitedParagraph),
      Content.Proposal(Content.Requirement.TooManyClaims(Count(12L), Count(15L)), Where("claims")),
      Internal.Unexpected(
        Internal.Id("3f2a9c1e"),
        Where("backup"),
        Internal.ExceptionName("java.lang.IllegalStateException"),
        External.Reported("java.lang.IllegalStateException: pg_dump vanished")
      ),
      Unclassified(Legacy.Text("cannot open the bundle")),
      Stored.Unrecognised(Stored.Text("merge apply crashed: NullPointerException"))
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
