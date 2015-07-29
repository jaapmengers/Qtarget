package achievements

import akka.actor.{ActorRef, Props}
import akka.persistence.{SnapshotOffer, PersistentActor}
import rx.lang.scala.{Observer, Subject}

object PersonalAchievementActor {
  def props(shooterName: String, achievementCommunicator: ActorRef, overallAchievementActor: ActorRef): Props = Props(new PersonalAchievementActor(shooterName, achievementCommunicator, overallAchievementActor))
}

case class Achievement(shooter: String, achievement: String)
case class ShooterState(hits: List[Hit], misses: List[Miss])

class PersonalAchievementActor(shooterName: String, achievementCommunicator: ActorRef, overallAchievementActor: ActorRef) extends PersistentActor {

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
      case x: Hit =>x
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

  override def receiveRecover: Receive = {
    case res: Result => updateState(res)
  }

  override def receiveCommand: Receive = {
    case res: Result => persist(res){ x =>
      val curTimeOption = if(state.hits.isEmpty) None else Some(state.hits.map(_.time).min)

      (curTimeOption, x) match {
        case (None, h: Hit) => overallAchievementActor ! PersonalRecord(shooterName, h.time)
        case (Some(c: Long), h: Hit) if h.time < c => overallAchievementActor ! PersonalRecord(shooterName, h.time)
        case _ => println(s"Doing nothing $curTimeOption $x")
      }

      updateState(x)
    }
    case r: ForwardedStatsRequest => r.sender ! getStats
  }

  override def persistenceId: String = shooterName
}
