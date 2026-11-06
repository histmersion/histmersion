# Rules for fixing compile errors on this branch

The tree was moved from primitives to newtypes. Fix errors so that the code is right, not merely compiling.

- An opaque newtype's only operations are its own. A domain operation belongs on the type or its companion: when code
  does `placeId.startsWith("pleiades_")`, add an extension such as `Place.Id.isPleiades` beside the type and call it.
- Do not bridge two newtypes with `A(b.value)` in domain code. If they are the same concept, make the signature use one
  type. `.value` and constructing from a raw value are fine at real boundaries: JSON, SQL, HTTP, CLI parsing, Markdown
  text assembly, a library call, and inside the type's own companion.
- Types whose names are positional (`...Result`, `...Part1`, `...Arg1`, `...S`, `...Raw`) are leftovers: replace them with
  the real concept nearby, or the plain type if it is a private helper's text or number, and delete the definition.
- Boolean results of predicates are plain `Boolean`. Private helpers inside a newtype's own companion may use the
  underlying value. Public signatures use concepts, not primitives.
- API-layer types (`com.histmersion.api`) are unconstrained and never validate. Never make one module depend on another
  to fix an error.
- Never use `asInstanceOf`, `@unchecked`, `@nowarn`, a global given conversion between types, or `Any`. Never weaken or
  delete a test or an assertion, and never change behaviour to get a pass: the output of each expression stays the same.
- No comments explaining the change. Do not reformat lines you are not changing. Do not touch files outside your list.
- Work only in this worktree. Never touch the live server, config, database or user data, and run no real network calls.
