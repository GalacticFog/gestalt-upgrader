package com.galacticfog.gestalt

import akka.actor.Actor

class Executor extends Actor {
  override def receive: Receive = {
    case m => println(m)
  }
}

object Executor {
  final val actorName = "executor"
}
