

## Conventions

1. When connecting to a location, it should always be expressed as a scala-uri Uri.
  This is because of proxies and things being between systems and blah blah blah
2. All primary APIs should be Futures
  This includes IO, HTTP, Definately on Traits (Business logic layer boundries)
3. __*NEVER*__ have an Await
  Await can be used in a test for its assertion, __*NEVER*__ in non-test code.
4. Avoid trying to handle exceptions from other external services
  For example, if a request to marathon fails with an exception that should propogate all the way up to the the server (in addition to interupting the other futures)
5. Function return types are explicit
