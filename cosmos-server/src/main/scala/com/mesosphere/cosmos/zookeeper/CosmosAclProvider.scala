package com.mesosphere.cosmos.zookeeper

import java.util.Arrays
import java.util.{List => JList}

import org.apache.curator.framework.api.ACLProvider
import org.apache.zookeeper.ZooDefs
import org.apache.zookeeper.data.ACL
import org.apache.zookeeper.data.Id


final class CosmosAclProvider private (acls: JList[ACL]) extends ACLProvider {
  def getAclForPath(path: String): JList[ACL] = acls

  def getDefaultAcl(): JList[ACL] = acls
}

object CosmosAclProvider {
  def apply(user: String, secret: String): CosmosAclProvider = {
    val userAcl = new ACL(ZooDefs.Perms.ALL, new Id("auth", s"$user:$secret"))
    val worldAcl = new ACL(ZooDefs.Perms.READ, new Id("world", "anyone"))

    new CosmosAclProvider(Arrays.asList(userAcl, worldAcl))
  }
}
