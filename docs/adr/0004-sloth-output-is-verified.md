# 0004. Sloth's rewritten bytecode is verified, and broken classes restored

- Status: accepted
- Date: 2026-11-21

## Context

Libraries compiled with Scala before 3.8 implement lazy vals with `sun.misc.Unsafe`, and JDK 25 prints a deprecation
warning on every run that touches them. Sloth rewrites those classes before native-image. Sloth rewrites whole classes,
though, and it has emitted invalid stack-map frames for at least one constructor that contains no lazy val at all:
sttp's `SafeGZIPInputStream` threw a `VerifyError`, so every gzip-encoded upstream response failed its request.

## Decision

- After Sloth, `SlothVerify.java` loads every class whose bytes Sloth changed, in a fresh class loader over the patched
  class path, with `getDeclaredConstructors()`. That links and verifies the class without running static initialisers.
  On a `VerifyError` that names the class, the original bytes are put back. A class without lazy vals loses nothing; one
  with them keeps the old scheme (and the warning) but works.
- A class that cannot be loaded at all (an optional dependency missing, a broken superclass) is not Sloth's doing; it
  is kept and judged in its own jar's pass.
- The tool runs in its own JVM, so the classes it loads never touch the build's.
- Tools that run outside the native image and still carry pre-3.8 lazy vals (the coursier-based plugin resolver) run
  with `--sun-misc-unsafe-memory-access=allow`. Without it the JDK prints its warning between the tool's own output
  lines.
- The smoke test fails when the CLI or the server prints the `sun.misc.Unsafe` warning, which would mean Sloth did not
  run.

## Consequences

- A Sloth bug degrades one class to the old lazy-val scheme instead of breaking requests at run time.
- The tool exits 1 when a restored class still does not verify, which stops the build.

## Code

`scripts/bundle/SlothVerify.java`, `kit-common.sh` (`hk_resolve`), `smoke.sh`, the Sloth wiring in `build.sbt` and
`project/`.
