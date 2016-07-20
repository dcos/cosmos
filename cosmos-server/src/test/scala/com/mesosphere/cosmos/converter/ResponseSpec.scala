package com.mesosphere.cosmos.converter

import com.mesosphere.cosmos.converter.Universe._
import com.mesosphere.cosmos.converter.Common._
import com.mesosphere.cosmos.thirdparty.marathon.model.AppId
import com.mesosphere.cosmos.{label,ServiceMarathonTemplateNotFound, internal, rpc}
import com.mesosphere.cosmos.converter.Response._
import com.mesosphere.cosmos.test.TestUtil.{MinimalPackageDefinition,MaximalPackageDefinition}

import java.nio.charset.StandardCharsets

import com.mesosphere.universe
import com.mesosphere.universe.v3.model.Cli
import com.mesosphere.universe.common.ByteBuffers

import com.twitter.bijection.Conversion
import com.twitter.bijection.Conversion.asMethod
import com.twitter.util.{Try,Return,Throw}

import org.scalatest.FreeSpec
import org.scalatest.prop.GeneratorDrivenPropertyChecks.forAll
import org.scalacheck.Gen

import cats.data.Xor
import scala.io.Source

final class ResponseSpec extends FreeSpec {
  "Conversion[rpc.v2.model.InstallResponse,Try[rpc.v1.model.InstallResponse]]" - {
    val vstring = "9.87.654.3210"
    val ver = universe.v3.model.PackageDefinition.Version(vstring)
    val name = "ResponseSpec"
    val appid = AppId("foobar")
    val clis = List(None, Some("post install notes"))
    val notes = List(None, Some(Cli(None)))
    val validV2s = for {
      n <- Gen.oneOf(clis)
      c <- Gen.oneOf(notes)
    } yield (rpc.v2.model.InstallResponse(name, ver, Some(appid), n, c))
    val invalidV2s = for {
      n <- Gen.oneOf(clis)
      c <- Gen.oneOf(notes)
    } yield (rpc.v2.model.InstallResponse(name, ver, None, n, c))


    "success" in {
      val v1 = rpc.v1.model.InstallResponse(name, ver.as[universe.v2.model.PackageDetailsVersion], appid)

      forAll(validV2s) { x => assertResult(Return(v1))(x.as[Try[rpc.v1.model.InstallResponse]]) }
    }
    "failure" in {
      //expecting failure due to missing marathon mustache
      forAll(invalidV2s) { x => assertResult(Throw(ServiceMarathonTemplateNotFound(name, ver)))(x.as[Try[rpc.v1.model.InstallResponse]]) }
    }
  }
  "Conversion[internal.model.PackageDefinition,Try[rpc.v1.model.DescribeResponse]]" - {
    "success" in {
      val m = MaximalPackageDefinition.marathon.get.v2AppMustacheTemplate
      val s = new String(ByteBuffers.getBytes(m), StandardCharsets.UTF_8)
      val conv = MaximalPackageDefinition.as[Try[rpc.v1.model.DescribeResponse]]
      assert(conv.isReturn)
      assertResult(s)                                                                                (conv.get.marathonMustache)
      assertResult(MaximalPackageDefinition.command.as[Option[universe.v2.model.Command]])           (conv.get.command)                              
      assertResult(MaximalPackageDefinition.config)                                                  (conv.get.config)                               
      assertResult(MaximalPackageDefinition.resource.map(_.as[universe.v2.model.Resource]))          (conv.get.resource)                             
      assertResult(MaximalPackageDefinition.packagingVersion.as[universe.v2.model.PackagingVersion]) (conv.get.`package`.packagingVersion)           
      assertResult(MaximalPackageDefinition.name)                                                    (conv.get.`package`.name)                       
      assertResult(MaximalPackageDefinition.version.as[universe.v2.model.PackageDetailsVersion])     (conv.get.`package`.version)                    
      assertResult(MaximalPackageDefinition.maintainer)                                              (conv.get.`package`.maintainer)                 
      assertResult(MaximalPackageDefinition.description)                                             (conv.get.`package`.description)                
      assertResult(MaximalPackageDefinition.tags.as[List[String]])                                   (conv.get.`package`.tags)                       
      assertResult(Some(MaximalPackageDefinition.selected))                                          (conv.get.`package`.selected)                   
      assertResult(MaximalPackageDefinition.scm)                                                     (conv.get.`package`.scm)                        
      assertResult(MaximalPackageDefinition.website)                                                 (conv.get.`package`.website)                    
      assertResult(Some(MaximalPackageDefinition.framework))                                         (conv.get.`package`.framework)                  
      assertResult(MaximalPackageDefinition.preInstallNotes)                                         (conv.get.`package`.preInstallNotes)            
      assertResult(MaximalPackageDefinition.postInstallNotes)                                        (conv.get.`package`.postInstallNotes)           
      assertResult(MaximalPackageDefinition.postUninstallNotes)                                      (conv.get.`package`.postUninstallNotes)         
      assertResult(MaximalPackageDefinition.licenses.as[Option[List[universe.v2.model.License]]])    (conv.get.`package`.licenses)                   
    }
    "failure" in {
      //expecting failure due to missing marathon mustache
      val e = ServiceMarathonTemplateNotFound(MinimalPackageDefinition.name, MinimalPackageDefinition.version)
      assertResult(Throw(e))(MinimalPackageDefinition.as[Try[rpc.v1.model.DescribeResponse]])
    }
  }

}
