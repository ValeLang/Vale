compiler-decided factors:

\- whether certain fields in structs and functions should be inlined

\- whether we use RC for all, some, or no immutables

\- whether we parallelize certain concurrent operations

\- how many pages per signal in the lightning thread\'s scheme

we can run with a ton of different combinations of these things, and
then plug the resulting runtimes into machine learning, i think.
