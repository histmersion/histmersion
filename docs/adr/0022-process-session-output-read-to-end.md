# 0022. A process session's output is read to the end before its exit is reported

- Status: accepted
- Date: 2026-11-21

## Context

`SystemProcesses.Session` (the plugin host's long-running tool, used by `keepassxc-cli open`) copies the tool's stdout
and stderr on two reader threads. A process can exit while a reader is still copying its last bytes out of the pipe.
`exit`, `close` and the awaits returned as soon as the process had exited, so a caller that read `err` right after
`exit` could miss the tool's last message. KeePassXcVault reads the exit and then stderr when a key is refused: on a CI
runner the wrong-password message was sometimes not copied yet, and the refusal said "no message" (VaultsSuite, CI
44902967881).

## Decision

Once the tool has exited, `exit`, `close` and an await whose condition does not hold wait until both readers have
reached the end of their streams, for at most 2 s. A process the tool started that keeps the pipes open is not waited
for beyond that.

## Consequences

`out` and `err` hold everything the tool printed by the time its exit is reported. A tool whose child keeps its pipes
open costs up to 2 s more when it exits.

## Code

`SystemProcesses.Session` (`awaitReadersAtEnd`, `exit`, `close`, `await`). Test: VaultsSuite, "keepassxc batch: a
refusal the tool's stderr carries only after it exited is still read".
