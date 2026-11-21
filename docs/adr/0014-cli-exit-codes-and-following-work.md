# 0014. The CLI's exit code follows the problem, and following server work is bounded

- Status: accepted
- Date: 2026-11-21

## Context

Scripts and agents drive Histmersion through `histmersion …`, and branch on its exit code. The code used to be chosen
at each failure site (1 usage, 2 refused, 3 unreachable), so the same problem exited differently depending on where it
was met, a failed job sometimes exited as a usage error, and nothing said whether a write that lost its connection had
been applied. Following a job or a backup polled until the work ended, with no limit, and the first failed poll ended
the command. The owner decided (E15) that the code is worked out from the problem; G111, G112 and G113 carried it out.

## Decision

**Exit codes** (`cli/ExitCode`)
- A failed command exits with a code worked out from its problems' cases and from where the CLI met them, never one
  given at the site: 1 usage, 2 refused, 3 unreachable, 4 not found, 5 conflict, 6 unavailable, 7 fault, 8 outcome
  unknown; success is 0. Each code says what a caller does next: 1 fails again unchanged, 3 is safe to send again,
  5 means read the record again, 6 means try later, 7 does not get better by retrying, 8 means check before sending
  again. The CLI reference (`docs/user-guide/reference/cli.md`) lists the same table.
- Where a problem was met (`ExitCode.Origin`) matters: the same case can be the caller's input here or the server's
  state. A file this machine cannot read, a bad local setting, an unclassified problem the CLI found itself, or a copy
  on this machine that failed is the command's to fix (1); a file or setting the server cannot read is a fault (7); an
  upstream the CLI cannot reach is unreachable (3), one the server cannot reach is unavailable (6). `Cli.Error(problems)`
  is a problem the CLI found, `Cli.Error.answered(problems)` one the server answered with (a refusal, a failed job, a
  bundle that did not verify).
- Among several problems, an unknown outcome wins, then the server out of reach, else the first problem's code, as the
  HTTP status is the first problem's.
- A child process's exit code (`Problem.Io.ExitCode`, a kit script, curl) is not the CLI's own. `histmersion-relink`'s
  own 1 is its usage error; its 2 (refused before building), 3 (the build or the self-check failed) and 4 (rolled
  back) are reported as the process's ending.

**What a lost connection means** (`cli/Client`)
- A connection never made sent nothing: unreachable (3), safe to send again. A write (POST, PUT, PATCH, DELETE) whose
  connection ended after it was sent, by a reset or a read that timed out, may have been applied:
  `Problem.Upstream.Unanswered`, outcome unknown (8). A read that failed changed nothing and is unreachable too.
- Connecting waits at most 10 s. An ordinary call has no client-side read timeout, because the server bounds every
  request itself and a backup takes longer than sttp's one-minute default. A poll of running work answers at once, so
  it waits 30 s; a server that stops answering without closing the connection is a failed poll, not a CLI that hangs.
- A backup's file name comes back over the wire and becomes a path on this machine, so the CLI checks it with the same
  gate the server applies before it downloads.

**Following server work** (`cli/JobFollow`)
- A job (`jobs`) and a backup are followed one way. Work that ends badly exits with its problem's code, never 0: a
  script must not read a failure as success.
- Following is bounded. A poll that fails for a moment (the server out of reach, busy, or answering nothing) is asked
  again after a wait that doubles from the poll interval up to 30 s; five failed polls in a row end the follow. Work
  still running when the follow timeout runs out ends with outcome unknown (8) and the last state seen, naming the
  command that shows how it ends. Any other failed poll (the job is gone, the key refused) ends the follow at once with
  its own code.
- The timeout is the setting `cli.follow-timeout-seconds` (`HISTMERSION_FOLLOW_TIMEOUT`), six hours by default because
  an OCR of a long book takes about one; a value of 0 or less is refused as a setting problem that names the value.
  Jobs are polled every second, a backup every five, since each backup answer prints a line.
- A merge apply interrupted by a server restart left nothing half applied, but whether it committed shows only in
  `merge list`, so its outcome is unknown here (8).

**`--json` on every command** (`cli/CommandLine`, `cli/Cli`)
- Every command takes `--json`. Under it a failure prints, on stdout, the body a failed HTTP request answers
  (`{"problems": [{"group", "case", "data", "text"}]}`), so a script reads the same typed entries from the CLI as from
  the API; a command line that does not parse is answered in JSON too. A command that did not declare `--json` gets it
  after its own options. `patch` and `article patch` keep their own `--json`, which takes the whole patch as its value.

**Tests**
- The transport tests run against a stub on the loopback interface that reads each request whole and closes the
  connection without answering, on every connection, because the JDK's HTTP client sends a GET a second time.
- `ExitOnProblemsSuite` searches the CLI's sources for a site that gives an exit code; its own pattern is assembled
  from two halves, so that the search does not find the suite itself.

## Consequences

- A script can tell a usage error, a refusal, an unreachable server and an unknown outcome apart, and knows from the
  code whether sending again is safe.
- No `histmersion` command waits for ever on a job, a backup or a silent server.

## Code

`modules/cli/src/main/scala/com/histmersion/cli/` (`ExitCode`, `Cli`, `Client`, `JobFollow`, `CommandLine`, `Runner`,
`PluginOps`, `MergeCmd`), `modules/problem/.../Problem.scala` (`Upstream.Unanswered`, `Config.Fault.NotPositiveSeconds`),
`modules/config/.../CliConfig.scala`, the suites `ExitOnProblemsSuite`, `ClientSuite`, `JobFollowSuite`,
`JsonEverywhereSuite`, and the rewrite `tools/refactor/DropCliExitCode.scala`, which dropped the explicit codes: a
named `exitCode = …` or a positional `ExitCode.<Name>` of every `Cli.Error(...)`. A site whose problem is an
in-place `Problem.Unclassified(...)` with a code other than usage would have changed its code (a local unclassified
problem exits as a usage error), so it was listed `retype` for a hand edit (`Cli.Error.answered`, or a typed case); a
code that is no literal was listed `by-hand`. It runs from the worktree root, `dry-run` first, then `apply`:
`bash tools/refactor/scalafix.sh --config tools/refactor/drop-cli-exit-code.conf --files modules/cli/src` (and
`drop-cli-exit-code-apply.conf`), then `cli/scalafmtOnly` on the changed files; re-run after an apply, its listing is
empty.
