package com.galacticfog.gestalt

sealed trait UpgradeStep {
  def warning: Boolean
}
