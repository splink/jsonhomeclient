package gkh.jsonhomeclient

import scala.language.postfixOps
import scala.concurrent.ExecutionContext.Implicits.global
import akka.actor.ActorSystem
import scala.util.control.NonFatal
import scala.util.{Try, Failure, Success}
import java.util.concurrent.atomic.AtomicReference
import scala.concurrent.duration._
import play.api.libs.json.JsValue
import scala.concurrent.Await
import play.api.Logger

/**
 * A cache that regularly reloads a json-home document, so that queries on the json-home document are
 * performed in memory and do not cause any I/O.
 *
 * @author <a href="mailto:martin.grotzke@inoio.de">Martin Grotzke</a>
 */
class JsonHomeCache(client: JsonHomeClient, system: ActorSystem, updateInterval: FiniteDuration = 30 minutes) {

  @volatile
  private var relsToUrls: Option[Map[LinkRelationType, String]] = None

  system.scheduler.schedule(0 seconds, updateInterval) {
    client.jsonHome().onComplete {
      case Success(jsonHome) => buildAndSetLinkRelationTypeMap(jsonHome)
      case Failure(t) => onFailure(t)
    }
  }

  private def onFailure(t: Throwable): Unit = {
    Logger.warn(s"An error has occured while loading json home from ${client.host}: $t")
    // Set to some empty map so that getUrl does not always block for 10 seconds! This would lead to
    // a request time > 10 sec for each request.
    if(relsToUrls.isEmpty) {
      relsToUrls = Some(Map.empty)
    }
  }

  private def buildAndSetLinkRelationTypeMap(jsonHome: JsValue) {
    Logger.debug(s"Got json home from ${client.host.jsonHomeUri}: $jsonHome}")
    val map = client.host.rels.foldLeft(Map.empty[LinkRelationType, String]) { (res, rel) =>
      getLinkUrl(jsonHome, rel) match {
        case Some(url) => res + (rel -> url)
        case None => res
      }
    }
    if (relsToUrls.isEmpty || map != relsToUrls.get) {
      Logger.debug(s"Setting rel->url map: $map")
      relsToUrls = Some(map)
    }
  }

  private def getLinkUrl(json: JsValue, linkRelation: LinkRelationType): Option[String] = {
    linkRelation match {
      case DirectLinkRelationType(_) => (json \ "resources" \ linkRelation.name \ "href").asOpt[String]
      case TemplateLinkRelationType(_) => (json \ "resources" \ linkRelation.name \ "href-template").asOpt[String]
    }
  }

  def host: JsonHomeHost = client.host

  def getUrl(rel: LinkRelationType): Option[String] = {
    if (relsToUrls.isEmpty) {
      // eagerly load data from client if not here yet due to async loading from scheduler
      Try(buildAndSetLinkRelationTypeMap(Await.result(client.jsonHome(), 10 seconds))).recover {
        case NonFatal(e) => onFailure(e)
      }
    }
    relsToUrls.flatMap(_.get(rel))
  }

}
