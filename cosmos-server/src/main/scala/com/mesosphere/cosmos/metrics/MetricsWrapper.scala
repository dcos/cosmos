package com.mesosphere.cosmos.metrics

import com.codahale.metrics.MetricRegistry
import com.twitter.finagle.stats.Counter
import com.twitter.finagle.stats.Gauge
import com.twitter.finagle.stats.Stat
import com.twitter.finagle.stats.StatsReceiver
import com.twitter.finagle.stats.Verbosity

class MetricsWrapper extends StatsReceiver {
  import MetricsWrapper._

  final val appender = "."

  override def repr: AnyRef = this

  override def counter(verbosity: Verbosity, name: String*): Counter =
    DCounter(name.mkString(appender))

  override def stat(verbosity: Verbosity, name: String*): Stat =
    DStat(name.mkString(appender))

  override def addGauge(verbosity: Verbosity, name: String*)(f: => Float): Gauge = ???
}

object MetricsWrapper {
  final val metrics: MetricRegistry = new MetricRegistry

  case class DStat(name: String) extends Stat {
    final val histogram = metrics.histogram(name)

    override def add(value: Float): Unit = histogram.update(value.toLong)
  }

  case class DCounter(name: String) extends Counter {
    final val meter = metrics.meter(name.toString)

    override def incr(delta: Long): Unit = meter.mark(delta)
  }

}
