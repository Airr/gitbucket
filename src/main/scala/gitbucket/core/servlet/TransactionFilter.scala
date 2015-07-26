package gitbucket.core.servlet

import javax.servlet._
import javax.servlet.http.HttpServletRequest
import org.scalatra.ScalatraBase
import org.slf4j.LoggerFactory
import slick.jdbc.JdbcBackend.{Database => SlickDatabase, Session}
import gitbucket.core.util.Keys

/**
 * Controls the transaction with the open session in view pattern.
 */
class TransactionFilter extends Filter {

  private val logger = LoggerFactory.getLogger(classOf[TransactionFilter])

  def init(config: FilterConfig) = {}

  def destroy(): Unit = {}

  def doFilter(req: ServletRequest, res: ServletResponse, chain: FilterChain): Unit = {
    if(req.asInstanceOf[HttpServletRequest].getServletPath().startsWith("/assets/")){
      // assets don't need transaction
      chain.doFilter(req, res)
    } else {
      Database() withTransaction { session =>
        // Register Scalatra error callback to rollback transaction
        ScalatraBase.onFailure { _ =>
          logger.debug("Rolled back transaction")
          session.rollback()
        }(req.asInstanceOf[HttpServletRequest])

        logger.debug("begin transaction")
        req.setAttribute(Keys.Request.DBSession, session)
        chain.doFilter(req, res)
        logger.debug("end transaction")
      }
    }
  }

}

object Database {

  private val logger = LoggerFactory.getLogger(Database.getClass)

  private lazy val db: SlickDatabase = SlickDatabase.forConfig("db")

  def apply(): SlickDatabase = db

  // TODO
  def getSession(req: ServletRequest): Session =
    req.getAttribute(Keys.Request.DBSession).asInstanceOf[Session]

  def close(): Unit = db.close

}
