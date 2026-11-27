---
name: delete-comment
description: Audit and delete code comments in Histmersion — classify each comment as tautology, stale, excuse or decision record, move decisions into docs/adr/, draft issues for excuses, and remove the comments with the committed tool. Use when CommentGrowthSuite fails, when cleaning a module's comments, or when asked to remove or audit comments.
---

# Delete comments

Code in this repository carries no comments (AGENTS.md, *Coding practices* 6). `CommentGrowthSuite` enforces it per
file: no file holds more comments than at the merge base with the pilot (a file the base does not have: none). Nothing
records the counts; a deleted comment is simply gone. This skill is the procedure for deleting them.

## 1. Scope

Counted: comments in Scala (JVM and Scala.js, `build.sbt`, `project/`), Java, JavaScript/MJS, CSS, HTML (including
inline `<script>`/`<style>`), Python (`#` comments and docstrings), shell and SQL, anywhere `git ls-files` lists them.
Adjacent line comments (`//`, `#`, `--`) form one block; a block or doc comment is a block of its own.

Not counted (the scanner exempts them, and you never delete them):

- **Applied Flyway migrations** (`modules/db/src/main/resources/db/migration/`): editing one changes its checksum, which
  `architecture/migrations.tsv` pins.
- **Vendored code** (any `vendor/` directory) and **test fixture pages** (HTML under `src/test/resources/`).
- **Functional comments**: `//> using`, `// scalafmt:`, `// format: off|on`, `// scalafix:`, `// nowarn`, shebangs,
  `# type:`, `# noqa`, `# pragma`, `# fmt:`, PEP 723 `# /// script` headers, `# shellcheck`, eslint/`@ts-`/`/*!`
  directives.
- **Help text a program prints from its own source**: a shell header inside a `sed -n 'A,B p' "$0"` range, a Python module
  docstring of a file that reads `__doc__`. Deleting those changes the program's output.
- Not code at all: `refactor-audit/`, the generated `plugin/` tree, `docs/user-guide/`, Markdown, config files
  (`.conf`, `.toml`, `.yml`), and strings (a `--` inside an SQL string literal is not a comment).

The source licence needs no headers (AGENTS.md, *Commits*), so there is no licence exemption.

**The one exception: public-API Scaladoc** (AGENTS.md rule 6). A `/** */` block is exempt only when the scanner's
`ApiDoc` finds all three: it sits on a public definition in the main sources of a module of `ApiDoc.PublicApiModules`
(`plugin-api`, `plugin-testkit`); it names no excuse word (`ApiDoc.excuses`); and it does not restate its signature (under
80 % of its content words come from the definition's name, parameters and types, and no `@param`/`@tparam`/`@return`
only repeats a name). The inventory's kind column says why a doc in those modules still counts: `doc not on a public
definition`, `doc excuse (…): file an issue`, `doc restating its signature`.

Passing the scanner is necessary, not sufficient. Review the exempt docs too (`show-kept`, below) and keep only those
that explain what the API does not: units, ranges, threading, error behaviour, lifecycle, who calls it and when. Delete
a doc that only gives an example name or lists fields, a doc left on the wrong declaration (stale), and a doc that
justifies a compatibility shim or shortcut (an excuse: draft the issue). Where a long doc is good but one tag restates a
name, `trim-tags` removes only those tags; a doc with an excuse clause next to real contract text may have the clause cut
by hand (comment text only, no code).

## 2. Look at the comments

```bash
scala-cli run --server=false -q tools/refactor/DeleteComments.scala -- inventory refactor-audit/<lane>/inventory.tsv
scala-cli run --server=false -q tools/refactor/DeleteComments.scala -- show modules/<module>/ > refactor-audit/<lane>/show.txt
scala-cli run --server=false -q tools/refactor/DeleteComments.scala -- show-kept modules/plugin-api/src/main/   # exempt API docs
scala-cli run --server=false -q tools/refactor/DeleteComments.scala -- trim-tags modules/plugin-api/src/main/   # restating tags only
```

`inventory` prints, per language: files, lines, counted blocks and comment lines, the lines deleting all of them would
shed (blank separator lines included) and the exempt counts. It writes one row per block. `show` prints each counted
block of the given path prefixes as `<lines> <text> => <the next line of code>`. Read every block with the code beside
it, and open the file when the next line is not enough. Never classify by keyword alone.

## 3. Classify each block

| Category | What it is | Action | Example |
|---|---|---|---|
| `tautology` | repeats what the names, types or code already say | delete | `/** The SHA-256 of some bytes. */ type Sha256` ; `# <dir>` after `f() {` ; `── section ──` rulers |
| `stale` | refers to code, behaviour or plans that no longer hold; a comment left on the wrong declaration | delete | "The enum replaces the `EdgeKind` string constants." |
| `excuse` | justifies a hack, workaround, shortcut, compatibility shim or TODO ("for now", "temporary", "kept so older … still work", "should be X but") | delete **and** draft an issue | "the default; kept so older command lines still work" |
| `decision` | an important, non-obvious decision: why this design, a constraint, an invariant, an external quirk | move into an ADR, then delete | "a LaunchAgent's PATH has no Homebrew, so the gpg program is recorded" |
| `keep` | must not be deleted (rare: you found an exemption the scanner misses) | leave; fix the scanner instead | — |

When a comment explains *how* code works, the code should say it: that is a tautology once the code is clear, or a
rename/extract in a separate behaviour-preserving change. Removing a comment never changes code in the same step.

## 4. Record the decisions

Write only the blocks that are not tautologies to an overrides file, one row each (tab-separated):

```
path	lines	language	category	reason
scripts/bundle/kit-common.sh	297-299	shell	decision	external quirk: a LaunchAgent's PATH has no Homebrew …: ADR-0003
```

`lines` is the block's range exactly as `show` prints it. Then assemble the decisions for the reviewed prefixes:

```bash
scala-cli run --server=false -q tools/refactor/DeleteComments.scala -- \
  decide notes/comment-audit/overrides/<batch>.tsv notes/comment-audit/decisions.tsv modules/<module>/
```

Every counted block under the prefixes gets a row in `notes/comment-audit/decisions.tsv`: its override, or `tautology`.
An exempt public-API doc gets its override, or `keep`; `apply` removes it only when the override says so.
An override that matches no block is an error (a typo, or the file changed since `show`). Only run `decide` over prefixes
whose every block you read.

## 5. ADRs and issues

**ADRs** live in `docs/adr/` (see its README for the MADR shape: Context, Decision, Consequences, Code). Group related
decisions into one record. Extend an existing record when the decision belongs to it; add the next number otherwise, and
add it to the README's table. Write the decision as the rule the code follows, with the facts the comment held (versions,
dates, measured numbers). Cite the reason column as `ADR-NNNN`.

**Issues** go to `notes/comment-audit/issues.md`: one per underlying shortcut (group duplicates), with a title, the
affected `file:line` list, the original excuse text, and what a proper fix looks like. Never file them on GitHub
yourself (the repository is public; the coordinator scans and files them). No personal data in either.

## 6. Remove and verify

```bash
scala-cli run --server=false -q tools/refactor/DeleteComments.scala -- apply notes/comment-audit/decisions.tsv modules/<module>/
```

The tool removes exactly the listed blocks: a whole-line comment with its line, a trailing one with the spaces before
it, an inline one with the space after it. Where a removed block stood between two blank lines, or between a blank line
and a closing brace, one blank line goes too. It refuses a row whose block no longer exists.

Then:

1. Format: `scala-cli fmt <file>` for each changed Scala file (project-scoped `scalafmtOnly` in sbt does the same). A
   one-member `extension` whose doc comment went collapses onto one line.
2. Check: `bash -n` every changed shell script, `python3 -m py_compile` every changed Python file, and run the scripts'
   tests (`just test-scripts`).
3. Compile and test the touched modules through `notes/forced-run.sh` (the lane rules).
4. Commit one module or package at a time, with the decisions, the ADRs and the issues it produced.
