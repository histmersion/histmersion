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
      case Invalid.Reference(form, at)                  => s"${path(at)}no secret reference: ${form.stored}"
      case Invalid.Broken(rule, at)                     => s"${path(at)}breaks ${rule.stored}"
      case NotFound.Entity(t, key)                      => s"no $t '$key'"
      case NotFound.Listed(t, key, listing)             => s"no $t '$key' listed by ${listing.productPrefix}"
      case NotFound.Copy(work, answers)                 => s"no open-access copy of '$work'${answeredStored(answers)}"
      case NotFound.Dangling(t, key, from, in)          => s"no $t '$key' in ${in.productPrefix}, referred to by $from"
      case NotFound.Name(place, label, name, lang, has) =>
        s"no name '$name' in '$lang' for '$label' [$place]${if has.isEmpty then "" else s" (has: ${quoted(has)})"}"
      case NotFound.SetByHand(place, label)       => s"nothing set by hand on '$label' [$place]"
      case NotFound.Undownloaded(layer, fetch)    => s"no download of the $layer layer; fetch with `$fetch`"
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
      case Conflict.Referred(place, label, refs)  =>
        s"place '$label' [$place] is referred to by ${refs.toVector.map(r => s"${r.count} ${r.by.productPrefix}").mkString(ListSeparator)}"
      case Refused.Policy(rule)                        => s"refused: ${rule.productPrefix}"
      case Refused.Quota(limit, used, cap, at)         => s"${limit.productPrefix} is spent: $used of $cap${until(at)}"
      case Refused.Space(work, needs, on, free, least) =>
        s"no room for ${(work.stored +: Vector(s"$needs", s"$on", s"$free", s"$least")).mkString(HoldSeparator)}"
      case Refused.Unauthenticated(credential)      => s"unauthenticated: needs ${credential.productPrefix}"
      case Refused.Misdirected(host)                => s"misdirected to host '$host'"
      case Refused.Disabled(feature, setting, v, e) =>
        s"$feature is switched off by $setting${v.fold("")(variable => s"; $variable=true switches it on")}" +
          e.fold("")(effect => s" (${effect.productPrefix})")
      case Upstream.Http(name, url, st, body, at)       => s"${path(at)}$name answered HTTP $st at $url — $body"
      case Upstream.Challenged(name, url, st, at)       => s"${path(at)}$name challenged with HTTP $st at $url"
      case Upstream.Limited(name, at)                   => s"$name is limited${until(at)}"
      case Upstream.Offline(name, at)                   => s"$name is offline${until(at)}"
      case Upstream.Changed(name)                       => s"$name's file changed"
      case Upstream.Rebuilt(url)                        => s"$url changed during the download"
      case Upstream.Mislisted(url, size, listed, build) => s"$url is $size bytes, listed $listed for '$build'"
      case Upstream.Unasked(why)                        => s"$NotAskedThisSweep${why.stored}"
      case Upstream.Unreachable(name, url, r, at)       => s"${path(at)}cannot reach $name at $url — $r"
      case Upstream.Timeout(name, url, after, at)       => s"${path(at)}$name did not answer at $url within $after ms"
      case Upstream.CallFailed(model, why)              => s"the call to $model failed (${why.stored})"
      case Upstream.CallThrew(model, reported)          => s"the call to $model threw — $reported"
      case Upstream.Replied(upstream, said)             => s"$upstream said — $said"
      case Upstream.McpServer(status)                   => s"agent mcp server $status"
      case Upstream.BadReply(name, url, s, r, at) => s"${path(at)}$name's answer at $url is not ${s.productPrefix} — $r"
      case Io.File(op, path, reported)            => s"could not ${op.productPrefix} $path — $reported"
      case Io.Wrong(path, Io.Kind.Inside(dir))    => s"$path is outside $dir"
      case Io.Wrong(path, expected)               => s"$path is not ${expected.productPrefix}"
      case Io.Corrupt(format, at, damage)  => s"the ${format.productPrefix} data at $at is damaged: ${damage.stored}"
      case Io.Process(tool, ending)        => s"$tool ${ending.stored}"
      case Io.Database(op, reported)       => s"the database refused to ${op.productPrefix} — $reported"
      case Io.Entry(archive, entry, fault) => s"${archive.productPrefix} entry $entry: ${fault.stored}"
      case Io.Absent(held, path, setting)  =>
        s"no ${held.productPrefix} at $path${setting.fold("")(s => s" (set $s)")}"
      case Io.Hashed(path, expected, found) => s"content of $path hashes to $found, not $expected"
      case Io.Extract(extract, pinning)     => s"extract '$extract' cannot be read: ${pinning.stored}"
      case Io.Oversize(path, mb, setting)   => s"$path is $mb MB, over $setting"
      case Config.Setting(key, fault)       => s"$key is wrong: ${fault.stored}"
      case Config.Unavailable(capability)   => s"this server has no ${capability.productPrefix}"
      case Config.Reload(stage, scope)      => s"reloading the settings failed at ${stage.productPrefix} in $scope"
      case Config.Cycle(scopes)             => s"reload cycle: ${scopes.toVector.mkString(CycleArrow)}"
      case Config.Definition(file, flaw)    => s"definition $file is broken: ${flaw.stored}"
      case Config.Unbuilt(scope, why)       => s"part $scope unbuilt (${why.storedEach})"
      case Config.Unchanged(why)            => s"reload changed nothing (${why.stored})"
      case Config.Drained(component, drain) =>
        s"drained ${drain.productPrefix}${drain match
            case Config.Drain.InUse(s)    => s" $s s"
            case Config.Drain.Unclosed(s) => s" $s s"
            case Config.Drain.ClosedInUse => ""
          }: $component"
      case Config.ApiKeyLogin(method)                => s"api key login $method"
      case Config.Exposed(bind, reach, exposure)     => s"exposed on '$bind' to '$reach': ${exposure.productPrefix}"
      case Config.ProxyUndeclared(publicUrl)         => s"undeclared proxy for '$publicUrl'"
      case Config.WildcardOverlap(wildcard, address) => s"wildcard '$wildcard' overlaps '$address'"
      case Config.FloorsUnread(Config.FloorsFrom.Saved, why)        => s"floors unread, saved (${why.storedEach})"
      case Config.FloorsUnread(Config.FloorsFrom.Setting(key), why) => s"floors unread, $key (${why.storedEach})"
      case Config.ReadAt(file, at, why)                             => s"settings $file read at $at (${why.storedEach})"
      case Config.Unpriced(model, metered)    => s"model $model is unpriced${metered.fold("")(m => s" (${m.stored})")}"
      case Plugin.Load(plugin, fault)         => s"plugin $plugin did not load: ${fault.stored}"
      case Plugin.Rejected(plugin, item, why) => s"$plugin did not take '$item': ${why.stored}"
      case Plugin.Vault(vault, failure)       => s"secret vault $vault failed: ${failure.stored}"
      case Plugin.Dependency(p, m, want, has) => s"$p needs $m $want; it ships $has"
      case Plugin.Said(plugin, claim, text)   => s"$plugin says (${claim.productPrefix}): $text"
      case Problem.Run.Paused(reason, at)     => s"paused for ${reason.productPrefix}${until(at)}"
      case Problem.Run.NoModel(role, passed)  => s"no model of the $role chain can run${passedStored(passed)}"
      case Problem.Run.Exhausted(role, floor, frees, passed) =>
        s"all models of the $role chain are spent (${floor.productPrefix}, ${frees.stored})${passedStored(passed)}"
      case Problem.Run.PassedOver(model, why) =>
        s"$model passed over (${why.toVector.map(_.stored).mkString(WhySeparator)})"
      case Problem.Run.Unassigned(role, asked)   => s"no model set for $role (${asked.productPrefix})"
      case Problem.Run.Cooling(until)            => s"cooling until ${until.iso}"
      case Problem.Run.BelowFloor(pool, reading) => s"below the floor of $pool ($reading)"
      case Problem.Run.BelowAllowance(limits)    =>
        s"below its allowance: ${limits.toVector.map(l => s"${l.limit}$HoldSeparator${l.used}$HoldSeparator${l.reserve}").mkString(PassedSeparator)}"
      case Problem.Run.AllowanceUnread(broken) =>
        val each = broken.toVector.map(b => s"${b.target}: ${b.why.toVector.map(_.stored).mkString(WhySeparator)}")
        s"allowance unreadable (${each.mkString(PassedSeparator)})"
      case Problem.Run.PlanOnly(role)                    => s"no fall from a plan to a paid key for $role"
      case Problem.Run.Stalled(step, minutes, times)     => s"stalled at step $step: $minutes min, $times times"
      case Problem.Run.Silent(step, minutes, stall, of)  => s"silent at step $step: $minutes min, stall $stall of $of"
      case Problem.Run.NoPipeline(pipeline)              => s"no pipeline '$pipeline' in this build"
      case Problem.Run.PipelineChanged                   => "pipeline changed"
      case Problem.Run.Reordered(seq, recorded, now)     => s"step $seq moved from '$recorded' to '$now'"
      case Problem.Run.CheckpointUnread(seq, step, r)    => s"step $seq ($step) checkpoint unreadable — $r"
      case Problem.Run.Vanished                          => "run vanished"
      case Problem.Run.Overspent(spent, budget)          => s"run overspent: $spent of $budget"
      case Problem.Run.OverTokens(used, budget, weight)  => s"run over tokens: $used of $budget, cache weight $weight"
      case Problem.Run.OverCap(cap, amount)              => s"spend cap ${cap.productPrefix} reached: $amount"
      case Problem.Run.MovedOn(run)                      => s"run $run moved on"
      case Problem.Run.NotNow(run, status, control)      => s"run $run is $status: no ${control.productPrefix}"
      case Problem.Run.Withdrawn                         => "rejected: the run was cancelled"
      case Problem.Run.AtStep(step, why)                 => s"at step $step (${why.storedEach})"
      case Problem.Run.AtModel(model, why)               => s"at model $model (${why.storedEach})"
      case Problem.Run.Unsettled(step, attempts)         => s"step $step unsettled after $attempts attempts"
      case Problem.Run.ProviderDown(step, why)           => s"provider down at step $step (${why.storedEach})"
      case Problem.Run.BudgetSpent(step)                 => s"budget spent at step $step"
      case Problem.Run.NoValidAnswer(step, attempts, ps) =>
        s"no valid answer at step $step after $attempts (${ps.map(_.stored).mkString(WhySeparator)})"
      case Problem.Run.Failed(why)              => s"step failed (${why.storedEach})"
      case Problem.Run.Declined(ps)             => s"answer rejected (${ps.map(_.stored).mkString(WhySeparator)})"
      case Problem.Run.LimitReached(why, until) => s"limit reached, skipped until ${until.iso} (${why.stored})"
      case Problem.Run.AllowanceReached(model)  => s"allowance reached by $model mid-step"
      case Problem.Run.OutputCapped(step, model, cap, sent, setting) =>
        s"output over the cap at step $step: $model, cap $cap, sent $sent, ${setting.productPrefix}"
      case Problem.Run.NoText(source)          => s"no text in '$source'"
      case Problem.Run.NoBody(article)         => s"no body to review in '$article'"
      case Problem.Run.GuardStopped(why)       => s"stopped by the quota guard: $why"
      case Problem.Run.NoLogin(why)            => s"no login: $why"
      case Problem.Run.ProbeUnread(exit, said) =>
        s"probe read no rate limit, exit $exit${said.fold("")(m => s" — $m")}"
      case Problem.Run.Blocked(block)            => s"held back: ${block.stored}"
      case Problem.Run.Interrupted               => "interrupted"
      case Problem.Run.CutShort(activity)        => s"cut short: ${activity.productPrefix}"
      case Problem.Run.Unrecorded(activity)      => s"unrecorded: ${activity.productPrefix}"
      case Problem.Run.Cancelled(by)             => s"cancelled by ${by.productPrefix}"
      case Problem.Run.Draining(since)           => s"draining since ${since.iso}"
      case Problem.Run.Aborted(step, cause)      => s"step $step cannot go on: ${cause.productPrefix}"
      case Content.Finding(cause)                => s"finding: ${cause.productPrefix}"
      case Content.Proposal(rule, at)            => s"$at: ${rule.stored}"
      case Content.Answer(rule)                  => s"the answer breaks ${rule.stored}"
      case Content.Undecoded(why, finish, began) =>
        s"answer undecodable: ${why.stored}${finish.fold("")(f => s"$PassedSeparator$FinishedBy$f")}" +
          began.fold("")(b => s"$PassedSeparator$AnswerBegan$b")
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

  private def quoted(values: Vector[Raw]): String = values.map(v => s"'$v'").mkString(ListSeparator)

  private def quotedList(values: Vector[Raw]): String =
    if values.isEmpty then "" else values.map(v => s"'$v'").mkString(" (one of: ", ", ", ")")

  private def quotedExamples(values: Vector[Raw]): String =
    if values.isEmpty then "" else values.map(v => s"'$v'").mkString(" (e.g. ", ", ", ")")

  extension (at: EpochMs) private def iso: String = s"${Instant.ofEpochMilli(s"$at".toLong)}"

  private def until(at: Option[EpochMs]): String = at.fold("")(t => s" until ${t.iso}")

  /** The models a chain passed over, each with its problems, one after another in parentheses: a nested sentence is
    * never the tail of the text, which a reader of a path (`<field>: <sentence>`) would take it for.
    */
  private def passedStored(passed: Vector[Problem.Run.PassedOver]): String =
    if passed.isEmpty then "" else passed.map(_.stored).mkString(" (", PassedSeparator, ")")

  /** Between the models a chain passed over (and the limits or allowances of one): a mark no sentence holds. */
  private val PassedSeparator = " ‖ "

  extension (why: NonEmptyVector[Problem])
    /** Problems held by one case, one after another. */
    private def storedEach: String = why.toVector.map(_.stored).mkString(WhySeparator)

  /** Between the problems of one model passed over: a mark no sentence holds, other than [[PassedSeparator]]. */
  private val WhySeparator = " ¦ "

  extension (frees: Problem.Run.Frees)
    private def stored: String = frees match
      case Problem.Run.Frees.At(at, model, held) => s"At ${at.iso} $model${heldStored(held)}"
      case Problem.Run.Frees.Released(held)      => s"Released${heldStored(held.toVector)}"
      case Problem.Run.Frees.Never               => "Never"

  /** A definition's flaw by its name, then what it holds, each after the hold separator; a list's items after the list
    * separator.
    */
  extension (flaw: Config.Flaw)
    private def stored: String =
      val held: Vector[String] = flaw match
        case Config.Flaw.HeaderUnparsable(r)          => Vector(s"$r")
        case Config.Flaw.NotList(field, value)        => Vector(s"$field", s"$value")
        case Config.Flaw.NameNotFile(named)           => Vector(s"$named")
        case Config.Flaw.RoleUnknown(roles)           => Vector(roles.mkString(ListSeparator))
        case Config.Flaw.RoleUnused(role, roles)      => Vector(s"$role", roles.mkString(ListSeparator))
        case Config.Flaw.ToolsUnoffered(tools, known) =>
          Vector(tools.mkString(ListSeparator), known.mkString(ListSeparator))
        case Config.Flaw.RuntimesUnknown(runtimes)   => Vector(runtimes.mkString(ListSeparator))
        case Config.Flaw.SkillNotNamed(name)         => Vector(s"$name")
        case Config.Flaw.IncludeMissing(include)     => Vector(s"$include")
        case Config.Flaw.SkillMissing(skill)         => Vector(s"$skill")
        case Config.Flaw.AudienceUnknown(audience)   => Vector(s"$audience")
        case Config.Flaw.MarkerUnclosed(audience)    => Vector(s"$audience")
        case Config.Flaw.MarkerNested(audience, out) => Vector(s"$audience", s"$out")
        case Config.Flaw.MarkerUnopened(audience)    => Vector(s"$audience")
        case Config.Flaw.ToolUnknown(tool)           => Vector(s"$tool")
        case Config.Flaw.NoHeader | Config.Flaw.HeaderUnclosed | Config.Flaw.HeaderNotMapping | Config.Flaw.NoName |
            Config.Flaw.NoDescription | Config.Flaw.WebUnknown | Config.Flaw.WritesUnknown |
            Config.Flaw.TurnsNotPositive | Config.Flaw.InteractiveUnset | Config.Flaw.DescriptionLong =>
          Vector.empty
      (flaw.productPrefix +: held).mkString(HoldSeparator)

  extension (decoding: Content.Decoding)
    private def stored: String = decoding match
      case Content.Decoding.Mismatch(path, reported) => s"Mismatch at $path — $reported"
      case Content.Decoding.NotJson(reported)        => s"NotJson — $reported"
      case Content.Decoding.Unreadable(reported)     => s"Unreadable — $reported"
      case Content.Decoding.NotSchema                => "NotSchema"

  /** Before how the call that brought an undecodable answer ended, and before the start of the answer. */
  private val FinishedBy = "finish "
  private val AnswerBegan = "began "

  private def heldStored(held: Vector[Problem.Run.HeldPool]): String =
    held.map(h => s"$HoldSeparator${h.title} [${h.pool}]").mkString

  private val CycleArrow = " → "

  /** What a sweep's watch stores when its index was left out of the sweep, before why. */
  private val NotAskedThisSweep = "not asked this sweep — "

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
        case Invalid.Rule.MergeId(stated)                         => Vector(s"$stated")
        case Invalid.Rule.DoiRecorded(purpose)                    => Vector(purpose.productPrefix)
        case Invalid.Rule.UploadBytes(bytes, limit)               => Vector(s"$bytes", s"$limit")
        case Invalid.Rule.InlineBytes(bytes, limit)               => Vector(s"$bytes", s"$limit")
        case Invalid.Rule.Base64Decodable(reported)               => Vector(s"$reported")
        case Invalid.Rule.RegionWithin(x, y, w, h, width, height) =>
          Vector(s"$x", s"$y", s"$w", s"$h", s"$width", s"$height")
        case Invalid.Rule.HintConfig(allowed)               => Vector(allowed.mkString(ListSeparator))
        case Invalid.Rule.RoundsWithin(stated, least, cap)  => Vector(s"$stated", s"$least", s"$cap")
        case Invalid.Rule.RoleUsed(owner, name, role, used) =>
          Vector(owner.productPrefix, s"$name", s"$role", used.mkString(ListSeparator))
        case Invalid.Rule.PointOnly(place, label)        => Vector(s"$place", s"$label")
        case Invalid.Rule.NameByHand(name, place, label) => Vector(s"$name", s"$place", s"$label")
        case Invalid.Rule.FocusRegion(name, known)       => Vector(s"$name", known.mkString(ListSeparator))
        case Invalid.Rule.FocusDeeper(extract)           => Vector(s"$extract")
        case Invalid.Rule.LabelsUnranged | Invalid.Rule.SeaFloorUnranged => Vector.empty
        case Invalid.Rule.ProviderOf(model, provider)                    => Vector(s"$model", s"$provider")
        case Invalid.Rule.OpencodeModelShape(model)                      => Vector(s"$model")
        case Invalid.Rule.HostProvider(provider)                         => Vector(s"$provider")
        case Invalid.Rule.OptionOf(option, owner, pipeline)              => Vector(s"$option", s"$owner", s"$pipeline")
        case Invalid.Rule.NewArticleFilter(target)                       => Vector(s"$target")
        case Invalid.Rule.NewArticleTitle(target)                        => Vector(s"$target")
        case Invalid.Rule.IdentifierGiven | Invalid.Rule.RunsCompared | Invalid.Rule.RunRepeated |
            Invalid.Rule.QueryGiven | Invalid.Rule.UpstreamRecord | Invalid.Rule.UploadGiven |
            Invalid.Rule.PathOrBase64 | Invalid.Rule.ServerSees | Invalid.Rule.OriginalForReocr |
            Invalid.Rule.OriginalForDownload | Invalid.Rule.Frontmatter | Invalid.Rule.NoteCitekey |
            Invalid.Rule.FilterRecorded | Invalid.Rule.ReviewLibraryWide | Invalid.Rule.EnrichLibraryWide |
            Invalid.Rule.SeedGoal | Invalid.Rule.WatchGoal | Invalid.Rule.EnrichCron | Invalid.Rule.HintSettings |
            Invalid.Rule.HintUntil | Invalid.Rule.HintTargets | Invalid.Rule.CronGiven | Invalid.Rule.ChainGiven =>
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
      case Io.Damage.Structure(flaw)         => s"Structure ${flaw.stored}"
      case Io.Damage.Bytes(expected, found)  => s"Bytes $expected/$found"
      case Io.Damage.Inflate(reported)       => s"Inflate — $reported"
      case Io.Damage.Unsupported(variant)    => s"Unsupported ${variant.stored}"
      case other                             => other.productPrefix

  /** A name, then what it holds, each after a space. */
  extension (flaw: Io.Flaw)
    private def stored: String =
      val held: Vector[String] = flaw match
        case Io.Flaw.NegativeAt(quantity, byte) => Vector(quantity.productPrefix, s"$byte")
        case Io.Flaw.DirectorySize(size)        => Vector(s"$size")
        case Io.Flaw.NegativeEntry(entry)       => Vector(s"$entry")
        case Io.Flaw.OffsetOutside(offset)      => Vector(s"$offset")
        case Io.Flaw.NegativeLength(length)     => Vector(s"$length")
        case Io.Flaw.RangeBytes(got, asked)     => Vector(s"$got", s"$asked")
        case Io.Flaw.NoTag(tag)                 => Vector(s"$tag")
        case Io.Flaw.NoStripForRow(row)         => Vector(s"$row")
        case _                                  => Vector.empty
      (flaw.productPrefix +: held).mkString(" ")

  extension (variant: Io.Variant)
    private def stored: String =
      val held: Vector[String] = variant match
        case Io.Variant.PngLayout(colour, depth, interlace) => Vector(s"$colour", s"$depth", s"$interlace")
        case Io.Variant.PngFilter(filter)                   => Vector(s"$filter")
        case Io.Variant.SpecVersion(found, reads)           => Vector(s"$found", s"$reads")
        case Io.Variant.Compression(found, reads)           => Vector(s"$found", s"$reads")
        case Io.Variant.SampleBits(bits)                    => Vector(s"$bits")
        case Io.Variant.TiffCompression(code)               => Vector(s"$code")
        case Io.Variant.Bands(count)                        => Vector(s"$count")
        case Io.Variant.Planar(configuration)               => Vector(s"$configuration")
        case Io.Variant.SampleFormat(format)                => Vector(s"$format")
        case _                                              => Vector.empty
      (variant.productPrefix +: held).mkString(" ")

  extension (work: Refused.Work)
    private def stored: String = work match
      case Refused.Work.Layer(title) => s"Layer $title"
      case other                     => other.productPrefix

  extension (pinning: Io.Pinning)
    private def stored: String =
      val held: Vector[String] = pinning match
        case Io.Pinning.Unlisted(manifest)                     => Vector(s"$manifest")
        case Io.Pinning.NotBuilt(manifest)                     => Vector(s"$manifest")
        case Io.Pinning.Mismatch(version, path, found, pinned) => Vector(s"$version", s"$path", s"$found", s"$pinned")
        case Io.Pinning.Unhosted(version, file, path, sha256, bytes, setting, variable) =>
          Vector(s"$version", s"$file", s"$path", s"$sha256", s"$bytes", s"$setting", s"$variable")
      (pinning.productPrefix +: held).mkString(HoldSeparator)

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
        case Conflict.Hold.StoredAs(citekey)          => Vector(s"$citekey")
        case Conflict.Hold.Focus(goal)                => Vector(s"$goal")
        case _                                        => Vector.empty
      (hold.productPrefix +: (if args.isEmpty then Vector.empty else Vector(args.mkString(HoldSeparator))))
        .mkString(" ")

  extension (ending: Io.Ending)
    private def stored: String = ending match
      case Io.Ending.Exited(code, stderr) => s"exited with $code — $stderr"
      case Io.Ending.TimedOut(after)      => s"did not finish within $after ms"
      case Io.Ending.NotStarted(reported) => s"could not be started — $reported"
      case Io.Ending.Unanswered(request)  => s"ended without answering $request"

  extension (fault: Config.Fault)
    private def stored: String = fault match
      case Config.Fault.Unset(variable)             => s"Unset $variable"
      case Config.Fault.Malformed(raw)              => s"Malformed '$raw'"
      case Config.Fault.Unknown(raw)                => s"Unknown '$raw'"
      case Config.Fault.Unregistered(id)            => s"Unregistered '$id'"
      case Config.Fault.Defaulted(raw, known, used) =>
        s"Defaulted '$raw' (${known.mkString(ListSeparator)}) as '$used'"
      case Config.Fault.NoPlugin(id)     => s"NoPlugin '$id'"
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

    private val Damages = Vector(Io.Damage.Checksum, Io.Damage.Truncated, Io.Damage.Undecodable, Io.Damage.Foreign)
    private val NotDownloaded = "(?s)^no download of the (.*) layer; fetch with `(.*)`$".r
    private val OfflineTill = "^(.+) is offline(?: until (\\S+))?$".r
    private val FileChanged = "(?s)^(.+)'s file changed$".r
    private val ChangedDuring = "^(\\S+) changed during the download$".r
    private val ListedSize = "(?s)^(\\S+) is (\\d+) bytes, listed (\\d+) for '(.*)'$".r
    private val NoRoom = "(?s)^no room for (.*)$".r
    private val Kinds = Vector(
      Io.Kind.File,
      Io.Kind.Directory,
      Io.Kind.Present,
      Io.Kind.Absent,
      Io.Kind.Executable,
      Io.Kind.Listed,
      Io.Kind.OnPath,
      Io.Kind.OnDisk
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
    /** A definition's flaw, from what [[Config.Flaw]]'s stored form wrote. */
    private def flawOf(written: Vector[String]): Option[Config.Flaw] =
      import Config.Flaw.*
      written match
        case Vector("HeaderUnparsable", r)       => Some(HeaderUnparsable(External.Reported(s"$r")))
        case Vector("NotList", field, value)     => Some(NotList(Field(s"$field"), Raw(s"$value")))
        case Vector("NameNotFile", named)        => Some(NameNotFile(Raw(s"$named")))
        case Vector("RoleUnknown", roles)        => Some(RoleUnknown(raws(roles)))
        case Vector("RoleUnused", role, roles)   => Some(RoleUnused(Raw(s"$role"), raws(roles)))
        case Vector("ToolsUnoffered", ts, known) => Some(ToolsUnoffered(raws(ts), raws(known)))
        case Vector("RuntimesUnknown", runtimes) => Some(RuntimesUnknown(raws(runtimes)))
        case Vector("SkillNotNamed", name)       => Some(SkillNotNamed(Raw(s"$name")))
        case Vector("IncludeMissing", include)   => Some(IncludeMissing(Raw(s"$include")))
        case Vector("SkillMissing", skill)       => Some(SkillMissing(Raw(s"$skill")))
        case Vector("AudienceUnknown", audience) => Some(AudienceUnknown(Raw(s"$audience")))
        case Vector("MarkerUnclosed", audience)  => Some(MarkerUnclosed(Raw(s"$audience")))
        case Vector("MarkerNested", audience, o) => Some(MarkerNested(Raw(s"$audience"), Raw(s"$o")))
        case Vector("MarkerUnopened", audience)  => Some(MarkerUnopened(Raw(s"$audience")))
        case Vector("ToolUnknown", tool)         => Some(ToolUnknown(Raw(s"$tool")))
        case Vector(name)                        =>
          word(
            Vector(
              NoHeader,
              HeaderUnclosed,
              HeaderNotMapping,
              NoName,
              NoDescription,
              WebUnknown,
              WritesUnknown,
              TurnsNotPositive,
              InteractiveUnset,
              DescriptionLong
            ),
            name
          )
        case _ => None

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
        case Vector("IdentifierGiven")   => Some(IdentifierGiven)
        case Vector("IdentifierKind", s) => Some(IdentifierKind(Raw(s)))
        case Vector("MergeId", s)        => Some(MergeId(Raw(s)))
        case Vector("DoiRecorded", p)    => word(Invalid.Purpose.values.toSeq, p).map(DoiRecorded(_))
        case Vector("UpstreamRecord")    => Some(UpstreamRecord)
        case Vector("UploadGiven")       => Some(UploadGiven)
        case Vector("UploadBytes", b, l) =>
          for
            bytes <- Count.read(b)
            limit <- Count.read(l)
          yield UploadBytes(bytes, limit)
        case Vector("InlineBytes", b, l) =>
          for
            bytes <- Count.read(b)
            limit <- Count.read(l)
          yield InlineBytes(bytes, limit)
        case Vector("Base64Decodable", r)             => Some(Base64Decodable(External.Reported.of(r)))
        case Vector("PathOrBase64")                   => Some(PathOrBase64)
        case Vector("ServerSees")                     => Some(ServerSees)
        case Vector("OriginalForReocr")               => Some(OriginalForReocr)
        case Vector("OriginalForDownload")            => Some(OriginalForDownload)
        case Vector("Frontmatter")                    => Some(Frontmatter)
        case Vector("NoteCitekey")                    => Some(NoteCitekey)
        case Vector("FilterRecorded")                 => Some(FilterRecorded)
        case Vector("ReviewLibraryWide")              => Some(ReviewLibraryWide)
        case Vector("EnrichLibraryWide")              => Some(EnrichLibraryWide)
        case Vector("SeedGoal")                       => Some(SeedGoal)
        case Vector("WatchGoal")                      => Some(WatchGoal)
        case Vector("EnrichCron")                     => Some(EnrichCron)
        case Vector("HintSettings")                   => Some(HintSettings)
        case Vector("HintUntil")                      => Some(HintUntil)
        case Vector("HintTargets")                    => Some(HintTargets)
        case Vector("CronGiven")                      => Some(CronGiven)
        case Vector("ChainGiven")                     => Some(ChainGiven)
        case Vector("HintConfig", allowed)            => Some(HintConfig(raws(allowed)))
        case Vector("RoleUsed", o, name, role, roles) =>
          word(Invalid.RoleOwner.values.toSeq, o).map(RoleUsed(_, Raw(name), Raw(role), raws(roles)))
        case Vector("PointOnly", p, l)       => Some(PointOnly(Raw(p), Raw(l)))
        case Vector("NameByHand", n, p, l)   => Some(NameByHand(Raw(n), Raw(p), Raw(l)))
        case Vector("FocusRegion", n, known) => Some(FocusRegion(Raw(n), raws(known)))
        case Vector("FocusDeeper", e)        => Count.read(e).map(FocusDeeper(_))
        case Vector("LabelsUnranged")        => Some(LabelsUnranged)
        case Vector("SeaFloorUnranged")      => Some(SeaFloorUnranged)
        case Vector("ProviderOf", m, p)      => Some(ProviderOf(Raw(m), Raw(p)))
        case Vector("OpencodeModelShape", m) => Some(OpencodeModelShape(Raw(m)))
        case Vector("HostProvider", p)       => Some(HostProvider(Raw(p)))
        case Vector("OptionOf", o, w, p)     => Some(OptionOf(Raw(o), Raw(w), Raw(p)))
        case Vector("NewArticleFilter", t)   => Some(NewArticleFilter(Raw(t)))
        case Vector("NewArticleTitle", t)    => Some(NewArticleTitle(Raw(t)))
        case Vector("RoundsWithin", s, l, c) =>
          for
            stated <- Count.read(s)
            least <- Count.read(l)
            cap <- Count.read(c)
          yield RoundsWithin(stated, least, cap)
        case Vector("RegionWithin", x, y, w, h, wd, ht) =>
          for
            cx <- Count.read(x)
            cy <- Count.read(y)
            cw <- Count.read(w)
            ch <- Count.read(h)
            width <- Count.read(wd)
            height <- Count.read(ht)
          yield RegionWithin(cx, cy, cw, ch, width, height)
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
    private val UnparsableAt = s"(?s)^${Prefix}the (\\w+) input at (.+?) is damaged: (.*)$$".r
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
    private val SwitchedOff =
      "(?s)^(.+) is switched off by (\\S+?)(?:; (\\S+)=true switches it on)?(?: \\((\\w+)\\))?$".r
    private val Answered = s"(?s)^$Prefix(\\S+) answered HTTP (\\d+) at (\\S+) — (.*)$$".r
    private val ChallengedAt = s"(?s)^$Prefix(\\S+) challenged with HTTP (\\d+) at (\\S+)$$".r
    private val NotAsked = s"(?s)^$NotAskedThisSweep(.*)$$".r
    private val LimitedTill = "^(\\S+) is limited(?: until (\\S+))?$".r
    private val CannotReach = s"(?s)^${Prefix}cannot reach (\\S+) at (\\S+) — (.*)$$".r
    private val NoAnswer = s"(?s)^$Prefix(\\S+) did not answer at (\\S+) within (\\d+) ms$$".r
    private val NotShaped = s"(?s)^$Prefix(\\S+)'s answer at (\\S+) is not (\\w+) — (.*)$$".r
    private val CouldNot = "(?s)^could not (\\w+) (.+?) — (.*)$".r
    private val IsNot = "(?s)^(.+) is not (\\w+)$".r
    private val IsOutside = "(?s)^(.+) is outside (.+)$".r
    private val Damaged = "(?s)^the (\\w+) data at (.+?) is damaged: (.*)$".r
    private val NamedName = "(?s)^no name '(.*)' in '(.*)' for '(.*)' \\[(.*)\\](?: \\(has: '(.*)'\\))?$".r
    private val NothingByHand = "(?s)^nothing set by hand on '(.*)' \\[(.*)\\]$".r
    private val PlaceReferred = "(?s)^place '(.*)' \\[(.*)\\] is referred to by (.+)$".r
    private val CountedBy = "^(\\d+) (\\w+)$".r
    private val AbsentAt = "(?s)^no (\\w+) at (.+?)(?: \\(set (\\S+)\\))?$".r
    private val ExtractOf = "(?s)^extract '(.*)' cannot be read: (.*)$".r
    private val OversizeAt = "(?s)^(.+) is (\\d+) MB, over (\\S+)$".r
    private val DamageCounts = "^(\\w+) (\\d+)/(\\d+)$".r
    private val DamageNewer = "^Newer (\\S*)/(\\S*)$".r
    private val DamageReported = "(?s)^(\\w+) — (.*)$".r
    private val DamageStructure = "^Structure (\\w+)((?: \\S+)*)$".r
    private val DamageUnsupported = "^Unsupported (\\w+)((?: \\S+)*)$".r
    private val Exited = "(?s)^(.+?) exited with (-?\\d+) — (.*)$".r
    private val Unfinished = "(?s)^(.+?) did not finish within (\\d+) ms$".r
    private val NotStarted = "(?s)^(.+?) could not be started — (.*)$".r
    private val UnansweredBy = "(?s)^(.+?) ended without answering (.+)$".r
    private val DefaultedAs = "(?s)^'(.*)' \\((.*)\\) as '(.*)'$".r
    private val SaidBy = "(?s)^(.+?) said — (.*)$".r
    private val McpServerIs = "^agent mcp server (\\S+)$".r
    private val ContentHashes = "(?s)^content of (.+) hashes to (\\S+), not (\\S+)$".r
    private val ApiKeyLoginBy = "^api key login (\\S+)$".r
    private val UnauthenticatedBy = "^unauthenticated: needs (\\w+)$".r
    private val MisdirectedTo = "(?s)^misdirected to host '(.*)'$".r
    private val ExposedOn = "(?s)^exposed on '(.*)' to '(.*)': (\\w+)$".r
    private val ProxyUndeclaredFor = "(?s)^undeclared proxy for '(.*)'$".r
    private val WildcardOverlaps = "(?s)^wildcard '(.*?)' overlaps '(.*)'$".r
    private val FloorsUnreadSaved = "(?s)^floors unread, saved \\((.*)\\)$".r
    private val FloorsUnreadOf = "(?s)^floors unread, (\\S+) \\((.*)\\)$".r
    private val SettingsReadAt = "(?s)^settings (.+?) read at (.+?) \\((.*)\\)$".r
    private val GuardStoppedBy = "(?s)^stopped by the quota guard: (.*)$".r
    private val NoLoginFor = "(?s)^no login: (.*)$".r
    private val ProbeUnreadExit = "(?s)^probe read no rate limit, exit (-?\\d+)(?: — (.*))?$".r
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
    private val NoModelFor = "(?s)^no model of the (\\S+) chain can run(?: \\((.*)\\))?$".r
    private val ChainSpent =
      "(?s)^all models of the (\\S+) chain are spent \\((\\w+), (At (\\S+) (\\S+)|Released|Never)((?: \\| [^|\\[\\]]+ \\[[^\\]]+\\])*)\\)(?: \\((.*)\\))?$".r
    private val PassedOverWhy = "(?s)^(\\S+) passed over \\((.*)\\)$".r
    private val NoModelSet = "^no model set for (\\S+) \\((\\w+)\\)$".r
    private val CoolingUntil = "^cooling until (\\S+)$".r
    private val BelowFloorOf = "(?s)^below the floor of (.+?) \\((.*)\\)$".r
    private val BelowAllowanceOf = "(?s)^below its allowance: (.*)$".r
    private val AllowanceUnreadable = "(?s)^allowance unreadable \\((.*)\\)$".r
    private val PlanOnlyFor = "^no fall from a plan to a paid key for (\\S+)$".r
    private val StalledAt = "^stalled at step (\\d+): (\\d+) min, (\\d+) times$".r
    private val SilentAt = "^silent at step (\\d+): (\\d+) min, stall (\\d+) of (\\d+)$".r
    private val NoPipelineIn = "(?s)^no pipeline '(.*)' in this build$".r
    private val StepMovedFrom = "(?s)^step (\\d+) moved from '(.*)' to '(.*)'$".r
    private val CheckpointUnreadable = "(?s)^step (\\d+) \\((.*?)\\) checkpoint unreadable — (.*)$".r
    private val RunOverspent = "^run overspent: (\\S+) of (\\S+)$".r
    private val RunOverTokens = "^run over tokens: (\\d+) of (\\d+), cache weight (\\S+)$".r
    private val SpendCapReached = "^spend cap (\\w+) reached: (\\S+)$".r
    private val RunMovedOn = "^run (\\S+) moved on$".r
    private val RunNotNow = "^run (\\S+) is (\\S+): no (\\w+)$".r
    private val AtStepOf = "(?s)^at step (.+?) \\((.*)\\)$".r
    private val UnsettledAt = "(?s)^step (.+) unsettled after (\\d+) attempts$".r
    private val ProviderDownAt = "(?s)^provider down at step (.+?) \\((.*)\\)$".r
    private val BudgetSpentAt = "(?s)^budget spent at step (.+)$".r
    private val NoValidAnswerAt = "(?s)^no valid answer at step (.+?) after (\\d+) \\((.*)\\)$".r
    private val HeldPoolOf = " \\| ([^|\\[\\]]+) \\[([^\\]]+)\\]".r
    private val PartUnbuilt = "(?s)^part (\\S+) unbuilt \\((.*)\\)$".r
    private val ReloadChangedNothing = "(?s)^reload changed nothing \\((.*)\\)$".r
    private val DrainedAs = "(?s)^drained (\\w+)(?: (\\d+) s)?: (.*)$".r
    private val DefinitionBroken = "(?s)^definition (.+?) is broken: (.*)$".r
    private val AtModelOf = "(?s)^at model (\\S+) \\((.*)\\)$".r
    private val CallFailedFor = "(?s)^the call to (\\S+) failed \\((.*)\\)$".r
    private val CallThrewFor = "(?s)^the call to (\\S+) threw — (.*)$".r
    private val StepFailed = "(?s)^step failed \\((.*)\\)$".r
    private val AnswerRejected = "(?s)^answer rejected \\((.*)\\)$".r
    private val LimitReachedUntil = "(?s)^limit reached, skipped until (\\S+) \\((.*)\\)$".r
    private val AllowanceReachedBy = "^allowance reached by (\\S+) mid-step$".r
    private val OutputOverCap = "(?s)^output over the cap at step (.+?): (\\S+), cap (\\d+), sent (\\d+), (\\w+)$".r
    private val NoTextIn = "(?s)^no text in '(.*)'$".r
    private val NoBodyIn = "(?s)^no body to review in '(.*)'$".r
    private val Undecodable =
      "(?s)^answer undecodable: (\\w+)(?: at (.*?))?(?: — (.*?))?(?: ‖ finish (.*?))?(?: ‖ began (.*))?$".r

    /** The models a chain passed over, from what [[passedStored]] wrote in the parentheses. */
    private def passedOf(written: String | Null): Option[Vector[Problem.Run.PassedOver]] =
      Option(written).fold(Some(Vector.empty)) { w =>
        val each = w.split(java.util.regex.Pattern.quote(PassedSeparator), -1).toVector.map(read)
        Option.when(each.forall(_.isInstanceOf[Problem.Run.PassedOver]))(each.collect {
          case p: Problem.Run.PassedOver => p
        })
      }

    /** The problems of one model passed over, or of an allowance's target. */
    private def whysOf(written: String): Option[NonEmptyVector[Problem]] =
      NonEmptyVector.from(written.split(java.util.regex.Pattern.quote(WhySeparator), -1).toVector.map(read))

    private def heldOf(written: String): Vector[Problem.Run.HeldPool] =
      HeldPoolOf
        .findAllMatchIn(written)
        .map(m => Problem.Run.HeldPool(Raw(s"${m.group(1)}"), Raw(s"${m.group(2)}")))
        .toVector
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

    /** A damage as [[Io.Damage.stored]] wrote it: its name, then two counts, what the reader reported, or its flaw. */
    private def damageOf(damage: String): Option[Io.Damage] = damage match
      case DamageCounts("Length", e, f) =>
        for
          ec <- Count.read(e)
          fc <- Count.read(f)
        yield Io.Damage.Length(ec, fc)
      case DamageCounts("Schema", e, f) =>
        for
          ec <- Count.read(e)
          fc <- Count.read(f)
        yield Io.Damage.Schema(ec, fc)
      case DamageNewer(e, f)               => Some(Io.Damage.Newer(Raw(s"$e"), Raw(s"$f")))
      case DamageReported("Unreadable", r) => Some(Io.Damage.Unreadable(External.Reported(s"$r")))
      case DamageCounts("Bytes", e, f)     =>
        for
          ec <- Count.read(e)
          fc <- Count.read(f)
        yield Io.Damage.Bytes(ec, fc)
      case DamageReported("Inflate", r)     => Some(Io.Damage.Inflate(External.Reported(s"$r")))
      case DamageStructure(flaw, held)      => flawOf(flaw, held.split(' ').toVector.filter(_.nonEmpty))
      case DamageUnsupported(variant, held) => variantOf(variant, held.split(' ').toVector.filter(_.nonEmpty))
      case other                            => word(Damages, other)

    private val FieldlessFlaws = Vector(
      Io.Flaw.NotFeatureCollection,
      Io.Flaw.NoLandPolygons,
      Io.Flaw.NotArray,
      Io.Flaw.NoBuild,
      Io.Flaw.NoImageHeader,
      Io.Flaw.TooDeep,
      Io.Flaw.RootOutside,
      Io.Flaw.MetadataOver,
      Io.Flaw.PrefixTooLong,
      Io.Flaw.PrefixIncomplete,
      Io.Flaw.PrefixInvalid,
      Io.Flaw.ChunkTruncated,
      Io.Flaw.StreamTruncated,
      Io.Flaw.NotVp8l,
      Io.Flaw.TransformTwice,
      Io.Flaw.CacheSize,
      Io.Flaw.SymbolOutside,
      Io.Flaw.CodeLengthsOver,
      Io.Flaw.CodeLengthsPast,
      Io.Flaw.ReferenceOutside,
      Io.Flaw.CacheWithout,
      Io.Flaw.CacheIndexOutside,
      Io.Flaw.NotLittleEndian,
      Io.Flaw.NoStripOffsets,
      Io.Flaw.NoPixelScale,
      Io.Flaw.NoTiePoint
    )

    /** A flaw as [[Io.Flaw.stored]] wrote it: its name, then what it holds. */
    private def flawOf(name: String, held: Vector[String]): Option[Io.Damage] =
      val flaw: Option[Io.Flaw] = (name, held) match
        case ("NegativeAt", Vector(q, b)) =>
          for
            quantity <- word(Io.Quantity.values.toSeq, q)
            byte <- Count.read(b)
          yield Io.Flaw.NegativeAt(quantity, byte)
        case ("DirectorySize", Vector(n))  => Count.read(n).map(Io.Flaw.DirectorySize(_))
        case ("NegativeEntry", Vector(n))  => Count.read(n).map(Io.Flaw.NegativeEntry(_))
        case ("OffsetOutside", Vector(n))  => Count.read(n).map(Io.Flaw.OffsetOutside(_))
        case ("NegativeLength", Vector(n)) => Count.read(n).map(Io.Flaw.NegativeLength(_))
        case ("RangeBytes", Vector(g, a))  =>
          for
            got <- Count.read(g)
            asked <- Count.read(a)
          yield Io.Flaw.RangeBytes(got, asked)
        case ("NoTag", Vector(n))         => Count.read(n).map(Io.Flaw.NoTag(_))
        case ("NoStripForRow", Vector(n)) => Count.read(n).map(Io.Flaw.NoStripForRow(_))
        case (other, Vector())            => word(FieldlessFlaws, other)
        case _                            => None
      flaw.map(Io.Damage.Structure(_))

    private val FieldlessVariants =
      Vector(Io.Variant.LossyWebP, Io.Variant.NoLossless, Io.Variant.Vp8lVersion, Io.Variant.BigTiff, Io.Variant.Tiled)

    /** A variant as [[Io.Variant.stored]] wrote it: its name, then what it holds. */
    private def variantOf(name: String, held: Vector[String]): Option[Io.Damage] =
      def one(n: String)(make: Count => Io.Variant): Option[Io.Variant] = Count.read(n).map(make)
      val variant: Option[Io.Variant] = (name, held) match
        case ("PngLayout", Vector(c, d, i)) =>
          for
            colour <- Count.read(c)
            depth <- Count.read(d)
            interlace <- Count.read(i)
          yield Io.Variant.PngLayout(colour, depth, interlace)
        case ("PngFilter", Vector(f))      => one(f)(Io.Variant.PngFilter(_))
        case ("SpecVersion", Vector(f, r)) =>
          for
            found <- Count.read(f)
            reads <- Count.read(r)
          yield Io.Variant.SpecVersion(found, reads)
        case ("Compression", Vector(f, r))  => Some(Io.Variant.Compression(Raw(f), Raw(r)))
        case ("SampleBits", Vector(b))      => one(b)(Io.Variant.SampleBits(_))
        case ("TiffCompression", Vector(c)) => one(c)(Io.Variant.TiffCompression(_))
        case ("Bands", Vector(n))           => one(n)(Io.Variant.Bands(_))
        case ("Planar", Vector(p))          => one(p)(Io.Variant.Planar(_))
        case ("SampleFormat", Vector(f))    => one(f)(Io.Variant.SampleFormat(_))
        case (other, Vector())              => word(FieldlessVariants, other)
        case _                              => None
      variant.map(Io.Damage.Unsupported(_))

    /** A pinning as [[Io.Pinning.stored]] wrote it: its name, then what it holds. */
    private def pinningOf(written: Vector[String]): Option[Io.Pinning] = written match
      case Vector("Unlisted", m)               => Some(Io.Pinning.Unlisted(Io.Path(m)))
      case Vector("NotBuilt", m)               => Some(Io.Pinning.NotBuilt(Io.Path(m)))
      case Vector("Mismatch", v, path, f, pin) => Some(Io.Pinning.Mismatch(Raw(v), Io.Path(path), Raw(f), Raw(pin)))
      case Vector("Unhosted", v, file, path, sha, b, setting, variable) =>
        Count
          .read(b)
          .map(
            Io.Pinning
              .Unhosted(Raw(v), Raw(file), Io.Path(path), Raw(sha), _, SettingKey(setting), SettingKey(variable))
          )
      case _ => None

    private val Readers: List[String => Option[Problem]] = List(
      // the cases that hold other problems first: a nested sentence may end in free text (a type's name, a reading),
      // so the text may also read as a path in front of that sentence; the case that wrote it is the one meant
      {
        case NoModelFor(role, passed) => passedOf(passed).map(Problem.Run.NoModel(Problem.Run.Role(s"$role"), _))
        case _                        => None
      },
      {
        case ChainSpent(role, floor, frees, at, model, held, passed) =>
          val read: Option[Problem.Run.Frees] = frees match
            case "Never"    => Option.when(held.isEmpty)(Problem.Run.Frees.Never)
            case "Released" => NonEmptyVector.from(heldOf(held)).map(Problem.Run.Frees.Released(_))
            case _          => momentOf(at).map(Problem.Run.Frees.At(_, Problem.Run.Model(s"$model"), heldOf(held)))
          for
            f <- word(Problem.Run.Floor.values.toSeq, floor)
            fr <- read
            ps <- passedOf(passed)
          yield Problem.Run.Exhausted(Problem.Run.Role(s"$role"), f, fr, ps)
        case _ => None
      },
      {
        case PassedOverWhy(model, why) => whysOf(why).map(Problem.Run.PassedOver(Problem.Run.Model(s"$model"), _))
        case _                         => None
      },
      {
        case NoModelSet(role, asked) =>
          word(Problem.Run.Asked.values.toSeq, asked).map(Problem.Run.Unassigned(Problem.Run.Role(s"$role"), _))
        case _ => None
      },
      {
        case CoolingUntil(until) => momentOf(until).map(Problem.Run.Cooling(_))
        case _                   => None
      },
      {
        case BelowFloorOf(pool, reading) => Some(Problem.Run.BelowFloor(Raw(s"$pool"), Raw(s"$reading")))
        case _                           => None
      },
      {
        case BelowAllowanceOf(written) =>
          val limits = written.split(java.util.regex.Pattern.quote(PassedSeparator), -1).toVector.map {
            _.split(java.util.regex.Pattern.quote(HoldSeparator), -1).toVector match
              case Vector(l, u, r) => Some(Problem.Run.Spent(Raw(s"$l"), Raw(s"$u"), Raw(s"$r")))
              case _               => None
          }
          Option
            .when(limits.forall(_.isDefined))(limits.flatten)
            .flatMap(NonEmptyVector.from)
            .map(Problem.Run.BelowAllowance(_))
        case _ => None
      },
      {
        case AllowanceUnreadable(written) =>
          val broken = written.split(java.util.regex.Pattern.quote(PassedSeparator), -1).toVector.map { b =>
            b.indexOf(": ") match
              case -1 => None
              case i  => whysOf(b.drop(i + 2)).map(Problem.Run.Unread(Raw(s"${b.take(i)}"), _))
          }
          Option
            .when(broken.forall(_.isDefined))(broken.flatten)
            .flatMap(NonEmptyVector.from)
            .map(Problem.Run.AllowanceUnread(_))
        case _ => None
      },
      {
        case PlanOnlyFor(role) => Some(Problem.Run.PlanOnly(Problem.Run.Role(s"$role")))
        case _                 => None
      },
      {
        case AtStepOf(step, why) => whysOf(why).map(Problem.Run.AtStep(Problem.Run.Step(s"$step"), _))
        case _                   => None
      },
      {
        case ProviderDownAt(step, why) => whysOf(why).map(Problem.Run.ProviderDown(Problem.Run.Step(s"$step"), _))
        case _                         => None
      },
      {
        case StepFailed(why)           => whysOf(why).map(Problem.Run.Failed(_))
        case PartUnbuilt(scope, why)   => whysOf(why).map(Config.Unbuilt(Config.Scope(s"$scope"), _))
        case ReloadChangedNothing(why) => Some(Config.Unchanged(read(why)))
        case AtModelOf(model, why)     => whysOf(why).map(Problem.Run.AtModel(Problem.Run.Model(s"$model"), _))
        case CallFailedFor(model, why) => Some(Upstream.CallFailed(Upstream.Name(s"$model"), read(why)))
        case CallThrewFor(model, r)    => Some(Upstream.CallThrew(Upstream.Name(s"$model"), External.Reported(s"$r")))
        case AnswerRejected(ps)        =>
          Some(Problem.Run.Declined(if ps.isEmpty then Vector.empty else whysOf(ps).fold(Vector.empty)(_.toVector)))
        case LimitReachedUntil(until, why) => momentOf(until).map(Problem.Run.LimitReached(read(why), _))
        case _                             => None
      },
      {
        case NoValidAnswerAt(step, attempts, ps) =>
          for n <- Count.read(attempts)
          yield Problem.Run.NoValidAnswer(
            Problem.Run.Step(s"$step"),
            n,
            if ps.isEmpty then Vector.empty else whysOf(ps).fold(Vector.empty)(_.toVector)
          )
        case _ => None
      },
      {
        case StalledAt(step, minutes, times) =>
          for
            st <- Count.read(step)
            m <- Count.read(minutes)
            t <- Count.read(times)
          yield Problem.Run.Stalled(st, m, t)
        case SilentAt(step, minutes, stall, of) =>
          for
            st <- Count.read(step)
            m <- Count.read(minutes)
            n <- Count.read(stall)
            o <- Count.read(of)
          yield Problem.Run.Silent(st, m, n, o)
        case NoPipelineIn(pipeline)            => Some(Problem.Run.NoPipeline(Raw(s"$pipeline")))
        case "pipeline changed"                => Some(Problem.Run.PipelineChanged)
        case StepMovedFrom(seq, recorded, now) =>
          Count.read(seq).map(Problem.Run.Reordered(_, Problem.Run.Step(s"$recorded"), Problem.Run.Step(s"$now")))
        case CheckpointUnreadable(seq, step, r) =>
          Count.read(seq).map(Problem.Run.CheckpointUnread(_, Problem.Run.Step(s"$step"), External.Reported(s"$r")))
        case "run vanished"                 => Some(Problem.Run.Vanished)
        case RunOverspent(spent, budget)    => Some(Problem.Run.Overspent(Raw(s"$spent"), Raw(s"$budget")))
        case RunOverTokens(used, budget, w) =>
          for
            u <- Count.read(used)
            b <- Count.read(budget)
          yield Problem.Run.OverTokens(u, b, Raw(s"$w"))
        case SpendCapReached(cap, amount) =>
          word(Problem.Run.Cap.values.toSeq, cap).map(Problem.Run.OverCap(_, Raw(s"$amount")))
        case RunMovedOn(run)                 => Some(Problem.Run.MovedOn(Raw(s"$run")))
        case RunNotNow(run, status, control) =>
          word(Problem.Run.Control.values.toSeq, control).map(Problem.Run.NotNow(Raw(s"$run"), Raw(s"$status"), _))
        case "rejected: the run was cancelled" => Some(Problem.Run.Withdrawn)
        case UnsettledAt(step, attempts)       =>
          Count.read(attempts).map(Problem.Run.Unsettled(Problem.Run.Step(s"$step"), _))
        case BudgetSpentAt(step)       => Some(Problem.Run.BudgetSpent(Problem.Run.Step(s"$step")))
        case AllowanceReachedBy(model) => Some(Problem.Run.AllowanceReached(Problem.Run.Model(s"$model")))
        case OutputOverCap(step, model, cap, sent, setting) =>
          for
            c <- Count.read(cap)
            n <- Count.read(sent)
            st <- word(Problem.Run.Ceiling.values.toSeq, setting)
          yield Problem.Run.OutputCapped(Problem.Run.Step(s"$step"), Problem.Run.Model(s"$model"), c, n, st)
        case DrainedAs(drain, seconds, component) =>
          val read: Option[Config.Drain] = (drain, Option(seconds)) match
            case ("InUse", Some(s))    => Count.read(s).map(Config.Drain.InUse(_))
            case ("Unclosed", Some(s)) => Count.read(s).map(Config.Drain.Unclosed(_))
            case ("ClosedInUse", None) => Some(Config.Drain.ClosedInUse)
            case _                     => None
          read.map(Config.Drained(Raw(s"$component"), _))
        case DefinitionBroken(file, written) =>
          flawOf(written.split(Regex.quote(HoldSeparator), -1).toVector).map(Config.Definition(Raw(s"$file"), _))
        case NoTextIn(source)                                 => Some(Problem.Run.NoText(Raw(s"$source")))
        case NoBodyIn(article)                                => Some(Problem.Run.NoBody(Raw(s"$article")))
        case Undecodable(kind, path, reported, finish, began) =>
          val why: Option[Content.Decoding] = (kind, Option(path), Option(reported)) match
            case ("Mismatch", Some(p), Some(r)) => Some(Content.Decoding.Mismatch(Raw(s"$p"), External.Reported(s"$r")))
            case ("NotJson", None, Some(r))     => Some(Content.Decoding.NotJson(External.Reported(s"$r")))
            case ("Unreadable", None, Some(r))  => Some(Content.Decoding.Unreadable(External.Reported(s"$r")))
            case ("NotSchema", None, None)      => Some(Content.Decoding.NotSchema)
            case _                              => None
          why.map(
            Content.Undecoded(
              _,
              Option(finish).map(f => External.Reported(s"$f")),
              Option(began).map(b => External.ModelSaid(s"$b"))
            )
          )
        case _ => None
      },
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
            case ("StoredAs", Vector(citekey))          => Some(Conflict.Hold.StoredAs(citekey))
            case ("Focus", Vector(goal))                => Some(Conflict.Hold.Focus(goal))
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
        case UnparsableAt(at, format, where, damage) =>
          for
            f <- word(Io.Format.values.toSeq, format)
            d <- damageOf(damage)
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
        case SwitchedOff(feature, setting, variable, effect) =>
          val read: Option[Option[Refused.Effect]] =
            Option(effect).fold(Some(None))(e => word(Refused.Effect.values.toSeq, e).map(Some(_)))
          read.map(
            Refused.Disabled(
              Refused.Feature(s"$feature"),
              SettingKey(s"$setting"),
              Option(variable).map(v => SettingKey(s"$v")),
              _
            )
          )
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
        case NotAsked(why) => Some(Upstream.Unasked(read(why)))
        case _             => None
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
        case Damaged(format, at, damage) =>
          for
            f <- word(Io.Format.values.toSeq, format)
            d <- damageOf(damage)
          yield Io.Corrupt(f, Where(s"$at"), d)
        case _ => None
      },
      {
        case NamedName(name, lang, label, place, has) =>
          Some(NotFound.Name(Raw(s"$place"), Raw(s"$label"), Raw(s"$name"), Raw(s"$lang"), quotedListOf(has)))
        case _ => None
      },
      {
        case NothingByHand(label, place) => Some(NotFound.SetByHand(Raw(s"$place"), Raw(s"$label")))
        case _                           => None
      },
      {
        case NotDownloaded(layer, fetch) => Some(NotFound.Undownloaded(Raw(s"$layer"), Raw(s"$fetch")))
        case _                           => None
      },
      {
        case OfflineTill(name, at) => untilOf(at).map(Upstream.Offline(Upstream.Name(s"$name"), _))
        case _                     => None
      },
      {
        case FileChanged(name) => Some(Upstream.Changed(Upstream.Name(s"$name")))
        case _                 => None
      },
      {
        case ChangedDuring(url) => Some(Upstream.Rebuilt(Upstream.Url(s"$url")))
        case _                  => None
      },
      {
        case ListedSize(url, size, listed, build) =>
          for
            s <- Count.read(size)
            l <- Count.read(listed)
          yield Upstream.Mislisted(Upstream.Url(s"$url"), s, l, Raw(s"$build"))
        case _ => None
      },
      {
        case NoRoom(written) =>
          written.split(Regex.quote(HoldSeparator), -1).toVector match
            case Vector(w, n, on, f, l) =>
              val work: Option[Refused.Work] = w match
                case "SeaFloor"                          => Some(Refused.Work.SeaFloor)
                case "Gebco"                             => Some(Refused.Work.Gebco)
                case "Labels"                            => Some(Refused.Work.Labels)
                case layer if layer.startsWith("Layer ") =>
                  Some(Refused.Work.Layer(Raw(s"${layer.stripPrefix("Layer ")}")))
                case _ => None
              for
                wk <- work
                needs <- Count.read(n)
                free <- Count.read(f)
                least <- Count.read(l)
              yield Refused.Space(wk, needs, Io.Path(s"$on"), free, least)
            case _ => None
        case _ => None
      },
      {
        case PlaceReferred(label, place, written) =>
          val refs = written.split(ListSeparator, -1).toVector.map {
            case CountedBy(n, by) =>
              for
                count <- Count.read(n)
                referrer <- word(Conflict.Referrer.values.toSeq, by)
              yield Conflict.Reference(count, referrer)
            case _ => None
          }
          Option
            .when(refs.forall(_.isDefined))(refs.flatten)
            .flatMap(NonEmptyVector.from)
            .map(Conflict.Referred(Raw(s"$place"), Raw(s"$label"), _))
        case _ => None
      },
      {
        case AbsentAt(held, path, setting) =>
          word(Io.Held.values.toSeq, held)
            .map(Io.Absent(_, Io.Path(s"$path"), Option(setting).map(s => SettingKey(s"$s"))))
        case _ => None
      },
      {
        case ExtractOf(extract, written) =>
          pinningOf(written.split(Regex.quote(HoldSeparator), -1).toVector).map(Io.Extract(Raw(s"$extract"), _))
        case _ => None
      },
      {
        case OversizeAt(path, mb, setting) =>
          Count.read(mb).map(Io.Oversize(Io.Path(s"$path"), _, SettingKey(s"$setting")))
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
        case UnansweredBy(tool, request) => Some(Io.Process(Io.Tool(s"$tool"), Io.Ending.Unanswered(Raw(s"$request"))))
        case _                           => None
      },
      {
        case SaidBy(upstream, said) =>
          Some(Upstream.Replied(Upstream.Name(s"$upstream"), External.Reported(s"$said")))
        case _ => None
      },
      {
        case McpServerIs(status)                  => Some(Upstream.McpServer(Raw(s"$status")))
        case ContentHashes(path, found, expected) =>
          Some(Io.Hashed(Io.Path(s"$path"), Raw(s"$expected"), Raw(s"$found")))
        case ApiKeyLoginBy(method)   => Some(Config.ApiKeyLogin(Raw(s"$method")))
        case UnauthenticatedBy(cred) => word(Refused.Credential.values.toSeq, cred).map(Refused.Unauthenticated(_))
        case MisdirectedTo(host)     => Some(Refused.Misdirected(Raw(s"$host")))
        case ExposedOn(bind, reach, exposure) =>
          word(Config.Exposure.values.toSeq, exposure).map(Config.Exposed(Raw(s"$bind"), Raw(s"$reach"), _))
        case ProxyUndeclaredFor(publicUrl)       => Some(Config.ProxyUndeclared(Raw(s"$publicUrl")))
        case WildcardOverlaps(wildcard, address) =>
          Some(Config.WildcardOverlap(Raw(s"$wildcard"), Raw(s"$address")))
        case FloorsUnreadSaved(why)   => whysOf(why).map(Config.FloorsUnread(Config.FloorsFrom.Saved, _))
        case FloorsUnreadOf(key, why) =>
          whysOf(why).map(Config.FloorsUnread(Config.FloorsFrom.Setting(SettingKey(s"$key")), _))
        case SettingsReadAt(file, at, why) => whysOf(why).map(Config.ReadAt(Io.Path(s"$file"), Io.Path(s"$at"), _))
        case GuardStoppedBy(why)           => Some(Problem.Run.GuardStopped(Raw(s"$why")))
        case NoLoginFor(why)               => Some(Problem.Run.NoLogin(Raw(s"$why")))
        case ProbeUnreadExit(exit, said)   =>
          Io.ExitCode.read(exit).map(Problem.Run.ProbeUnread(_, Option(said).map(m => External.Reported(s"$m"))))
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
            case ("Unset", Some(v))                                 => Some(Config.Fault.Unset(SettingKey(s"$v")))
            case ("Malformed", Some(_))                             => quoted.map(Config.Fault.Malformed(_))
            case ("Unknown", Some(_))                               => quoted.map(Config.Fault.Unknown(_))
            case ("Unregistered", Some(_))                          => quoted.map(Config.Fault.Unregistered(_))
            case ("Defaulted", Some(DefaultedAs(raw, known, used))) =>
              Some(Config.Fault.Defaulted(Raw(s"$raw"), raws(known), Raw(s"$used")))
            case ("NoPlugin", Some(_))           => quoted.map(Config.Fault.NoPlugin(_))
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
