package com.mesosphere.cosmos

import java.time
import java.time.Instant

import akka.actor.ActorSystem
import akka.pattern.after
import java.util.concurrent.{TimeoutException => JavaTimeoutException}

import scala.concurrent.duration.{Duration, FiniteDuration}
import scala.concurrent.{ExecutionContext, Future, Promise, blocking => blockingCall}
import scala.util.{Failure, Random, Success}
import scala.util.control.NonFatal
import scala.concurrent.duration._

// scalastyle:off

/**
 * Extension of a TimeoutException that allows a cause
 */
case class TimeoutException(reason: String, cause: Throwable) extends JavaTimeoutException(reason) {
  def this(reason: String) = this(reason, null)
  override def getCause: Throwable = cause
}

object TimeoutException {
  def apply(reason: String): TimeoutException = new TimeoutException(reason)
}


/**
 * Functional transforms to retry methods using a form of Exponential Backoff with decorrelated jitter.
 *
 * See also: https://www.awsarchitectureblog.com/2015/03/backoff.html
 */
object Retry {
  val DefaultMaxAttempts: Int = 5
  val DefaultMinDelay: FiniteDuration = 10.millis
  val DefaultMaxDelay: FiniteDuration = 1.second
  val DefaultMaxDuration: FiniteDuration = 24.hours

  type RetryOnFn = Throwable => Boolean
  val defaultRetry: RetryOnFn = NonFatal(_)

  private[util] def randomBetween(min: Long, max: Long): Long = {
    require(min <= max)
    math.min(math.abs(Random.nextLong() % (max - min + 1)) + min, max)
  }

  /**
   * Retry a non-blocking call
   *
   * @param name Identifier for logging
   * @param maxAttempts The maximum number of attempts before failing
   * @param minDelay The minimum delay between invocations
   * @param maxDelay The maximum delay between invocations
   * @param maxDuration The maximum amount of time to allow the operation to complete
   * @param retryOn A method that returns true for Throwables which should be retried
   * @param f The method to transform
   * @param system The Akka actor system to execute on
   * @param ctx The execution context to run the method on
   * @tparam T The result type of 'f'
   * @return The result of 'f', TimeoutException if 'f' failed 'maxAttempts' with retry-able exceptions
   *         and the last exception that was thrown, or the last exception thrown if 'f' failed with a
   *         non-retry-able exception.
   */
  def apply[T](
    name: String,
    maxAttempts: Int = DefaultMaxAttempts,
    minDelay: FiniteDuration = DefaultMinDelay,
    maxDelay: FiniteDuration = DefaultMaxDelay,
    maxDuration: Duration = DefaultMaxDuration,
    retryOn: RetryOnFn = defaultRetry)(f: => Future[T])(
    implicit system: ActorSystem,
    ctx: ExecutionContext): Future[T] = {
    val promise = Promise[T]()

    require(
      maxDelay < maxDuration,
      s"maxDelay of ${maxDelay.toSeconds} seconds is larger than the maximum allowed duration: $maxDuration")

    def retry(attempt: Int, lastDelay: FiniteDuration): Unit = {
      val startedAt = Instant.now()
      f.onComplete {
        case Success(result) =>
          promise.success(result)
        case Failure(e) if retryOn(e) =>
          val expired = time.Duration.between(startedAt, Instant.now()).toMillis >= maxDuration.toMillis
          if (attempt + 1 < maxAttempts && !expired) {
            val jitteredLastDelay = lastDelay.toNanos * 3
            val nextDelay = randomBetween(
              lastDelay.toNanos, if (jitteredLastDelay < 0 || jitteredLastDelay > maxDelay.toNanos) maxDelay.toNanos else jitteredLastDelay).nano

            require(
              nextDelay <= maxDelay,
              s"nextDelay of ${nextDelay.toNanos}ns is too big, may not exceed ${maxDelay.toNanos}ns")

            system.scheduler.scheduleOnce(nextDelay)(retry(attempt + 1, nextDelay))
          } else {
            if (expired) {
              promise.failure(TimeoutException(s"$name failed to complete in under $maxDuration. Last error: ${e.getMessage}", e))
            } else {
              promise.failure(TimeoutException(s"$name failed after $maxAttempts attempt(s). Last error: ${e.getMessage}", e))
            }
          }
        case Failure(e) =>
          promise.failure(e)
      }

    }
    retry(0, minDelay)
    Timeout(maxDuration, name = Some(name))(promise.future)
  }

  /**
   * Retry a blocking call
   *
   * @param name Identifier for logging
   * @param maxAttempts The maximum number of attempts before failing
   * @param minDelay The minimum delay between invocations
   * @param maxDelay The maximum delay between invocations
   * @param maxDuration The maximum amount of time to allow the operation to complete
   * @param retryOn A method that returns true for Throwables which should be retried
   * @param f The method to transform
   * @param system The Akka system to execute on
   * @param ctx The execution context to run the method on
   * @tparam T The result type of 'f'
   * @return The result of 'f', TimeoutException if 'f' failed 'maxAttempts' with retry-able exceptions
   *         and the last exception that was thrown, or the last exception thrown if 'f' failed with a
   *         non-retry-able exception.
   */
  def blocking[T](
    name: String,
    maxAttempts: Int = 5, // scalastyle:off magic.number
    minDelay: FiniteDuration = DefaultMinDelay,
    maxDelay: FiniteDuration = DefaultMaxDelay,
    maxDuration: Duration = DefaultMaxDuration,
    retryOn: RetryOnFn = defaultRetry)(f: => T)(
    implicit system: ActorSystem,
    ctx: ExecutionContext): Future[T] = {
    apply(name, maxAttempts, minDelay, maxDelay, maxDuration, retryOn)(Future(blockingCall(f)))
  }
}

/**
 * Function transformations to make a method timeout after a given duration.
 */
object Timeout {
  /**
   * Timeout a blocking call
   * @param timeout The maximum duration the method may execute in
   * @param name Name of the operation
   * @param f The blocking call
   * @param system The Akka actor system
   * @param ctx The execution context to execute 'f' in
   * @tparam T The result type of 'f'
   * @return The eventual result of calling 'f' or TimeoutException if it didn't complete in time.
   */
  def blocking[T](timeout: FiniteDuration, name: Option[String] = None)(f: => T)(
    implicit
    system: ActorSystem,
    ctx: ExecutionContext): Future[T] =
    apply(timeout, name)(Future(blockingCall(f))(ctx))(system, ctx)

  /**
   * Timeout a non-blocking call.
   * @param timeout The maximum duration the method may execute in
   * @param name Name of the operation
   * @param f The blocking call
   * @param system The Akka actor system
   * @param ctx The execution context to execute 'f' in
   * @tparam T The result type of 'f'
   * @return The eventual result of calling 'f' or TimeoutException if it didn't complete
   */
  def apply[T](timeout: Duration, name: Option[String] = None)(f: => Future[T])(
    implicit
    system: ActorSystem,
    ctx: ExecutionContext): Future[T] = {
    require(timeout != Duration.Zero)

    timeout match {
      case duration: FiniteDuration =>
        def t: Future[T] = after(duration, system.scheduler)(
          Future.failed(new TimeoutException(s"${name.getOrElse("None")} timed out after $timeout")))
        Future.firstCompletedOf(Seq(f, t))
      case _ => f
    }
  }
}

// scalastyle:on
