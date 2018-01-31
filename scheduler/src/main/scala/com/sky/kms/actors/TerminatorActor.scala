package com.sky.kms.actors

import akka.Done
import akka.actor.{Actor, ActorRef, ActorSystem, CoordinatedShutdown, Props, Terminated}
import cats.Eval

import scala.concurrent.Future

class TerminatorActor(terminate: Eval[Future[Done]], actorsToWatch: ActorRef*) extends Actor {

  override def preStart(): Unit =
    actorsToWatch foreach (context watch)

  override def receive: Receive = {
    case Terminated(_) =>
      terminate.value
      context stop self
  }
}

object TerminatorActor {
  def create(actors: ActorRef*)(implicit system: ActorSystem): ActorRef =
    system.actorOf(Props(new TerminatorActor(Eval.later(CoordinatedShutdown(system).run), actors: _*)))
}
