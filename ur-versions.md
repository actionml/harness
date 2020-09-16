
# The Universal Recommender (UR) Versions

The Universal Recommender is included with Harness. It functions as a Harness "Engine".

## UR-1.0.0 (work-in-progress) with harness-1.0.0

This is in the develop branch of Harness and sill be adding the following features

 - dithering, to inject some level of random noise in ranking so recs show more variation on predictable time scales. This makes the UR behave, in some sense, like a giant multi-armed bandit.

## UR-0.9.0 with harness-0.6.0

 - improved requests per second for input and queries
 - Elasticsearch 6.x and 7 support
 - ES 7 has scoring differences and the integration test has not been updated to reflect these so do not trust the test on ES 7
 - Realtime property changes for $set, no need to re-train to have them updated in the model
 - Background index updates for Mongo when changing the TTL in a dataset
 - New statuses for long lived Jobs, these are now persistent over a restart of Harness. Spark Jobs now have a "completed" or "failed" status.
 - Extended JSON for Engine Instance Config files, which pulls values from env.

## UR-0.8.0 with harness-0.4.0

 - feature parity with the PIO version 0.7.3

