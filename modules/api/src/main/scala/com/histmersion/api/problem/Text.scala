package com.histmersion
package api
package problem

import java.time.Instant

import util.problem.{External, NonEmptyVector, Problem, Problems}
import util.problem.Problem.*

/** The English sentence of every problem: what the HTTP body's `text`, the CLI's `error:` line, the MCP tool's error
  * and the web's error line show. It may change wording freely; what a column stores is [[Stored]]'s, frozen.
  *
  * Outside text ([[External]]) follows the case's own words after an em dash.
  */
object Text:

  extension (problem: Problem)
    /** The sentence that says what failed. */
    def sentence: String = problem match
      case Invalid.Malformed(t, c, raw, at)    => s"${path(at)}'$raw' is not a ${t.short}: ${c.description}"
      case Invalid.Unknown(t, raw, known, at)  => s"${path(at)}unknown ${t.short} '$raw'${oneOf(known)}"
      case Invalid.Missing(field, at)          => s"${path(at)}$field is required"
      case Invalid.OutOfRange(t, raw, b, at)   => s"${path(at)}${t.short} $raw is ${b.range}"
      case Invalid.Inconsistent(fs, rule, at)  => s"${path(at)}${rule.of(fs)}"
      case NotFound.Entity(t, key)             => s"no ${t.short} '$key'"
      case Conflict.AlreadyExists(t, key)      => s"${t.short} '$key' already exists"
      case Conflict.Renamed(t, asked, now)     => s"${t.short} '$asked' was renamed to '$now' — write to '$now' instead"
      case Conflict.Stale(t, key, sent, now)   => s"${t.short} '$key' has moved on: you sent $sent, it is now $now"
      case Conflict.WrongState(t, key, is, ws) => s"${t.short} '$key' is $is; this needs ${ws.mkString(" or ")}"
      case Conflict.Busy(activity)             => s"${activity.named} is still running"
      case Conflict.Referenced(t, key, by, n)  => s"${t.short} '$key' is referred to by $n ${by.short}"
      case Refused.Policy(rule)                => rule.said
      case Refused.Quota(limit, used, cap, at) => s"${limit.named} is spent: $used of $cap${until(at)}"
      case Refused.Disabled(feature, setting)  => s"$feature is switched off ($setting)"
      case Upstream.Http(name, url, st, body)  => s"$name answered HTTP $st at $url — $body"
      case Upstream.Unreachable(name, url, r)  => s"cannot reach $name at $url — $r"
      case Upstream.Timeout(name, url, after)  => s"$name did not answer at $url within $after ms"
      case Upstream.BadReply(name, url, s, r)  => s"$name's answer at $url is not ${words(s.productPrefix)} — $r"
      case Io.File(op, path, reported)         => s"could not ${words(op.productPrefix)} $path — $reported"
      case Io.Wrong(path, expected)            => s"$path ${expected.missed}"
      case Io.Corrupt(format, at, damage)      => s"the ${words(format.productPrefix)} data at $at ${damage.said}"
      case Io.Process(tool, ending)            => s"$tool ${ending.said}"
      case Io.Database(op, reported)           => s"the database refused to ${words(op.productPrefix)} — $reported"
      case Config.Setting(key, fault)          => s"$key ${fault.said}"
      case Config.Unavailable(capability)      => s"this server has no ${words(capability.productPrefix)}"
      case Config.Reload(stage, scope)         => s"reloading the settings failed at ${words(stage.productPrefix)} ($scope)"
      case Plugin.Load(plugin, fault)          => s"plugin $plugin did not load: ${fault.said}"
      case Plugin.Rejected(plugin, item, why)  => s"$plugin did not take $item: ${words(why.productPrefix)}"
      case Plugin.Vault(vault, failure)        => s"secret vault $vault failed: ${failure.said}"
      case Plugin.Said(plugin, claim, text)    => s"$plugin (${words(claim.productPrefix)}): $text"
      case Problem.Run.Paused(reason, at)              => s"paused for ${words(reason.productPrefix)}${until(at)}"
      case Problem.Run.NoModel(role, tried)            => s"no model of the $role chain can run${triedModels(tried)}"
      case Problem.Run.Blocked(block)                  => s"held back: ${block.said}"
      case Problem.Run.Interrupted                     => "interrupted by a restart"
      case Problem.Run.Cancelled(by)                   => s"cancelled by ${words(by.productPrefix)}"
      case Problem.Run.Draining(since)                 => s"stopped while the autopilot drains (since ${since.iso})"
      case Problem.Run.Aborted(step, cause)            => s"step $step cannot go on: ${words(cause.productPrefix)}"
      case Content.Finding(cause)              => cause.said
      case Content.Proposal(rule, at)          => s"$at: ${rule.said}"
      case Internal.Unexpected(where, e, r)    => s"$where failed unexpectedly ($e) — $r"
      case Unclassified(text)                  => s"$text"
      case Stored.Unrecognised(text)           => s"$text"

  extension (problems: Problems)
    /** One sentence per problem, in order. */
    def sentences: Vector[String] = problems.toVector.map(_.sentence)

  extension (typeName: TypeName)
    /** A type's name without its package: `Citekey`, `Source.Status`. */
    def short: String = s"$typeName".split('.').dropWhile(_.headOption.forall(_.isLower)).mkString(".")

  /** A code name in words: `MergeApply` is `merge apply`. */
  def words(name: String): String = name.flatMap(c => if c.isUpper then s" ${c.toLower}" else s"$c").trim

  private def path(at: Vector[Field]): String = if at.isEmpty then "" else s"${at.mkString(".")}: "

  extension (at: EpochMs) private def iso: String = s"${Instant.ofEpochMilli(s"$at".toLong)}"

  private def until(at: Option[EpochMs]): String = at.fold("")(t => s" until ${t.iso}")

  private def oneOf(known: Vector[Raw]): String = if known.isEmpty then "" else s" (one of: ${known.mkString(", ")})"

  extension (bounds: Invalid.Bounds) private def range: String = bounds match
    case Invalid.Bounds(Some(least), Some(greatest)) => s"not between $least and $greatest"
    case Invalid.Bounds(Some(least), None)           => s"below $least"
    case Invalid.Bounds(None, Some(greatest))        => s"above $greatest"
    case Invalid.Bounds(None, None)                  => "out of range"

  extension (rule: Invalid.Exclusion) private def of(fields: NonEmptyVector[Field]): String =
    val named = fields.toVector.mkString(", ")
    rule match
      case Invalid.Exclusion.Together  => s"give $named together, or none of them"
      case Invalid.Exclusion.OneOf     => s"give exactly one of $named"
      case Invalid.Exclusion.AtMostOne => s"give at most one of $named"
      case Invalid.Exclusion.Ordered   => s"$named must be in order"


  extension (activity: Conflict.Activity) private def named: String = activity match
    case Conflict.Activity.CalibreGui => "the Calibre GUI"
    case other                        => s"a ${words(other.productPrefix)}"

  extension (rule: Refused.Rule) private def said: String = rule match
    case Refused.Rule.RunTokenRequired  => "a live run token is required"
    case Refused.Rule.ApiKeyRequired    => "the API key is required"
    case Refused.Rule.WorkingDirDenied  => "the command may not write in this working directory"
    case Refused.Rule.ReadOnlyQuery     => "a query may only read: it holds a statement that writes"
    case Refused.Rule.LoginOutsideLocal => "this login is refused while the server listens beyond this machine"
    case Refused.Rule.LegalOnly         => "only legal open-access copies may be fetched"

  extension (limit: Refused.Limit) private def named: String = limit match
    case Refused.Limit.DailySpend    => "today's agent spend"
    case Refused.Limit.MonthlySpend  => "this month's agent spend"
    case Refused.Limit.RunSpend      => "the run's spend"
    case Refused.Limit.ProviderQuota => "the provider's quota"
    case Refused.Limit.RateLimit     => "the rate limit"
    case Refused.Limit.PlanAllowance => "the plan's allowance"

  extension (kind: Io.Kind) private def missed: String = kind match
    case Io.Kind.File       => "is not a file"
    case Io.Kind.Directory  => "is not a directory"
    case Io.Kind.Present    => "is not there"
    case Io.Kind.Absent     => "is there already"
    case Io.Kind.Executable => "is not executable"

  extension (damage: Io.Damage) private def said: String = damage match
    case Io.Damage.Checksum                => "fails its checksum"
    case Io.Damage.Truncated               => "is cut short"
    case Io.Damage.Undecodable             => "cannot be decoded"
    case Io.Damage.Length(expected, found) => s"holds $found units, not $expected"

  extension (ending: Io.Ending) private def said: String = ending match
    case Io.Ending.Exited(code, stderr) => s"exited with $code — $stderr"
    case Io.Ending.TimedOut(after)      => s"did not finish within $after ms"
    case Io.Ending.NotStarted(reported) => s"could not be started — $reported"

  extension (fault: Config.Fault) private def said: String = fault match
    case Config.Fault.Empty            => "is empty"
    case Config.Fault.Unset(variable)  => s"reads $variable, which is not set"
    case Config.Fault.Malformed(raw)   => s"is not valid: '$raw'"
    case Config.Fault.Unknown(raw)     => s"has an unknown value '$raw'"
    case Config.Fault.Conflicts(other) => s"contradicts $other"
    case Config.Fault.NoSecret         => "names a secret no vault holds"

  extension (fault: Plugin.Fault) private def said: String = fault match
    case Plugin.Fault.Threw(reported) => s"loading it threw — $reported"
    case other                        => words(other.productPrefix)

  extension (failure: Plugin.Retrieval) private def said: String = failure match
    case Plugin.Retrieval.TimedOut(after) => s"it did not answer within $after ms"
    case Plugin.Retrieval.Failed(stderr)  => s"its tool failed — $stderr"
    case other                               => words(other.productPrefix)

  private def triedModels(tried: Vector[Problem.Run.Model]): String =
    if tried.isEmpty then "" else s" (passed over: ${tried.mkString(", ")})"

  extension (block: Problem.Run.Block) private def said: String = block match
    case Problem.Run.Block.PoolFull(pool) => s"the $pool pool runs as many as it may"
    case other                    => words(other.productPrefix)

  extension (cause: Content.Flaw) private def said: String = cause match
    case Content.Flaw.HandWrittenSourcesCited => "a hand-written Sources cited section (the page generates it)"
    case Content.Flaw.UncitedParagraph        => "a paragraph cites no source"
    case Content.Flaw.UnknownCitekey          => "a citation names no source in the corpus"
    case Content.Flaw.FormattingIntroduced    => "a formatting error the last edits introduced"
    case Content.Flaw.MissingSection          => "a required section is missing"

  extension (rule: Content.Requirement) private def said: String = rule match
    case Content.Requirement.NoYearZero               => "there is no year 0; use BCE years as negative numbers"
    case Content.Requirement.CitesNoSource            => "the text cites no source"
    case Content.Requirement.UnknownCitekey           => "it names a source that is not in the corpus"
    case Content.Requirement.OutsidePeriod            => "it falls outside the period"
    case Content.Requirement.TooManyClaims(cap, gave) => s"at most $cap claims per source (you gave $gave)"
