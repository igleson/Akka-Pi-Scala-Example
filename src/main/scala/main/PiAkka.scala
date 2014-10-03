package main

import akka.actor.{ActorSystem, ActorRef, Props, Actor}
import akka.routing.RoundRobinRouter

object Global {
  val ITERATIONS = 10000;
}

sealed trait PiMessage

case object Calculate extends PiMessage

case class Work(start: Int, nrOfElements: Int) extends PiMessage

case class Result(value: Double) extends PiMessage

case class PiApproximation(pi: Double, duration: Long)

class PiCalculator extends Actor {

  override def receive = {
    case Work(start, nrOfElements) => sender ! Result(calculatePiFor(start, nrOfElements))
  }

  def calculatePiFor(start: Int, nrOfElements: Int): Double = {
    var acc = 0.0
    for (i ← start until (start + nrOfElements))
      acc += 4.0 * (1 - (i % 2) * 2) / (2 * i + 1)
    acc
  }
}

class Master(nrOfWorkers: Int, nrOfMessages: Int, nrOfElements: Int, listener: ActorRef)
  extends Actor {

  var pi: Double = _
  var nrOfResults: Int = _
  val start: Long = System.currentTimeMillis

  val workerRouter = context.actorOf(
    Props[PiCalculator].withRouter(RoundRobinRouter(nrOfWorkers)), name = "workerRouter")

  override def receive = {
    case Calculate =>
      for (i ← 0 until nrOfMessages) workerRouter ! Work(i * nrOfElements, nrOfElements-1)
    case Result(value) =>
      pi += value
      nrOfResults += 1
      if (nrOfResults == nrOfMessages) {
        // Send the result to the listener
        listener ! PiApproximation(pi, System.currentTimeMillis - start)
        // Stops this actor and all its supervised children
        context.stop(self)
      }
  }
}

class Listener extends Actor {
  def receive = {
    case PiApproximation(pi, duration) ⇒
      println("\n\tPi approximation: \t\t%s\n\tCalculation time: \t%s"
        .format(pi, duration))
      context.system.shutdown()

      var pi2: Double = 0

      val start: Long = System.currentTimeMillis
      for (i ← 0 until Global.ITERATIONS) pi2 += 4.0 * (1 - (i % 2) * 2) / (2 * i + 1)
      println("\n\tPi approximation: \t\t%s\n\tCalculation time: \t%s"
        .format(pi, System.currentTimeMillis - start))
  }
}

object Pi extends App {


  calculate(nrOfWorkers = 8, nrOfElements = Global.ITERATIONS, nrOfMessages = 8)

  def calculate(nrOfWorkers: Int, nrOfElements: Int, nrOfMessages: Int) {
    // Create an Akka system
    val system = ActorSystem("PiSystem")

    // create the result listener, which will print the result and shutdown the system
    val listener = system.actorOf(Props[Listener], name = "listener")

    // create the master
    val master = system.actorOf(Props(new Master(
      nrOfWorkers, nrOfMessages, nrOfElements, listener)),
      name = "master")

    // start the calculation
    master ! Calculate
  }
}