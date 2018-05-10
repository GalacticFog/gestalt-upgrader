package com.galacticfog.gestalt

import java.util.UUID

import play.api.libs.json.{JsObject, Json}

case class MetaProviderProto(image: String)
case class MetaProvider(fqon: String, name: String, id: UUID, providerType: UUID, image: Option[String], config: JsObject = Json.obj()) {
  def getProto = MetaProviderProto(image getOrElse "")
}

sealed trait UpgradeStep {
  def warning: Boolean
  def message: String
}

case object BackupDatabase extends UpgradeStep {
  override def warning: Boolean = false
  override def message: String = "Back up database"
}

case class UpgradeBaseService(name: String, expected: String, target: String, actual: String) extends UpgradeStep {
  override def message: String = {
    val msg = s"Upgrade base service ${name} from ${actual} to ${target}"
    if (warning) "WARNING: " + msg + s" (expected image ${expected})" else msg
  }
  override def warning: Boolean = expected != actual
}

case class UpgradeExecutor(expected: MetaProviderProto, target: MetaProviderProto, actual: MetaProvider) extends UpgradeStep {
  override def warning: Boolean = expected != actual.getProto
  override def message: String = {
    val msg = s"Upgrade laser executor ${actual.fqon}/${actual.name} (${actual.id}) from ${(actual.config \ "env" \ "public" \ "IMAGE").asOpt[String].getOrElse("none")} to ${target.image}"
    if (warning) s"WARNING: " + msg + s" (expected image ${expected.image})" else msg
  }
}

case class UpgradeProvider(expected: MetaProviderProto, target: MetaProviderProto, actual: MetaProvider) extends UpgradeStep {
  override def warning: Boolean = expected != actual.getProto
  override def message: String = {
    val msg = s"Upgrade meta provider ${actual.fqon}/${actual.name} (${actual.id}) from ${actual.image.getOrElse("none")} to ${target.image}"
    if (warning) s"WARNING: " + msg + s" (expected image ${expected.image})" else msg
  }
}
