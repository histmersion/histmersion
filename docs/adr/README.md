# Architecture decision records

Code in this repository carries no comments (AGENTS.md, *Coding practices* 6; the `G22comment` ratchet in
`GuidelineRatchetSuite`). What a comment used to say goes to one of three places:

- what the code does: its names, types and structure (rename, extract, introduce a type);
- a shortcut, workaround or "for now": an issue that describes the proper fix;
- an important, non-obvious decision (why this design, a constraint, an invariant, an external quirk): a record here.

Each record is short and stands on its own, in the MADR shape:

```markdown
# NNNN. Title

- Status: accepted | superseded by NNNN
- Date: YYYY-MM-DD

## Context
What forces the decision: the constraint, the external behaviour, the problem.

## Decision
What was decided, stated as the rule the code follows.

## Consequences
What follows from it, good and bad, and what would change it.

## Code
The files (and functions) that implement it, so a reader of the code can find the reason and the reverse.
```

Records are numbered in order and never renumbered. A record that no longer holds is marked superseded by the one
that replaces it rather than edited into a different decision. Group related decisions into one record; one record per
comment is too fine. The `delete-comment` skill (`.claude/skills/delete-comment/SKILL.md`) describes how comments are
audited and moved here.

| Record | Title |
|---|---|
| [0001](0001-bundle-install-links-locally.md) | The bundle links its server on the installing machine, with a prebuilt fallback |
| [0002](0002-macos-local-signing-identity.md) | macOS binaries are signed with a stable local identity, untrusted by default |
| [0003](0003-gpg-provenance-at-service-start.md) | Optional GPG provenance, verified at every service start |
| [0004](0004-sloth-output-is-verified.md) | Sloth's rewritten bytecode is verified, and broken classes restored |
| [0005](0005-relocatable-bundled-runtimes.md) | The bundled runtimes are self-contained and relocatable |
| [0006](0006-installer-keeps-user-settings.md) | The installer keeps the user's settings |
| [0007](0007-shell-scripts-bash32-and-isolated-tests.md) | Shell scripts run on Bash 3.2, and their tests never touch the user's machine state |
| [0008](0008-plugin-authoring-against-the-bundle.md) | Plugins are authored against the bundle's own Maven repository |
| [0009](0009-newtype-kernel.md) | One newtype kernel gives every concept its instances, and conversions follow Iron |
| [0010](0010-value-conventions.md) | Conventions of the shared values and vocabularies |
| [0011](0011-test-plugin-fixtures.md) | Test plugins prove one property each, with no network |
| [0012](0012-developer-build-and-test-tooling.md) | Developer build and test tooling |
| [0013](0013-ocr-converter.md) | The OCR converter is a stateless Python sidecar |
| [0014](0014-cli-exit-codes-and-following-work.md) | The CLI's exit code follows the problem, and following server work is bounded |
| [0015](0015-web-bundle-build-identity.md) | The page and the server agree on the web bundle's build |
| [0016](0016-server-pages-on-api-html.md) | The server's pages are built from api.Html, and search marks only text |
| [0017](0017-ratchet-checker-scope.md) | What two ratchet checkers count: newtype companions and test suites |
| [0018](0018-structured-log.md) | The structured log: one writer, a table and rolling files under the same ids, traced on the thread |
| [0019](0019-newtype-wire-codecs.md) | Unconstrained newtypes cross the wire unchecked; only they take a text codec |
| [0020](0020-checker-scope-lifted-instances-and-own-names.md) | What two more ratchet checkers count: lifted newtype instances and a file's own type names |
| [0021](0021-stall-watchdog-interrupts-before-killing.md) | The stall watchdog interrupts a run before it kills its processes, and a stalled run checkpoints nothing |
| [0022](0022-process-session-output-read-to-end.md) | A process session's output is read to the end before its exit is reported |
| [0025](0025-map-markers-drawn-on-a-canvas.md) | The map's point markers are drawn on a canvas; their elements stay as the accessible proxies |
| [0026](0026-wire-enums-and-schemas-from-one-config.md) | A wire enum of words is a string; codecs and schemas read one config, and a refused input is a problem |
