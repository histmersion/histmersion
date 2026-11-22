# 0022. A process session's output is read to the end before its exit is reported

- Status: accepted
- Date: 2026-11-21 (amended the same day: the session reads FIFOs it owns, not the JDK's process pipes)

## Context

`SystemProcesses.Session` (the plugin host's long-running tool, used by `keepassxc-cli open`) copies the tool's stdout
and stderr on two reader threads. A process can exit while a reader is still copying its last bytes out of the pipe.
`exit`, `close` and the awaits returned as soon as the process had exited, so a caller that read `err` right after
`exit` could miss the tool's last message. KeePassXcVault reads the exit and then stderr when a key is refused: on a CI
runner the wrong-password message was sometimes not copied yet, and the refusal said "no message" (VaultsSuite, CI
44902967881).

Waiting for the readers was not enough on Linux (CI 44941200067, 44941720342): the JDK does not leave the pipes
behind `Process.getInputStream` / `getErrorStream` open until their writers close them. On Linux and macOS
`ProcessImpl` registers an exit hook (`ProcessPipeInputStream.processExited`, run by the process reaper thread) that,
as soon as the tool exits, copies only the bytes already in the pipe, closes the read end and swaps in a byte-array
stream. Whatever a process the tool started writes afterwards is lost, and the readers see end of stream at once. The
hook is `synchronized` on the stream, as is `read`, so a reader already blocked in `read` holds it off until its read
returns: that is why macOS (slower to spawn, so the readers were always reading first) passed and Linux, where a
`sh` script exits within milliseconds and the first virtual reader thread may not have started yet, did not. A
standalone reproduction (`sh` script that exits 1 while a background child writes to stderr 1 s later) lost the
message on Linux in the cold first run, and on both systems when the reader started 100 ms after the spawn.

## Decision

The session gives the tool two named pipes (FIFOs, made with `mkfifo -m 600` in a private temporary directory) as its
stdout and stderr instead of the JDK's pipes, and opens their read ends before the tool starts (a read-write open
first, so neither open blocks). The JDK only opens and closes the write ends, so no exit hook touches them: the read
ends see end of stream when the tool and every process that inherited them have closed them. The FIFO names are
removed as soon as the tool has started. When the FIFOs cannot be made, the session falls back to the JDK's pipes.

Once the tool has exited, `exit`, `close` and an await whose condition does not hold wait until both readers have
reached the end of their streams, for at most 2 s. A process the tool started that keeps the pipes open is not waited
for beyond that.

## Consequences

`out` and `err` hold everything the tool, and any process it started, printed before the reported exit plus up to
2 s after it. A tool whose child keeps its pipes open costs up to 2 s more when it exits. Starting a session spawns
`mkfifo` once (a POSIX utility present on macOS and Linux). The one-shot `run` and `capture` still read the JDK's
pipes: they hand over what the tool wrote before it exited, not what a child writes later.

## Code

`SystemProcesses.Session` (`start`, `fifoDir`, `readEnds`, `awaitReadersAtEnd`, `exit`, `close`, `await`). Test:
VaultsSuite, "keepassxc batch: a refusal the tool's stderr carries only after it exited is still read".
