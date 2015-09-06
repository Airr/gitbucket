package gitbucket.core.service

import gitbucket.core.model.{Issue, PullRequest}
import gitbucket.core.model.Profile._
import gitbucket.core.util.JGitUtil
import profile.api._


trait PullRequestService { self: IssuesService =>
  import PullRequestService._

  def getPullRequest(owner: String, repository: String, issueId: Int)
                    : DBIO[Option[(Issue, PullRequest)]] =
    for {
      issue   <- getIssue(owner, repository, issueId.toString)
      pullreq <- PullRequests.filter(_.byPrimaryKey(owner, repository, issueId)).result.headOption

    } yield issue.flatMap { x =>
      pullreq.map(x -> _)
    }

  def updateCommitId(owner: String, repository: String, issueId: Int, commitIdTo: String, commitIdFrom: String)
                    : DBIO[Int] =
    PullRequests.filter(_.byPrimaryKey(owner, repository, issueId))
                .map(pr => pr.commitIdTo -> pr.commitIdFrom)
                .update((commitIdTo, commitIdFrom))

  def getPullRequestCountGroupByUser(closed: Boolean, owner: Option[String], repository: Option[String])
                                    : DBIO[Seq[PullRequestCount]] =
    PullRequests
      .join(Issues).on { (t1, t2) => t1.byPrimaryKey(t2.userName, t2.repositoryName, t2.issueId) }
      .filter { case (t1, t2) =>
        (t2.closed         === closed.bind) &&
        (t1.userName       === owner.get.bind, owner.isDefined) &&
        (t1.repositoryName === repository.get.bind, repository.isDefined)
      }
      .groupBy { case (t1, t2) => t2.openedUserName }
      .map { case (userName, t) => userName -> t.length }
      .sortBy(_._2 desc)
      .result
      .map { _.map ( x => PullRequestCount(x._1, x._2) ) }

  def createPullRequest(originUserName: String, originRepositoryName: String, issueId: Int,
        originBranch: String, requestUserName: String, requestRepositoryName: String, requestBranch: String,
        commitIdFrom: String, commitIdTo: String): DBIO[Int] =
    PullRequests += PullRequest(
      originUserName,
      originRepositoryName,
      issueId,
      originBranch,
      requestUserName,
      requestRepositoryName,
      requestBranch,
      commitIdFrom,
      commitIdTo)

  def getPullRequestsByRequest(userName: String, repositoryName: String, branch: String, closed: Boolean)
                              : DBIO[Seq[PullRequest]] =
    PullRequests
      .join(Issues).on { (t1, t2) => t1.byPrimaryKey(t2.userName, t2.repositoryName, t2.issueId) }
      .filter { case (t1, t2) =>
        (t1.requestUserName       === userName.bind) &&
        (t1.requestRepositoryName === repositoryName.bind) &&
        (t1.requestBranch         === branch.bind) &&
        (t2.closed                === closed.bind)
      }
      .map { case (t1, t2) => t1 }
      .result

  /**
   * for repository viewer.
   * 1. find pull request from from `branch` to othre branch on same repository
   *   1. return if exists pull request to `defaultBranch`
   *   2. return if exists pull request to othre branch
   * 2. return None
   */
  def getPullRequestFromBranch(userName: String, repositoryName: String, branch: String, defaultBranch: String)
                              : DBIO[Option[(PullRequest, Issue)]] =
    PullRequests
      .join(Issues).on { (t1, t2) => t1.byPrimaryKey(t2.userName, t2.repositoryName, t2.issueId) }
      .filter { case (t1, t2) =>
        (t1.requestUserName       === userName.bind) &&
        (t1.requestRepositoryName === repositoryName.bind) &&
        (t1.requestBranch         === branch.bind) &&
        (t1.userName              === userName.bind) &&
        (t1.repositoryName        === repositoryName.bind) &&
        (t2.closed                === false.bind)
      }
      .sortBy{ case (t1, t2) => t1.branch =!= defaultBranch.bind }
      .result
      .headOption

  /**
   * Fetch pull request contents into refs/pull/${issueId}/head and update pull request table.
   */
  def updatePullRequests(owner: String, repository: String, branch: String): DBIO[Unit] =
    getPullRequestsByRequest(owner, repository, branch, false).flatMap { pullreq =>
      DBIO.seq(pullreq.map { x =>
        for {
          exists <- Repositories.filter(_.byRepository(x.userName, x.repositoryName)).exists.result
          if exists
          (commitIdTo, commitIdFrom) = JGitUtil.updatePullRequest(
            x.userName, x.repositoryName, x.branch, x.issueId,
            x.requestUserName, x.requestRepositoryName, x.requestBranch)
          _ <- updateCommitId(x.userName, x.repositoryName, x.issueId, commitIdTo, commitIdFrom)

        } yield ()
      }: _*)
    }

  def getPullRequestByRequestCommit(userName: String, repositoryName: String, toBranch:String, fromBranch: String, commitId: String)
                                   : DBIO[Option[(PullRequest, Issue)]] = {
    if(toBranch == fromBranch){
      DBIO.successful(None)
    } else {
      PullRequests
        .join(Issues).on { (t1, t2) => t1.byPrimaryKey(t2.userName, t2.repositoryName, t2.issueId) }
        .filter { case (t1, t2) =>
          (t1.userName              === userName.bind) &&
          (t1.repositoryName        === repositoryName.bind) &&
          (t1.branch                === toBranch.bind) &&
          (t1.requestUserName       === userName.bind) &&
          (t1.requestRepositoryName === repositoryName.bind) &&
          (t1.requestBranch         === fromBranch.bind) &&
          (t1.commitIdTo            === commitId.bind)
        }
        .result
        .headOption
    }
  }
}

object PullRequestService {

  val PullRequestLimit = 25

  case class PullRequestCount(userName: String, count: Int)

}
