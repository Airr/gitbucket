package gitbucket.core.model

trait CommitStatusComponent extends TemplateComponent { self: Profile =>
  import profile.api._
  import self._

  implicit val commitStateColumnType = MappedColumnType.base[CommitState, String](b => b.name , i => CommitState(i))

  lazy val CommitStatuses = TableQuery[CommitStatuses]

  class CommitStatuses(tag: Tag) extends Table[CommitStatus](tag, "COMMIT_STATUS") with CommitTemplate {
    val commitStatusId = column[Int]("COMMIT_STATUS_ID", O AutoInc)
    val context = column[String]("CONTEXT")
    val state = column[CommitState]("STATE")
    val targetUrl = column[Option[String]]("TARGET_URL")
    val description = column[Option[String]]("DESCRIPTION")
    val creator = column[String]("CREATOR")
    val registeredDate = column[java.util.Date]("REGISTERED_DATE")
    val updatedDate = column[java.util.Date]("UPDATED_DATE")
    def * = (commitStatusId, userName, repositoryName, commitId, context, state, targetUrl, description, creator, registeredDate, updatedDate) <> (CommitStatus.tupled, CommitStatus.unapply)
    def byPrimaryKey(id: Int) = commitStatusId === id.bind
  }
}


case class CommitStatus(
  commitStatusId: Int = 0,
  userName: String,
  repositoryName: String,
  commitId: String,
  context: String,
  state: CommitState,
  targetUrl: Option[String],
  description: Option[String],
  creator: String,
  registeredDate: java.util.Date,
  updatedDate: java.util.Date
)


sealed abstract class CommitState(val name: String)


object CommitState {
  case object ERROR extends CommitState("error")
  case object FAILURE extends CommitState("failure")
  case object PENDING extends CommitState("pending")
  case object SUCCESS extends CommitState("success")

  val values: Seq[CommitState] = Seq(PENDING, SUCCESS, ERROR, FAILURE)

  private val map: Map[String, CommitState] = values.map(enum => enum.name -> enum).toMap

  def apply(name: String): CommitState = map(name)

  def valueOf(name: String): Option[CommitState] = map.get(name)

  /**
   * failure if any of the contexts report as error or failure
   * pending if there are no statuses or a context is pending
   * success if the latest status for all contexts is success
   */
  def combine(statuses: Set[CommitState]): CommitState = {
    if(statuses.isEmpty){
      PENDING
    } else if(statuses.contains(CommitState.ERROR) || statuses.contains(CommitState.FAILURE)) {
      FAILURE
    } else if(statuses.contains(CommitState.PENDING)) {
      PENDING
    } else {
      SUCCESS
    }
  }
}
