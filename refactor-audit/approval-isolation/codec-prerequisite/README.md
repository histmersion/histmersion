# Status codec prerequisite

The untouched engine baseline failed all 30 tests while reading the stored
`unassessed` status. SourceRepo imports the API Source model; the existing general
database status codec is for the distinct domain Source.Status. Magnum therefore
used enum-name conversion for the API type rather than the established lowercase
vocabulary conversion.

SourceStatusColumn is a semantic Scalafix rule anchored to the resolved SourceRepo
object and its API Source import. It adds an owner-local API status codec with the
same case-insensitive wire parsing, write spelling and Unassessed fallback as the
domain codec. This is a separate prerequisite bug fix, not an isolation repair count.

The original 30-failure baseline remains under ../engine-tests. The corrected
30-test baseline and its after-ownership comparison remain under
../engine-tests-after-codec; both passed with no skips. The eight database/in-memory
approval contracts also passed unchanged. A coordinate-only source assertion
correction records putIn's three-line movement, retaining its expected zero finding.
