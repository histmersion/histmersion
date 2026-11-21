# Issues drafted by the comment audit

One issue per underlying shortcut that an "excuse" comment justified. The comment is deleted from the code; the issue
keeps what it said and what a proper fix looks like. The coordinator files them (the repository is public).

## 1. Legacy installer flags kept for old command lines

- **Where:** `scripts/bundle/install-linux.sh:39`, `scripts/bundle/install-macos.sh:50` (`--local-build`);
  `scripts/bundle/codesign-identity-macos.sh:20-25` and `scripts/bundle/signing.sh:126` (`--no-trust`).
- **Excuse text:** "the default; kept so older command lines still work"; "--no-trust, the default, is still accepted".
- **Proper fix:** nothing is published, so there are no old command lines to keep working (no compatibility
  engineering). Drop `--local-build` from both installers and `--no-trust` from `codesign-identity-macos.sh` and
  `histmersion-signing setup-macos` (an unknown flag is then a usage error), update `docs/user-guide` where they appear,
  and extend `scripts/bundle/tests/kit-scripts-test.sh` to assert the refusal.

## 2. Legacy `oa-index` plugin switch still maps onto its successors

- **Where:** `scripts/bundle/smoke.sh:114-115` (the check), the mapping in `util/plugins/LegacyPlugins` (server).
- **Excuse text:** "one plugin per open-access index (D4): the former oa-index's switch still turns all four off — a
  successor's own setting wins — with a deprecation warning".
- **Proper fix:** remove the legacy `oa-index` mapping and its deprecation warning; a config that still names
  `oa-index` is reported as an unknown plugin like any other. Remove the smoke check and add a config test for the
  unknown-plugin report. Only the user's own live config needs a one-off edit, which the release notes can state.

## 3. Runless manager items counted under the review pipeline by habit

- **Where:** `modules/values/src/main/scala/com/histmersion/domain/automation/Pipeline.scala:113-115`
  (`Pipeline.Fallback`) and its callers in the managers' counting.
- **Excuse text:** "The pipeline a manager's item that starts no agent run of its own is counted under (the review's,
  as the managers have always counted them)."
- **Proper fix:** an item that starts no agent run has no pipeline; model that (an `Option[Pipeline]`, or a manager
  item kind of its own) and count such items under their manager rather than under `ReviewArticle`. The managers' pages
  and their tests then show them separately instead of inflating the review's numbers.

Issues 1-3 were filed as #9, #10 and #11.

## 4. plugin-api binary-compatibility shims

- **Where:** `modules/plugin-api/src/main/scala/com/histmersion/plugin/AiCapabilities.scala:23,32`,
  `AiTurn.scala:63,87`, `AiUsage.scala:16,26` (secondary constructors and `Apply…` aliases), and the defaults of
  `Plugin.Context`'s later registries (`sourceFetchers`, `quota`, `licences`, `gazetteerSources`, `sourceCollections`)
  in `Plugin.scala`.
- **Excuse text:** "The constructor of a provider compiled before the loop's notes and verbatim failures existed";
  "The request as a host or a test compiled before the rendered schema and its description were passed wrote it";
  "The constructor of a provider compiled before reasoning tokens were counted"; "a default that refuses every
  addition, so a context written for an earlier extension point — a test's — still compiles".
- **Proper fix:** nothing is published and Histmersion is the only plugin provider (G55), so there are no old plugin
  builds to keep linking. Remove the secondary constructors and aliases; make the later registries and members abstract
  (or give them defaults only where "the host lends none" is a real host configuration, said in the contract), and
  update the first-party plugins, plugin-host, the testkit, the fixtures and the testkit API snapshot in the same change.
