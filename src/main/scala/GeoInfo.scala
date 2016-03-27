import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.stream.scaladsl.{Flow, Sink, Source}
import akka.http.scaladsl.model.{HttpResponse, HttpRequest}
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.client.RequestBuilding.Get
import akka.stream.{ActorMaterializer, Materializer}
import akka.http.scaladsl.unmarshalling.Unmarshal
import scala.concurrent.{ExecutionContextExecutor, Future}
import java.io.IOException
import scala.math._
import spray.json.DefaultJsonProtocol

case class IpInfo(
  ip: String,
  country_name: Option[String],
  city: Option[String],
  latitude: Option[Double],
  longitude: Option[Double]
)

object GeoInfoJsonProtocol extends DefaultJsonProtocol {
  implicit val ipInfoFormat = jsonFormat5(IpInfo)
}

trait GeoInfo {
  import scala.concurrent.ExecutionContext.Implicits.global
  import spray.json._
  import GeoInfoJsonProtocol._

  private def calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double = {
    val EarthRadius = 6371.0 // km
    val φ1 = toRadians(lat1)
    val φ2 = toRadians(lat2)
    val deltaφ = toRadians(lat2 - lat1)
    val deltaλ = toRadians(lon2 - lon1)
    val a = pow(sin(deltaφ / 2), 2) + cos(φ1) * cos(φ2) * pow(sin(deltaλ / 2), 2)
    val c = 2 * atan2(sqrt(a), sqrt(1 - a))
    EarthRadius * c
  }

  private def calculateDistance(ip1Info: IpInfo, ip2Info: IpInfo): Either[String, Double] = {
    (for {
      lat1 <- ip1Info.latitude
      lon1 <- ip1Info.longitude
      lat2 <- ip2Info.latitude
      lon2 <- ip2Info.longitude
    } yield calculateDistance(lat1, lat2, lon1, lon2))
      .toRight[String]("some value is not available")
  }

  protected def fetchRawGeoInfo(ip: String): Future[Either[String, String]]

  def geoInfo(ip: String): Future[Either[String, IpInfo]] = {
    fetchRawGeoInfo(ip).map {
      _.right.map { _.parseJson.convertTo[IpInfo] }
    }
  }

  def distance(ip1: String, ip2: String): Future[Either[String, Double]] = {
    val future1 = geoInfo(ip1)
    val future2 = geoInfo(ip2)

    (future1 zip future2) map {
      case (e1, e2) => {
        for {
          ipInfo1 <- e1.right
          ipInfo2 <- e2.right
          result  <- calculateDistance(ipInfo1, ipInfo2).right
        } yield result
      }
    }
  }
}

// fetch Geo info using http backend
trait HttpBackend extends GeoInfo {
  private implicit val system: ActorSystem = ActorSystem()
  private implicit val executor = system.dispatcher
  private implicit val materializer: Materializer = ActorMaterializer()

  private def freeGeoIpRequest(request: HttpRequest): Future[HttpResponse] =
    Source.single(request).via(Http().outgoingConnection("freegeoip.net", 80)).runWith(Sink.head)

  override protected def fetchRawGeoInfo(ip: String): Future[Either[String, String]] = {
    freeGeoIpRequest(Get(s"/json/$ip")).flatMap { response =>
      response.status match {
        case OK         => Unmarshal(response.entity).to[String].map(Right(_))
        case BadRequest => Future.successful(Left(s"$ip: incorrect IP format"))
        case _          => Unmarshal(response.entity).to[String].flatMap { entity =>
          val error = s"FreeGeoIP request failed with status code ${response.status} and entity $entity"
          Future.failed(new IOException(error))
        }
      }
    }
  }
}

// fetch Geo info using file backend
trait FileBackend { self: GeoInfo =>
  import java.io.File
  import scala.concurrent.ExecutionContext.Implicits.global

  private val geoInfoDir = new File(getClass.getResource("/geoinfo").toURI)

  override protected def fetchRawGeoInfo(ip: String): Future[Either[String, String]] = {
    Future {
      geoInfoDir.listFiles
        .filter(_.isFile)
        .find(_.getName.replaceFirst(".json$", "") == ip)
        .map { io.Source.fromFile(_).mkString }
        .toRight[String] { s"no file for ip: $ip is found" }
    }
  }
}

object Main {
  val geoInfoFile = new GeoInfo with FileBackend
  val geoInfoHttp = new GeoInfo with HttpBackend
}
