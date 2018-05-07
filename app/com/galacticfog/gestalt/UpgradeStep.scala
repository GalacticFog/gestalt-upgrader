package com.galacticfog.gestalt

import java.util.UUID

sealed trait UpgradeStep {
  def warning: Boolean
  def message: String
}

case class UpgradeBaseService(name: String, expected: String, target: String, actual: String) extends UpgradeStep {
  override def message: String = {
    val msg = s"Upgrade base service ${name} from ${actual} to ${target}"
    if (warning) "WARNING: " + msg + s" (expected image ${expected})" else msg
  }
  override def warning: Boolean = expected != actual
}

case class MetaProviderProto(image: String)
case class MetaProvider(fqon: String, name: String, id: UUID, providerType: UUID, image: String, hasContainers: Boolean) {
  def getProto = MetaProviderProto(image)
}

case class UpgradeProvider(expected: MetaProviderProto, target: MetaProviderProto, actual: MetaProvider, reprovision: Boolean) extends UpgradeStep {
  override def warning: Boolean = expected != actual.getProto
  override def message: String = {
    val msg = s"Upgrade meta provider ${actual.fqon}/${actual.name} (${actual.id}) from ${actual.image} to ${target.image}"
    if (warning) s"WARNING: " + msg + s" (expected image ${expected.image})" else msg
  }
}
