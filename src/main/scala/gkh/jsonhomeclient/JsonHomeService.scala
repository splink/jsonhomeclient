package gkh.jsonhomeclient

import com.damnhandy.uri.template.UriTemplate

/**
 * The json-home service allows to resolve urls (href/href-template) for a given json-home host and a given
 * link releation type.
 *
 * @author <a href="mailto:martin.grotzke@inoio.de">Martin Grotzke</a>
 */
class JsonHomeService(cachesByHost: Map[JsonHomeHost, JsonHomeCache]) {

  /**
   * Determines the url (json-home "href") for the given json home host and the given direct link relation.
   */
  def getUrl(host: JsonHomeHost, relation: DirectLinkRelationType): Option[String] = {
    cachesByHost.get(host).flatMap(_.getUrl(relation))
  }

  /**
   * Determines the url (json-home "href-template") for the given json home host and the given template link relation.
   * The href template variables are replaced using the provided params.
   */
  def getUrl(host: JsonHomeHost, relation: TemplateLinkRelationType, params: Map[String, Any]): Option[String] = {
    cachesByHost.get(host).flatMap(_.getUrl(relation)).map { hrefTemplate =>
      params.foldLeft(UriTemplate.fromTemplate(hrefTemplate))((res, param) => res.set(param._1, param._2)).expand()
    }
  }

}

object JsonHomeService {

  def apply(caches: Seq[JsonHomeCache]): JsonHomeService = {
    val cachesByHost = caches.foldLeft(Map.empty[JsonHomeHost, JsonHomeCache]) { (res, cache) =>
      res + (cache.host -> cache)
    }
    new JsonHomeService(cachesByHost)
  }

}