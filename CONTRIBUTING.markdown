# Guide for contributors

This project follows a standard [fork and pull][fork-and-pull] model for accepting code contributions.

## Design Conventions

* When connecting to a location, it should always be expressed as a scala-uri Uri.
  * Having a full Uri allows us to not have to worry about the large complexities that can arise when connecting to
    services. This buffers the code and implementation from things like reverse-proxies, http vs https,
    username:password
* Prefer connecting to host names instead of IPs
  * Host names can easily move atop ip addresses without application reconfiguration, name resolution can be run again
* All primary APIs should be Futures
  * This includes IO, HTTP, definitely on Traits (Business logic layer boundaries)
* __*NEVER*__ use Await
  * Await can only be used in a test for its assertion, __*NEVER*__ in non-test code.
* Avoid trying to handle exceptions from other external services
  * For example, if a request to Marathon fails with an exception that should propagate all the way up to the the
    server (in addition to interrupting any other futures that might be running for the request)
* `CosmosError` is our base error type which extends RuntimeException and comes with a defined encoder

## Write Code

#### Coding Conventions

* Code and comments should be formatted to a width no greater than 120 columns.
* Avoid using `lazy val` wherever possible.
* Avoid creating mixins
* Model should all have a corresponding vendor MediaType
* Model objects (case classes) should generally avoid having logic in the class or a companion object, instead create
  an `Ops` class and a potential `syntax` conversion.
* Value classes should be used when there is any possibility of ambiguity surrounding the name of the class ("version"
  is a good example in the current code)
* Function return types are explicit


## Write Tests

Cosmos uses the [ScalaTest][scalatest] library for writing unit and integration tests.

There are two test scopes in the project. First, `test` which represents unit tests -- that is, tests with no
dependencies outside the codebase. Second, `it` which represents integration tests -- that is, tests which interact
with an instance of Cosmos connected to an actual DCOS Cluster.

It should be easy to figure out what a test is actually verifying.

## Submit a pull request

* In general we discourage force pushing to an active pull-request branch that other people are
  commenting on or contributing to, and suggest using `git merge master` during development. Once
  development is complete, use `git rebase master` and force push to [clean up the history][squash].
* Commit messages should generally use the present tense, normal sentence capitalization, and no final
  punctuation.

[fork-and-pull]: https://help.github.com/articles/using-pull-requests/
[scalatest]: http://www.scalatest.org/
[squash]: http://gitready.com/advanced/2009/02/10/squashing-commits-with-rebase.html
