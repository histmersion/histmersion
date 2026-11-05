# Unread projection: final design verified

Final acceptance is `reconciliation-2`, over `author-build-1` and the complete
three-preview patch chain. Full production/test compilation passes on 3,158 files
in 28 modules. Twenty projection/controller/type tests pass before and after with
unchanged test bodies; five codec round-trip tests also pass after the shared-type
relocation. No tests skipped. Fresh architecture/model scans match the build hashes.
The gate checks both final String/Pure types and absence of the old library owner
and duplicate personal author-name type.

Exactly seven original confirmed production relationships are repaired. Two old
identities are explicitly replaced (the converter owner and a test import), and two
codec-test references follow the shared type, all without repair credit. The final
review enumerates three accepted contributor identities and 25 test non-violations;
all other identities retain their evidence. Both unreviewed observations that failed
the gate are retained. Final history reports no unreviewed new or reopened finding.

Current bounded isolation treatment is 111 accepted production contributions and
four unresolved registry relationships, with no confirmed outstanding production
finding in this reviewed set. Tests retain 82 adjudicated non-violations and 131
candidates. There are 77 ambiguous owner relationships and 182,326 unclassified
direction-evidence rows; these are not compliant-by-default. Model inventory is
30,393 production member primitive leaves, 62,229 all-scope primitive leaves and
46,915 unresolved leaves. Extracting the row conversion exposes its seven storage
parameters as member signatures; the higher count is retained, not suppressed.

The seven reviewed isolation relationships come from personal's UnreadItem exposing
library AuthorName and Source.Status, and its controller calling the library status
renderer. The final design shares the existing AuthorName in the domain kernel and
gives UnreadItem its own opaque String/Pure Status for the displayed status value.
Library interprets its enum and converts to that display value in SourceReadable;
personal does not import the library enum or duplicate its validation.

The first implementation also introduced UnreadItem.AuthorName. Mechanical checks
passed, but design review rejected this duplicate: it has exactly the same purpose
as the existing AuthorName. That conflicts with the shared-concept requirement.
preview-1, build-1, identity-review-1 and reconciliation-1 are retained as superseded
checkpoints, not final acceptance or automation credit. Author relocation and the
shared-author preview correct that decision through Scalafix. No type is weakened
to a primitive and no second author-name concept is retained intentionally.

Foundation-preview-1 extracts the existing row-construction expression into
SourceReadable.row and adds UnreadProjectionSuite through AstRecipe. The four tests
exercise that production conversion through the personal controller: every status
spelling and case folding, missing/unknown/untrimmed status handling, author order,
duplicates and whitespace, plus identity/title/year/period rendering. Foundation
compilation and twenty tests pass before type changes. The first type implementation
also passes full production/test compilation and the same twenty tests without skips.
SQL queries, transaction boundaries and table access are unchanged; these tests do
not establish live database execution.

The new contributor and test references are not repairs. The first raw isolation
observation failed on 29 new IDs. Review enumerated four identities in the existing
accepted SourceReadable contribution and 25 test non-violations. One old contributor
identity moved to the extracted method; one test import changed from class to companion
symbol without changing the test file. Both retain history with no repair credit.
The original baseline and failed observation remain. The corrected final source
requires fresh review fingerprints; the first review cannot silently authorize it.

IsolationHistory now supports exact reviewed contribution identities only when they
name an original accepted parent, the same source file and receiver package, exact
owner/target symbols and unchanged reviewed source hashes. These are explicit review
events, not package allowlists. Its self-tests retain equal-count/reappearance/scope
checks and reject unaccepted parents, changed files and changed receiver packages.
The first attempted self-test command used an ineffective CLI setting and therefore
correctly refused the still-running architecture scan; the explicit self-test config
then passed. No observation was accepted by that failed command.

This is manual/design work applied through reusable AST and semantic rules. No bulk
automation percentage or independent detector-quality acceptance is claimed. Live
database execution and native-image behavior remain outside this batch's evidence.

Next are the four registry relationships: ImportOptions and BundleService currently
transport SourceReferrers, but only the library's source importer consumes it. Review
injecting that registry into the library-owned carried-kind contribution instead of
carrying it through generic import options, preserving rename ordering and transactions.
