package gitbucket.core.service

import gitbucket.core.model.CommitComment
import gitbucket.core.model.Profile._
import profile.api._


trait CommitsService {

  def getCommitComments(owner: String, repository: String, commitId: String, pullRequest: Boolean): DBIO[Seq[CommitComment]] =
    CommitComments filter {
      t => t.byCommit(owner, repository, commitId) && (t.pullRequest === pullRequest || pullRequest)
    } result

  def getCommitComment(owner: String, repository: String, commentId: String): DBIO[Option[CommitComment]] =
    if (commentId forall (_.isDigit))
      CommitComments filter { t =>
        t.byPrimaryKey(commentId.toInt) && t.byRepository(owner, repository)
      } result headOption
    else
      DBIO successful None

  def createCommitComment(owner: String, repository: String, commitId: String, loginUser: String,
    content: String, fileName: Option[String], oldLine: Option[Int], newLine: Option[Int], pullRequest: Boolean): DBIO[Int] =
    CommitComments.autoInc += CommitComment(
      userName          = owner,
      repositoryName    = repository,
      commitId          = commitId,
      commentedUserName = loginUser,
      content           = content,
      fileName          = fileName,
      oldLine           = oldLine,
      newLine           = newLine,
      registeredDate    = currentDate,
      updatedDate       = currentDate,
      pullRequest       = pullRequest)

  def updateCommitComment(commentId: Int, content: String): DBIO[Int] =
    CommitComments
      .filter (_.byPrimaryKey(commentId))
      .map { t =>
      t.content -> t.updatedDate
    }.update (content, currentDate)

  def deleteCommitComment(commentId: Int): DBIO[Int] =
    CommitComments filter (_.byPrimaryKey(commentId)) delete
}
