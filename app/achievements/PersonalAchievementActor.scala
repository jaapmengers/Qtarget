package achievements

import akka.actor.{ActorRef, Props}
import akka.persistence.{SnapshotOffer, PersistentActor}
import rx.lang.scala.{Observer, Subject}

object PersonalAchievementActor {
  def props(shooterName: String, achievementCommunicator: ActorRef): Props = Props(new PersonalAchievementActor(shooterName, achievementCommunicator))
}

case class Achievement(shooter: String, achievement: String)

class PersonalAchievementActor(shooterName: String, achievementCommunicator: ActorRef) extends PersistentActor {

  val results = Subject[Result]

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
  }

  override def receiveRecover: Receive = {
    case res: Result => {
      updateState(res)
    }
  }

  override def receiveCommand: Receive = {
    case res: Result => persist(res)(updateState)
  }

  override def persistenceId: String = shooterName
}
