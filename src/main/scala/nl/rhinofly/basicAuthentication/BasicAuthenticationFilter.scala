package nl.rhinofly.basicAuthentication

import java.util.UUID

import scala.concurrent.Future

import org.apache.commons.codec.binary.Base64

import play.api.Configuration
import play.api.http.HeaderNames.AUTHORIZATION
import play.api.http.HeaderNames.WWW_AUTHENTICATE
import play.api.mvc.Filter
import play.api.mvc.RequestHeader
import play.api.mvc.Result
import play.api.mvc.Results.Unauthorized

class BasicAuthenticationFilter(configurationFactory: => BasicAuthenticationFilterConfiguration) extends Filter {

  def apply(next: RequestHeader => Future[Result])(requestHeader: RequestHeader): Future[Result] =
    if (configuration.enabled && !isAuthorized(requestHeader))
      Future successful Unauthorized.withHeaders(WWW_AUTHENTICATE -> realm)
    else next(requestHeader)

  private lazy val configuration = configurationFactory

  private def isAuthorized(requestHeader: RequestHeader) =
    requestHeader.headers.get(AUTHORIZATION)
      .map(_ == expectedHeaderValue)
      .getOrElse(false)

  private lazy val expectedHeaderValue = {
    val combined = configuration.username + ":" + configuration.password
    val credentials = Base64.encodeBase64String(combined.getBytes)
    basic(credentials)
  }

  private def realm = basic(s"""realm=\"${configuration.realm}"""")

  private def basic(content: String) = s"Basic $content"
}

object BasicAuthenticationFilter {
  def apply() = new BasicAuthenticationFilter(
    BasicAuthenticationFilterConfiguration.parse(
      play.api.Play.current.configuration
    )
  )
}

case class BasicAuthenticationFilterConfiguration(
  realm: String,
  enabled: Boolean,
  username: String,
  password: String)

object BasicAuthenticationFilterConfiguration {
  
  private val defaultRealm = "Application"
  private def credentialsMissingRealm(realm:String) = 
    s"$realm: The username or password could not be found in the configuration."
  
  def parse(configuration: Configuration) = {

    val root = "basicAuthentication."
    def boolean(key: String) = configuration.getBoolean(root + key)
    def string(key: String) = configuration.getString(root + key)

    val enabled = boolean("enabled").getOrElse(true)

    val credentials: Option[(String, String)] = for {
      username <- string("username")
      password <- string("password")
    } yield (username, password)

    val (username, password) = {
      def uuid = UUID.randomUUID.toString
      credentials.getOrElse((uuid, uuid))
    }

    def realm(hasCredentials:Boolean) = {
      val realm = string("realm").getOrElse(defaultRealm)
      if (hasCredentials) realm
      else credentialsMissingRealm(realm)
    }

    BasicAuthenticationFilterConfiguration(
      realm(credentials.isDefined),
      enabled,
      username,
      password
    )
  }
}