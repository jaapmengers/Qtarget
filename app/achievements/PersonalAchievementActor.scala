package achievements

import akka.actor.{Actor, ActorRef, Props}
import rx.lang.scala.{Observer, Subject}

object PersonalAchievementActor {
  def props(shooterName: String, achievementCommunicator: ActorRef): Props = Props(new PersonalAchievementActor(shooterName, achievementCommunicator))
}

case class Achievement(shooter: String, achievement: String)
case class ShooterState(hits: List[Hit], misses: List[Miss])
case class ResultsSnapshot(results: List[Result])

class PersonalAchievementActor(shooterName: String, achievementCommunicator: ActorRef) extends Actor {

  val results = Subject[Result]
  var state = ShooterState(Nil, Nil)

  results.filter {
    case x: Hit => true
    case _ => false
  }.tumbling(5).subscribe { x =>
    x.length.subscribe { y =>
      if(y == 5)
        achievementCommunicator ! Achievement(shooterName, "5 hits!")
    }
  }

  results.tumbling(5).subscribe { x =>
    x.collect {
      case x: Hit => x
    }.length.subscribe { x =>
      if(x == 5)
        achievementCommunicator ! Achievement(shooterName, "5 consecutive hits!")
    }
  }

  results.filter {
    case x: Hit if math.round(x.time / 100.0) == 42 => true
    case _ => false
  }.subscribe { _ =>
    achievementCommunicator ! Achievement(shooterName, "Target hit in 4.2 seconds!")
  }

  def updateState(res: Result): Unit = {
    results.onNext(res)
    res match {
      case h: Hit => state = state.copy(hits = h :: state.hits)
      case m: Miss => state = state.copy(misses = m :: state.misses)
    }
  }

  def getStats: StatsResponse = {
    val bestTime = if(state.hits.isEmpty) 0 else state.hits.map(_.time).min
    StatsResponse(bestTime, state.hits.length, state.misses.length)
  }

  override def receive: Receive = {
    case snapShot: ResultsSnapshot => snapShot.results.foreach(updateState)
    case res: Result => updateState(res)
    case r: ForwardedStatsRequest => r.sender ! getStats
  }
}
