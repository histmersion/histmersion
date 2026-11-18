package com.histmersion
package util.problem
import com.histmersion.util.nonempty.NonEmptyVector

import java.time.Instant

import Problem.*

/** The English sentence of every problem: what the HTTP body's `text`, the CLI's `error:` line, the MCP tool's error
  * and the web's error line show. It may change wording freely; what a column stores is [[Stored]]'s, frozen.
  *
  * Outside text ([[External]]) follows the case's own words after an em dash.
  */
object Text:

  extension (problem: Problem)
    /** The sentence that says what failed. */
    def sentence: String = problem match
      case Invalid.Malformed(t, c, raw, at)    => s"${path(at)}'$raw' is not ${t.aNoun}: ${c.description}"
      case Invalid.Unknown(t, raw, known, at)  => s"${path(at)}unknown ${t.noun} '$raw'${oneOf(known)}"
      case Invalid.Unreadable(t, raw, eg, at)  => s"${path(at)}'$raw' is not ${t.aNoun}${forExample(eg)}"
      case Invalid.Missing(field, at)          => s"${path(at)}$field is required"
      case Invalid.Undeclared(field, ds, at)   => s"${path(at)}unknown key '$field'${oneOf(ds.map(d => Raw(s"$d")))}"
      case Invalid.Wrapped(_, raw, member, at) =>
        s"${path(at)}a '$raw' is not the value itself: give its \"$member\" member"
      case Invalid.StoredOn(_, item, owner, at) =>
        s"${path(at)}$item is stored on '$owner', not here: patch '$owner' to record it"
      case Invalid.Ambiguous(t, raw, n, at) =>
        s"${path(at)}the ${t.noun} '$raw' matches $n places — quote more of it so it matches one"
      case Invalid.Repeated(t, raw, at)        => s"${path(at)}${t.noun} '$raw' is given more than once"
      case Invalid.Misplaced(field, under, at) => s"${path(at)}'$field' belongs under '$under' ($under.$field)"
      case Invalid.OutOfRange(t, raw, b, at)   => s"${path(at)}${t.noun} $raw is ${b.range}"
      case Invalid.Inverted(start, end, at)    =>
        s"${path(at)}the period would start (${start.era}) after it ends (${end.era})"
      case Invalid.OutOfTime(year, least, greatest, at) =>
        s"${path(at)}${year.era} is outside the representable range [${least.era}, ${greatest.era}] — deep time is " +
          "signed YEARS, BCE negative"
      case Invalid.Unparsable(format, where, damage, at) =>
        s"${path(at)}the ${format.named} data at $where ${damage.said(format)}"
      case Invalid.Inconsistent(fs, rule, at)              => s"${path(at)}${rule.of(fs)}"
      case Invalid.Reference(form, at)                     => s"${path(at)}${form.said}"
      case Invalid.Broken(rule, at)                        => s"${path(at)}${rule.said}"
      case NotFound.Entity(t, key) if t.short == "Citekey" => s"No source '$key'"
      // by its noun: the name the newtype module captured may end in a form segment (`ProviderKind.Wire.T`)
      case NotFound.Entity(t, key) if t.noun == "provider kind" =>
        s"no AI provider '$key' is registered (its plugin is switched off or was removed)"
      case NotFound.Entity(t, key)                 => s"no ${t.noun} '$key'"
      case NotFound.Listed(t, key, listing)        => s"no ${t.noun} '$key' (${listing.command} lists them)"
      case NotFound.Copy(work, answers)            => s"no legal open-access PDF found for $work${answered(answers)}"
      case NotFound.Dangling(t, key, from, within) => within.missed(t, key, from)
      case NotFound.Name(place, label, name, lang, has) =>
        s"'$label' [$place] has no name '$name'${if s"$lang".isEmpty then "" else s" in $lang"}${hasIn(has)}"
      case NotFound.SetByHand(place, label)    => s"'$label' [$place] has no kind, period or point set by hand"
      case NotFound.Undownloaded(layer, fetch) => s"the $layer layer is not downloaded — run `$fetch`"
      case Conflict.Held(subject, hold)        => hold.said(subject)
      case Conflict.Job(job, status, control)  => control.said(job, status)
      case Conflict.AlreadyExists(t, key)      => s"${t.noun} '$key' already exists"
      case Conflict.Renamed(t, asked, now)     => s"${t.noun} '$asked' was renamed to '$now' — write to '$now' instead"
      case Conflict.Aliased(t, key, of)        =>
        s"'$key' is an old ${t.noun} of '$of' and still resolves to it — pick another"
      case Conflict.OldKey(t, asked, now, action) =>
        s"'$asked' is an old ${t.noun} of '$now' — ${words(action.productPrefix)} '$now' instead"
      case Conflict.Collides(t, key, by)     => s"${t.noun} '$key' already exists — '$by' would collide with it"
      case Conflict.Claimed(t, key, clash)   => clash.said(t, key)
      case Conflict.Stale(t, key, sent, now) =>
        s"${t.noun} '$key' has moved on: you sent $sent, it is now $now — read it again and re-apply your change"
      case Conflict.WrongState(t, key, is, ws) => s"${t.noun} '$key' is $is; this needs ${ws.mkString(" or ")}"
      case Conflict.Busy(activity)             => activity.running
      case Conflict.Referenced(t, key, by, n)  =>
        s"${t.noun} '$key' is referred to by ${by.counted(n)} — remove those references first (there is no override)"
      case Conflict.Referred(place, label, refs) =>
        s"'$label' [$place] is referred to by ${refs.toVector.map(_.said).mkString(", ")} — merge it into the place it " +
          "is the same as instead, or move those first"
      case Refused.Policy(rule)                               => rule.said
      case Refused.Quota(limit, used, cap, at)                => s"${limit.named} is spent: $used of $cap${until(at)}"
      case Refused.Space(work, needs, on, free, least)        => work.refused(needs, on, free, least)
      case Refused.Unauthenticated(Refused.Credential.ApiKey) =>
        "this server needs its API key from your network — send it as `Authorization: Bearer <key>` (the CLI " +
          "reads HISTMERSION_TOKEN or the file cli.token-file names; the web UI asks for it once)"
      case Refused.Unauthenticated(Refused.Credential.RunToken) => "a live run token is required"
      case Refused.Misdirected(host)                            =>
        s"this server does not answer to the name '$host' — if that is its own name, add it to auth.hosts on the " +
          "server and restart it"
      case Refused.Disabled(feature, setting, variable, None) =>
        s"$feature is switched off ($setting = false)${variable.fold("")(v => s" — set $v=true on the server to use it")}"
      case Refused.Disabled(feature, setting, variable, Some(Refused.Effect.WritesLibrary)) =>
        s"$feature is switched off ($setting = false) — set it to true${variable.fold("")(v => s" ($v=true)")} to let " +
          "the server write to the library"
      case Refused.Disabled(feature, setting, variable, Some(Refused.Effect.Unauthorised)) =>
        s"$feature obtains copies without the rights holder's authorisation and stays off ($setting = false)" +
          variable.fold("")(v => s" — set $v=true on the server to allow it")
      case Upstream.Http(name, url, st, body, at) => s"${path(at)}$name answered HTTP $st at $url — $body"
      case Upstream.Challenged(name, url, st, at) =>
        s"${path(at)}$name answered HTTP $st at $url with a bot-protection challenge, not the page"
      case Upstream.Limited(name, at) => s"$name's usage limit is reached${until(at)}"
      case Upstream.Offline(name, _)  => s"$name was unreachable a moment ago; not asked again yet"
      case Upstream.Changed(name)     => s"$name's file changed"
      case Upstream.Rebuilt(url)      =>
        s"$url changed during the download (size or ETag differ) — run the fetch again for the new build"
      case Upstream.Mislisted(url, size, listed, build) =>
        s"$url is $size bytes, but the provider lists $listed for $build"
      case Upstream.Unasked(why)                  => s"not asked this sweep — ${why.sentence}"
      case Upstream.Unreachable(name, url, r, at) => s"${path(at)}cannot reach $name at $url — $r"
      case Upstream.Timeout(name, url, after, at) => s"${path(at)}$name did not answer at $url within $after ms"
      case Upstream.CallFailed(model, why)        => s"the call to $model failed: ${why.sentence}"
      case Upstream.CallThrew(model, reported)    => s"the call to $model failed: $reported"
      case Upstream.Replied(upstream, said)       => s"$upstream: $said"
      case Upstream.McpServer(status)             => s"the agent MCP server is $status"
      case Upstream.BadReply(name, url, s, r, at) =>
        s"${path(at)}$name's answer at $url is not ${s.noun} — $r"
      case Io.File(op, path, reported)      => s"could not ${words(op.productPrefix)} $path — $reported"
      case Io.Wrong(path, expected)         => s"$path ${expected.missed}"
      case Io.Corrupt(format, at, damage)   => s"the ${format.named} data at $at ${damage.said(format)}"
      case Io.Absent(held, path, setting)   => held.missed(path, setting)
      case Io.Hashed(path, expected, found) =>
        s"$path: its content hashes to $found, not to $expected, the hash it is stored under"
      case Io.Extract(extract, pinning)   => pinning.said(extract)
      case Io.Oversize(path, mb, setting) =>
        s"$path is $mb MB, over $setting — convert it to one feature per line (.ndjson)"
      case Io.Process(tool, ending)        => s"$tool ${ending.said}"
      case Io.Database(op, reported)       => s"the database refused to ${words(op.productPrefix)} — $reported"
      case Io.Entry(archive, entry, fault) => fault.said(words(archive.productPrefix), entry)
      case Config.Setting(key, fault)      => s"$key ${fault.said}"
      case Config.Unavailable(Config.Capability.Supervisor) =>
        "This server was not started by a service that starts it again, so it cannot restart itself. " +
          "Stop it and start it again yourself."
      case Config.Unavailable(Config.Capability.SettingsFile) =>
        "This server was started without a settings file, so Settings cannot change anything. " +
          "Set HISTMERSION_CONFIG to the file to use and start it again."
      case Config.Unavailable(Config.Capability.HotGraph)    => "hot graph: closed"
      case Config.Unavailable(Config.Capability.ClaudeLogin) =>
        "not logged in — run `claude`, then /login (or `claude setup-token` for a long-lived token)"
      case Config.Unavailable(Config.Capability.AppServerRead) => "no app-server read in this context"
      case Config.Unavailable(Config.Capability.PlanProbe)     => "no probe in this context"
      case Config.Unavailable(Config.Capability.LoginCheck)    => "not checked in this context"
      case Config.ApiKeyLogin(method)                          =>
        s"logged in with an API key ($method), not a Claude plan — log in with your Claude account, or set use-api-key"
      case Config.Exposed(bind, reach, Config.Exposure.Unlisted) =>
        s"server.host lists '$bind', which can be reached from $reach; none of auth.trusted-networks, " +
          "auth.authenticated-networks and auth.refused-networks names that network — bind to 127.0.0.1 only " +
          "(HISTMERSION_BIND), or list the network as trusted, as authenticated (with auth.api-key set) or as refused"
      case Config.Exposed(bind, reach, Config.Exposure.NoKey) =>
        s"server.host lists '$bind', which can be reached from $reach; auth.authenticated-networks names that " +
          "network, but auth.api-key is empty — set the key (HISTMERSION_API_KEY), list the network in " +
          "auth.refused-networks, or bind to 127.0.0.1 only (HISTMERSION_BIND)"
      case Config.ProxyUndeclared(publicUrl) =>
        s"server.public-url = '$publicUrl' is https, so a reverse proxy serves it, and auth.trusted-networks trusts " +
          "loopback, where a proxy on this machine connects from — declare the proxy by listing loopback in " +
          "auth.proxy-networks (what it forwards then needs auth.api-key), or take loopback out of " +
          "auth.trusted-networks"
      case Config.WildcardOverlap(wildcard, address) =>
        s"server.host lists '$wildcard' and '$address' on one port, but '$wildcard' already listens on every address " +
          s"of this computer — list '$wildcard' alone, or only specific addresses (HISTMERSION_BIND)"
      case Config.BindRepeated(address, again) =>
        s"server.host lists the address '$address' twice (as '$address' and as '$again'), and a second listener " +
          "cannot bind an address the first already holds — list each address once (HISTMERSION_BIND)"
      case Config.FloorsUnread(Config.FloorsFrom.Saved, why) =>
        s"saved floors do not parse: ${why.toVector.map(_.sentence).mkString("; ")}"
      case Config.FloorsUnread(Config.FloorsFrom.Setting(key), why) =>
        s"$key: ${why.toVector.map(_.sentence).mkString("; ")}"
      case Config.ReadAt(file, at, why) =>
        why.toVector.map(_.sentence.replace(s"$at", s"$file")).mkString("; ")
      case Config.Unavailable(capability) => s"this server has no ${words(capability.productPrefix)}"
      case Config.Reload(stage, scope)    => s"reloading the settings failed at ${words(stage.productPrefix)} ($scope)"
      case Config.Cycle(scopes)           =>
        s"the parts a reload rebuilds depend on each other in a cycle: ${scopes.toVector.mkString(" → ")}"
      case Config.Definition(file, flaw) => s"$file${flaw.said}"
      case Config.Unbuilt(scope, why)    => s"$scope: ${why.toVector.map(_.sentence).mkString("; ")}"
      case Config.Unchanged(why)         => s"config reload: nothing changed — ${why.sentence}"
      case Config.Drained(component, Config.Drain.InUse(seconds)) =>
        s"config reload: $component is still in use after $seconds s — it closes when its last user lets go"
      case Config.Drained(component, Config.Drain.ClosedInUse) => s"shutdown: closing $component while still in use"
      case Config.Drained(component, Config.Drain.Unclosed(seconds)) =>
        s"$component: its scope did not close within $seconds s"
      case Config.Unpriced(model, metered) =>
        s"model '$model' has no price — add it to agents.prices (HISTMERSION_AGENTS_PRICES), e.g. \"$model=0/0\" for " +
          s"a free local model${metered.fold("")(m => s" (${m.said})")}"
      case Plugin.Load(plugin, Plugin.Fault.SignerChanged(pinned, seen)) =>
        s"plugin $plugin did not load: it was installed signed by $pinned, this jar is signed by $seen — remove the " +
          s"plugin first with `histmersion plugin rm $plugin` to trust the new key"
      case Plugin.Load(plugin, fault)                     => s"plugin $plugin did not load: ${fault.said}"
      case Plugin.Dependency(plugin, module, want, ships) =>
        s"$plugin needs $module $want, but this Histmersion ships $ships — build the plugin against $ships " +
          s"(the histmersion-plugin-platform POM), or install a Histmersion that ships $want"
      case Plugin.Rejected(plugin, item, why) => s"$plugin did not take $item: ${why.said}"
      case Plugin.Vault(vault, failure)       => s"secret vault $vault failed: ${failure.said}"
      case Plugin.Said(plugin, claim, text)   => s"$plugin (${words(claim.productPrefix)}): $text"
      case Problem.Run.Paused(Problem.Run.PauseReason.Requested, None) => "paused on request"
      case Problem.Run.Paused(reason, at)    => s"paused for ${words(reason.productPrefix)}${until(at)}"
      case Problem.Run.Halted(cause, why)    => s"${cause.word}: ${why.sentence}"
      case Problem.Run.Unstarted             => "paused before it started"
      case Problem.Run.UploadUnheld          => "the upload is no longer held by this process"
      case Problem.Run.NoModel(role, passed) =>
        s"no model of the $role chain can run${if passed.isEmpty then "" else s": ${passedOver(passed)}"}"
      case Problem.Run.Exhausted(role, floor, frees, passed) =>
        s"all models for role $role are ${floor.said}; ${frees.said} (${passedOver(passed)})"
      case Problem.Run.PassedOver(model, why) => s"$model: ${why.toVector.map(_.sentence).mkString("; ")}"
      case Problem.Run.Unassigned(role, Problem.Run.Asked.Call) =>
        s"no model for the $role role — set agents.roles.$role " +
          s"(HISTMERSION_AGENTS_ROLE_${s"$role".toUpperCase(java.util.Locale.ROOT).replace('-', '_')}) to " +
          s"provider:model[ > provider:model …], save one with `histmersion agent models set $role <provider:model>…`, " +
          "or pass one"
      case Problem.Run.Unassigned(role, Problem.Run.Asked.Listing) =>
        s"no model — set agents.roles.$role or `histmersion agent models set $role …`"
      case Problem.Run.Cooling(until)            => s"its plan reported its usage limit; skipped until ${until.iso}"
      case Problem.Run.BelowFloor(pool, reading) => s"the $pool is below its floor: $reading"
      case Problem.Run.BelowAllowance(limits)    =>
        s"below its allowance: ${limits.toVector.map(l => s"${l.limit} (${l.used} used, reserve ${l.reserve})").mkString(", ")}"
      case Problem.Run.AllowanceUnread(broken) =>
        val each = broken.toVector.map(b => s"${b.target}: ${b.why.toVector.map(_.sentence).mkString("; ")}")
        s"its allowance cannot be read (${each.mkString("; ")})"
      case Problem.Run.PlanOnly(role) =>
        s"not used — the chain does not fall from a plan to a paid key (histmersion agent quota fallthrough $role on)"
      case Problem.Run.Stalled(step, minutes, times) =>
        s"stalled $times times at step $step (no sign of life for $minutes min each time; agents.watchdog)"
      case Problem.Run.Silent(step, minutes, stall, of) =>
        s"no sign of life for $minutes min at step $step (stall $stall of $of)"
      case Problem.Run.NoPipeline(pipeline) => s"no pipeline '$pipeline' in this build"
      case Problem.Run.PipelineChanged      =>
        "the pipeline's version changed since this run started; start a new run"
      case Problem.Run.Reordered(seq, recorded, now) =>
        s"step $seq is '$recorded' in the checkpoint but '$now' now — the pipeline changed; start a new run"
      case Problem.Run.CheckpointUnread(seq, step, r)   => s"step $seq ($step): the checkpoint no longer decodes ($r)"
      case Problem.Run.Vanished                         => "the run disappeared"
      case Problem.Run.Overspent(spent, budget)         => s"the run spent $spent of its $budget USD"
      case Problem.Run.OverTokens(used, budget, weight) =>
        s"the run used $used of its $budget budget tokens (cache reads count $weight: agents.budgets.cache-read-weight)"
      case Problem.Run.OverCap(Problem.Run.Cap.Daily, amount) =>
        s"today's agent spend reached agents.budgets.daily-usd ($amount)"
      case Problem.Run.OverCap(Problem.Run.Cap.Monthly, amount) =>
        s"this month's agent spend reached agents.budgets.monthly-usd ($amount)"
      case Problem.Run.MovedOn(run)                                   => s"run $run moved on; look again"
      case Problem.Run.NotNow(run, status, Problem.Run.Control.Pause) =>
        s"run $run is $status; only a queued or running run can be paused"
      case Problem.Run.NotNow(run, status, Problem.Run.Control.Resume) =>
        s"run $run is $status; only a paused run can be resumed"
      case Problem.Run.NotNow(run, status, Problem.Run.Control.Cancel) =>
        s"run $run is $status; it has already finished"
      case Problem.Run.Withdrawn                 => "rejected: the run was cancelled"
      case Problem.Run.AtStep(step, why)         => s"$step: ${why.toVector.map(_.sentence).mkString("; ")}"
      case Problem.Run.AtModel(model, why)       => s"$model: ${why.toVector.map(_.sentence).mkString("; ")}"
      case Problem.Run.Unsettled(step, attempts) =>
        s"$step: $attempts attempts without an answer; the chain keeps running out mid-step"
      case Problem.Run.ProviderDown(step, why) =>
        s"$step: ${why.toVector.map(_.sentence).mkString("; ")} — fix the provider, then resume"
      case Problem.Run.BudgetSpent(step)                 => s"$step: the run's budget is exhausted"
      case Problem.Run.NoValidAnswer(step, attempts, ps) =>
        s"$step: no valid answer after $attempts attempt(s)${
            if ps.isEmpty then "" else ps.map(_.sentence).mkString(": ", "; ", "")
          }"
      case Problem.Run.Failed(why)        => why.toVector.map(_.sentence).mkString("; ")
      case Problem.Run.Declined(problems) =>
        if problems.isEmpty then "the answer was rejected" else problems.map(_.sentence).mkString("; ")
      case Problem.Run.LimitReached(why, until) => s"${why.sentence} — skipped until ${until.iso}"
      case Problem.Run.AllowanceReached(model)  =>
        s"$model reached its allowance during the step; the chain's next model takes it"
      case Problem.Run.OutputCapped(step, model, cap, sent, setting) =>
        s"$step: $model's answer exceeded the $cap output-token cap (the step sent $sent) — raise ${setting.said} " +
          "(a thinking model's reasoning counts toward it), restart the server, then resume the run"
      case Problem.Run.NoText(source)          => s"$source has no text — ingest or OCR it first"
      case Problem.Run.NoBody(article)         => s"article '$article' has no body to review"
      case Problem.Run.GuardStopped(why)       => s"claude -p stopped by the quota guard: $why"
      case Problem.Run.NoLogin(why)            => s"unavailable: no login — $why"
      case Problem.Run.ProbeUnread(exit, said) =>
        s"the probe printed no rate_limit_event (exit $exit${said.fold("")(m => s": $m")})"
      case Problem.Run.Blocked(block)        => s"held back: ${block.said}"
      case Problem.Run.Interrupted           => "interrupted by a restart"
      case Problem.Run.CutShort(activity)    => activity.cutShort
      case Problem.Run.Unrecorded(activity)  => activity.unrecorded
      case Problem.Run.Cancelled(by)         => s"cancelled by ${words(by.productPrefix)}"
      case Problem.Run.Draining(Some(since)) => s"stopped while the autopilot drains (since ${since.iso})"
      case Problem.Run.Draining(None)       => "stopped while the autopilot drains: it resumes once the drain is lifted"
      case Problem.Run.Aborted(step, cause) => s"step $step cannot go on: ${words(cause.productPrefix)}"
      case Content.Finding(cause)           => cause.said
      case Content.Proposal(rule, at)       => s"$at: ${rule.said}"
      case Content.Answer(rule)             => rule.said
      case Content.NotProposed(why)         => s"not proposed: ${why.sentence}"
      case Content.Undecoded(why, finish, began) =>
        s"${why.said}${finish.fold("")(f => s" ($f)")}${began.fold("")(b => s" — the answer began: $b")}"
      case Internal.Unexpected(id, where, e, r) => s"$where failed unexpectedly ($e, problem $id) — $r"
      case Unclassified(text)                   => s"$text"
      case Stored.Unrecognised(text)            => s"$text"
      case Relayed(sentence)                    => s"$sentence"

    /** The sentence without the one item or input path it names: what a report counts problems of a kind by (every
      * record a collection refused as restricted is one reason, not one sentence per record; the report is about one
      * collection, so a refusal's reason does not name it again).
      */
    def reason: String = problem match
      case Plugin.Rejected(_, _, why)            => why.said
      case NotFound.Entity(t, _)                 => s"no such ${t.noun}"
      case Invalid.Malformed(t, c, raw, _)       => Invalid.Malformed(t, c, raw, Vector.empty).sentence
      case Invalid.Unknown(t, raw, known, _)     => Invalid.Unknown(t, raw, known, Vector.empty).sentence
      case Invalid.Unreadable(t, raw, eg, _)     => Invalid.Unreadable(t, raw, eg, Vector.empty).sentence
      case Invalid.Missing(field, _)             => Invalid.Missing(field, Vector.empty).sentence
      case Invalid.Undeclared(field, ds, _)      => Invalid.Undeclared(field, ds, Vector.empty).sentence
      case Invalid.Wrapped(t, raw, member, _)    => Invalid.Wrapped(t, raw, member, Vector.empty).sentence
      case Invalid.StoredOn(t, item, owner, _)   => Invalid.StoredOn(t, item, owner, Vector.empty).sentence
      case Invalid.Ambiguous(t, raw, n, _)       => Invalid.Ambiguous(t, raw, n, Vector.empty).sentence
      case Invalid.Repeated(t, raw, _)           => Invalid.Repeated(t, raw, Vector.empty).sentence
      case Invalid.Misplaced(field, under, _)    => Invalid.Misplaced(field, under, Vector.empty).sentence
      case Invalid.OutOfRange(t, raw, bounds, _) => Invalid.OutOfRange(t, raw, bounds, Vector.empty).sentence
      case Invalid.Inverted(start, end, _)       => Invalid.Inverted(start, end, Vector.empty).sentence
      case Invalid.OutOfTime(year, least, g, _)  => Invalid.OutOfTime(year, least, g, Vector.empty).sentence
      case Invalid.Unparsable(f, where, d, _)    => Invalid.Unparsable(f, where, d, Vector.empty).sentence
      case Invalid.Broken(rule, _)               => rule.said
      case other                                 => other.sentence

  extension (problems: Problems)
    /** One sentence per problem, in order. */
    def sentences: Vector[String] = problems.toVector.map(_.sentence)

  extension (cause: Problem.Run.PauseCause)
    /** The word a stopped run's sentence starts with: the agents' pause reason as the wire spells it (`plan-quota`). */
    def word: String = cause match
      case Problem.Run.PauseCause.Budget      => "budget"
      case Problem.Run.PauseCause.Interrupted => "interrupted"
      case Problem.Run.PauseCause.Requested   => "requested"
      case Problem.Run.PauseCause.Provider    => "provider"
      case Problem.Run.PauseCause.Allowance   => "allowance"
      case Problem.Run.PauseCause.PlanQuota   => "plan-quota"
      case Problem.Run.PauseCause.Stalled     => "stalled"

  extension (typeName: TypeName)
    /** A type's name without its package: `Citekey`, `Source.Status`. A `Wire` newtype is the wire spelling of the type
      * it is nested in, and is named by that type: `ProviderKind.Wire` is `ProviderKind`.
      */
    def short: String =
      val named = s"$typeName".split('.').toVector.dropWhile(_.headOption.forall(_.isLower))
      (if named.size > 1 && named.lastOption.contains("Wire") then named.dropRight(1) else named).mkString(".")

  extension (typeName: TypeName)
    /** The type as a reader names it, in English, derived from its name: the words of its own name, read in the
      * singular, qualified by the type it is nested in when its own name is one word (`Source.Status` is `source
      * status`, `Queue.QueueItem.Id` is `queue item id`, `Article.Edits.Anchor` is `edit anchor`, `Agents.ModelRef` is
      * `model reference`, `MergeResolutions` is `merge resolution`). A `Wire` or `Spec` type is the written form of the
      * type it is nested in and is named by it (`ProviderKind.Wire` is `provider kind`, `LimitSpec.Window` is `limit
      * window`); a case of a `Kind` enum is named by itself (`DeleteBlocker.Kind.Job` is `job`); a request record does
      * not qualify (`Place.Add.Kind` is `place kind`); an abbreviation is spelled out ([[Spelled]]). It may read
      * loosely; it never shows a package, a dot or a code name.
      */
    def noun: String =
      val named = s"$typeName".split('.').toVector.filter(_.nonEmpty).dropWhile(_.headOption.forall(_.isLower))
      val segments = named.reverse.dropWhile(s => named.size > 1 && FormSegments(s)).reverse
      segments.lastOption.fold(s"$typeName".toLowerCase) { own =>
        val named = nounWords(own)
        val timed = named.size > 1 && named.last == "at"
        val ownWords = if timed then named.dropRight(1).map(w => Happened.getOrElse(w, w)) :+ "time" else named
        val enclosing =
          segments.dropRight(1).reverse.dropWhile(Transparent).headOption.filterNot(_ == "Kind").map(nounWords)
        val qualified = enclosing match
          case Some(outer) if ownWords.size == 1 && outer.contains(ownWords.head) => ownWords
          case Some(outer) if ownWords.size == 1 || timed                         => outer.map(singular) ++ ownWords
          case _                                                                  => ownWords
        val read = Units.get(qualified.last) match
          case Some(unit) if qualified.size > 1 => qualified.dropRight(1) ++ Vector("in", unit)
          case _                                => qualified.dropRight(1) :+ singular(qualified.last)
        read.flatMap(w => Spelled.getOrElse(w, w).split(' ')).mkString(" ")
      }

    /** The noun after `a` or `an`. */
    def aNoun: String = a(typeName.noun)

    /** The noun counted: `1 article`, `3 findings`. */
    def counted(n: Count): String = if s"$n" == "1" then s"$n ${typeName.noun}" else s"$n ${plural(typeName.noun)}"

  /** The segments that name the written form of the type they are nested in, not a type of their own. */
  private val FormSegments = Set("Wire", "Spec", "Written", "T")

  /** The records a request or a view is nested in, named for what is done (`Place.Add.Kind` is a place's kind): they do
    * not qualify a noun, the type they are nested in does.
    */
  private val Transparent = Set("Add", "Put", "Set", "Patch", "Edit", "Info", "Input", "Request")

  /** Abbreviations and code words in a type's name, spelled as a reader writes them. */
  private val Spelled: Map[String, String] = Map(
    "ref" -> "reference",
    "pct" -> "percentage",
    "usd" -> "dollar amount",
    "bbox" -> "bounding box",
    "lang" -> "language",
    "geojson" -> "GeoJSON",
    "json" -> "JSON",
    "pg" -> "Postgres",
    "iiif" -> "IIIF",
    "ai" -> "AI",
    "http" -> "HTTP",
    "id" -> "id",
    "int" -> "whole number",
    "calibre" -> "Calibre",
    "citekey" -> "citekey",
    "doi" -> "DOI",
    "url" -> "URL",
    "uri" -> "URI",
    "isbn" -> "ISBN",
    "yubikey" -> "YubiKey",
    "periodo" -> "PeriodO",
    "wikidata" -> "Wikidata",
    "pleiades" -> "Pleiades"
  )

  /** What happened, in a time's name (`CreatedAt` is a `creation time`, qualified by what was created). */
  private val Happened: Map[String, String] = Map(
    "created" -> "creation",
    "updated" -> "update",
    "done" -> "completion",
    "checked" -> "check",
    "started" -> "start",
    "finished" -> "finish",
    "decided" -> "decision",
    "deleted" -> "deletion",
    "closed" -> "closing"
  )

  /** A unit a type's name ends in: the value is a quantity in it (`RefreshIntervalMinutes` is `refresh interval in
    * minutes`).
    */
  private val Units: Map[String, String] = Map(
    "minutes" -> "minutes",
    "seconds" -> "seconds",
    "millis" -> "milliseconds",
    "ms" -> "milliseconds",
    "hours" -> "hours",
    "days" -> "days",
    "bytes" -> "bytes",
    "pixels" -> "pixels"
  )

  /** A type name's words, lowercase, with a trailing `Spec` dropped (`LimitSpec` is `limit`): `ModelRef` is `model
    * ref`, `BBox` is `bbox`, `HTTPMode` is `http mode`.
    */
  private def nounWords(segment: String): Vector[String] =
    val split = segment
      .replaceAll("([A-Z]+)([A-Z][a-z])", "$1 $2")
      .replaceAll("([a-z0-9])([A-Z])", "$1 $2")
      .split(' ')
      .toVector
      .filter(_.nonEmpty)
      .map(_.toLowerCase)
    val joined = if split.forall(_.length == 1) then Vector(split.mkString) else split
    val bboxed = joined.mkString(" ").replace("b box", "bbox").replace("yubi key", "yubikey").split(' ').toVector
    if bboxed.size > 1 && bboxed.last == "spec" then bboxed.dropRight(1) else bboxed

  /** One of a plural word: `resolutions` is `resolution`, `entries` is `entry`; `status`, `class`, `basis` stay. */
  private def singular(word: String): String =
    if word.endsWith("ies") && word.length > 4 then s"${word.dropRight(3)}y"
    else if word == "aliases" || Vector("sses", "xes", "ches", "shes").exists(word.endsWith) then word.dropRight(2)
    else if Vector("ss", "us", "is").exists(word.endsWith) || !word.endsWith("s") || Uncounted(word) then word
    else word.dropRight(1)

  /** Words that end in s in the singular too. */
  private val Uncounted = Set("commons", "news", "series", "species", "alias", "canvas")

  /** Many of a noun phrase: its last word in the plural (`finding` is `findings`, `entry` is `entries`). */
  def plural(noun: String): String =
    val (init, last) = noun.splitAt(noun.lastIndexOf(' ') + 1)
    val many =
      if last.endsWith("y") && !Vector("ay", "ey", "oy", "uy").exists(last.endsWith) then s"${last.dropRight(1)}ies"
      else if Vector("s", "x", "ch", "sh").exists(last.endsWith) then s"${last}es"
      else s"${last}s"
    s"$init$many"

  /** A noun phrase after `a` or `an`, by its first sound: `an article`, `a queue item id`, `a user`, `an HTTP mode`. */
  def a(noun: String): String =
    val first = noun.takeWhile(_ != ' ')
    val vowelSound =
      if first.nonEmpty && first.forall(c => c.isUpper || c.isDigit) then "AEFHILMNORSX".contains(first.head)
      else
        "aeiou".contains(first.headOption.getOrElse(' ').toLower) &&
        !Vector("uni", "use", "usu", "uti", "eu", "one").exists(first.toLowerCase.startsWith)
    s"${if vowelSound then "an" else "a"} $noun"

  /** A code name in words: `MergeApply` is `merge apply`. */
  def words(name: String): String = name.flatMap(c => if c.isUpper then s" ${c.toLower}" else s"$c").trim

  private def path(at: Vector[Field]): String = if at.isEmpty then "" else s"${at.mkString(".")}: "

  extension (at: EpochMs) private def iso: String = s"${Instant.ofEpochMilli(s"$at".toLong)}"

  private def until(at: Option[EpochMs]): String = at.fold("")(t => s" until ${t.iso}")

  private def forExample(examples: Vector[Raw]): String =
    if examples.isEmpty then "" else s" (e.g. ${examples.mkString(", ")})"

  /** A share to two decimals: `0.42`. */
  extension (share: Share)
    private def twoDecimals: String = s"${BigDecimal(s"$share".toDouble).setScale(2, BigDecimal.RoundingMode.HALF_UP)}"

  /** `1st`, `2nd`, `11th`, `23rd`. */
  private def ordinal(n: Long): String =
    val suffix =
      if n % 100 >= 11 && n % 100 <= 13 then "th"
      else
        n % 10 match
          case 1 => "st"
          case 2 => "nd"
          case 3 => "rd"
          case _ => "th"
    s"$n$suffix"

  /** `1 source`, `3 sources`. */
  extension (n: Count) private def sources: String = if s"$n" == "1" then s"$n source" else s"$n sources"

  private def oneOf(known: Vector[Raw]): String = if known.isEmpty then "" else s" (one of: ${known.mkString(", ")})"

  extension (bounds: Invalid.Bounds)
    private def range: String = bounds match
      case Invalid.Bounds(Some(least), Some(greatest)) => s"not between $least and $greatest"
      case Invalid.Bounds(Some(least), None)           => s"below $least"
      case Invalid.Bounds(None, Some(greatest))        => s"above $greatest"
      case Invalid.Bounds(None, None)                  => "out of range"

  extension (rule: Invalid.Rule)
    private def said: String = rule match
      case Invalid.Rule.CronFields(stated, shortcuts) =>
        s"'$stated' is not a cron expression: give five fields (minute hour day-of-month month day-of-week, in UTC) " +
          s"or one of ${shortcuts.mkString(", ")}"
      case Invalid.Rule.CronField(stated, part, flaw)    => s"'$stated': ${words(part.productPrefix)}: ${flaw.said}"
      case Invalid.Rule.CronNeverFires(stated)           => s"'$stated' never fires"
      case Invalid.Rule.CronTooOften(stated, gap, least) =>
        s"'$stated' would fire every $gap minute(s); the least is $least (agents.scheduler.min-interval-minutes)"
      case Invalid.Rule.ZoomWithin(zoom, greatest) => s"zoom $zoom is outside 0..$greatest"
      case Invalid.Rule.TileWithin(x, y, zoom)     => s"tile $x/$y is outside the grid of zoom $zoom"
      case Invalid.Rule.IdentifierGiven            => "empty identifier — give a DOI, arxiv:<id>, or a citekey"
      case Invalid.Rule.IdentifierKind(stated)     => s"'$stated' is neither a DOI, an arXiv id, nor a citekey"
      case Invalid.Rule.ActivityPage(stated)       => s"'$stated' is not a page of the activity list"
      case Invalid.Rule.ActivityPageTime(stated)   =>
        s"'$stated' is not a page of the activity list (its time is not an instant)"
      case Invalid.Rule.EventCatalogue(stated, known) =>
        s"unknown event catalogue '$stated' (one of: ${known.mkString(", ")})"
      case Invalid.Rule.CitekeyUnchanged(citekey)  => s"'$citekey' is already the citekey of that source"
      case Invalid.Rule.CitekeyLinkShaped(citekey) =>
        s"'$citekey' is a valid citekey but not a LINK-shaped one — `[[$citekey]]` would resolve to an " +
          "article, not the source. Use lower-case letters, digits and '_' with a four-digit year or an " +
          "underscore (e.g. page1959_history)"
      case Invalid.Rule.AssetBase64(asset, reported)    => s"asset '$asset': not valid base64 ($reported)"
      case Invalid.Rule.AssetEmpty(asset)               => s"asset '$asset': empty"
      case Invalid.Rule.AssetBytes(asset, bytes, limit) =>
        s"asset '$asset': $bytes bytes exceeds the $limit-byte limit (limits.asset-bytes)"
      case Invalid.Rule.RunsCompared                              => "give at least two runs to compare"
      case Invalid.Rule.RunRepeated                               => "a run is given twice"
      case Invalid.Rule.RunsPipeline(run, pipeline, first, other) =>
        s"run $run is $pipeline, run $first $other: compare runs of one pipeline"
      case Invalid.Rule.RunsTarget(run, target, first, other) =>
        s"run $run is on $target, run $first on $other: compare runs on one target"
      case Invalid.Rule.QueryGiven       => "the query is empty — give the words to find"
      case Invalid.Rule.RegexTime(limit) =>
        s"the regex ran longer than $limit ms over this body — make it more specific " +
          "(anchor it, avoid nested repetition like (a+)+)"
      case Invalid.Rule.RegexValid(description, position, pattern) =>
        s"not a valid regex: $description${position.fold("")(p => s" at position $p")} in `$pattern` " +
          "(leave regex off to match the text literally)"
      case Invalid.Rule.MergeId(stated) =>
        s"'$stated' is not a merge id (the id `merge list` prints, or its first characters)"
      case Invalid.Rule.DoiRecorded(Invalid.Purpose.Lookup)     => "no DOI recorded — nothing to look up upstream"
      case Invalid.Rule.DoiRecorded(Invalid.Purpose.References) => "no DOI — a reference list needs one"
      case Invalid.Rule.DoiRecorded(Invalid.Purpose.Assessment) => "no DOI — an assessment needs one"
      case Invalid.Rule.DoiRecorded(Invalid.Purpose.Citations)  => "no DOI — the citation graph is keyed by DOI"
      case Invalid.Rule.UpstreamRecord                          =>
        "no upstream record (OpenAlex and Crossref both miss it) — check the identifier, or pass --citekey to " +
          "create the source anyway and fill the metadata in by hand"
      case Invalid.Rule.UploadGiven               => "the upload is empty"
      case Invalid.Rule.UploadBytes(bytes, limit) =>
        s"upload is $bytes bytes, over the $limit-byte limit (limits.upload-bytes)"
      case Invalid.Rule.InlineBytes(bytes, limit) =>
        s"payload is $bytes bytes, over the $limit-byte limit (limits.inline-bytes) — pass `path` instead, or " +
          "upload with `histmersion ingest`"
      case Invalid.Rule.Base64Decodable(reported) => s"base64 payload is not decodable: $reported"
      case Invalid.Rule.PathOrBase64              =>
        "give either `path` (a file the SERVER can see) or `base64` (the document itself)"
      case Invalid.Rule.ServerSees =>
        "the server cannot see a file there (paths are resolved INSIDE the server, not on your machine) — use " +
          "`base64`, or `histmersion ingest <file>` from the host"
      case Invalid.Rule.OriginalForReocr =>
        "no original stored — the server keeps uploads in its spool directory (converter.spool-dir); re-run " +
          "`histmersion ingest <file> --citekey <citekey> --force`"
      case Invalid.Rule.OriginalForDownload =>
        "no original stored — the server keeps uploads in its spool directory (converter.spool-dir); pass the file " +
          "yourself (--file)"
      case Invalid.Rule.Frontmatter    => "no YAML frontmatter in the note"
      case Invalid.Rule.NoteCitekey    => "note has no citekey (none in the frontmatter, none supplied)"
      case Invalid.Rule.FilterRecorded =>
        "records no filter, so it has no generated coverage — set the filter it is compiled from: histmersion " +
          "article patch <slug> --filter-query … (or --filter-topics/--filter-tags), or patch_article with `filter`"
      case Invalid.Rule.RegionWithin(x, y, w, h, width, height) =>
        s"region ($x,$y,$w,$h) is outside image bounds (${width}x$height)"
      case Invalid.Rule.ReviewLibraryWide =>
        "the review manager is library-wide: give no goal (narrow it with config includeGoal)"
      case Invalid.Rule.EnrichLibraryWide =>
        "the enrich manager is library-wide: give no goal"
      case Invalid.Rule.SeedGoal =>
        "a seeding manager is for one research area: give its goal"
      case Invalid.Rule.WatchGoal =>
        "a watcher is for one research area: give its goal"
      case Invalid.Rule.EnrichCron =>
        "the enrich manager has no cron: the ingests and the Enrich buttons feed it"
      case Invalid.Rule.HintSettings =>
        "a config action names the settings it changes"
      case Invalid.Rule.HintUntil =>
        "a hold hint needs `until` (a date) or `untilSource` (a DOI or citekey)"
      case Invalid.Rule.HintTargets =>
        "a structured hint names its targets, a tag, a topic or a goal (or is about one item)"
      case Invalid.Rule.CronGiven =>
        "give `cron` to set a schedule (or `remove: true`)"
      case Invalid.Rule.ChainGiven =>
        "a chain needs at least one provider:model (reset the role to go back to config)"
      case Invalid.Rule.HintConfig(allowed) =>
        s"unknown hint action 'config'${oneOf(allowed)} — change the settings with the manager's config instead"
      case Invalid.Rule.RoundsWithin(_, least, cap) =>
        s"rounds must be between $least and $cap (agents.review.rounds-cap)"
      case Invalid.Rule.RoleUsed(Invalid.RoleOwner.Pipeline, name, role, roles) =>
        s"$name uses no $role role (its roles: ${
            if roles.isEmpty then "none — it calls no model" else roles.mkString(", ")
          })"
      case Invalid.Rule.RoleUsed(Invalid.RoleOwner.Manager, kind, role, roles) =>
        s"'$role' is not a role a $kind manager runs (${roles.mkString(", ")})"
      case Invalid.Rule.PointOnly(place, label) =>
        s"'$label' [$place] has a shape, not a point — a point set by hand moves only a place known by a point"
      case Invalid.Rule.FocusRegion(name, known) =>
        s"no focus region '$name' in basemap.terrain.focus-regions (${known.mkString(", ")}): give its --bbox"
      case Invalid.Rule.FocusDeeper(extract) =>
        s"a focus region holds the zooms deeper than the extract's ($extract): ask for more than that"
      case Invalid.Rule.LabelsUnranged              => "the physical names are not read with range requests"
      case Invalid.Rule.SeaFloorUnranged            => "the sea floor is not read with range requests"
      case Invalid.Rule.ProviderOf(model, provider) =>
        s"$model is not ${if s"$provider".headOption.exists("aeiou".contains(_)) then "an" else "a"} $provider model"
      case Invalid.Rule.OpencodeModelShape(model) =>
        s"$model: an opencode model is <provider>/<model>, e.g. opencode:openai/gpt-5"
      case Invalid.Rule.HostProvider(provider) =>
        s"$provider is Histmersion's own provider; it is run by the host, not through the SPI"
      case Invalid.Rule.OptionOf(option, owner, pipeline) => s"$option is an option of $owner, not of $pipeline"
      case Invalid.Rule.NewArticleFilter(target)          =>
        s"'$target' is a new article: give a filter (query, topics or tags) for the sources to compile it from"
      case Invalid.Rule.NewArticleTitle(target)        => s"'$target' is a new article: give its title"
      case Invalid.Rule.NameByHand(name, place, label) =>
        s"'$name' is a name the gazetteer seed or an imported source gives '$label' [$place], not one added by hand — " +
          "removing it would last only until the next import"

  extension (year: Invalid.Year)
    /** The year as a reader writes it: `900 BCE`, `530 CE`; there is no year 0, which is read as 1 BCE. */
    def era: String =
      val signed = s"$year".toLong
      if signed < 0 then s"${-signed} BCE" else if signed == 0 then "1 BCE" else s"$signed CE"

  extension (listing: NotFound.Listing)
    /** The command, as a reader types it. */
    private def command: String = listing match
      case NotFound.Listing.Merges => "`histmersion merge list`"

  extension (clash: Conflict.Clash)
    /** Why a key chosen for a new value is not free, naming the key by its own word and the value by its type's (a
      * `Place.Id` is the `id` of a `place`), and that another must be chosen.
      */
    private def said(typeName: TypeName, key: Raw): String =
      val (own, owner) = typeName.parts
      clash match
        case Conflict.Clash.Taken        => s"'$key' is already the $own of ${a(owner)} — choose another $own"
        case Conflict.Clash.Removed      => s"'$key' is the $own of ${a(owner)} that was removed — choose another $own"
        case Conflict.Clash.OldKeyOf(of) =>
          s"'$key' is an old $own of '$of' and still resolves to it — choose another $own"

  extension (typeName: TypeName)
    /** A key type's own word and the word of the type it is the key of: `Place.Id` is (`id`, `place`); a type nested in
      * nothing is the key of a `record`.
      */
    private def parts: (String, String) =
      val named = s"$typeName".split('.').toVector.filter(_.nonEmpty).dropWhile(_.headOption.forall(_.isLower))
      val own = named.lastOption.fold(s"$typeName".toLowerCase)(o => nounWords(o).mkString(" "))
      val owner = named.dropRight(1).lastOption.fold("record")(o => nounWords(o).map(singular).mkString(" "))
      (own, owner)

  extension (flaw: Invalid.CronFlaw)
    private def said: String = flaw match
      case Invalid.CronFlaw.NotNumber(stated, least, greatest) => s"'$stated' is not a number from $least to $greatest"
      case Invalid.CronFlaw.Step(stated)                       => s"step '$stated' must be a positive number"
      case Invalid.CronFlaw.Slashes(stated)                    => s"'$stated' has more than one '/'"
      case Invalid.CronFlaw.Backwards(stated)                  => s"range '$stated' runs backwards"
      case Invalid.CronFlaw.NotRange(stated)                   => s"'$stated' is not a range"

  extension (rule: Invalid.Exclusion)
    private def of(fields: NonEmptyVector[Field]): String =
      val named = fields.toVector.mkString(", ")
      rule match
        case Invalid.Exclusion.Together   => s"give $named together, or none of them"
        case Invalid.Exclusion.OneOf      => s"give exactly one of $named"
        case Invalid.Exclusion.AtMostOne  => s"give at most one of $named"
        case Invalid.Exclusion.AtLeastOne => s"give at least one of $named"
        case Invalid.Exclusion.Ordered    => s"$named must be in order"
        case Invalid.Exclusion.Same       => s"$named must be the same"
        case Invalid.Exclusion.Distinct   => s"$named must differ"

  /** The forms a secret's reference takes. */
  private val ReferenceForms = "value:<literal>, env:<NAME> or <vault>:<ref>"

  extension (form: Invalid.Form)
    private def said: String = form match
      case Invalid.Form.Bare            => s"a secret is a reference ($ReferenceForms), not a bare value"
      case Invalid.Form.EmptyValue      => "value: has no value"
      case Invalid.Form.EnvName(name)   => s"env:$name is not an environment variable name"
      case Invalid.Form.NoEntry(vault)  => s"$vault: names no entry"
      case Invalid.Form.VaultName(name) =>
        s"a secret is a reference ($ReferenceForms); '$name' is not a vault name ([a-z0-9][a-z0-9-]*)"

  extension (activity: Conflict.Activity)
    private def running: String = activity match
      case Conflict.Activity.CalibreGui =>
        "the Calibre GUI is running — quit it first (calibredb needs exclusive access to the library)"
      case Conflict.Activity.TileRequests => "too many tile requests waiting"
      case Conflict.Activity.CodexRun     => "a Codex run is using the home — not now"
      case other                          => s"a ${words(other.productPrefix)} is still running"

  extension (activity: Conflict.Activity)
    /** The command that lists what the activity did, when it has one. */
    private def listing: Option[String] = activity match
      case Conflict.Activity.MergeApply => Some("`histmersion merge list`")
      case _                            => None

    private def cutShort: String = activity match
      case Conflict.Activity.MergeApply =>
        "the server stopped while this merge apply ran — its transaction was committed or rolled back as a whole, " +
          "never half applied; `histmersion merge list` shows whether it was applied, and planning the bundle again " +
          "shows what is left"
      case other =>
        s"the server stopped while a ${words(other.productPrefix)} ran — it was committed or rolled back as a whole, " +
          s"never half applied${other.listing.fold("")(l => s"; $l shows whether it took effect")}"

    private def unrecorded: String =
      s"the ${words(activity.productPrefix)} finished but its result was not recorded" +
        activity.listing.fold("")(l => s" — $l shows what was applied")

  extension (metered: Config.Metered)
    private def said: String = metered match
      case Config.Metered.ApiKey(provider) => s"$provider is set to use an API key, so it bills the API, not the plan"
      case Config.Metered.Opencode         => "an opencode model is metered unless it is openai/… on a ChatGPT login"

  extension (rule: Refused.Rule)
    private def said: String = rule match
      case Refused.Rule.RunTokenRequired => "a live run token is required"
      case Refused.Rule.ApiKeyRequired   => "the API key is required"
      case Refused.Rule.WorkingDirDenied => "the command may not write in this working directory"
      case Refused.Rule.ReadOnlyQuery    => "a query may only read: it holds a statement that writes"
      case Refused.Rule.OneStatement     =>
        "a query runs exactly one statement: drop the ';'-separated batch and ask again"
      case Refused.Rule.LoginOutsideLocal => "this login is refused while the server listens beyond this machine"
      case Refused.Rule.LegalOnly         => "only legal open-access copies may be fetched"
      case Refused.Rule.ReadOnly          => "the collection is read-only"
      case Refused.Rule.LendingOnly       => "the item is lending-only: borrow it on its site"
      case Refused.Rule.RangesOnly        => "this client reads ranges of a file, never a whole file"
      case Refused.Rule.RegistrationOver  =>
        "registration is over: a plugin adds to a registry only from inside its register"
      case Refused.Rule.PersonOnly => "bookmarks, notes and read marks are a person's own; an automated run has none"
      case Refused.Rule.TokenOtherAgent  => "this run token is not for that agent"
      case Refused.Rule.PlanOutsideLocal =>
        "this provider is refused while the server listens beyond loopback: it spends your own plan, and another " +
          "client could spend it"
      case Refused.Rule.ClaudeCodeOutsideLocal =>
        "claude-code is refused while the server listens beyond loopback: another client could spend " +
          "your Claude plan. Bind to 127.0.0.1 (HISTMERSION_BIND), or — when every client of this server is " +
          "you — set agents.providers.claude-code.allow-non-loopback (HISTMERSION_AGENTS_CLAUDE_ALLOW_NON_LOOPBACK)"
      case Refused.Rule.CodexOutsideLocal =>
        "codex is refused while the server listens beyond loopback: another client could spend your ChatGPT plan"
      case Refused.Rule.OpencodeChatgptOutsideLocal =>
        "opencode on a ChatGPT login is refused while the server listens beyond loopback"
      case Refused.Rule.OrdinaryUsageDenied =>
        "the ChatGPT plan does not allow ordinary usage now (ordinaryUsageAllowed: false)"
      case Refused.Rule.CrossSite =>
        "a change sent by a page of another site is refused — if that page is this server's own (behind a proxy, or " +
          "under another name), add its origin to auth.origins on the server and restart it"
      case Refused.Rule.NetworkNotAccepted =>
        "this server does not accept requests from your network — its settings list it neither in " +
          "auth.trusted-networks nor in auth.authenticated-networks"
      case Refused.Rule.NetworkRefused =>
        "this server refuses every request from your network — its settings list it in auth.refused-networks"
      case Refused.Rule.UndeclaredProxy =>
        "this request says a proxy forwarded it (Forwarded, X-Forwarded-For, X-Forwarded-Host, X-Forwarded-Proto, " +
          "X-Real-IP or Via), but it comes from no network the server lists in auth.proxy-networks — send it " +
          "directly, or, when a proxy serves it, add the proxy's network to auth.proxy-networks on the server and " +
          "restart it"

  extension (limit: Refused.Limit)
    private def named: String = limit match
      case Refused.Limit.DailySpend    => "today's agent spend"
      case Refused.Limit.MonthlySpend  => "this month's agent spend"
      case Refused.Limit.RunSpend      => "the run's spend"
      case Refused.Limit.ProviderQuota => "the provider's quota"
      case Refused.Limit.RateLimit     => "the rate limit"
      case Refused.Limit.PlanAllowance => "the plan's allowance"

  extension (shape: Upstream.Shape)
    /** What the answer should have been, as a noun phrase: `a byte range`, `JSON`. */
    private def noun: String = shape match
      case Upstream.Shape.Json     => "JSON"
      case Upstream.Shape.Xml      => "XML"
      case Upstream.Shape.Html     => "HTML"
      case Upstream.Shape.Text     => "text"
      case Upstream.Shape.Image    => "an image"
      case Upstream.Shape.Document => "a document"
      case Upstream.Shape.Archive  => "an archive"
      case Upstream.Shape.Problems => "a list of problems"
      case Upstream.Shape.Range    => "a byte range"

  extension (kind: Io.Kind)
    private def missed: String = kind match
      case Io.Kind.File              => "is not a file"
      case Io.Kind.Directory         => "is not a directory"
      case Io.Kind.Present           => "is not there"
      case Io.Kind.Absent            => "is there already"
      case Io.Kind.Executable        => "is not executable"
      case Io.Kind.Listed            => "is not in the manifest"
      case Io.Kind.OnPath            => "was not found on the PATH"
      case Io.Kind.OnDisk            => "is not on disk"
      case Io.Kind.Inside(directory) => s"is outside $directory, the directory it is confined to"

  extension (damage: Io.Damage)
    private def said(format: Io.Format): String = damage match
      case Io.Damage.Checksum                => "fails its checksum"
      case Io.Damage.Truncated               => "is cut short"
      case Io.Damage.Undecodable             => "cannot be decoded"
      case Io.Damage.Length(expected, found) => s"holds $found units, not $expected"
      case Io.Damage.Unreadable(reported)    => s"cannot be read — $reported"
      case Io.Damage.Newer(found, reads)  => s"is in format $found, newer than the format $reads this Histmersion reads"
      case Io.Damage.Schema(found, reads) =>
        s"comes from database schema V$found, newer than this Histmersion's V$reads — upgrade Histmersion"
      case Io.Damage.Structure(flaw)        => flaw.said
      case Io.Damage.Foreign                => s"is not ${format.article} (no magic number)"
      case Io.Damage.Bytes(expected, found) => s"holds $found bytes, not $expected"
      case Io.Damage.Inflate(reported)      => s"does not inflate — $reported"
      case Io.Damage.Unsupported(variant)   => variant.said

  extension (format: Io.Format)
    /** The format's name as a reader knows it: `PNG`, `GeoJSON`; the others in plain words (`json`, `bundle`). */
    private def named: String = format match
      case Io.Format.Png     => "PNG"
      case Io.Format.PmTiles => "PMTiles"
      case Io.Format.GeoJson => "GeoJSON"
      case Io.Format.WebP    => "WebP"
      case Io.Format.Gzip    => "gzip"
      case Io.Format.Mvt     => "vector tile"
      case Io.Format.GeoTiff => "GeoTIFF"
      case other             => words(other.productPrefix)

    /** One document of the format, with its article: `a PNG`, `a PMTiles archive`. */
    private def article: String = format match
      case Io.Format.Png     => "a PNG"
      case Io.Format.WebP    => "a WebP file"
      case Io.Format.PmTiles => "a PMTiles archive"
      case Io.Format.GeoTiff => "a TIFF"
      case other             => s"${other.named} data"

  extension (variant: Io.Variant)
    private def said: String = variant match
      case Io.Variant.PngLayout(colour, depth, interlace) =>
        s"is a PNG of colour type $colour, depth $depth, interlace $interlace: only 8-bit RGB(A) non-interlaced PNGs " +
          "are read"
      case Io.Variant.PngFilter(filter)         => s"uses PNG filter $filter, which does not exist (filters are 0 to 4)"
      case Io.Variant.LossyWebP                 => "is a lossy (VP8) WebP: only lossless WebP is read"
      case Io.Variant.NoLossless                => "has no VP8L chunk: only lossless WebP is read"
      case Io.Variant.Vp8lVersion               => "is of an unknown VP8L version"
      case Io.Variant.SpecVersion(found, reads) => s"is spec version $found, which is not supported (only $reads)"
      case Io.Variant.Compression(found, reads) => s"uses $found compression, which is not supported (only $reads)"
      case Io.Variant.BigTiff                   => "is not a classic TIFF (BigTIFF is not read)"
      case Io.Variant.SampleBits(bits)          => s"has $bits-bit samples (only 16-bit are read)"
      case Io.Variant.TiffCompression(code)     => s"has compression $code (only uncompressed grids are read)"
      case Io.Variant.Bands(count)              => s"has $count bands (only one is read)"
      case Io.Variant.Planar(configuration)     => s"has planar configuration $configuration (only 1 is read)"
      case Io.Variant.SampleFormat(format)      => s"has sample format $format (only signed integers are read)"
      case Io.Variant.Tiled                     => "is a tiled TIFF (only strips are read)"

  extension (flaw: Io.Flaw)
    private def said: String = flaw match
      case Io.Flaw.NotFeatureCollection   => "is not a FeatureCollection"
      case Io.Flaw.NoLandPolygons         => "holds no land polygons"
      case Io.Flaw.NotArray               => "is not a JSON array"
      case Io.Flaw.NoBuild                => "lists no build"
      case Io.Flaw.NoImageHeader          => "has no image header (IHDR)"
      case Io.Flaw.NegativeAt(q, byte)    => s"holds a negative ${words(q.productPrefix)} at byte $byte"
      case Io.Flaw.DirectorySize(size)    => s"has an implausible directory size $size"
      case Io.Flaw.NegativeEntry(entry)   => s"has a negative offset in entry $entry"
      case Io.Flaw.OffsetOutside(offset)  => s"has an offset $offset that lies outside its file"
      case Io.Flaw.NegativeLength(length) => s"has a negative length $length"
      case Io.Flaw.TooDeep                => "nests its directories too deep"
      case Io.Flaw.RootOutside            => "lies outside the first 16 KiB"
      case Io.Flaw.MetadataOver           => "is over 16 MiB"
      case Io.Flaw.PrefixTooLong          => "has a prefix code longer than 15 bits"
      case Io.Flaw.PrefixIncomplete       => "has an incomplete or over-subscribed prefix code"
      case Io.Flaw.PrefixInvalid          => "has an invalid prefix code"
      case Io.Flaw.ChunkTruncated         => "has a truncated chunk"
      case Io.Flaw.StreamTruncated        => "is truncated"
      case Io.Flaw.NotVp8l                => "is not a VP8L stream"
      case Io.Flaw.TransformTwice         => "uses a transform twice"
      case Io.Flaw.CacheSize              => "has a colour cache of an invalid size"
      case Io.Flaw.SymbolOutside          => "has a symbol outside its alphabet"
      case Io.Flaw.CodeLengthsOver        => "has more code lengths than symbols"
      case Io.Flaw.CodeLengthsPast        => "has code lengths past the alphabet"
      case Io.Flaw.ReferenceOutside       => "has a backward reference outside the image"
      case Io.Flaw.CacheWithout           => "has a colour-cache code without a cache"
      case Io.Flaw.CacheIndexOutside      => "has a colour-cache index outside the cache"
      case Io.Flaw.RangeBytes(got, asked) => s"holds $got bytes for a range of $asked"
      case Io.Flaw.NotLittleEndian        => "is not a little-endian TIFF"
      case Io.Flaw.NoTag(tag)             => s"has no tag $tag"
      case Io.Flaw.NoStripOffsets         => "has no strip offsets"
      case Io.Flaw.NoPixelScale           => "has no pixel scale (not a GeoTIFF)"
      case Io.Flaw.NoTiePoint             => "has no tie point (not a GeoTIFF)"
      case Io.Flaw.NoStripForRow(row)     => s"has no strip for row $row"

  extension (held: Io.Held)
    private def missed(path: Io.Path, setting: Option[SettingKey]): String =
      val set = setting.fold("")(s => s" (set $s)")
      held match
        case Io.Held.GazetteerSeed     => s"no gazetteer seed at $path$set"
        case Io.Held.GazetteerManifest => s"no gazetteer manifest at $path$set"
        case Io.Held.LpfFiles          => s"no LPF files in $path$set (put World Historical Gazetteer downloads there)"
        case Io.Held.SeedFiles         => s"no JSON files under $path$set"
        case Io.Held.CodexHome         => s"no Codex home at $path$set"

  extension (pinning: Io.Pinning)
    private def said(extract: Raw): String = pinning match
      case Io.Pinning.Unlisted(manifest) => s"$manifest pins no '$extract' extract"
      case Io.Pinning.NotBuilt(manifest) =>
        s"$extract is not pinned yet ($manifest names no built file or sha256 for it) — build it with " +
          "db/gazetteer/build.py and pin it (db/gazetteer/README.md, \"Rebuilding and re-pinning\")"
      case Io.Pinning.Mismatch(version, path, found, pinned) =>
        s"$path has sha256 $found, but $extract $version is pinned at $pinned — refused (rebuild it with " +
          "db/gazetteer/build.py and re-pin, or fetch the pinned file)"
      case Io.Pinning.Unhosted(version, file, path, sha256, bytes, setting, variable) =>
        s"$extract $version: $file is not at $path, and its pin has no download url (the extracts are not hosted). " +
          s"Put the file there — sha256 $sha256, ${bytes.human} — copied from another install or rebuilt with " +
          s"db/gazetteer/build.py (db/gazetteer/README.md); the folder is $setting ($variable; by default the XDG " +
          "cache directory, $XDG_CACHE_HOME/histmersion/gazetteer or ~/.cache/histmersion/gazetteer). Then run the " +
          "load again."

  extension (bytes: Count)
    /** A size in bytes as people read it: `812 B`, `1.4 MB` (powers of 1000, one decimal). */
    private def human: String =
      val n = s"$bytes".toLong
      val units = Vector("kB", "MB", "GB", "TB")
      if n < 1000 then s"$n B"
      else
        val exp = math.min(units.size, (math.log(n.toDouble) / math.log(1000)).toInt)
        val tenths = math.round(n.toDouble / math.pow(1000, exp.toDouble) * 10)
        s"${tenths / 10}.${tenths % 10} ${units(exp - 1)}"

  extension (work: Refused.Work)
    private def refused(needs: Count, on: Io.Path, free: Count, least: Count): String =
      val room = s"(${free.human} free), which would leave less than ${least.human} (basemap.min-free-gb)"
      work match
        case Refused.Work.Layer(title) =>
          s"refused: the $title layer needs ${needs.human} more on $on $room. Free some space, fetch a smaller region " +
            "(--bbox) or fewer zooms (--max-zoom / --terrain-max-zoom), or move the files with basemap.dir."
        case Refused.Work.SeaFloor => s"refused: the sea floor needs about ${needs.human} on $on $room"
        case Refused.Work.Gebco    =>
          s"refused: the GEBCO sea floor needs about ${needs.human} on $on $room. Fetch a smaller region (--bbox), or " +
            "the ETOPO1 sea floor (basemap.bathymetry.source = etopo1)."
        case Refused.Work.Labels => s"refused: the physical names need about ${needs.human} on $on $room"

  extension (reference: Conflict.Reference)
    private def said: String = reference.by match
      case Conflict.Referrer.Events       => s"${reference.count} event(s)"
      case Conflict.Referrer.Attestations => s"${reference.count} attestation(s)"
      case Conflict.Referrer.AssetLinks   => s"${reference.count} asset link(s)"
      case Conflict.Referrer.ChildPlaces  => s"${reference.count} child place(s)"
      case Conflict.Referrer.ManualNames  => s"${reference.count} name(s) added by hand"
      case Conflict.Referrer.OnlyPlaceOf  => s"${reference.count} finding(s) it is the only place of"

  /** The languages a place has a name in by hand, after a name it lacks in the one asked: ` (it has it with en, no
    * language — pass that language)`.
    */
  private def hasIn(has: Vector[Raw]): String =
    if has.isEmpty then ""
    else
      s" (it has it with ${has.map(l => if s"$l".isEmpty then "no language" else s"$l").mkString(", ")} — pass that " +
        "language)"

  extension (fault: Io.Fault)
    private def said(archive: String, entry: Io.Path): String = fault match
      case Io.Fault.Missing      => s"the $archive holds no $entry"
      case Io.Fault.Omitted      => s"$entry is listed in the manifest but missing from the $archive"
      case Io.Fault.Unlisted     => s"$entry is in the $archive but not in the manifest"
      case Io.Fault.Repeated     => s"$entry appears twice in the $archive"
      case Io.Fault.Foreign      => s"$entry is not a file a $archive holds"
      case Io.Fault.Ahead(first) => s"$entry comes first in the $archive, where $first belongs"
      case Io.Fault.Unreferenced => s"$entry is in the $archive, but nothing refers to it"
      case Io.Fault.Dangling(n)  =>
        s"$entry is referred to ${if s"$n" == "1" then "once" else s"$n times"}, but the $archive does not hold it"
      case Io.Fault.Differs(measure, listed, found) =>
        s"$entry ${measure.holds(found)}, but the manifest lists $listed"

  extension (measure: Io.Measure)
    private def holds(found: Raw): String = measure match
      case Io.Measure.Sha256  => s"has sha256 $found"
      case Io.Measure.Bytes   => s"holds $found bytes"
      case Io.Measure.Lines   => s"holds $found lines"
      case Io.Measure.Entries => s"holds $found entries"

  /** The chain's answers after a missing copy: ` — unpaywall: no copy — none; landing: refused — …`. */
  private def answered(answers: Vector[NotFound.Answer]): String =
    if answers.isEmpty then "" else answers.map(_.line).mkString(" — ", "; ", "")

  extension (answer: NotFound.Answer)
    /** One fetcher's answer as the ingest report and the acquisition list show it: `oapen: no copy — no record`. */
    def line: String = s"${answer.fetcher}: ${answer.kind.label} — ${answer.why.sentence}"

  extension (kind: NotFound.Kind)
    /** The words for how a fetcher came back: `no copy`, `refused`. */
    def label: String = kind match
      case NotFound.Kind.Miss    => "no copy"
      case NotFound.Kind.Refused => "refused"

  extension (scope: NotFound.Scope)
    private def missed(t: TypeName, key: Raw, from: Where): String = scope match
      case NotFound.Scope.Bundle       => s"$from: ${t.noun} $key is not in the bundle"
      case NotFound.Scope.BundleOrHere => s"$from: needs ${t.noun} $key, which is neither in the bundle nor here"
      case NotFound.Scope.Gazetteer    =>
        s"$from: ${t.noun} '$key' is not in this gazetteer — run `histmersion init` (or import that place) first"

  extension (hold: Conflict.Hold)
    private def said(subject: Where): String = hold match
      case Conflict.Hold.Open                  => s"$subject: open — decide it (`merge plan --write-template`)"
      case Conflict.Hold.SameWork(here, basis) =>
        s"$subject: the same work as $here here ($basis) — folding the two into one is `merge apply`'s"
      case Conflict.Hold.Folds(gate, decided, into) =>
        s"$subject: $gate decided $decided — folding it into $into is `merge apply`'s"
      case Conflict.Hold.Merges =>
        s"$subject: the bundle's and this collection's are merged into one — that is `merge apply`'s"
      case Conflict.Hold.Renames =>
        s"citekeys given to resolve a collision ($subject) — that is `merge apply`'s"
      case Conflict.Hold.Renumbers        => s"$subject: a label taken here, renumbered — that is `merge apply`'s"
      case Conflict.Hold.Carries(decided) => s"$subject = $decided: carrying that out is `merge apply`'s"
      case Conflict.Hold.Stale            =>
        s"$subject: not an issue of this plan — stale (the records changed since it was decided) or never one"
      case Conflict.Hold.Conditional(on, when, now) =>
        s"$subject: applies only if $on = $when, and $on ${now.fold("does not apply")(d => s"is $d")}"
      case Conflict.Hold.Collides          => s"several records of the bundle map onto $subject here"
      case Conflict.Hold.PlanChanged(sent) =>
        s"the plan is $subject, not $sent: this collection, the bundle or the resolutions changed since it was " +
          "planned — plan again, review it, and apply that plan"
      case Conflict.Hold.TextNotSent(decided) =>
        s"$subject: $decided — the merged text was not sent (the CLI reads the file)"
      case Conflict.Hold.CitekeyOfAnother(citekey) =>
        s"$subject: '$citekey' is a citekey (or an old one) of another source here — merge under the other citekey"
      case Conflict.Hold.CitekeyTaken(citekey) =>
        s"$subject: '$citekey' is already a citekey (or an old one) here or in the bundle"
      case Conflict.Hold.Focus(goal) =>
        s"$subject is held by the autopilot's focus: goal $goal is not one of the active goals. Pin it to run it now " +
          s"(histmersion agent autopilot focus pin $goal), or raise the cap (focus max <n>)"
      case Conflict.Hold.StoredAs(citekey) =>
        s"$subject is already stored as '$citekey' — pass force to ingest it again under a different citekey"

  extension (ending: Io.Ending)
    private def said: String = ending match
      case Io.Ending.Exited(code, stderr) =>
        s"exited with $code${if s"$stderr".isEmpty then "" else s" — $stderr"}"
      case Io.Ending.TimedOut(after)      => s"did not finish within $after ms"
      case Io.Ending.NotStarted(reported) => s"could not be started — $reported"
      case Io.Ending.Unanswered(request)  => s"ended without answering $request"

  extension (fault: Config.Fault)
    private def said: String = fault match
      case Config.Fault.Empty                       => "is empty"
      case Config.Fault.Unset(variable)             => s"reads $variable, which is not set"
      case Config.Fault.Malformed(raw)              => s"is not valid: '$raw'"
      case Config.Fault.Unknown(raw)                => s"has an unknown value '$raw'"
      case Config.Fault.Unregistered(id)            => s"names '$id', which no enabled plugin registered — skipped"
      case Config.Fault.Defaulted(raw, known, used) =>
        s"= '$raw' is neither ${known.mkString(" nor ")}; treated as $used"
      case Config.Fault.NoPlugin(id)     => s"names '$id', which is no plugin in this binary"
      case Config.Fault.Conflicts(other) => s"contradicts $other"
      case Config.Fault.NoSecret         => "names a secret no vault holds"
      case Config.Fault.Block            => "is a block of settings, not one setting: set one of the settings inside it"
      case Config.Fault.WrongKind(e, f)  => s"must be ${e.said}, not ${f.said}"
      case Config.Fault.Unreadable(r)    => s"cannot be read — $r"
      case Config.Fault.Undeclared       => "is no setting: it is ignored"
      case Config.Fault.NotInDefaults    => "is no setting of the plugin: its defaults do not declare it"
      case Config.Fault.OutsideRoot      => "is no setting: it is ignored (settings go under histmersion { … })"
      case Config.Fault.Credential(v)    =>
        "holds a key or password itself, so it is not written into the settings file: " +
          v.fold("set it in the server's environment instead")(n =>
            s"set the environment variable $n for the server instead"
          )
      case Config.Fault.Rejected(u, st) => s"was refused by $u (HTTP $st): check the key it holds"
      case Config.Fault.Literal         =>
        "holds value:, which writes the key itself into the settings file, where anyone who can read the file can " +
          "read it: use env:<NAME> or a password manager's entry, or confirm that you want value:"
      case Config.Fault.Substituted =>
        "is written in the settings file with a substitution (${…}) or +=, which is kept exactly as it is: change it " +
          "in the file itself"
      case Config.Fault.Included(file) => s"is set in $file, which the settings file includes: change it there"
      case Config.Fault.NotReadBack    => "did not read back as it was written"
      case Config.Fault.KeyNeeded(n)   =>
        s"holds no key, and the plugin needs $n: set it to a secret reference (\"env:<NAME>\" or a vault's, e.g. " +
          "\"keepassxc:<entry>#password\"); it was not asked"

  extension (kind: Config.ValueKind)
    private def said: String = kind match
      case Config.ValueKind.Text   => "text"
      case Config.ValueKind.Number => "a number"
      case Config.ValueKind.Flag   => "true or false"
      case Config.ValueKind.List   => "a list [ … ]"
      case Config.ValueKind.Block  => "a block of settings"
      case Config.ValueKind.Null   => "null"

  extension (fault: Plugin.Fault)
    private def said: String = fault match
      case Plugin.Fault.Threw(reported)            => s"loading it threw — $reported"
      case Plugin.Fault.SpiMismatch(built, speaks) =>
        s"it was built against plugin SPI $built; this Histmersion speaks SPI $speaks — rebuild the plugin against SPI $speaks"
      case Plugin.Fault.ForeignSpi(speaks) =>
        s"it was built against another plugin SPI; this Histmersion speaks SPI $speaks — rebuild the plugin against SPI $speaks"
      case Plugin.Fault.MissingClass(name) =>
        s"class '$name' is missing: the plugin uses a library that is neither part of Histmersion nor installed with it — " +
          "install the plugin with `histmersion plugin add` from a jar published with its POM, which lists its dependencies"
      case Plugin.Fault.Duplicate                   => "another plugin claims its id too"
      case Plugin.Fault.NullComponent               => "it registered null in place of a component"
      case Plugin.Fault.SignerChanged(pinned, seen) =>
        s"it was installed signed by $pinned, this jar is signed by $seen"
      case Plugin.Fault.NoProvider =>
        "it holds no plugin (no META-INF/services/com.histmersion.plugin.Plugin provider)"
      case Plugin.Fault.Taken(by) => s"its id is already provided by $by"
      case other                  => words(other.productPrefix)

  extension (refusal: Plugin.Refusal)
    private def said: String = refusal match
      case Plugin.Refusal.LicenceNotAllowed(f)     => s"licence $f not allowed"
      case Plugin.Refusal.NotOpen(field, stated)   => s"not Open Access ($field is ${statedOr(stated)})"
      case Plugin.Refusal.NoOpenImage(field, said) => s"no CC0 image ($field is ${statedOr(said)})"
      case other                                   => words(other.productPrefix)

  private def statedOr(stated: Vector[Raw]): String = if stated.isEmpty then "missing" else stated.mkString(", ")

  extension (failure: Plugin.Retrieval)
    private def said: String = failure match
      case Plugin.Retrieval.TimedOut(after) => s"it did not answer within $after ms"
      case Plugin.Retrieval.NoVault        => "no vault of that name is registered (enable the plugin that provides it)"
      case Plugin.Retrieval.Failed(stderr) => s"its tool failed — $stderr"
      case Plugin.Retrieval.Blank(ref)     => s"'$ref' has an empty value"
      case Plugin.Retrieval.Absent(ref)    => s"it holds no '$ref'"
      case Plugin.Retrieval.Unread(ref, exit, stderr) => s"it could not read '$ref' (exit $exit) — $stderr"
      case Plugin.Retrieval.NoKey(configured, seen)   =>
        val sees = if seen.isEmpty then "ykman sees no YubiKey" else s"ykman sees ${seen.mkString(", ")}"
        s"none of the configured YubiKeys (${configured.mkString(", ")}) is connected ($sees) — plug one in"
      case Plugin.Retrieval.Key(key, attempt, stderr) =>
        s"YubiKey $key: ${attempt.said}${stderr.fold("")(e => s" — $e")}"
      case other => words(other.productPrefix)

  extension (attempt: Plugin.Attempt)
    private def said: String = attempt match
      case Plugin.Attempt.NotConnected     => "not connected"
      case Plugin.Attempt.NotTouched       => "not touched in time"
      case Plugin.Attempt.WrongCredentials => "invalid credentials (the password, key file or YubiKey is wrong)"
      case Plugin.Attempt.ChallengeFailed  => "challenge-response failed"
      case Plugin.Attempt.Refused          => "an error KeePassXC gave"

  private def passedOver(passed: Vector[Problem.Run.PassedOver]): String = passed.map(_.sentence).mkString("; ")

  extension (floor: Problem.Run.Floor)
    private def said: String = floor match
      case Problem.Run.Floor.PlanOrAllowance => "below their plan floor or allowance"
      case Problem.Run.Floor.Allowance       => "below their allowance"

  extension (frees: Problem.Run.Frees)
    private def said: String = frees match
      case Problem.Run.Frees.At(at, model, held) =>
        (s"next reset at ${at.iso} ($model), when the run resumes by itself" +: held.map(_.said)).mkString("; ")
      case Problem.Run.Frees.Released(held) => s"no window frees by itself: ${held.toVector.map(_.said).mkString("; ")}"
      case Problem.Run.Frees.Never          => "no window frees by itself — raise or reset the allowance"

  /** What is wrong with a definition, after its file: most after a colon, what it names that is missing without. */
  extension (flaw: Config.Flaw)
    private def said: String = flaw match
      case Config.Flaw.NoHeader                => ": no YAML header (--- … ---)"
      case Config.Flaw.HeaderUnclosed          => ": the YAML header is not closed"
      case Config.Flaw.HeaderNotMapping        => ": the YAML header is not a mapping"
      case Config.Flaw.HeaderUnparsable(r)     => s": the YAML header does not parse: $r"
      case Config.Flaw.NotList(field, value)   => s": $field: expected a list, not '$value'"
      case Config.Flaw.NoName                  => ": no name"
      case Config.Flaw.NameNotFile(named)      => s": name '$named' is not the file's"
      case Config.Flaw.NoDescription           => ": no description"
      case Config.Flaw.RoleUnknown(roles)      => s": model-role is not one of ${roles.mkString(", ")}"
      case Config.Flaw.RoleUnused(role, roles) =>
        s": role $role is no contributed workflow's (one of: ${roles.mkString(", ")})"
      case Config.Flaw.ToolsUnoffered(tools, known) =>
        s": tools.histmersion names tools an agent cannot be offered: ${tools.mkString(", ")} " +
          s"(one of ${known.mkString(", ")})"
      case Config.Flaw.WebUnknown                  => ": tools.web is none, discover, search or fetch"
      case Config.Flaw.WritesUnknown               => ": writes is none, propose or direct"
      case Config.Flaw.TurnsNotPositive            => ": max-turns is a positive number"
      case Config.Flaw.RuntimesUnknown(runtimes)   => s": unknown runtime(s) ${runtimes.mkString(", ")}"
      case Config.Flaw.InteractiveUnset            => ": an interactive agent needs `interactive: {model, tools}`"
      case Config.Flaw.SkillNotNamed(name)         => s": its name is not '$name'"
      case Config.Flaw.IncludeMissing(include)     => s" includes '$include', which is not in agents/_shared/"
      case Config.Flaw.SkillMissing(skill)         => s" lists skill '$skill', which is not in skills/"
      case Config.Flaw.AudienceUnknown(audience)   => s": names no audience: only:$audience"
      case Config.Flaw.MarkerUnclosed(audience)    => s": only:$audience is never closed"
      case Config.Flaw.MarkerNested(audience, out) => s": only:$audience opens inside only:$out"
      case Config.Flaw.MarkerUnopened(audience)    => s": /only:$audience closes nothing open"
      case Config.Flaw.ToolUnknown(tool)           => s" names {{tool:$tool}}, which is not an MCP tool"
      case Config.Flaw.DescriptionLong             => ": the description is over 1,024 characters (opencode refuses it)"

  extension (setting: Problem.Run.Ceiling)
    private def said: String = setting match
      case Problem.Run.Ceiling.StepMaxTokens => "agents.step.max-tokens (HISTMERSION_AGENTS_MAX_TOKENS)"
      case Problem.Run.Ceiling.StepOwn       =>
        "the step's own cap (agents.review.fixer-max-tokens or agents.compile.draft-max-tokens)"

  extension (decoding: Content.Decoding)
    private def said: String = decoding match
      case Content.Decoding.Mismatch(path, reported) => s"the answer does not match the schema at $path: $reported"
      case Content.Decoding.NotJson(reported)        => s"the answer is not valid JSON: $reported"
      case Content.Decoding.Unreadable(reported)     => s"the answer could not be decoded: $reported"
      case Content.Decoding.NotSchema                => "the answer is not the JSON the schema asks for"

  extension (held: Problem.Run.HeldPool)
    private def said: String =
      s"the ${held.title} is held (release it with `histmersion agent quota release ${held.pool}`)"

  extension (block: Problem.Run.Block)
    private def said: String = block match
      case Problem.Run.Block.PoolFull(pool) => s"the $pool pool runs as many as it may"
      case other                            => words(other.productPrefix)

  extension (control: Conflict.Control)
    private def said(job: Raw, status: Conflict.State): String = control match
      case Conflict.Control.OnePiece(kind) =>
        s"a $kind job cannot be paused or cancelled: it runs as one piece and ends by itself"
      case Conflict.Control.Unrecognised        => s"job $job is $status"
      case Conflict.Control.Ended               => s"job $job has ended ($status)"
      case Conflict.Control.AlreadyPaused       => s"job $job is already paused"
      case Conflict.Control.NotStarted          => s"job $job has not started yet: cancel it instead"
      case Conflict.Control.PausedBeforeRestart =>
        s"job $job was paused before the server restarted: start it again — it goes on where it stopped"
      case Conflict.Control.NotPaused => s"job $job is not paused"

  extension (cause: Content.Flaw)
    private def said: String = cause match
      case Content.Flaw.HandWrittenSourcesCited => "a hand-written Sources cited section (the page generates it)"
      case Content.Flaw.UncitedParagraph        => "a paragraph cites no source"
      case Content.Flaw.UnknownCitekey          => "a citation names no source in the corpus"
      case Content.Flaw.FormattingIntroduced    => "a formatting error the last edits introduced"
      case Content.Flaw.MissingSection          => "a required section is missing"

  extension (rule: Content.Requirement)
    private def said: String = rule match
      case Content.Requirement.NoYearZero     => "there is no year 0; use BCE years as negative numbers"
      case Content.Requirement.CitesNoSource  => "the text cites no source"
      case Content.Requirement.UnknownCitekey => "it names a source that is not in the corpus"
      case Content.Requirement.OutsidePeriod  => "it falls outside the period"
      case Content.Requirement.StandIn        =>
        "it is a stand-in (a placeholder, TODO or lorem ipsum), not the text: write it, or leave it out"
      case Content.Requirement.TooManyClaims(cap, gave) => s"at most $cap claims per source (you gave $gave)"
      case Content.Requirement.ExactTitle               =>
        "it is empty — copy the source's exact title from get_source"
      case Content.Requirement.ReplaceText =>
        "a replace with no new text — give the text, or use delete to remove the passage"
      case Content.Requirement.AnchorInArticle =>
        "it is not in the article — copy it exactly from the current body (earlier edits included)"
      case Content.Requirement.QuoteGiven              => "no quote"
      case Content.Requirement.QuoteInRecords(records) =>
        s"the quote is not in the record of ${records.mkString(" or ")}"
      case Content.Requirement.KnownCitekey(stated)    => s"'$stated' is not a citekey"
      case Content.Requirement.ListedCandidate(stated) => s"'$stated' is not one of the listed candidates"
      case Content.Requirement.EvidenceGiven           => "no evidence given"
      case Content.Requirement.StatusNamed             => "a status change names no status"
      case Content.Requirement.NotUnassessed           => "unassessed is not a change to propose"
      case Content.Requirement.EdgeTarget              => "the edge target is not a citekey"
      case Content.Requirement.NoSelfEdge              => "an edge from a source to itself"
      case Content.Requirement.PositiveBce(stated)     => s"$stated BCE: a BCE year is a positive number"
      case Content.Requirement.CeFromOne(stated)       =>
        s"$stated CE: there is no year 0 (or a negative CE year); use BCE"
      case Content.Requirement.Calibrated(stated) =>
        s"$stated BP is an uncalibrated radiocarbon age; it cannot be stored as a calendar year"
      case Content.Requirement.StartBeforeEnd(start, end) => s"starts ($start) after it ends ($end)"
      case Content.Requirement.HistoricalReckoning        => "year 0 does not exist (1 BCE is -1, 1 CE is 1)"
      case Content.Requirement.YearWithin(greatest)       => s"a year beyond ±$greatest"
      case Content.Requirement.WithinSourcePeriod(start, end, ownStart, ownEnd) =>
        s"$start..$end lies outside the source's own period $ownStart..$ownEnd"
      case Content.Requirement.LinkTarget(target)  => s"[[$target]] is neither a citekey nor an article"
      case Content.Requirement.UnescapedLinks      => "escaped link brackets (\\[\\[…\\]\\]) link nothing"
      case Content.Requirement.SettingsGiven       => "give the settings to change as key=value"
      case Content.Requirement.KnownItems(unknown) =>
        s"not among the manager's items: ${unknown.mkString(", ")} — name them exactly as listed"
      case Content.Requirement.ItemsNamed        => "name the items (targets) or a tag"
      case Content.Requirement.HoldUntil         => "give `until` (a date) or `untilSource` (a DOI or citekey)"
      case Content.Requirement.DateGiven(stated) => s"'$stated' is not a date (2026-11-01)"
      case Content.Requirement.PriorityWithin(stated, max) => s"priority $stated is outside ±$max"
      case Content.Requirement.ExistingItems               => "none of the items it names exists"
      case Content.Requirement.FilterSelects               => "the filter selects nothing: give a query, topics or tags"
      case Content.Requirement.FilterRationale => "the rationale is empty — say why these sources are the article's"
      case Content.Requirement.PlanRationale   => "the rationale is empty — say how the articles cover the area"
      case Content.Requirement.ArticleTitle    => "it has no title"
      case Content.Requirement.ArticleFilter   => "it has no filter: give a query, topics or tags"
      case Content.Requirement.SummaryGiven    => "the summary is empty"
      case Content.Requirement.StatusEvidence  => "the status evidence is empty — say, from the text, why that status"
      case Content.Requirement.NotesGiven      => "the notes are empty"
      case Content.Requirement.ClaimGiven      => "the claim is empty"
      case Content.Requirement.NoClaim         => "no claim"
      case Content.Requirement.QuoteInRecord(citekey) =>
        s"the quote is not in the record of $citekey — copy it exactly"
      case Content.Requirement.ReasonGiven                     => "say why the record is not used"
      case Content.Requirement.StatusUnstated(citekey, status) =>
        s"the first citation of [[$citekey]] does not say it is $status"
      case Content.Requirement.QuoteInWindow(best) =>
        s"the quote is not in the text (best match ${best.twoDecimals}) — copy it exactly"
      case Content.Requirement.CenturyAsYear(year, century) =>
        val c = s"$century".toLong
        s"the year $year looks like a century stored as a year: '${ordinal(c)} century' is the years " +
          s"${(c - 1) * 100 + 1}–${c * 100} (BCE negative); a year below 100 needs the text to state it (e.g. '79 CE')"
      case Content.Requirement.PointerPhrase(phrase) =>
        s"'$phrase' only makes sense next to the table: a finding must stand alone on the map: name the date/event " +
          "instead of pointing at the table"
      case Content.Requirement.PlacesResolve(resolved, total, least, missing) =>
        val names = missing.map(u => s"'${u.place}' (finding ${u.finding})").mkString(", ")
        s"only $resolved of $total places resolve to a gazetteer place (at least $least% must); not found: $names — " +
          "give a place name the gazetteer knows (a site, city or region), or leave place null for a non-spatial claim"
      case Content.Requirement.ArticlesGiven                       => "the plan proposes no article"
      case Content.Requirement.TooManyArticles(cap, gave)          => s"at most $cap articles (you gave $gave)"
      case Content.Requirement.FilterTooNarrow(line, total, least) =>
        s"$line returns ${total.sources}; it must return at least $least — widen it (fewer topics or tags, a broader query)"
      case Content.Requirement.FilterTooBroad(line, total, most) =>
        s"$line returns $total sources; at most $most — narrow it (add a topic or tag, a sharper query)"
      case Content.Requirement.FilterMisses(line, returned, cited, share, least, missed) =>
        s"$line returns $returned of the $cited sources the article cites ($share%); it must return at least $least% " +
          s"— it misses ${missed.mkString(", ")}"
      case Content.Requirement.TextGiven             => "the text is empty"
      case Content.Requirement.KnownHeading(section) =>
        s"no article section '$section' — name an existing heading, or null to add at the end"
      case Content.Requirement.NoSourcesCited => "a Sources cited section is generated by the page; do not write one"
      case Content.Requirement.ParagraphCited(opening) =>
        s"a paragraph with no [[citekey]]: \"${s"$opening".take(80)}\""
      case Content.Requirement.StillUncited(opening) =>
        s"still a paragraph with no [[citekey]]: \"${s"$opening".take(80)}\""
      case Content.Requirement.SectionPresent(alternatives) =>
        s"no ${alternatives.map(a => s"'$a'").mkString(" or ")} section (writing guide rule 4)"
      case Content.Requirement.NoFiller(matches)    => s"banned filler — $matches"
      case Content.Requirement.ClaimTabled(citekey) => s"[[$citekey]] is cited but no claim of it is in the claim table"
      case Content.Requirement.TitleGiven           => "the title is empty — give the new article's title"
      case Content.Requirement.ArticleSummary       =>
        "the summary is empty — say in one or two sentences what the article answers"
      case Content.Requirement.NotUsedGenerated =>
        "a Not used section: code generates it from the filter — give each left-out source's reason in `reasons`"
      case Content.Requirement.MethodGenerated    => "a _Method_ note: code generates it from the filter"
      case Content.Requirement.NotUsedHandWritten =>
        "a hand-written Not used section: the page generates it from the article's filter and its reasons"
      case Content.Requirement.MethodHandWritten =>
        "a hand-written Method note: the page generates it (the filter, its counts, the statuses, the edges)"
      case Content.Requirement.ReasonForTabled(citekey) =>
        s"a reason for [[$citekey]], which is not a source of the claim table"
      case Content.Requirement.ReasonForUncited(citekey) =>
        s"a reason for [[$citekey]], which the draft cites (a reason is only for a source it leaves out)"
      case Content.Requirement.ReasonText(citekey)      => s"an empty reason for [[$citekey]]"
      case Content.Requirement.TabledAccounted(citekey) =>
        s"[[$citekey]] has claims in the table but is neither cited nor given a reason"
      case Content.Requirement.BodyGiven                           => "the body is empty"
      case Content.Requirement.FormatClean(rule, message, excerpt) =>
        val at = s"$excerpt"
        s"$rule — $message" + (if at.isEmpty then "" else s" · `${at.replace("`", "'")}`")
      case Content.Requirement.SubGoalTitle(slug)       => s"sub-goal '$slug' has no title"
      case Content.Requirement.QueryGiven               => "an empty query"
      case Content.Requirement.QueryWithin(query, most) =>
        s"query '${s"$query".take(40)}…' is longer than $most characters"
      case Content.Requirement.DoiGiven(stated)           => s"'$stated' is not a DOI (10.xxxx/…)"
      case Content.Requirement.TooManySubGoals(cap, gave) => s"at most $cap sub-goals (you gave $gave)"
      case Content.Requirement.TooManyQueries(cap, gave)  => s"at most $cap queries (you gave $gave)"
      case Content.Requirement.TooManyWorks(cap, gave)    => s"at most $cap works (you gave $gave)"
      case Content.Requirement.PlanGiven          => "the plan proposes nothing: give sub-goals, queries or works"
      case Content.Requirement.GoalPlanRationale  => "the rationale is empty — say how the plan covers the goal"
      case Content.Requirement.Panel(rule)        => rule.said
      case Content.Requirement.ProposedOnce(slug) => s"'$slug' proposed twice"
      case Content.Requirement.SlugFree           => "an article with this slug exists (or did)"
      case Content.Requirement.ArticleSources(line, total, least) => s"$line returns ${total.sources}; at least $least"
      case Content.Requirement.ArticleOverlap(share, other, most) =>
        f"${s"$share".toDouble * 100}%.0f%% of its sources are [[$other]]'s already (less than ${s"$most".toDouble * 100}%.0f%%)"
      case Content.Requirement.ArticlePassed =>
        "no proposed article passed the checks: fix the ones above or propose others"

  extension (rule: Content.PanelRule)
    private def said: String = rule match
      case Content.PanelRule.RecordsCited                        => "this reviewer cites no records"
      case Content.PanelRule.QuoteInSourceRecord(citekey, quote) =>
        s"the quote is not in the record of [[$citekey]]: \"${s"$quote".take(80)}\""
      case Content.PanelRule.SeverityKnown(stated) => s"severity '$stated' is neither major nor minor"
      case Content.PanelRule.ProblemStated         => "no problem stated"
      case Content.PanelRule.WhereStated           =>
        "no where stated — give the section heading or the passage's first words"
      case Content.PanelRule.VerdictGiven          => "no verdict given — PASS, MINOR or MAJOR"
      case Content.PanelRule.VerdictKnown(stated)  => s"verdict '$stated' is not PASS, MINOR or MAJOR"
      case Content.PanelRule.VerdictShown(verdict) =>
        s"the verdict is $verdict but no finding is ${s"$verdict".toLowerCase(java.util.Locale.ROOT)} — list the " +
          "finding that makes it so"
      case Content.PanelRule.CitekeyGiven               => "no citekey"
      case Content.PanelRule.SpotCited                  => "the article does not cite it — check a source it cites"
      case Content.PanelRule.SpotVerdict(stated, known) => s"verdict '$stated' is not one of ${known.mkString(", ")}"
      case Content.PanelRule.ClaimInArticle => "the claim is not in the article — copy the article's words exactly"
      case Content.PanelRule.RecordRead     =>
        "no get_source or passages call for it in this step — read the record before reporting what it says"
      case Content.PanelRule.SpotChecksVerified(verified, required, calls) =>
        s"spot-checks: $verified of the $required required are verified (on different cited sources; this step made " +
          s"$calls get_source/passages call(s)) — read the records now with get_source or passages, and report each " +
          "check with the record's sentence copied from the result"
      case Content.PanelRule.SlotKnown(stated, known) => s"empty slot '$stated' is not one of ${known.mkString(", ")}"
      case Content.PanelRule.ExplainBackGiven         => "no explain-back"
      case Content.PanelRule.CriticisedClaimQuoted    =>
        "its quote is not in the article — quote the article's sentence that states the criticised claim (or the " +
          "conclusion the critique would change), copied exactly"
      case Content.PanelRule.PairNamed(finding) =>
        s"$finding (missing-critique, major) names no pair of the critique checklist (its `pair` is empty): name the " +
          "pair it is about in `pair` (src>dst) — a disagreement with no recorded edge is a proposal, not a MAJOR"
      case Content.PanelRule.ChecklistPair       => "not a pair of the critique checklist"
      case Content.PanelRule.PairOpen(state)     => s"the pair is already $state"
      case Content.PanelRule.FindingStands       => "a missing-critique MAJOR names it too (the finding stands)"
      case Content.PanelRule.ReasonStated        => "no reason"
      case Content.PanelRule.ReasonWithin(n, at) => s"the reason is $n characters (at most $at)"
      case Content.PanelRule.PairsRuled(unruled) =>
        s"open pairs of the critique checklist without a ruling: ${unruled.mkString(", ")} — for each, a " +
          "missing-critique MAJOR naming it in `pair` (it bears on a claim the article makes), or a `notMaterial` " +
          "entry with the reason"
      case Content.PanelRule.CheckId(asked) =>
        s"a check with no id — give the id of the finding it checks (${asked.mkString(", ")})"
      case Content.PanelRule.AskedFinding(asked) =>
        s"not one of the findings you were asked to check (${asked.mkString(", ")})"
      case Content.PanelRule.QuoteInArticle(quote) =>
        s"the quote is not in the current article — copy it exactly: \"${s"$quote".take(80)}\""
      case Content.PanelRule.CheckQuoted(shows) => s"no quote — quote the current text that shows it ${shows.said}"
      case Content.PanelRule.CheckNoted         => "no note — say why it is not fixed (the fixer reads it)"
      case Content.PanelRule.CheckGiven         => "no check — say whether it is fixed"
      case Content.PanelRule.KnownFinding(id)   => s"there is no finding '$id'"
      case Content.PanelRule.FindingListed      => "no such finding"
      case Content.PanelRule.Rejectable(id, Content.Unrejectable.MechanicalCheck) =>
        s"$id is a mechanical check; it cannot be rejected, only fixed"
      case Content.PanelRule.Rejectable(id, Content.Unrejectable.ReadersConfusion) =>
        s"$id is a reader's confusion; it cannot be rejected — define or restructure instead"
      case Content.PanelRule.RejectionReason(id) => s"rejection of $id gives no reason — say why the finding is wrong"
      case Content.PanelRule.RejectionCitekey(id, stated) =>
        s"rejection of $id names '$stated', which is not a citekey"
      case Content.PanelRule.RejectionQuote(citekey) =>
        s"the quote is not in the record of $citekey — quote it exactly or apply the finding"
      case Content.PanelRule.WontFixId   => "a won't-fix with no id — give the id of the minor finding you decline"
      case Content.PanelRule.SuchFinding => "there is no such finding"
      case Content.PanelRule.OnlyMinorDeclined(id) =>
        s"$id is MAJOR: apply it or reject it — only a minor finding can be declined"
      case Content.PanelRule.GiveReason                 => "give the reason"
      case Content.PanelRule.PairAnswered(id, src, dst) =>
        s"$id is listed as applied, but [[$src]] and [[$dst]] are not cited in one paragraph — cite both where the " +
          "critique is discussed, or set it aside (`setAside`) with a reason"
      case Content.PanelRule.CritiquesDiscussed(cited, pairs) =>
        s"your edits cite ${cited.map(c => s"[[$c]]").mkString(", ")}, whose recorded critiques no paragraph " +
          s"discusses: ${pairs.map(p => s"${p.pair} (${p.kinds.mkString("/")})").mkString(", ")} — for each, cite " +
          "the critic in the paragraph that cites the criticised work, or set the pair aside (`setAside` with its " +
          "`pair`, no `id`) with a reason"
      case Content.PanelRule.ClosedKept(line) => s"your edits reopen a closed finding — $line: keep what fixed it"
      case Content.PanelRule.MajorDecided(id, Content.Decision.ApplyOrReject) =>
        s"major finding $id is neither applied nor rejected"
      case Content.PanelRule.MajorDecided(id, Content.Decision.DeclineUnfiltered) =>
        s"major finding $id is neither applied, rejected nor declined — a coverage finding on an article that " +
          "records no filter needs the filter recorded, which no edit does: put it in wontFix with that reason (it " +
          "stays open for a person)"
      case Content.PanelRule.EditMade(applied) =>
        s"you list ${applied.mkString(", ")} as applied, but no edit changed the article"
      case Content.PanelRule.ChecklistEdge(pair, remedy) =>
        s"$pair is not a pair of the article's critique checklist (no recorded edge)${remedy.said}"
      case Content.PanelRule.SetAsideNamed =>
        "a set-aside with neither an id nor a pair — give the finding's id, or the pair (src>dst)"
      case Content.PanelRule.MissingCritiqueOnly => "only a missing-critique finding can be set aside"
      case Content.PanelRule.FindingPair         => "the finding names no pair — give it in `pair` (src>dst)"
      case Content.PanelRule.ReasonsUnfiltered   =>
        "the article records no filter, so it has no generated Not used list: leave `reasons` empty"
      case Content.PanelRule.StoredReason  => "empty, and no reason is stored for it"
      case Content.PanelRule.FilterReturns => "the filter does not return it — give reasons only for its sources"
      case Content.PanelRule.LeftOut       => "the article cites it — a reason is only for a source it leaves out"
      case Content.PanelRule.StillUnplaced(findings, left) =>
        s"${findings.mkString(", ")}: still neither cited nor given a reason: " +
          left.take(20).map(ck => s"[[$ck]]").mkString(", ") + (if left.size > 20 then s" (+${left.size - 20} more)"
                                                                else "")

  extension (shows: Content.Shows)
    private def said: String = shows match
      case Content.Shows.Fixed      => "fixed"
      case Content.Shows.StillThere => "still there"

  extension (remedy: Content.Remedy)
    private def said: String = remedy match
      case Content.Remedy.Unstated      => ""
      case Content.Remedy.ApplyOrReject => " — apply or reject the finding"
