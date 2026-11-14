package com.histmersion
package util.problem

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
      case Invalid.Malformed(t, c, raw, at)   => s"${path(at)}'$raw' is not a ${t.short}: ${c.description}"
      case Invalid.Unknown(t, raw, known, at) => s"${path(at)}unknown ${t.short} '$raw'${oneOf(known)}"
      case Invalid.Unreadable(t, raw, eg, at) => s"${path(at)}'$raw' is not a ${t.short}${forExample(eg)}"
      case Invalid.Missing(field, at)         => s"${path(at)}$field is required"
      case Invalid.Undeclared(field, ds, at)  => s"${path(at)}unknown key '$field'${oneOf(ds.map(d => Raw(s"$d")))}"
      case Invalid.Wrapped(_, raw, member, at) =>
        s"${path(at)}a $raw is not the value itself: give its \"$member\" member"
      case Invalid.StoredOn(_, item, owner, at) =>
        s"${path(at)}$item is stored on '$owner', not here: patch '$owner' to record it"
      case Invalid.Ambiguous(t, raw, n, at)   =>
        s"${path(at)}the ${t.short} '$raw' matches $n places — quote more of it so it matches one"
      case Invalid.Repeated(t, raw, at)        => s"${path(at)}${t.short} '$raw' is given more than once"
      case Invalid.Misplaced(field, under, at) => s"${path(at)}'$field' belongs under '$under' ($under.$field)"
      case Invalid.OutOfRange(t, raw, b, at)   => s"${path(at)}${t.short} $raw is ${b.range}"
      case Invalid.Inconsistent(fs, rule, at)  => s"${path(at)}${rule.of(fs)}"
      case Invalid.Reference(form, at)         => s"${path(at)}${form.said}"
      case NotFound.Entity(t, key) if t.short == "Citekey" => s"No source '$key'"
      case NotFound.Entity(t, key)                         => s"no ${t.short} '$key'"
      case NotFound.Copy(work)                             => s"no legal open-access PDF found for $work"
      case Conflict.AlreadyExists(t, key)                  => s"${t.short} '$key' already exists"
      case Conflict.Renamed(t, asked, now)     => s"${t.short} '$asked' was renamed to '$now' — write to '$now' instead"
      case Conflict.Stale(t, key, sent, now)   => s"${t.short} '$key' has moved on: you sent $sent, it is now $now"
      case Conflict.WrongState(t, key, is, ws) => s"${t.short} '$key' is $is; this needs ${ws.mkString(" or ")}"
      case Conflict.Busy(activity)             => activity.running
      case Conflict.Referenced(t, key, by, n)  => s"${t.short} '$key' is referred to by $n ${by.short}"
      case Refused.Policy(rule)                => rule.said
      case Refused.Quota(limit, used, cap, at) => s"${limit.named} is spent: $used of $cap${until(at)}"
      case Refused.Disabled(feature, setting)  => s"$feature is switched off ($setting)"
      case Upstream.Http(name, url, st, body, at) => s"${path(at)}$name answered HTTP $st at $url — $body"
      case Upstream.Challenged(name, url, st, at) =>
        s"${path(at)}$name answered HTTP $st at $url with a bot-protection challenge, not the page"
      case Upstream.Limited(name, at)             => s"$name's usage limit is reached${until(at)}"
      case Upstream.Unreachable(name, url, r, at) => s"${path(at)}cannot reach $name at $url — $r"
      case Upstream.Timeout(name, url, after, at) => s"${path(at)}$name did not answer at $url within $after ms"
      case Upstream.BadReply(name, url, s, r, at) =>
        s"${path(at)}$name's answer at $url is not ${s.noun} — $r"
      case Io.File(op, path, reported)    => s"could not ${words(op.productPrefix)} $path — $reported"
      case Io.Wrong(path, expected)       => s"$path ${expected.missed}"
      case Io.Corrupt(format, at, damage) => s"the ${words(format.productPrefix)} data at $at ${damage.said}"
      case Io.Process(tool, ending)       => s"$tool ${ending.said}"
      case Io.Database(op, reported)      => s"the database refused to ${words(op.productPrefix)} — $reported"
      case Config.Setting(key, fault)     => s"$key ${fault.said}"
      case Config.Unavailable(capability) => s"this server has no ${words(capability.productPrefix)}"
      case Config.Reload(stage, scope)    => s"reloading the settings failed at ${words(stage.productPrefix)} ($scope)"
      case Config.Cycle(scopes)           =>
        s"the parts a reload rebuilds depend on each other in a cycle: ${scopes.toVector.mkString(" → ")}"
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
      case Plugin.Rejected(plugin, item, why)   => s"$plugin did not take $item: ${why.said}"
      case Plugin.Vault(vault, failure)         => s"secret vault $vault failed: ${failure.said}"
      case Plugin.Said(plugin, claim, text)     => s"$plugin (${words(claim.productPrefix)}): $text"
      case Problem.Run.Paused(reason, at)       => s"paused for ${words(reason.productPrefix)}${until(at)}"
      case Problem.Run.NoModel(role, tried)     => s"no model of the $role chain can run${triedModels(tried)}"
      case Problem.Run.Blocked(block)           => s"held back: ${block.said}"
      case Problem.Run.Interrupted              => "interrupted by a restart"
      case Problem.Run.CutShort(activity)       => activity.cutShort
      case Problem.Run.Unrecorded(activity)     => activity.unrecorded
      case Problem.Run.Cancelled(by)            => s"cancelled by ${words(by.productPrefix)}"
      case Problem.Run.Draining(since)          => s"stopped while the autopilot drains (since ${since.iso})"
      case Problem.Run.Aborted(step, cause)     => s"step $step cannot go on: ${words(cause.productPrefix)}"
      case Content.Finding(cause)               => cause.said
      case Content.Proposal(rule, at)           => s"$at: ${rule.said}"
      case Internal.Unexpected(id, where, e, r) => s"$where failed unexpectedly ($e, problem $id) — $r"
      case Unclassified(text)                   => s"$text"
      case Stored.Unrecognised(text)            => s"$text"

    /** The sentence without the one item or input path it names: what a report counts problems of a kind by (every
      * record a collection refused as restricted is one reason, not one sentence per record; the report is about one
      * collection, so a refusal's reason does not name it again).
      */
    def reason: String = problem match
      case Plugin.Rejected(_, _, why)            => why.said
      case NotFound.Entity(t, _)                 => s"no such ${t.short}"
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
      case other                                 => other.sentence

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

  private def forExample(examples: Vector[Raw]): String =
    if examples.isEmpty then "" else s" (e.g. ${examples.mkString(", ")})"

  private def oneOf(known: Vector[Raw]): String = if known.isEmpty then "" else s" (one of: ${known.mkString(", ")})"

  extension (bounds: Invalid.Bounds)
    private def range: String = bounds match
      case Invalid.Bounds(Some(least), Some(greatest)) => s"not between $least and $greatest"
      case Invalid.Bounds(Some(least), None)           => s"below $least"
      case Invalid.Bounds(None, Some(greatest))        => s"above $greatest"
      case Invalid.Bounds(None, None)                  => "out of range"

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
      case other => s"a ${words(other.productPrefix)} is still running"

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
      case Io.Kind.Inside(directory) => s"is outside $directory, the directory it is confined to"

  extension (damage: Io.Damage)
    private def said: String = damage match
      case Io.Damage.Checksum                => "fails its checksum"
      case Io.Damage.Truncated               => "is cut short"
      case Io.Damage.Undecodable             => "cannot be decoded"
      case Io.Damage.Length(expected, found) => s"holds $found units, not $expected"
      case Io.Damage.Unreadable(reported)    => s"cannot be read — $reported"
      case Io.Damage.Newer(found, reads) => s"is in format $found, newer than the format $reads this Histmersion reads"

  extension (ending: Io.Ending)
    private def said: String = ending match
      case Io.Ending.Exited(code, stderr) => s"exited with $code — $stderr"
      case Io.Ending.TimedOut(after)      => s"did not finish within $after ms"
      case Io.Ending.NotStarted(reported) => s"could not be started — $reported"

  extension (fault: Config.Fault)
    private def said: String = fault match
      case Config.Fault.Empty            => "is empty"
      case Config.Fault.Unset(variable)  => s"reads $variable, which is not set"
      case Config.Fault.Malformed(raw)   => s"is not valid: '$raw'"
      case Config.Fault.Unknown(raw)     => s"has an unknown value '$raw'"
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
        s"class $name is missing: the plugin uses a library that is neither part of Histmersion nor installed with it — " +
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

  private def triedModels(tried: Vector[Problem.Run.Model]): String =
    if tried.isEmpty then "" else s" (passed over: ${tried.mkString(", ")})"

  extension (block: Problem.Run.Block)
    private def said: String = block match
      case Problem.Run.Block.PoolFull(pool) => s"the $pool pool runs as many as it may"
      case other                            => words(other.productPrefix)

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
