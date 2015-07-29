package achievements

import akka.actor.ActorRef
import akka.persistence.PersistentActor

case class PersonalRecord(shooter: String, time: Long)

class OverallAchievementActor extends PersistentActor {

  var entries = Map.empty[String, PersonalRecord]

  override def receiveRecover: Receive = {
    case pr: PersonalRecord => updateState(pr)
  }

  override def receiveCommand: Receive = {
    case pr: PersonalRecord => persist(pr)(updateState)
    case rq: ForwardedRankingRequest => rq.sender ! getRanking
  }

  def getRanking: List[PersonalRecord] = {
    entries.values.toList.sortBy(_.time)
  }

  def updateState(pr: PersonalRecord) = {

    println(s"Receiving pr: $pr")

    val bestTime = entries.get(pr.shooter) match {
      case Some(x: PersonalRecord) if x.time < pr.time => x
      case _ => pr
    }

    entries = entries + (pr.shooter -> bestTime)
  }

  override def persistenceId: String = "overall-achievement-actor"
}
