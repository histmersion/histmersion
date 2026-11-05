# Runtime checkpoint after build-v7

Full production and test compilation: PASS, 3,143 source files, hashes stable.
No database process was started and no DB-backed or HTTP suite was executed here.

The original six-suite API regression ledger retains all 115 identities. Its 113
passing tests remain passing; the same two ArticlesSuite failures retain their full,
exact failure messages. No skip or newly failing identity is accepted. See
tests/baseline.tsv and tests/after.tsv. BatchTestVerification now permits explicitly
named baseline failures only with exact evidence equality and refuses to overwrite
any existing observation. These failures remain outstanding, not waived as compliant.

The domain-tests-v2 run passes 31 tests, no failures or skips:

| Suite | Passed |
|---|---:|
| CompileArticleSuite | 12 |
| EmptyAnswerFieldsSuite | 15 |
| VocabularyWireSuite | 3 |
| LeadServiceInMemorySuite | 1 |

Additional service/controller run passes 28 tests, no failures or skips:

| Suite | Passed |
|---|---:|
| GoalServiceInMemorySuite | 9 |
| EventServiceInMemorySuite | 6 |
| AttestationServiceInMemorySuite | 1 |
| AssetServiceInMemorySuite | 5 |
| AssetLinkServiceInMemorySuite | 2 |
| EventsControllersSuite | 4 |
| EventLinksControllersSuite | 1 |

The latter run was the following single sbt invocation, exit 0 (the test reports
are in target/out/jvm/scala-3.10.0/histmersion-server/test-reports):

```
sbt --server -batch 'set every scalafmtOnCompile := false' 'set ThisBuild / scalacOptions += "-Xsemanticdb"' 'server/testOnly *GoalServiceInMemorySuite *EventServiceInMemorySuite *AttestationServiceInMemorySuite *AssetServiceInMemorySuite *AssetLinkServiceInMemorySuite *EventsControllersSuite *EventLinksControllersSuite'
```

The preceding web checkpoint passed 11 tests separately. These runtime checks do
not establish detector precision/recall, the migration's 90% target, or compliance of
the primitive factory parameters and remaining primitive consumers.
