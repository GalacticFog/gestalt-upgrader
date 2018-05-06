package com.galacticfog.gestalt

import javax.inject.Inject

trait Planner {
  def computePlan(): Seq[UpgradeStep]
}

object Planner {
  val actorName = "planner"
}

class Planner16 @Inject() () extends Planner {
  override def computePlan(): Seq[UpgradeStep] = ???
}
