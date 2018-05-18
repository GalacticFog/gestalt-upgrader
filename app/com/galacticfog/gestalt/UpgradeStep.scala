package com.galacticfog.gestalt

import java.util.UUID

import play.api.libs.json.{JsObject, Json}

case class MetaProviderProto(image: String) {
  override def toString: String = image
}
case class MetaProvider(fqon: String, name: String, id: UUID, providerType: UUID, image: Option[String], config: JsObject = Json.obj(), services: Seq[JsObject] = Seq.empty) {
  def getProto = MetaProviderProto(image getOrElse "")
}

sealed trait UpgradeStep {
  def label = this.getClass.getSimpleName
  def warning: Boolean
  def message: String
}

case class BaseServiceProto(image: String) {
  override def toString = image
}

trait BaseService {
  def name: String
  def image: String
  def numInstances: Int
  def getProto: BaseServiceProto = BaseServiceProto(this.image)
}
case object BaseServices {
  val SECURITY = "security"
  val META = "meta"
  val UI = "ui-react"
}

case object BackupDatabase extends UpgradeStep {
  override def warning: Boolean = false
  override def message: String = "Back up database"
}

case class SuspendBaseService(svc: BaseService) extends UpgradeStep {
  override def label = s"SuspendBaseService(${svc.name})"
  override def warning: Boolean = false
  override def message: String  = s"Suspend ${svc.name} to 0 instances"
}

case class ResumeBaseService(svc: BaseService) extends UpgradeStep {
  override def label = s"ResumeBaseService(${svc.name})"
  override def warning: Boolean = false
  override def message: String  = s"Resume ${svc.name} to ${svc.numInstances} instances"
}

case class MetaMigration(version: String) extends UpgradeStep {
  override def label = s"MigrateMeta($version)"
  override def warning: Boolean = false
  override def message: String = s"Perform meta schema migration '$version'"
}

case class UpgradeBaseService(expected: BaseServiceProto, target: BaseServiceProto, actual: BaseService) extends UpgradeStep {
  override def label = s"UpgradeBaseService(${actual.name})"
  override def message: String = {
    val msg = s"Upgrade base service ${actual.name} from ${actual.getProto.image} to ${target.image}"
    if (warning) "WARNING: " + msg + s" (expected image ${expected})" else msg
  }
  override def warning: Boolean = expected != actual.getProto
}

case class UpgradeExecutor(expected: MetaProviderProto, target: MetaProviderProto, actual: MetaProvider) extends UpgradeStep {
  override def label = s"UpgradeExecutor(${actual.name})"
  override def warning: Boolean = expected != actual.getProto
  override def message: String = {
    val msg = s"Upgrade laser executor ${actual.fqon}/${actual.name} (${actual.id}) from ${(actual.config \ "env" \ "public" \ "IMAGE").asOpt[String].getOrElse("none")} to ${target.image}"
    if (warning) s"WARNING: " + msg + s" (expected image ${expected.image})" else msg
  }
}

case class UpgradeProvider(expected: MetaProviderProto, target: MetaProviderProto, actual: MetaProvider) extends UpgradeStep {
  override def label = s"UpgradeProvider(${actual.name})"
  override def warning: Boolean = expected != actual.getProto
  override def message: String = {
    val msg = s"Upgrade meta provider ${actual.fqon}/${actual.name} (${actual.id}) from ${actual.image.getOrElse("none")} to ${target.image}"
    if (warning) s"WARNING: " + msg + s" (expected image ${expected.image})" else msg
  }
}

