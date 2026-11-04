# Feature ownership rewrite verification

The input fixtures cover same-package references, a class and object moving together, a dependency remaining in the old package, renamed and grouped imports, qualified references, constructor/type references and unrelated same-named symbols. `expected/` holds the reviewed output. No assertions or application behavior are changed by the ownership rewrite.

Use Scala 3.10.0 and `-source:future` to compile the output. The built-in `Patch.replaceSymbols` was unsuitable here: it generated `=>` import aliases, rejected by this source mode, and discarded the alias at call sites. FeatureOwners instead preserves `as` and the local alias spelling.

Run Scalafix CLI 0.14.9 on Scala 2.13.18 with `--scala-version 3.10.0`. The fixture source root is `tools/refactor/owner-fixture/src/test/scala`; compile its SemanticDB into `target/owner-fixture`. Configurations:

- `owner-fixture.conf`: inspect; expect three files and ten references.
- `owner-fixture-apply.conf`: apply using `--out-from tools/refactor/owner-fixture/src/test/scala --out-to target/owner-fixture-output/src/test/scala`. File relocation is disabled for this output comparison. Compare the three resulting files to `expected/`, then compile with `-source:future`, writing SemanticDB into `target/owner-fixture-output-classes` with the output directory as source root.
- `owner-fixture-check.conf`: check the original input; it must reject ten old-owner references.
- `owner-fixture-output-check.conf`: check the compiled output using its source root and classpath; it must pass with zero references.
- `owner-fixture-collision.conf`: must reject an existing destination before patching.
- `owner-fixture-stale.conf`: must reject a changed ownership policy before patching.

Source fingerprint rejection is tested by replacing one hash in the generated `refactor-audit/owner-fixture/sources.tsv` with an invalid hash and invoking the apply configuration: it must fail with `Stale inventory` before patching. Regenerate the manifest with the inspect configuration afterward; the input and expected fixture sources never need changing.

The application configuration is `feature-owners.conf`, with explicit source/destination paths, packages and all top-level names. Inspect visits the whole production/test tree and records source hashes. Apply checks that manifest and policy, patches resolved references, then parses every moved file and verifies its new package before any file moves. The entire operation is not a filesystem transaction: a runtime or compilation failure still requires inspecting the worktree. Check mode, after recompilation, enforces complete coverage, destination existence and absence of old symbols. Adding another batch requires changing the mapping configuration and rerunning inspect, not writing another rule.
