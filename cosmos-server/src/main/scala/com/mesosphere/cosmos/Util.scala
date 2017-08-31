package com.mesosphere.cosmos

object Util {
  def rewriteWithProxyURL(url : String) : String = {
    s"${adminRouterUri()}package/resource?url=$url"
  }
}
