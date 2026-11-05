# Approval ownership repair

The 14-file Scalafix batch moves source-proposal recording, duplicate detection and
their repository operations into library.workflows. SourceApprovalService supplies
database and in-memory implementations. The engine retains approval lifecycle reads,
decisions and run/subject approvals. SourceProposals and CurrencyCheckPipeline now
reach the library-owned service, and composition supplies the existing transactor,
engine approval reader and clock. Source.Status and Edge.Kind remain typed.

The database implementation records and reads the resulting approval on the same
connection in its transaction. SourceApprovalRepo borrows DbCon and remains private
to workflows. The in-memory implementation shares the original atomic insertion
operation through AutomationState. The existing schema and uniqueness indexes did
not change. Unrelated AgentRepo.record overloads remain intact.

Full production and test compilation passed. The eight database/in-memory contract
tests retain exactly the same passing identities and outcomes, with no skips. The
30-test engine suite initially exposed a pre-existing API status codec mismatch;
a separate semantic Scalafix patch corrected its owner-local database codec. The
original 30 failures remain preserved. All 30 tests passed before and after applying
the ownership batch, with unchanged identities. The local PostgreSQL cluster is stopped.

The first post-repair measurement verified removal of all 54 reviewed production
relationships and retention of every other production identity. The gate rejected
ten new test relationships, which were then individually reviewed as legitimate
fixture/contract calls into the library service. An additive event retains their
exact identities, source hashes and rationale; the original baseline is unchanged.
Two of these use local compiler owners and remain explicitly identity-ambiguous.

The standalone identity gate passes with that event. CLI mutation checks verify that
equal-count substitutions and reappearance still fail; adoption checks additionally
reject changed source evidence, a changed classification and the wrong baseline.
The fresh integrated assessment passed across 3,140 source/test files with stable
source and policy fingerprints and both regression gates passing. The preceding
rejected run remains preserved; aggregate historical baselines remain incomparable.

The reviewed production subset is repaired, not the entire module-isolation family.
It leaves 170 production candidates and 21 explicit contribution-interface exceptions;
tests retain 138 candidates and 61 adjudicated non-violations. Unclassified-direction
evidence remains unresolved. Capability findings stay at 147 with no identity changes.
The model inventory retains its increase to 30,437 production member primitive leaves
and 46,873 unresolved leaves across all scopes. No newtype fixes or independent 95%
quality result are claimed. Copy automation remains paused and the overall goal open.

Evidence: wiring/final-plan-v2.tsv and final-verification.txt; final-source-plan.tsv
also includes the codec prerequisite; verified-findings.tsv records the 54 repaired
identities. All preview failures, coordinate corrections, original test baselines,
raw observations and the rejected isolation observation remain available.
