package achievements

import akka.actor.Props
import akka.persistence.{SnapshotOffer, PersistentActor}

case class PersonalAchievementState(results: List[Result] = Nil){
  def updated(res: Result): PersonalAchievementState = copy(res :: results)
  override def toString = results.reverse.toString
}

object PersonalAchievementActor {
  def props(shooterName: String): Props = Props(new PersonalAchievementActor(shooterName))
}

class PersonalAchievementActor(shooterName: String) extends PersistentActor {

  var state = PersonalAchievementState()

  def updateState(res: Result): Unit = {
    println(s"Updating state with $res")
    state = state.updated(res)
  }

  override def receiveRecover: Receive = {
    case res: Result => {
      println("receiveRecover")
      updateState(res)
    }
    case SnapshotOffer(_, snapshot: PersonalAchievementState) => state = snapshot
  }

  override def receiveCommand: Receive = {
    case res: Result => persist(res)(updateState)
    case "print" => println(state)
    case "snap" => saveSnapshot(state)
    case "boom" => throw new Exception("boom")
  }

  override def persistenceId: String = shooterName
}
