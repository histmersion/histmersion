package com.histmersion
package api
package problem
import com.histmersion.util.nonempty.NonEmptyVector

import java.time.Instant

import scala.util.Try
import scala.util.matching.Regex

import util.problem.{External, Legacy, Problem}
import util.problem.Problem.*

/** How a problem is kept in a text column (`jobs.error`, `status_note`, …), and how a column's text is read back.
  *
  * The wording here is FROZEN: rows written by any earlier version must read as they were, so a case's sentence never
  * changes once it is released (the English [[Text]] may). A text is read as a case only if writing that case gives the
  * identical text back; any other text reads as [[Problem.Stored.Unrecognised]], and writing that gives the text back.
  * A type is stored by its full name, a code word by the case's own name.
  */
object Column:

  extension (problem: Problem)
    /** The frozen sentence of the problem, as a column keeps it. */
    def stored: String = problem match
      case Invalid.Malformed(t, c, raw, at)   => s"${path(at)}'$raw' is no $t: it fails ${c.name} (${c.description})"
      case Invalid.Unknown(t, raw, known, at) => s"${path(at)}unknown $t '$raw'${quotedList(known)}"
      case Invalid.Unreadable(t, raw, eg, at) => s"${path(at)}'$raw' is unreadable as $t${quotedExamples(eg)}"
      case Invalid.Missing(field, at)         => s"${path(at)}$field is required"
      case Invalid.Undeclared(field, ds, at) => s"${path(at)}$field is undeclared${quotedList(ds.map(d => Raw(s"$d")))}"
      case Invalid.Wrapped(t, raw, member, at)  => s"${path(at)}'$raw' wraps a $t in $member"
      case Invalid.StoredOn(t, item, owner, at) => s"${path(at)}$t $item is stored on '$owner'"
      case Invalid.Ambiguous(t, raw, n, at)     => s"${path(at)}$t '$raw' matches $n"
      case Invalid.Repeated(t, raw, at)         => s"${path(at)}$t '$raw' is repeated"
      case Invalid.Misplaced(field, under, at)  => s"${path(at)}$field belongs under $under"
      case Invalid.OutOfRange(t, raw, b, at)    =>
        s"${path(at)}$t '$raw' is out of [${b.least.mkString}, ${b.greatest.mkString}]"
      case Invalid.Inconsistent(fs, rule, at) => s"${path(at)}${fs.toVector.mkString(", ")} break ${rule.productPrefix}"
      case Invalid.Inverted(start, end, at)   => s"${path(at)}the period would start in $start after it ends in $end"
      case Invalid.OutOfTime(year, least, greatest, at)  => s"${path(at)}year $year is beyond [$least, $greatest]"
      case Invalid.Unparsable(format, where, damage, at) =>
        s"${path(at)}the ${format.productPrefix} input at $where is damaged: ${damage.stored}"
      case Invalid.Reference(form, at)            => s"${path(at)}no secret reference: ${form.stored}"
      case Invalid.Broken(rule, at)               => s"${path(at)}breaks ${rule.stored}"
      case NotFound.Entity(t, key)                => s"no $t '$key'"
      case NotFound.Listed(t, key, listing)       => s"no $t '$key' listed by ${listing.productPrefix}"
      case NotFound.Copy(work, answers)           => s"no open-access copy of '$work'${answeredStored(answers)}"
      case NotFound.Dangling(t, key, from, in)    => s"no $t '$key' in ${in.productPrefix}, referred to by $from"
      case Conflict.Held(subject, hold)           => s"held ${hold.stored}: $subject"
      case Conflict.Job(job, status, control)     => s"job '$job' is $status: ${control.stored}"
      case Conflict.AlreadyExists(t, key)         => s"$t '$key' already exists"
      case Conflict.Renamed(t, asked, now)        => s"$t '$asked' was renamed to '$now'"
      case Conflict.Aliased(t, key, of)           => s"$t '$key' is an old key of '$of'"
      case Conflict.OldKey(t, asked, now, action) => s"${action.productPrefix} by an old key: $t '$asked' is now '$now'"
      case Conflict.Collides(t, key, by)          => s"$t '$key' already exists; '$by' collides"
      case Conflict.Claimed(t, key, clash)        => s"$t '$key' is claimed: ${clash.stored}"
      case Conflict.Stale(t, key, sent, now)      => s"$t '$key' has moved on: sent $sent, stored $now"
      case Conflict.WrongState(t, key, is, ws)    => s"$t '$key' is $is; it needs ${ws.mkString(" or ")}"
      case Conflict.Busy(activity)                => s"${activity.productPrefix} is still running"
      case Conflict.Referenced(t, key, by, n)     => s"$t '$key' is referred to by $n $by"
      case Refused.Policy(rule)                   => s"refused: ${rule.productPrefix}"
      case Refused.Quota(limit, used, cap, at)    => s"${limit.productPrefix} is spent: $used of $cap${until(at)}"
      case Refused.Disabled(feature, setting)     => s"$feature is switched off by $setting"
      case Upstream.Http(name, url, st, body, at) => s"${path(at)}$name answered HTTP $st at $url — $body"
      case Upstream.Challenged(name, url, st, at) => s"${path(at)}$name challenged with HTTP $st at $url"
      case Upstream.Limited(name, at)             => s"$name is limited${until(at)}"
      case Upstream.Unreachable(name, url, r, at) => s"${path(at)}cannot reach $name at $url — $r"
      case Upstream.Timeout(name, url, after, at) => s"${path(at)}$name did not answer at $url within $after ms"
      case Upstream.BadReply(name, url, s, r, at) => s"${path(at)}$name's answer at $url is not ${s.productPrefix} — $r"
      case Io.File(op, path, reported)            => s"could not ${op.productPrefix} $path — $reported"
      case Io.Wrong(path, Io.Kind.Inside(dir))    => s"$path is outside $dir"
      case Io.Wrong(path, expected)               => s"$path is not ${expected.productPrefix}"
      case Io.Corrupt(format, at, damage)     => s"the ${format.productPrefix} data at $at is damaged: ${damage.stored}"
      case Io.Process(tool, ending)           => s"$tool ${ending.stored}"
      case Io.Database(op, reported)          => s"the database refused to ${op.productPrefix} — $reported"
      case Io.Entry(archive, entry, fault)    => s"${archive.productPrefix} entry $entry: ${fault.stored}"
      case Config.Setting(key, fault)         => s"$key is wrong: ${fault.stored}"
      case Config.Unavailable(capability)     => s"this server has no ${capability.productPrefix}"
      case Config.Reload(stage, scope)        => s"reloading the settings failed at ${stage.productPrefix} in $scope"
      case Config.Cycle(scopes)               => s"reload cycle: ${scopes.toVector.mkString(CycleArrow)}"
      case Config.Unpriced(model, metered)    => s"model $model is unpriced${metered.fold("")(m => s" (${m.stored})")}"
      case Plugin.Load(plugin, fault)         => s"plugin $plugin did not load: ${fault.stored}"
      case Plugin.Rejected(plugin, item, why) => s"$plugin did not take '$item': ${why.stored}"
      case Plugin.Vault(vault, failure)       => s"secret vault $vault failed: ${failure.stored}"
      case Plugin.Dependency(p, m, want, has) => s"$p needs $m $want; it ships $has"
      case Plugin.Said(plugin, claim, text)   => s"$plugin says (${claim.productPrefix}): $text"
      case Problem.Run.Paused(reason, at)     => s"paused for ${reason.productPrefix}${until(at)}"
      case Problem.Run.NoModel(role, tried)   => s"no model of the $role chain can run${passedOver(tried)}"
      case Problem.Run.Blocked(block)         => s"held back: ${block.stored}"
      case Problem.Run.Interrupted            => "interrupted"
      case Problem.Run.CutShort(activity)     => s"cut short: ${activity.productPrefix}"
      case Problem.Run.Unrecorded(activity)   => s"unrecorded: ${activity.productPrefix}"
      case Problem.Run.Cancelled(by)          => s"cancelled by ${by.productPrefix}"
      case Problem.Run.Draining(since)        => s"draining since ${since.iso}"
      case Problem.Run.Aborted(step, cause)   => s"step $step cannot go on: ${cause.productPrefix}"
      case Content.Finding(cause)             => s"finding: ${cause.productPrefix}"
      case Content.Proposal(rule, at)         => s"$at: ${rule.stored}"
      case Content.Answer(rule)               => s"the answer breaks ${rule.stored}"
      case Internal.Unexpected(id, where, e, r) => s"$where failed unexpectedly ($e, problem $id) — $r"
      case Unclassified(text)                   => s"unclassified: $text"
      case Stored.Unrecognised(text)            => s"$text"
      case Relayed(sentence)                    => s"$sentence"

  /** The problem a column's text stands for: the case whose frozen sentence it is, or the text as it was. Only the JVM
    * reads the sentences back ([[SentenceReaders]]): in the browser, which receives problems typed, every text is kept
    * as it was.
    */
  def read(stored: String): Problem =
    SentenceReaders
      .cases(stored)
      .find(_.stored == stored)
      .getOrElse(Stored.Unrecognised(Stored.Text(s"$stored")))

  // ---- the parts of a sentence --------------------------------------------------------------------------------

  private val PathSeparator = " / "

  private def path(at: Vector[Field]): String = if at.isEmpty then "" else s"${at.mkString(PathSeparator)}: "

  /** The chain's answers after a missing copy, each answer's problem in its frozen sentence. A text with answers reads
    * back as [[Problem.Stored.Unrecognised]]: the answers' problems are not parsed back.
    */
  private def answeredStored(answers: Vector[NotFound.Answer]): String =
    if answers.isEmpty then ""
    else
      answers
        .map(a => s"${a.fetcher}: ${a.kind.productPrefix} — ${a.why.stored}")
        .mkString(" — ", "; ", "")

  private def quotedList(values: Vector[Raw]): String =
    if values.isEmpty then "" else values.map(v => s"'$v'").mkString(" (one of: ", ", ", ")")

  private def quotedExamples(values: Vector[Raw]): String =
    if values.isEmpty then "" else values.map(v => s"'$v'").mkString(" (e.g. ", ", ", ")")

  extension (at: EpochMs) private def iso: String = s"${Instant.ofEpochMilli(s"$at".toLong)}"

  private def until(at: Option[EpochMs]): String = at.fold("")(t => s" until ${t.iso}")

  private def passedOver(tried: Vector[Problem.Run.Model]): String =
    if tried.isEmpty then "" else s" (passed over: ${tried.mkString(", ")})"

  private val CycleArrow = " → "

  private val HoldSeparator = " | "
  private val ListSeparator = ", "

  /** A rule's name, then what it holds, each after the hold separator; a list's items after the list separator. */
  extension (rule: Invalid.Rule)
    private def stored: String =
      val held: Vector[String] = rule match
        case Invalid.Rule.CronFields(stated, shortcuts)      => Vector(s"$stated", shortcuts.mkString(ListSeparator))
        case Invalid.Rule.CronField(stated, part, flaw)      => Vector(s"$stated", part.productPrefix) ++ flaw.held
        case Invalid.Rule.CronNeverFires(stated)             => Vector(s"$stated")
        case Invalid.Rule.CronTooOften(stated, gap, least)   => Vector(s"$stated", s"$gap", s"$least")
        case Invalid.Rule.ZoomWithin(zoom, greatest)         => Vector(s"$zoom", s"$greatest")
        case Invalid.Rule.TileWithin(x, y, zoom)             => Vector(s"$x", s"$y", s"$zoom")
        case Invalid.Rule.IdentifierKind(stated)             => Vector(s"$stated")
        case Invalid.Rule.ActivityPage(stated)               => Vector(s"$stated")
        case Invalid.Rule.ActivityPageTime(stated)           => Vector(s"$stated")
        case Invalid.Rule.EventCatalogue(stated, known)      => Vector(s"$stated", known.mkString(ListSeparator))
        case Invalid.Rule.CitekeyUnchanged(citekey)          => Vector(s"$citekey")
        case Invalid.Rule.CitekeyLinkShaped(citekey)         => Vector(s"$citekey")
        case Invalid.Rule.AssetBase64(asset, reported)       => Vector(s"$asset", s"$reported")
        case Invalid.Rule.AssetEmpty(asset)                  => Vector(s"$asset")
        case Invalid.Rule.AssetBytes(asset, bytes, limit)    => Vector(s"$asset", s"$bytes", s"$limit")
        case Invalid.Rule.RunsPipeline(run, p, first, fp)    => Vector(s"$run", s"$p", s"$first", s"$fp")
        case Invalid.Rule.RunsTarget(run, t, first, ft)      => Vector(s"$run", s"$t", s"$first", s"$ft")
        case Invalid.Rule.RegexTime(limit)                   => Vector(s"$limit")
        case Invalid.Rule.RegexValid(description, at, regex) =>
          Vector(s"$description", at.fold("")(p => s"$p"), s"$regex")
        case Invalid.Rule.MergeId(stated) => Vector(s"$stated")
        case Invalid.Rule.IdentifierGiven | Invalid.Rule.RunsCompared | Invalid.Rule.RunRepeated |
            Invalid.Rule.QueryGiven =>
          Vector.empty
      (rule.productPrefix +: held).mkString(HoldSeparator)

  extension (clash: Conflict.Clash)
    private def stored: String = clash match
      case Conflict.Clash.OldKeyOf(of) => s"${clash.productPrefix} '$of'"
      case other                       => other.productPrefix

  extension (flaw: Invalid.CronFlaw)
    private def held: Vector[String] = flaw match
      case Invalid.CronFlaw.NotNumber(stated, least, greatest) =>
        Vector("NotNumber", s"$stated", s"$least", s"$greatest")
      case Invalid.CronFlaw.Step(stated)      => Vector("Step", s"$stated")
      case Invalid.CronFlaw.Slashes(stated)   => Vector("Slashes", s"$stated")
      case Invalid.CronFlaw.Backwards(stated) => Vector("Backwards", s"$stated")
      case Invalid.CronFlaw.NotRange(stated)  => Vector("NotRange", s"$stated")

  extension (form: Invalid.Form)
    private def stored: String = form match
      case Invalid.Form.EnvName(name)   => s"EnvName '$name'"
      case Invalid.Form.NoEntry(vault)  => s"NoEntry '$vault'"
      case Invalid.Form.VaultName(name) => s"VaultName '$name'"
      case other                        => other.productPrefix

  extension (damage: Io.Damage)
    private def stored: String = damage match
      case Io.Damage.Length(expected, found) => s"Length $expected/$found"
      case Io.Damage.Unreadable(reported)    => s"Unreadable — $reported"
      case Io.Damage.Newer(found, reads)     => s"Newer $found/$reads"
      case Io.Damage.Schema(found, reads)    => s"Schema $found/$reads"
      case other                             => other.productPrefix

  extension (fault: Io.Fault)
    private def stored: String = fault match
      case Io.Fault.Ahead(first)                    => s"Ahead $first"
      case Io.Fault.Dangling(uses)                  => s"Dangling $uses"
      case Io.Fault.Differs(measure, listed, found) => s"Differs ${measure.productPrefix} $listed/$found"
      case other                                    => other.productPrefix

  extension (control: Conflict.Control)
    private def stored: String = control match
      case Conflict.Control.OnePiece(kind) => s"OnePiece $kind"
      case other                           => other.productPrefix

  extension (hold: Conflict.Hold)
    private def stored: String =
      val args: Vector[String] = hold match
        case Conflict.Hold.SameWork(here, basis)      => Vector(s"$here", s"$basis")
        case Conflict.Hold.Folds(gate, decided, into) => Vector(s"$gate", s"$decided", s"$into")
        case Conflict.Hold.Carries(decided)           => Vector(s"$decided")
        case Conflict.Hold.Conditional(on, when, now) => Vector(s"$on", s"$when") ++ now.map(n => s"$n")
        case Conflict.Hold.PlanChanged(sent)          => Vector(s"$sent")
        case Conflict.Hold.TextNotSent(decided)       => Vector(s"$decided")
        case Conflict.Hold.CitekeyOfAnother(citekey)  => Vector(s"$citekey")
        case Conflict.Hold.CitekeyTaken(citekey)      => Vector(s"$citekey")
        case _                                        => Vector.empty
      (hold.productPrefix +: (if args.isEmpty then Vector.empty else Vector(args.mkString(HoldSeparator))))
        .mkString(" ")

  extension (ending: Io.Ending)
    private def stored: String = ending match
      case Io.Ending.Exited(code, stderr) => s"exited with $code — $stderr"
      case Io.Ending.TimedOut(after)      => s"did not finish within $after ms"
      case Io.Ending.NotStarted(reported) => s"could not be started — $reported"

  extension (fault: Config.Fault)
    private def stored: String = fault match
      case Config.Fault.Unset(variable)  => s"Unset $variable"
      case Config.Fault.Malformed(raw)   => s"Malformed '$raw'"
      case Config.Fault.Unknown(raw)     => s"Unknown '$raw'"
      case Config.Fault.Conflicts(other) => s"Conflicts $other"
      case Config.Fault.WrongKind(e, f)  => s"WrongKind ${e.productPrefix}/${f.productPrefix}"
      case Config.Fault.Unreadable(r)    => s"Unreadable — $r"
      case Config.Fault.Credential(v)    => s"Credential${v.fold("")(n => s" $n")}"
      case Config.Fault.Included(file)   => s"Included $file"
      case Config.Fault.Rejected(u, st)  => s"Rejected $u/$st"
      case Config.Fault.KeyNeeded(needs) => s"KeyNeeded — $needs"
      case other                         => other.productPrefix

  extension (refusal: Plugin.Refusal)
    private def stored: String = refusal match
      case Plugin.Refusal.LicenceNotAllowed(family)  => s"LicenceNotAllowed $family"
      case Plugin.Refusal.NotOpen(field, stated)     => s"NotOpen $field is ${stated.mkString(ListSeparator)}"
      case Plugin.Refusal.NoOpenImage(field, stated) => s"NoOpenImage $field is ${stated.mkString(ListSeparator)}"
      case other                                     => other.productPrefix

  extension (fault: Plugin.Fault)
    private def stored: String = fault match
      case Plugin.Fault.Threw(reported)             => s"Threw — $reported"
      case Plugin.Fault.SpiMismatch(built, speaks)  => s"SpiMismatch $built/$speaks"
      case Plugin.Fault.SignerChanged(pinned, seen) => s"SignerChanged $pinned / $seen"
      case Plugin.Fault.Taken(by)                   => s"Taken $by"
      case Plugin.Fault.ForeignSpi(speaks)          => s"ForeignSpi $speaks"
      case Plugin.Fault.MissingClass(name)          => s"MissingClass $name"
      case other                                    => other.productPrefix

  extension (failure: Plugin.Retrieval)
    private def stored: String = failure match
      case Plugin.Retrieval.TimedOut(after)           => s"TimedOut $after ms"
      case Plugin.Retrieval.Failed(stderr)            => s"Failed — $stderr"
      case Plugin.Retrieval.Blank(ref)                => s"Blank '$ref'"
      case Plugin.Retrieval.Absent(ref)               => s"Absent '$ref'"
      case Plugin.Retrieval.Unread(ref, exit, stderr) => s"Unread '$ref' exit $exit — $stderr"
      case Plugin.Retrieval.NoKey(configured, seen)   =>
        s"NoKey ${configured.mkString(ListSeparator)} / ${seen.mkString(ListSeparator)}"
      case Plugin.Retrieval.Key(key, attempt, stderr) =>
        s"Key $key ${attempt.productPrefix}${stderr.fold("")(e => s" — $e")}"
      case other => other.productPrefix

  extension (metered: Config.Metered)
    private def stored: String = metered match
      case Config.Metered.ApiKey(provider) => s"ApiKey $provider"
      case Config.Metered.Opencode         => "Opencode"

  extension (block: Problem.Run.Block)
    private def stored: String = block match
      case Problem.Run.Block.PoolFull(pool) => s"PoolFull $pool"
      case other                            => other.productPrefix

  extension (rule: Content.Requirement)
    private def stored: String = rule match
      case Content.Requirement.TooManyClaims(cap, gave)            => s"TooManyClaims $cap/$gave"
      case Content.Requirement.TooManyArticles(cap, gave)          => s"TooManyArticles $cap/$gave"
      case Content.Requirement.TooManySubGoals(cap, gave)          => s"TooManySubGoals $cap/$gave"
      case Content.Requirement.TooManyQueries(cap, gave)           => s"TooManyQueries $cap/$gave"
      case Content.Requirement.TooManyWorks(cap, gave)             => s"TooManyWorks $cap/$gave"
      case Content.Requirement.KnownHeading(section)               => s"KnownHeading '$section'"
      case Content.Requirement.ParagraphCited(opening)             => s"ParagraphCited '$opening'"
      case Content.Requirement.StillUncited(opening)               => s"StillUncited '$opening'"
      case Content.Requirement.SectionPresent(alts)                => s"SectionPresent ${alts.mkString(ListSeparator)}"
      case Content.Requirement.NoFiller(matches)                   => s"NoFiller '$matches'"
      case Content.Requirement.ClaimTabled(citekey)                => s"ClaimTabled '$citekey'"
      case Content.Requirement.ReasonForTabled(citekey)            => s"ReasonForTabled '$citekey'"
      case Content.Requirement.ReasonForUncited(citekey)           => s"ReasonForUncited '$citekey'"
      case Content.Requirement.ReasonText(citekey)                 => s"ReasonText '$citekey'"
      case Content.Requirement.TabledAccounted(citekey)            => s"TabledAccounted '$citekey'"
      case Content.Requirement.FormatClean(rule, message, excerpt) =>
        s"FormatClean '${Vector(rule, excerpt, message).mkString("', '")}'"
      case Content.Requirement.SubGoalTitle(slug)         => s"SubGoalTitle '$slug'"
      case Content.Requirement.QueryWithin(query, most)   => s"QueryWithin '$query' in '$most'"
      case Content.Requirement.DoiGiven(stated)           => s"DoiGiven '$stated'"
      case Content.Requirement.QuoteInRecords(records)    => s"QuoteInRecords ${records.mkString(ListSeparator)}"
      case Content.Requirement.KnownCitekey(stated)       => s"KnownCitekey '$stated'"
      case Content.Requirement.ListedCandidate(stated)    => s"ListedCandidate '$stated'"
      case Content.Requirement.PositiveBce(stated)        => s"PositiveBce '$stated'"
      case Content.Requirement.CeFromOne(stated)          => s"CeFromOne '$stated'"
      case Content.Requirement.Calibrated(stated)         => s"Calibrated '$stated'"
      case Content.Requirement.StartBeforeEnd(start, end) => s"StartBeforeEnd '$start'..'$end'"
      case Content.Requirement.YearWithin(greatest)       => s"YearWithin '$greatest'"
      case Content.Requirement.WithinSourcePeriod(start, end, ownStart, ownEnd) =>
        s"WithinSourcePeriod '$start'..'$end' in '$ownStart'..'$ownEnd'"
      case Content.Requirement.LinkTarget(target)                  => s"LinkTarget '$target'"
      case Content.Requirement.KnownItems(unknown)                 => s"KnownItems ${unknown.mkString(ListSeparator)}"
      case Content.Requirement.DateGiven(stated)                   => s"DateGiven '$stated'"
      case Content.Requirement.PriorityWithin(stated, max)         => s"PriorityWithin '$stated' in '$max'"
      case Content.Requirement.FilterTooNarrow(line, total, least) => s"FilterTooNarrow $total/$least '$line'"
      case Content.Requirement.QuoteInWindow(best)                 => s"QuoteInWindow $best"
      case Content.Requirement.CenturyAsYear(year, century)        => s"CenturyAsYear $century '$year'"
      case Content.Requirement.PointerPhrase(phrase)               => s"PointerPhrase '$phrase'"
      case Content.Requirement.QuoteInRecord(citekey)              => s"QuoteInRecord '$citekey'"
      case Content.Requirement.StatusUnstated(citekey, status)     => s"StatusUnstated '$citekey' in '$status'"
      case Content.Requirement.PlacesResolve(resolved, total, least, missing) =>
        s"PlacesResolve $resolved/$total $least ${missing.map(u => s"${u.finding}:${u.place}").mkString(HoldSeparator)}"
      case Content.Requirement.FilterTooBroad(line, total, most) => s"FilterTooBroad $total/$most '$line'"
      case Content.Requirement.FilterMisses(line, returned, cited, share, least, missed) =>
        s"FilterMisses $returned/$cited $share/$least '$line' ${missed.mkString(ListSeparator)}"
      case other => other.productPrefix

  /** The readers of the frozen sentences, one per sentence, with what only reading needs. An object of its own, so a
    * platform that reads no column ([[SentenceReaders]]) links none of it.
    */
  private[problem] object Reading:

    /** The cases a column's text may stand for: each reader's answer, in order. */
    def cases(stored: String): Iterator[Problem] = Readers.iterator.flatMap(reader => reader(stored))

    private def pathOf(written: String | Null): Vector[Field] =
      Option(written).fold(Vector.empty)(_.split(PathSeparator, -1).toVector.map(f => Field(s"$f")))

    private def quotedListOf(written: String | Null): Vector[Raw] =
      Option(written).fold(Vector.empty)(_.split("', '", -1).toVector.map(v => Raw(s"$v")))

    private def momentOf(iso: String): Option[EpochMs] = Try(Instant.parse(iso).toEpochMilli).toOption.map(EpochMs(_))

    private def untilOf(written: String | Null): Option[Option[EpochMs]] =
      Option(written).fold(Some(None))(momentOf(_).map(Some(_)))

    private def raws(written: String): Vector[Raw] =
      if written.isEmpty then Vector.empty else written.split(ListSeparator, -1).toVector.map(r => Raw(s"$r"))

    /** The case of an enum of code words named `name`. */
    private def word[E <: Product](values: Seq[E], name: String | Null): Option[E] =
      Option(name).flatMap(n => values.find(_.productPrefix == n))

    private val Damages = Vector(Io.Damage.Checksum, Io.Damage.Truncated, Io.Damage.Undecodable)
    private val Kinds = Vector(
      Io.Kind.File,
      Io.Kind.Directory,
      Io.Kind.Present,
      Io.Kind.Absent,
      Io.Kind.Executable,
      Io.Kind.Listed,
      Io.Kind.OnPath
    )
    private val Faults = Vector(
      Config.Fault.Empty,
      Config.Fault.NoSecret,
      Config.Fault.Block,
      Config.Fault.Undeclared,
      Config.Fault.NotInDefaults,
      Config.Fault.OutsideRoot,
      Config.Fault.Credential(None),
      Config.Fault.Literal,
      Config.Fault.Substituted,
      Config.Fault.NotReadBack
    )
    private val Forms = Vector(Invalid.Form.Bare, Invalid.Form.EmptyValue)
    private val EntryFaults = Vector(
      Io.Fault.Missing,
      Io.Fault.Omitted,
      Io.Fault.Unlisted,
      Io.Fault.Repeated,
      Io.Fault.Foreign,
      Io.Fault.Unreferenced
    )
    private val Holds = Vector(
      Conflict.Hold.Open,
      Conflict.Hold.Merges,
      Conflict.Hold.Renames,
      Conflict.Hold.Renumbers,
      Conflict.Hold.Stale,
      Conflict.Hold.Collides
    )
    private val LoadFaults = Vector(
      Plugin.Fault.Missing,
      Plugin.Fault.NullComponent,
      Plugin.Fault.BadDescriptor,
      Plugin.Fault.VersionMismatch,
      Plugin.Fault.Duplicate,
      Plugin.Fault.NoProvider
    )
    private val Refusals = Vector(
      Plugin.Refusal.NoLicence,
      Plugin.Refusal.NoImage,
      Plugin.Refusal.NoName,
      Plugin.Refusal.NoLocation,
      Plugin.Refusal.NoDate,
      Plugin.Refusal.Duplicate,
      Plugin.Refusal.Restricted
    )
    private val VaultFailures =
      Vector(Plugin.Retrieval.NoVault, Plugin.Retrieval.NoEntry, Plugin.Retrieval.EmptyValue, Plugin.Retrieval.Locked)
    private val Blocks = Vector(
      Problem.Run.Block.SpendCap,
      Problem.Run.Block.Draining,
      Problem.Run.Block.OutsideWindow,
      Problem.Run.Block.Waiting
    )
    private val ProposalRules = Vector(
      Content.Requirement.NoYearZero,
      Content.Requirement.CitesNoSource,
      Content.Requirement.UnknownCitekey,
      Content.Requirement.OutsidePeriod,
      Content.Requirement.StandIn,
      Content.Requirement.ExactTitle,
      Content.Requirement.ReplaceText,
      Content.Requirement.AnchorInArticle,
      Content.Requirement.QuoteGiven,
      Content.Requirement.EvidenceGiven,
      Content.Requirement.StatusNamed,
      Content.Requirement.NotUnassessed,
      Content.Requirement.EdgeTarget,
      Content.Requirement.NoSelfEdge,
      Content.Requirement.UnescapedLinks,
      Content.Requirement.SettingsGiven,
      Content.Requirement.ItemsNamed,
      Content.Requirement.HoldUntil,
      Content.Requirement.ExistingItems,
      Content.Requirement.HistoricalReckoning,
      Content.Requirement.FilterSelects,
      Content.Requirement.FilterRationale,
      Content.Requirement.PlanRationale,
      Content.Requirement.ArticleTitle,
      Content.Requirement.ArticleFilter,
      Content.Requirement.ArticlesGiven,
      Content.Requirement.SummaryGiven,
      Content.Requirement.StatusEvidence,
      Content.Requirement.NotesGiven,
      Content.Requirement.ClaimGiven,
      Content.Requirement.NoClaim,
      Content.Requirement.ReasonGiven,
      Content.Requirement.TextGiven,
      Content.Requirement.NoSourcesCited,
      Content.Requirement.TitleGiven,
      Content.Requirement.ArticleSummary,
      Content.Requirement.NotUsedGenerated,
      Content.Requirement.MethodGenerated,
      Content.Requirement.NotUsedHandWritten,
      Content.Requirement.MethodHandWritten,
      Content.Requirement.BodyGiven,
      Content.Requirement.QueryGiven,
      Content.Requirement.PlanGiven,
      Content.Requirement.GoalPlanRationale
    )

    /** The rule a column wrote, from its name and what it held. */
    private def ruleOf(written: Vector[String]): Option[Invalid.Rule] =
      import Invalid.Rule.*
      def flawOf(held: Vector[String]): Option[Invalid.CronFlaw] = held match
        case Vector("NotNumber", s, l, g) =>
          for
            least <- Count.read(l)
            greatest <- Count.read(g)
          yield Invalid.CronFlaw.NotNumber(Raw(s), least, greatest)
        case Vector("Step", s)      => Some(Invalid.CronFlaw.Step(Raw(s)))
        case Vector("Slashes", s)   => Some(Invalid.CronFlaw.Slashes(Raw(s)))
        case Vector("Backwards", s) => Some(Invalid.CronFlaw.Backwards(Raw(s)))
        case Vector("NotRange", s)  => Some(Invalid.CronFlaw.NotRange(Raw(s)))
        case _                      => None
      written match
        case Vector("CronFields", s, eg)   => Some(CronFields(Raw(s), raws(eg)))
        case "CronField" +: s +: p +: flaw =>
          for
            part <- word(Invalid.CronPart.values.toSeq, p)
            f <- flawOf(flaw)
          yield CronField(Raw(s), part, f)
        case Vector("CronNeverFires", s)     => Some(CronNeverFires(Raw(s)))
        case Vector("CronTooOften", s, g, l) =>
          for
            gap <- Count.read(g)
            least <- Count.read(l)
          yield CronTooOften(Raw(s), gap, least)
        case Vector("ZoomWithin", z, g) =>
          for
            zoom <- Count.read(z)
            greatest <- Count.read(g)
          yield ZoomWithin(zoom, greatest)
        case Vector("TileWithin", x, y, z) =>
          for
            cx <- Count.read(x)
            cy <- Count.read(y)
            zoom <- Count.read(z)
          yield TileWithin(cx, cy, zoom)
        case Vector("IdentifierGiven")      => Some(IdentifierGiven)
        case Vector("IdentifierKind", s)    => Some(IdentifierKind(Raw(s)))
        case Vector("MergeId", s)           => Some(MergeId(Raw(s)))
        case Vector("ActivityPage", s)      => Some(ActivityPage(Raw(s)))
        case Vector("ActivityPageTime", s)  => Some(ActivityPageTime(Raw(s)))
        case Vector("EventCatalogue", s, k) => Some(EventCatalogue(Raw(s), raws(k)))
        case Vector("CitekeyUnchanged", c)  => Some(CitekeyUnchanged(Raw(c)))
        case Vector("CitekeyLinkShaped", c) => Some(CitekeyLinkShaped(Raw(c)))
        case Vector("AssetBase64", a, r)    => Some(AssetBase64(Raw(a), External.Reported.of(r)))
        case Vector("AssetEmpty", a)        => Some(AssetEmpty(Raw(a)))
        case Vector("AssetBytes", a, b, l)  =>
          for
            bytes <- Count.read(b)
            limit <- Count.read(l)
          yield AssetBytes(Raw(a), bytes, limit)
        case Vector("RunsCompared")                             => Some(RunsCompared)
        case Vector("RunRepeated")                              => Some(RunRepeated)
        case Vector("RunsPipeline", r, p, f, o)                 => Some(RunsPipeline(Raw(r), Raw(p), Raw(f), Raw(o)))
        case Vector("RunsTarget", r, t, f, o)                   => Some(RunsTarget(Raw(r), Raw(t), Raw(f), Raw(o)))
        case Vector("QueryGiven")                               => Some(QueryGiven)
        case Vector("RegexTime", l)                             => Millis.read(l).map(RegexTime(_))
        case "RegexValid" +: d +: at +: regex if regex.nonEmpty =>
          // the pattern is written last, so a ` | ` in it (an alternation) reads back as the pattern's own
          val position = if at.isEmpty then Some(None) else Count.read(at).map(Some(_))
          position.map(RegexValid(External.Reported.of(d), _, Raw(regex.mkString(HoldSeparator))))
        case _ => None

    private val Controls = Vector(
      Conflict.Control.Unrecognised,
      Conflict.Control.Ended,
      Conflict.Control.AlreadyPaused,
      Conflict.Control.NotStarted,
      Conflict.Control.PausedBeforeRestart,
      Conflict.Control.NotPaused
    )

    // ---- the readers: one per frozen sentence; a reader's case counts only if it writes the text back -------------

    private val Prefix = "(?:(.+?): )?"

    private val MalformedAt = s"(?s)^$Prefix'(.*)' is no (\\S+): it fails (.+) \\((.*)\\)$$".r
    private val UnknownAt = s"(?s)^${Prefix}unknown (\\S+) '(.*?)'(?: \\(one of: '(.*)'\\))?$$".r
    private val UnreadableAt = s"(?s)^$Prefix'(.*?)' is unreadable as (\\S+)(?: \\(e\\.g\\. '(.*)'\\))?$$".r
    private val MisplacedAt = s"(?s)^$Prefix(\\S+) belongs under (\\S+)$$".r
    private val RepeatedAt = s"(?s)^$Prefix(\\S+) '(.*)' is repeated$$".r
    private val AmbiguousAt = s"(?s)^$Prefix(\\S+) '(.*)' matches (\\d+)$$".r
    private val MissingAt = s"(?s)^$Prefix(\\S+) is required$$".r
    private val UndeclaredAt = s"(?s)^$Prefix(\\S+) is undeclared(?: \\(one of: '(.*)'\\))?$$".r
    private val WrappedAt = s"(?s)^$Prefix'(.*)' wraps a (\\S+) in (\\S+)$$".r
    private val StoredOnAt = s"(?s)^$Prefix(\\S+) (.+) is stored on '(.*)'$$".r
    private val OutOfRangeAt = s"(?s)^$Prefix(\\S+) '(.*)' is out of \\[(.*?), (.*?)\\]$$".r
    private val InconsistentAt = s"(?s)^$Prefix(.+) break (\\w+)$$".r
    private val ReferenceAt = s"(?s)^${Prefix}no secret reference: (\\w+)(?: '(.*)')?$$".r
    private val NoEntity = "(?s)^no (\\S+) '(.*)'$".r
    private val NoListed = "(?s)^no (\\S+) '(.*)' listed by (\\w+)$".r
    private val InvertedAt = s"(?s)^${Prefix}the period would start in (-?\\d+) after it ends in (-?\\d+)$$".r
    private val BeyondAt = s"(?s)^${Prefix}year (-?\\d+) is beyond \\[(-?\\d+), (-?\\d+)\\]$$".r
    private val UnparsableAt =
      s"(?s)^${Prefix}the (\\w+) input at (.+?) is damaged: (\\w+)(?: (\\d+)/(\\d+)| — (.*))?$$".r
    private val ByOldKey = "(?s)^(\\w+) by an old key: (\\S+) '(.*)' is now '(.*)'$".r
    private val CollidesWith = "(?s)^(\\S+) '(.*)' already exists; '(.*)' collides$".r
    private val ClaimedBy = "(?s)^(\\S+) '(.*)' is claimed: (\\w+)(?: '(.*)')?$".r
    private val DanglingIn = "(?s)^no (\\S+) '(.*)' in (\\w+), referred to by (.*)$".r
    private val HeldBy = "(?s)^held (\\w+)(?: (.*?))?: (.*)$".r
    private val JobControl = "(?s)^job '(.*)' is (\\S*): (\\w+)(?: (.*))?$".r
    private val EntryOf = "(?s)^(\\w+) entry (.+?): (\\w+)(?: (.*))?$".r
    private val Differing = "(?s)^(\\w+) (\\S*)/(\\S*)$".r
    private val NoCopy = "(?s)^no open-access copy of '([^']*)'$".r
    private val Exists = "(?s)^(\\S+) '(.*)' already exists$".r
    private val RenamedTo = "(?s)^(\\S+) '(.*)' was renamed to '(.*)'$".r
    private val OldKey = "(?s)^(\\S+) '(.*)' is an old key of '(.*)'$".r
    private val MovedOn = "(?s)^(\\S+) '(.*)' has moved on: sent (.*), stored (.*)$".r
    private val InState = "(?s)^(\\S+) '(.*)' is (\\S+); it needs (.*)$".r
    private val Running = "^(\\w+) is still running$".r
    private val ReferredTo = "(?s)^(\\S+) '(.*)' is referred to by (\\d+) (\\S+)$".r
    private val RefusedBy = "^refused: (\\w+)$".r
    private val Spent = "^(\\w+) is spent: (\\S+) of (\\S+?)(?: until (\\S+))?$".r
    private val SwitchedOff = "(?s)^(.+) is switched off by (\\S+)$".r
    private val Answered = s"(?s)^$Prefix(\\S+) answered HTTP (\\d+) at (\\S+) — (.*)$$".r
    private val ChallengedAt = s"(?s)^$Prefix(\\S+) challenged with HTTP (\\d+) at (\\S+)$$".r
    private val LimitedTill = "^(\\S+) is limited(?: until (\\S+))?$".r
    private val CannotReach = s"(?s)^${Prefix}cannot reach (\\S+) at (\\S+) — (.*)$$".r
    private val NoAnswer = s"(?s)^$Prefix(\\S+) did not answer at (\\S+) within (\\d+) ms$$".r
    private val NotShaped = s"(?s)^$Prefix(\\S+)'s answer at (\\S+) is not (\\w+) — (.*)$$".r
    private val CouldNot = "(?s)^could not (\\w+) (.+?) — (.*)$".r
    private val IsNot = "(?s)^(.+) is not (\\w+)$".r
    private val IsOutside = "(?s)^(.+) is outside (.+)$".r
    private val Damaged = "(?s)^the (\\w+) data at (.+?) is damaged: (\\w+)(?: (\\d+)/(\\d+)| — (.*))?$".r
    private val Exited = "(?s)^(.+?) exited with (-?\\d+) — (.*)$".r
    private val Unfinished = "(?s)^(.+?) did not finish within (\\d+) ms$".r
    private val NotStarted = "(?s)^(.+?) could not be started — (.*)$".r
    private val DatabaseRefused = "(?s)^the database refused to (\\w+) — (.*)$".r
    private val WrongSetting = "(?s)^(\\S+) is wrong: (\\w+)(?: (.*))?$".r
    private val NoCapability = "^this server has no (\\w+)$".r
    private val ReloadFailed = "(?s)^reloading the settings failed at (\\w+) in (.*)$".r
    private val ReloadCycle = "(?s)^reload cycle: (.+)$".r
    private val NotLoaded = "(?s)^plugin (\\S+) did not load: (\\w+)(?: (.*))?$".r
    private val Needs = "(?s)^(\\S+) needs (\\S+) (\\S+); it ships (\\S+)$".r
    private val Pair = "^(\\S+)/(\\S+)$".r
    private val Quoted = "(?s)^'(.*)'$".r
    private val UnreadAt = "(?s)^'(.*)' exit (-?\\d+) — (.*)$".r
    private val KeyTried = "(?s)^(\\S+) (\\w+)(?: — (.*))?$".r
    private val CenturyOf = "(?s)^(\\d+) '(.*)'$".r
    private val PlacesOf = "(?s)^(\\d+)/(\\d+) (\\d+) (.*)$".r
    private val UnplacedOf = "(?s)^(\\d+):(.*)$".r
    private val CountedLine = "(?s)^(\\d+)/(\\d+) '(.*)'$".r
    private val MissedLine = "(?s)^(\\d+)/(\\d+) (\\d+)/(\\d+) '(.*)' (.*)$".r
    private val NotTaken = "(?s)^(\\S+) did not take '(.*)': (\\w+)(?: (.*))?$".r
    private val FieldStates = "(?s)^(\\S+) is (.*)$".r
    private val VaultFailed = "(?s)^secret vault (\\S+) failed: (\\w+)(?: (.*))?$".r
    private val Says = "(?s)^(\\S+) says \\((\\w+)\\): (.*)$".r
    private val PausedFor = "^paused for (\\w+)(?: until (\\S+))?$".r
    private val NoModelFor = "(?s)^no model of the (\\S+) chain can run(?: \\(passed over: (.*)\\))?$".r
    private val HeldBack = "(?s)^held back: (\\w+)(?: (.*))?$".r
    private val CancelledBy = "^cancelled by (\\w+)$".r
    private val DrainingSince = "^draining since (\\S+)$".r
    private val CannotGoOn = "(?s)^step (.+) cannot go on: (\\w+)$".r
    private val FindingOf = "^finding: (\\w+)$".r
    private val AnswerBreaks = "(?s)^the answer breaks (.*)$".r
    private val ProposalAt = "(?s)^(.+): (\\w+)(?: (\\d+)/(\\d+))?$".r

    /** The requirements stored with what was written after their name: the place is read up to the first of them, as
      * what follows is free text (a filter's line, a quote) that may hold a colon.
      */
    private val WrittenRules = Vector(
      "QuoteInRecords",
      "KnownCitekey",
      "ListedCandidate",
      "PositiveBce",
      "CeFromOne",
      "Calibrated",
      "LinkTarget",
      "StartBeforeEnd",
      "YearWithin",
      "WithinSourcePeriod",
      "KnownItems",
      "DateGiven",
      "PriorityWithin",
      "FilterTooNarrow",
      "FilterTooBroad",
      "FilterMisses",
      "QuoteInWindow",
      "CenturyAsYear",
      "PointerPhrase",
      "QuoteInRecord",
      "StatusUnstated",
      "PlacesResolve",
      "KnownHeading",
      "ParagraphCited",
      "StillUncited",
      "SectionPresent",
      "NoFiller",
      "ClaimTabled",
      "ReasonForTabled",
      "ReasonForUncited",
      "ReasonText",
      "TabledAccounted",
      "FormatClean",
      "SubGoalTitle",
      "QueryWithin",
      "DoiGiven"
    )
    private val ProposalWith = s"(?s)^(.+?): (${WrittenRules.mkString("|")}) (.*)$$".r
    private val OneQuoted = "(?s)^'(.*)'$".r
    private val TwoQuoted = "(?s)^'(.*)'(\\.\\.| in )'(.*)'$".r
    private val FourQuoted = "(?s)^'(.*)'\\.\\.'(.*)' in '(.*)'\\.\\.'(.*)'$".r
    private val UnpricedModel = "(?s)^model (\\S+) is unpriced(?: \\((\\w+)(?: (\\S+))?\\))?$".r
    private val CutShortBy = "^cut short: (\\w+)$".r
    private val UnrecordedBy = "^unrecorded: (\\w+)$".r
    private val Crashed = "(?s)^(.+) failed unexpectedly \\((\\S+), problem (\\S+)\\) — (.*)$".r
    private val UnclassifiedText = "(?s)^unclassified: (.*)$".r
    private val BrokenAt = s"(?s)^${Prefix}breaks (.*)$$".r

    /** A damage as [[Io.Damage.stored]] wrote it: its name, then two counts or what the reader reported. */
    private def damageOf(damage: String, expected: String, found: String, reported: String): Option[Io.Damage] =
      (Option(expected), Option(found), Option(reported)) match
        case (Some(e), Some(f), None) if damage == "Length" =>
          for
            ec <- Count.read(e)
            fc <- Count.read(f)
          yield Io.Damage.Length(ec, fc)
        case (Some(e), Some(f), None) if damage == "Newer"  => Some(Io.Damage.Newer(Raw(s"$e"), Raw(s"$f")))
        case (Some(e), Some(f), None) if damage == "Schema" =>
          for
            ec <- Count.read(e)
            fc <- Count.read(f)
          yield Io.Damage.Schema(ec, fc)
        case (None, None, Some(r)) if damage == "Unreadable" => Some(Io.Damage.Unreadable(External.Reported(s"$r")))
        case (None, None, None)                              => word(Damages, damage)
        case _                                               => None

    private val Readers: List[String => Option[Problem]] = List(
      {
        case BrokenAt(at, written) =>
          ruleOf(written.split(Regex.quote(HoldSeparator), -1).toVector).map(Invalid.Broken(_, pathOf(at)))
        case _ => None
      },
      {
        case MalformedAt(at, raw, t, name, description) =>
          Some(
            Invalid.Malformed(
              TypeName(s"$t"),
              Constraint(Constraint.Name(s"$name"), Constraint.Description(s"$description")),
              Raw(s"$raw"),
              pathOf(at)
            )
          )
        case _ => None
      },
      {
        case UnknownAt(at, t, raw, known) =>
          Some(Invalid.Unknown(TypeName(s"$t"), Raw(s"$raw"), quotedListOf(known), pathOf(at)))
        case _ => None
      },
      {
        case UnreadableAt(at, raw, t, eg) =>
          Some(Invalid.Unreadable(TypeName(s"$t"), Raw(s"$raw"), quotedListOf(eg), pathOf(at)))
        case _ => None
      },
      {
        case MisplacedAt(at, field, under) => Some(Invalid.Misplaced(Field(s"$field"), Field(s"$under"), pathOf(at)))
        case _                             => None
      },
      {
        case RepeatedAt(at, t, raw) => Some(Invalid.Repeated(TypeName(s"$t"), Raw(s"$raw"), pathOf(at)))
        case _                      => None
      },
      {
        case AmbiguousAt(at, t, raw, n) =>
          Count.read(n).map(count => Invalid.Ambiguous(TypeName(s"$t"), Raw(s"$raw"), count, pathOf(at)))
        case _ => None
      },
      {
        case MissingAt(at, field) => Some(Invalid.Missing(Field(s"$field"), pathOf(at)))
        case _                    => None
      },
      {
        case UndeclaredAt(at, field, declared) =>
          Some(Invalid.Undeclared(Field(s"$field"), quotedListOf(declared).map(d => Field(s"$d")), pathOf(at)))
        case _ => None
      },
      {
        case WrappedAt(at, raw, t, member) =>
          Some(Invalid.Wrapped(TypeName(s"$t"), Raw(s"$raw"), Field(s"$member"), pathOf(at)))
        case _ => None
      },
      {
        case StoredOnAt(at, t, item, owner) =>
          Some(Invalid.StoredOn(TypeName(s"$t"), Raw(s"$item"), Raw(s"$owner"), pathOf(at)))
        case _ => None
      },
      {
        case OutOfRangeAt(at, t, raw, least, greatest) =>
          val bound = (b: String) => Option(b).filter(_.nonEmpty).map(v => Raw(s"$v"))
          Some(
            Invalid.OutOfRange(TypeName(s"$t"), Raw(s"$raw"), Invalid.Bounds(bound(least), bound(greatest)), pathOf(at))
          )
        case _ => None
      },
      {
        case InconsistentAt(at, fields, rule) =>
          for
            fs <- NonEmptyVector.from(fields.split(", ", -1).toVector.map(f => Field(s"$f")))
            r <- word(Invalid.Exclusion.values.toSeq, rule)
          yield Invalid.Inconsistent(fs, r, pathOf(at))
        case _ => None
      },
      {
        case ReferenceAt(at, form, raw) =>
          val read: Option[Invalid.Form] = (form, Option(raw)) match
            case ("EnvName", Some(r))   => Some(Invalid.Form.EnvName(Raw(s"$r")))
            case ("NoEntry", Some(r))   => Some(Invalid.Form.NoEntry(Raw(s"$r")))
            case ("VaultName", Some(r)) => Some(Invalid.Form.VaultName(Raw(s"$r")))
            case (_, None)              => word(Forms, form)
            case _                      => None
          read.map(Invalid.Reference(_, pathOf(at)))
        case _ => None
      },
      {
        case DanglingIn(t, key, in, from) =>
          word(NotFound.Scope.values.toSeq, in)
            .map(NotFound.Dangling(TypeName(s"$t"), Raw(s"$key"), Where(s"$from"), _))
        case _ => None
      },
      {
        case HeldBy(name, written, subject) =>
          val args = Option(written).fold(Vector.empty[Raw])(_.split(" \\| ", -1).toVector.map(a => Raw(s"$a")))
          val hold: Option[Conflict.Hold] = (name, args) match
            case ("SameWork", Vector(here, basis))      => Some(Conflict.Hold.SameWork(here, basis))
            case ("Folds", Vector(gate, decided, into)) => Some(Conflict.Hold.Folds(gate, decided, into))
            case ("Carries", Vector(decided))           => Some(Conflict.Hold.Carries(decided))
            case ("Conditional", Vector(on, when))      => Some(Conflict.Hold.Conditional(on, when, None))
            case ("Conditional", Vector(on, when, now)) => Some(Conflict.Hold.Conditional(on, when, Some(now)))
            case ("PlanChanged", Vector(sent))          => Some(Conflict.Hold.PlanChanged(sent))
            case ("TextNotSent", Vector(decided))       => Some(Conflict.Hold.TextNotSent(decided))
            case ("CitekeyOfAnother", Vector(citekey))  => Some(Conflict.Hold.CitekeyOfAnother(citekey))
            case ("CitekeyTaken", Vector(citekey))      => Some(Conflict.Hold.CitekeyTaken(citekey))
            case (other, Vector())                      => word(Holds, other)
            case _                                      => None
          hold.map(Conflict.Held(Where(s"$subject"), _))
        case _ => None
      },
      {
        case EntryOf(archive, entry, name, written) =>
          val fault: Option[Io.Fault] = (name, Option(written)) match
            case ("Ahead", Some(first))                               => Some(Io.Fault.Ahead(Io.Path(s"$first")))
            case ("Dangling", Some(n))                                => Count.read(n).map(Io.Fault.Dangling(_))
            case ("Differs", Some(Differing(measure, listed, found))) =>
              word(Io.Measure.values.toSeq, measure).map(Io.Fault.Differs(_, Raw(s"$listed"), Raw(s"$found")))
            case (other, None) => word(EntryFaults, other)
            case _             => None
          for
            a <- word(Io.Format.values.toSeq, archive)
            f <- fault
          yield Io.Entry(a, Io.Path(s"$entry"), f)
        case _ => None
      },
      {
        case NoEntity(t, key) => Some(NotFound.Entity(TypeName(s"$t"), Raw(s"$key")))
        case _                => None
      },
      {
        case NoListed(t, key, listing) =>
          word(NotFound.Listing.values.toSeq, listing).map(NotFound.Listed(TypeName(s"$t"), Raw(s"$key"), _))
        case _ => None
      },
      {
        case InvertedAt(at, start, end) =>
          for
            s <- Invalid.Year.read(start)
            e <- Invalid.Year.read(end)
          yield Invalid.Inverted(s, e, pathOf(at))
        case _ => None
      },
      {
        case BeyondAt(at, year, least, greatest) =>
          for
            y <- Invalid.Year.read(year)
            l <- Invalid.Year.read(least)
            g <- Invalid.Year.read(greatest)
          yield Invalid.OutOfTime(y, l, g, pathOf(at))
        case _ => None
      },
      {
        case UnparsableAt(at, format, where, damage, expected, found, reported) =>
          for
            f <- word(Io.Format.values.toSeq, format)
            d <- damageOf(damage, expected, found, reported)
          yield Invalid.Unparsable(f, Where(s"$where"), d, pathOf(at))
        case _ => None
      },
      {
        case ByOldKey(action, t, asked, now) =>
          word(Conflict.Action.values.toSeq, action)
            .map(Conflict.OldKey(TypeName(s"$t"), Raw(s"$asked"), Raw(s"$now"), _))
        case _ => None
      },
      {
        case CollidesWith(t, key, by) => Some(Conflict.Collides(TypeName(s"$t"), Raw(s"$key"), Raw(s"$by")))
        case _                        => None
      },
      {
        case ClaimedBy(t, key, clash, of) =>
          val read: Option[Conflict.Clash] = (clash, Option(of)) match
            case ("OldKeyOf", Some(o)) => Some(Conflict.Clash.OldKeyOf(Raw(s"$o")))
            case ("Taken", None)       => Some(Conflict.Clash.Taken)
            case ("Removed", None)     => Some(Conflict.Clash.Removed)
            case _                     => None
          read.map(Conflict.Claimed(TypeName(s"$t"), Raw(s"$key"), _))
        case _ => None
      },
      {
        case NoCopy(work) => Some(NotFound.Copy(Raw(s"$work"), Vector.empty))
        case _            => None
      },
      {
        case Exists(t, key) => Some(Conflict.AlreadyExists(TypeName(s"$t"), Raw(s"$key")))
        case _              => None
      },
      {
        case RenamedTo(t, asked, now) => Some(Conflict.Renamed(TypeName(s"$t"), Raw(s"$asked"), Raw(s"$now")))
        case _                        => None
      },
      {
        case OldKey(t, key, of) => Some(Conflict.Aliased(TypeName(s"$t"), Raw(s"$key"), Raw(s"$of")))
        case _                  => None
      },
      {
        case MovedOn(t, key, sent, now) =>
          Some(Conflict.Stale(TypeName(s"$t"), Raw(s"$key"), Conflict.Stamp(s"$sent"), Conflict.Stamp(s"$now")))
        case _ => None
      },
      {
        case InState(t, key, is, wanted) =>
          val states =
            if wanted.isEmpty then Vector.empty else wanted.split(" or ", -1).toVector.map(w => Conflict.State(s"$w"))
          Some(Conflict.WrongState(TypeName(s"$t"), Raw(s"$key"), Conflict.State(s"$is"), states))
        case _ => None
      },
      {
        case Running(activity) => word(Conflict.Activity.values.toSeq, activity).map(Conflict.Busy(_))
        case _                 => None
      },
      {
        case ReferredTo(t, key, count, by) =>
          Count.read(count).map(Conflict.Referenced(TypeName(s"$t"), Raw(s"$key"), TypeName(s"$by"), _))
        case _ => None
      },
      {
        case RefusedBy(rule) => word(Refused.Rule.values.toSeq, rule).map(Refused.Policy(_))
        case _               => None
      },
      {
        case Spent(limit, used, cap, at) =>
          for
            l <- word(Refused.Limit.values.toSeq, limit)
            u <- Refused.Amount.read(used)
            c <- Refused.Amount.read(cap)
            until <- untilOf(at)
          yield Refused.Quota(l, u, c, until)
        case _ => None
      },
      {
        case SwitchedOff(feature, setting) =>
          Some(Refused.Disabled(Refused.Feature(s"$feature"), SettingKey(s"$setting")))
        case _ => None
      },
      {
        case Answered(at, name, status, url, body) =>
          Upstream.Status
            .read(status)
            .map(
              Upstream
                .Http(Upstream.Name(s"$name"), Upstream.Url(s"$url"), _, External.ReplyExcerpt(s"$body"), pathOf(at))
            )
        case _ => None
      },
      {
        case ChallengedAt(at, name, status, url) =>
          Upstream.Status
            .read(status)
            .map(Upstream.Challenged(Upstream.Name(s"$name"), Upstream.Url(s"$url"), _, pathOf(at)))
        case _ => None
      },
      {
        case LimitedTill(name, at) => untilOf(at).map(Upstream.Limited(Upstream.Name(s"$name"), _))
        case _                     => None
      },
      {
        case CannotReach(at, name, url, reported) =>
          Some(
            Upstream
              .Unreachable(Upstream.Name(s"$name"), Upstream.Url(s"$url"), External.Reported(s"$reported"), pathOf(at))
          )
        case _ => None
      },
      {
        case NoAnswer(at, name, url, after) =>
          Millis.read(after).map(Upstream.Timeout(Upstream.Name(s"$name"), Upstream.Url(s"$url"), _, pathOf(at)))
        case _ => None
      },
      {
        case NotShaped(at, name, url, shape, reported) =>
          word(Upstream.Shape.values.toSeq, shape)
            .map(
              Upstream.BadReply(
                Upstream.Name(s"$name"),
                Upstream.Url(s"$url"),
                _,
                External.Reported(s"$reported"),
                pathOf(at)
              )
            )
        case _ => None
      },
      {
        case CouldNot(op, path, reported) =>
          word(Io.Access.values.toSeq, op).map(Io.File(_, Io.Path(s"$path"), External.Reported(s"$reported")))
        case _ => None
      },
      {
        case IsNot(path, kind) => word(Kinds, kind).map(Io.Wrong(Io.Path(s"$path"), _))
        case _                 => None
      },
      {
        case IsOutside(path, dir) => Some(Io.Wrong(Io.Path(s"$path"), Io.Kind.Inside(Io.Path(s"$dir"))))
        case _                    => None
      },
      {
        case Damaged(format, at, damage, expected, found, reported) =>
          for
            f <- word(Io.Format.values.toSeq, format)
            d <- damageOf(damage, expected, found, reported)
          yield Io.Corrupt(f, Where(s"$at"), d)
        case _ => None
      },
      {
        case Exited(tool, code, stderr) =>
          Io.ExitCode
            .read(code)
            .map(c => Io.Process(Io.Tool(s"$tool"), Io.Ending.Exited(c, External.Stderr(s"$stderr"))))
        case _ => None
      },
      {
        case Unfinished(tool, after) =>
          Millis.read(after).map(a => Io.Process(Io.Tool(s"$tool"), Io.Ending.TimedOut(a)))
        case _ => None
      },
      {
        case NotStarted(tool, reported) =>
          Some(Io.Process(Io.Tool(s"$tool"), Io.Ending.NotStarted(External.Reported(s"$reported"))))
        case _ => None
      },
      {
        case DatabaseRefused(op, reported) =>
          word(Io.DbOp.values.toSeq, op).map(Io.Database(_, External.Reported(s"$reported")))
        case _ => None
      },
      {
        case WrongSetting(key, fault, argument) =>
          val arg = Option(argument)
          val quoted = arg
            .filter(a => a.length >= 2 && a.startsWith("'") && a.endsWith("'"))
            .map(a => Raw(s"${a.drop(1).dropRight(1)}"))
          val read: Option[Config.Fault] = (fault, arg) match
            case ("Unset", Some(v))              => Some(Config.Fault.Unset(SettingKey(s"$v")))
            case ("Malformed", Some(_))          => quoted.map(Config.Fault.Malformed(_))
            case ("Unknown", Some(_))            => quoted.map(Config.Fault.Unknown(_))
            case ("Conflicts", Some(o))          => Some(Config.Fault.Conflicts(SettingKey(s"$o")))
            case ("WrongKind", Some(Pair(e, f))) =>
              for
                ek <- word(Config.ValueKind.values.toSeq, e)
                fk <- word(Config.ValueKind.values.toSeq, f)
              yield Config.Fault.WrongKind(ek, fk)
            case ("Unreadable", Some(r)) if r.startsWith("— ") =>
              Some(Config.Fault.Unreadable(External.Reported(s"${r.stripPrefix("— ")}")))
            case ("Credential", Some(v))         => Some(Config.Fault.Credential(Some(SettingKey(s"$v"))))
            case ("Included", Some(f))           => Some(Config.Fault.Included(Io.Path(s"$f")))
            case ("Rejected", Some(Pair(u, st))) =>
              Upstream.Status.read(st).map(Config.Fault.Rejected(Upstream.Name(s"$u"), _))
            case ("KeyNeeded", Some(n)) if n.startsWith("— ") =>
              Some(Config.Fault.KeyNeeded(External.PluginSaid(s"${n.stripPrefix("— ")}")))
            case (_, None) => word(Faults, fault)
            case _         => None
          read.map(Config.Setting(SettingKey(s"$key"), _))
        case _ => None
      },
      {
        case ReloadCycle(scopes) =>
          NonEmptyVector.from(scopes.split(CycleArrow, -1).toVector.map(s => Config.Scope(s"$s"))).map(Config.Cycle(_))
        case _ => None
      },
      {
        case NoCapability(capability) => word(Config.Capability.values.toSeq, capability).map(Config.Unavailable(_))
        case _                        => None
      },
      {
        case ReloadFailed(stage, scope) =>
          word(Config.Stage.values.toSeq, stage).map(Config.Reload(_, Config.Scope(s"$scope")))
        case _ => None
      },
      {
        case NotLoaded(plugin, fault, argument) =>
          val read: Option[Plugin.Fault] = (fault, Option(argument)) match
            case ("Threw", Some(r)) if r.startsWith("— ") =>
              Some(Plugin.Fault.Threw(External.Reported(s"${r.stripPrefix("— ")}")))
            case ("SpiMismatch", Some(Pair(b, s))) =>
              Some(Plugin.Fault.SpiMismatch(Raw(s"$b"), Raw(s"$s")))
            case ("SignerChanged", Some(a)) =>
              a.split(" / ", -1).toList match
                case List(pinned, seen) =>
                  Some(Plugin.Fault.SignerChanged(Plugin.Signer(s"$pinned"), Plugin.Signer(s"$seen")))
                case _ => None
            case ("Taken", Some(by))          => Some(Plugin.Fault.Taken(Raw(s"$by")))
            case ("ForeignSpi", Some(s))      => Some(Plugin.Fault.ForeignSpi(Raw(s"$s")))
            case ("MissingClass", Some(name)) => Some(Plugin.Fault.MissingClass(Raw(s"$name")))
            case (_, None)                    => word(LoadFaults, fault)
            case _                            => None
          read.map(Plugin.Load(Plugin.Id(s"$plugin"), _))
        case _ => None
      },
      {
        case Needs(plugin, module, wanted, shipped) =>
          Some(Plugin.Dependency(Plugin.Id(s"$plugin"), Raw(s"$module"), Raw(s"$wanted"), Raw(s"$shipped")))
        case _ => None
      },
      {
        case NotTaken(plugin, item, refusal, argument) =>
          val read: Option[Plugin.Refusal] = (refusal, Option(argument)) match
            case ("LicenceNotAllowed", Some(family)) => Some(Plugin.Refusal.LicenceNotAllowed(Raw(s"$family")))
            case ("NotOpen", Some(FieldStates(field, stated))) =>
              Some(Plugin.Refusal.NotOpen(Field(s"$field"), raws(stated)))
            case ("NoOpenImage", Some(FieldStates(field, stated))) =>
              Some(Plugin.Refusal.NoOpenImage(Field(s"$field"), raws(stated)))
            case (_, None) => word(Refusals, refusal)
            case _         => None
          read.map(Plugin.Rejected(Plugin.Id(s"$plugin"), Raw(s"$item"), _))
        case _ => None
      },
      {
        case VaultFailed(vault, failure, argument) =>
          val read: Option[Plugin.Retrieval] = (failure, Option(argument)) match
            case ("TimedOut", Some(a)) if a.endsWith(" ms") =>
              Millis.read(a.stripSuffix(" ms")).map(Plugin.Retrieval.TimedOut(_))
            case ("Failed", Some(a)) if a.startsWith("— ") =>
              Some(Plugin.Retrieval.Failed(External.Stderr(s"${a.stripPrefix("— ")}")))
            case ("Blank", Some(Quoted(r)))                  => Some(Plugin.Retrieval.Blank(Raw(s"$r")))
            case ("Absent", Some(Quoted(r)))                 => Some(Plugin.Retrieval.Absent(Raw(s"$r")))
            case ("Unread", Some(UnreadAt(r, exit, stderr))) =>
              Io.ExitCode.read(exit).map(Plugin.Retrieval.Unread(Raw(s"$r"), _, External.Stderr(s"$stderr")))
            case ("NoKey", Some(a)) =>
              a.split(" / ", -1).toList match
                case List(configured, seen) => Some(Plugin.Retrieval.NoKey(raws(configured), raws(seen)))
                case _                      => None
            case ("Key", Some(KeyTried(key, attempt, stderr))) =>
              word(Plugin.Attempt.values.toSeq, attempt)
                .map(Plugin.Retrieval.Key(Raw(s"$key"), _, Option(stderr).map(e => External.Stderr(s"$e"))))
            case (_, None) => word(VaultFailures, failure)
            case _         => None
          read.map(Plugin.Vault(Plugin.Id(s"$vault"), _))
        case _ => None
      },
      {
        case Says(plugin, claim, text) =>
          word(Plugin.Claim.values.toSeq, claim)
            .map(Plugin.Said(Plugin.Id(s"$plugin"), _, External.PluginSaid(s"$text")))
        case _ => None
      },
      {
        case PausedFor(reason, at) =>
          for
            r <- word(Problem.Run.PauseReason.values.toSeq, reason)
            until <- untilOf(at)
          yield Problem.Run.Paused(r, until)
        case _ => None
      },
      {
        case NoModelFor(role, tried) =>
          val models = Option(tried).fold(Vector.empty)(_.split(", ", -1).toVector.map(m => Problem.Run.Model(s"$m")))
          Some(Problem.Run.NoModel(Problem.Run.Role(s"$role"), models))
        case _ => None
      },
      {
        case HeldBack(block, pool) =>
          val read: Option[Problem.Run.Block] = Option(pool) match
            case Some(p) => Option.when(block == "PoolFull")(Problem.Run.Block.PoolFull(Problem.Run.Pool(s"$p")))
            case None    => word(Blocks, block)
          read.map(Problem.Run.Blocked(_))
        case _ => None
      },
      {
        case "interrupted" => Some(Problem.Run.Interrupted)
        case _             => None
      },
      {
        case CutShortBy(activity) => word(Conflict.Activity.values.toSeq, activity).map(Problem.Run.CutShort(_))
        case _                    => None
      },
      {
        case UnrecordedBy(activity) => word(Conflict.Activity.values.toSeq, activity).map(Problem.Run.Unrecorded(_))
        case _                      => None
      },
      {
        case UnpricedModel(model, metered, provider) =>
          val read: Option[Option[Config.Metered]] = (Option(metered), Option(provider)) match
            case (None, None)              => Some(None)
            case (Some("ApiKey"), Some(p)) => Some(Some(Config.Metered.ApiKey(Upstream.Name(s"$p"))))
            case (Some("Opencode"), None)  => Some(Some(Config.Metered.Opencode))
            case _                         => None
          read.map(Config.Unpriced(Problem.Run.Model(s"$model"), _))
        case _ => None
      },
      {
        case CancelledBy(actor) => word(Problem.Run.Actor.values.toSeq, actor).map(Problem.Run.Cancelled(_))
        case _                  => None
      },
      {
        case DrainingSince(since) => momentOf(since).map(Problem.Run.Draining(_))
        case _                    => None
      },
      {
        case CannotGoOn(step, cause) =>
          word(Problem.Run.AbortCause.values.toSeq, cause).map(Problem.Run.Aborted(Problem.Run.Step(s"$step"), _))
        case _ => None
      },
      {
        case FindingOf(cause)   => word(Content.Flaw.values.toSeq, cause).map(Content.Finding(_))
        case AnswerBreaks(rule) =>
          read(s"answer: $rule") match
            case Content.Proposal(r, _) => Some(Content.Answer(r))
            case _                      => None
        case _ => None
      },
      {
        case ProposalAt(at, rule, cap, gave) =>
          val read: Option[Content.Requirement] = (Option(cap), Option(gave)) match
            case (Some(c), Some(g)) =>
              for
                cc <- Count.read(c)
                gc <- Count.read(g)
                capped <- rule match
                  case "TooManyClaims"   => Some(Content.Requirement.TooManyClaims(cc, gc))
                  case "TooManyArticles" => Some(Content.Requirement.TooManyArticles(cc, gc))
                  case "TooManySubGoals" => Some(Content.Requirement.TooManySubGoals(cc, gc))
                  case "TooManyQueries"  => Some(Content.Requirement.TooManyQueries(cc, gc))
                  case "TooManyWorks"    => Some(Content.Requirement.TooManyWorks(cc, gc))
                  case _                 => None
              yield capped
            case (None, None) => word(ProposalRules, rule)
            case _            => None
          read.map(Content.Proposal(_, Where(s"$at")))
        case _ => None
      },
      {
        case JobControl(job, status, name, kind) =>
          val control = (name, Option(kind)) match
            case ("OnePiece", Some(k)) => Some(Conflict.Control.OnePiece(Raw(s"$k")))
            case (other, None)         => word(Controls, other)
            case _                     => None
          control.map(Conflict.Job(Raw(s"$job"), Conflict.State(s"$status"), _))
        case _ => None
      },
      {
        case ProposalWith(at, rule, written) =>
          val one = written match
            case OneQuoted(r) => Some(Raw(s"$r"))
            case _            => None
          val two = written match
            case TwoQuoted(a, _, b) => Some((Raw(s"$a"), Raw(s"$b")))
            case _                  => None
          val read: Option[Content.Requirement] = rule match
            case "QuoteInRecords"     => Some(Content.Requirement.QuoteInRecords(raws(written)))
            case "KnownCitekey"       => one.map(Content.Requirement.KnownCitekey(_))
            case "ListedCandidate"    => one.map(Content.Requirement.ListedCandidate(_))
            case "PositiveBce"        => one.map(Content.Requirement.PositiveBce(_))
            case "CeFromOne"          => one.map(Content.Requirement.CeFromOne(_))
            case "Calibrated"         => one.map(Content.Requirement.Calibrated(_))
            case "LinkTarget"         => one.map(Content.Requirement.LinkTarget(_))
            case "StartBeforeEnd"     => two.map(Content.Requirement.StartBeforeEnd(_, _))
            case "YearWithin"         => one.map(Content.Requirement.YearWithin(_))
            case "WithinSourcePeriod" =>
              written match
                case FourQuoted(a, b, c, d) =>
                  Some(Content.Requirement.WithinSourcePeriod(Raw(s"$a"), Raw(s"$b"), Raw(s"$c"), Raw(s"$d")))
                case _ => None
            case "KnownItems"                         => Some(Content.Requirement.KnownItems(raws(written)))
            case "DateGiven"                          => one.map(Content.Requirement.DateGiven(_))
            case "PriorityWithin"                     => two.map(Content.Requirement.PriorityWithin(_, _))
            case "FilterTooNarrow" | "FilterTooBroad" =>
              written match
                case CountedLine(n, bound, line) =>
                  for
                    nc <- Count.read(n)
                    bc <- Count.read(bound)
                  yield
                    if rule == "FilterTooNarrow" then Content.Requirement.FilterTooNarrow(Raw(s"$line"), nc, bc)
                    else Content.Requirement.FilterTooBroad(Raw(s"$line"), nc, bc)
                case _ => None
            case "QuoteInWindow" => Share.read(written).map(Content.Requirement.QuoteInWindow(_))
            case "CenturyAsYear" =>
              written match
                case CenturyOf(century, year) =>
                  Count.read(century).map(Content.Requirement.CenturyAsYear(Raw(s"$year"), _))
                case _ => None
            case "PointerPhrase"  => one.map(Content.Requirement.PointerPhrase(_))
            case "QuoteInRecord"  => one.map(Content.Requirement.QuoteInRecord(_))
            case "StatusUnstated" => two.map(Content.Requirement.StatusUnstated(_, _))
            case "PlacesResolve"  =>
              written match
                case PlacesOf(resolved, total, least, missing) =>
                  val unplaced = missing.split(" \\| ", -1).toVector.filter(_.nonEmpty).map {
                    case UnplacedOf(n, place) => Count.read(n).map(Content.Unplaced(Raw(s"$place"), _))
                    case _                    => None
                  }
                  for
                    r <- Count.read(resolved)
                    t <- Count.read(total)
                    l <- Count.read(least)
                    if unplaced.forall(_.isDefined)
                  yield Content.Requirement.PlacesResolve(r, t, l, unplaced.flatten)
                case _ => None
            case "KnownHeading"     => one.map(Content.Requirement.KnownHeading(_))
            case "ParagraphCited"   => one.map(Content.Requirement.ParagraphCited(_))
            case "StillUncited"     => one.map(Content.Requirement.StillUncited(_))
            case "SectionPresent"   => Some(Content.Requirement.SectionPresent(raws(written)))
            case "NoFiller"         => one.map(Content.Requirement.NoFiller(_))
            case "ClaimTabled"      => one.map(Content.Requirement.ClaimTabled(_))
            case "ReasonForTabled"  => one.map(Content.Requirement.ReasonForTabled(_))
            case "ReasonForUncited" => one.map(Content.Requirement.ReasonForUncited(_))
            case "ReasonText"       => one.map(Content.Requirement.ReasonText(_))
            case "TabledAccounted"  => one.map(Content.Requirement.TabledAccounted(_))
            case "SubGoalTitle"     => one.map(Content.Requirement.SubGoalTitle(_))
            case "DoiGiven"         => one.map(Content.Requirement.DoiGiven(_))
            case "QueryWithin"      =>
              two.flatMap((query, most) => Count.read(s"$most").map(Content.Requirement.QueryWithin(query, _)))
            case "FormatClean" =>
              written match
                case OneQuoted(fields) =>
                  quotedListOf(fields) match
                    case Vector(rule, excerpt, message) => Some(Content.Requirement.FormatClean(rule, message, excerpt))
                    case _                              => None
                case _ => None
            case "FilterMisses" =>
              written match
                case MissedLine(returned, cited, share, least, line, missed) =>
                  for
                    r <- Count.read(returned)
                    c <- Count.read(cited)
                    sh <- Count.read(share)
                    l <- Count.read(least)
                  yield Content.Requirement.FilterMisses(
                    Raw(s"$line"),
                    r,
                    c,
                    sh,
                    l,
                    raws(missed)
                  )
                case _ => None
            case _ => None
          read.map(Content.Proposal(_, Where(s"$at")))
        case _ => None
      },
      {
        case Crashed(where, exception, id, reported) =>
          Some(
            Internal.Unexpected(
              Internal.Id(s"$id"),
              Where(s"$where"),
              Internal.ExceptionName(s"$exception"),
              External.Reported(s"$reported")
            )
          )
        case _ => None
      },
      {
        case UnclassifiedText(text) => Some(Unclassified(Legacy.Text(s"$text")))
        case _                      => None
      }
    )
