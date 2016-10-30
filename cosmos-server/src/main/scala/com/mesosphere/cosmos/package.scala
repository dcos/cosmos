package com.mesosphere

import com.twitter.util.ScheduledThreadPoolTimer
import com.twitter.util.Timer

package object cosmos {
  implicit val globalTimer: Timer = new ScheduledThreadPoolTimer()
}
