import akka.actor.ActorSystem
import akka.http.scaladsl.model.ContentTypes
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.testkit.ScalatestRouteTest
import com.actionml.entity.Dataset
import com.actionml.http.RestServer
import com.actionml.http.routes.DatasetsRouter
import com.actionml.service.{CreateDataset, DatasetService, DeleteDataset}
import de.heikoseeberger.akkahttpcirce.CirceSupport
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{FlatSpec, Matchers}
import scaldi.akka.AkkaInjectable
import scaldi.{Injector, Module}
import io.circe.generic.auto._

class DatasetsRouterTest extends FlatSpec with Matchers with ScalatestRouteTest with ScalaFutures with CirceSupport with AkkaInjectable{

  private val presetDatasetId = "PRESET_DATASET_ID"
  private val notExistDatasetId = "UNKNOWN_DATASET_ID"
  private val genDatasetID = "GEN_DATASET_ID"

  implicit val injector = new BaseModule

  private val routes = inject[DatasetsRouter].route

  "DatasetsRouter" should "create new dataset" in {

    Put(s"/datasets") ~> routes ~> check {
      status shouldBe Created
      contentType shouldBe ContentTypes.`application/json`
      responseAs[Dataset] should be(Dataset(genDatasetID))
    }

    Put(s"/datasets/") ~> routes ~> check {
      status shouldBe Created
      contentType shouldBe ContentTypes.`application/json`
      responseAs[Dataset] should be(Dataset(genDatasetID))
    }

    Put(s"/datasets/$presetDatasetId") ~> routes ~> check {
      status shouldBe Created
      contentType shouldBe ContentTypes.`application/json`
      responseAs[Dataset] should be(Dataset(presetDatasetId))
    }

    Post(s"/datasets/$presetDatasetId") ~> routes ~> check {
      status shouldBe Created
      contentType shouldBe ContentTypes.`application/json`
      responseAs[Dataset] should be(Dataset(presetDatasetId))
    }

  }

  "DatasetsRouter" should "delete exist dataset" in {

    Delete(s"/datasets/$presetDatasetId") ~> routes ~> check {
      status shouldBe OK
      contentType shouldBe ContentTypes.`application/json`
      responseAs[Dataset] should be(Dataset(presetDatasetId))
    }

    Delete(s"/datasets/$presetDatasetId/") ~> routes ~> check {
      status shouldBe OK
      contentType shouldBe ContentTypes.`application/json`
      responseAs[Dataset] should be(Dataset(presetDatasetId))
    }

    Delete(s"/datasets/$notExistDatasetId/") ~> Route.seal(routes) ~> check {
      println(response, status)
      status shouldBe NotFound
    }

  }

  class BaseModule extends Module{
    bind[ActorSystem] to ActorSystem("ActionMLRestServer") destroyWith(_.terminate())
    bind[RestServer] to new RestServer
    bind[DatasetsRouter] to new DatasetsRouter
    bind[DatasetService] to new MockDatasetService
  }

  class MockDatasetService(implicit inj: Injector) extends DatasetService with AkkaInjectable{
    override def receive: Receive = {
      case CreateDataset ⇒
        val dataset = Dataset(id = genDatasetID)
        log.info("Create new dataset, {}", dataset)
        sender() ! Some(dataset)

      case CreateDataset(datasetId) ⇒
        val dataset = Dataset(id = datasetId)
        log.info("Create new dataset, {}", dataset)
        sender() ! Some(dataset)

      case DeleteDataset(datasetId) ⇒
        val dataset = Dataset(id = datasetId)
        if (datasetId == presetDatasetId) {
          log.info("Delete exist dataset, {}", datasetId)
          sender() ! Some(dataset)
        } else {
          log.info("Not delete unknown dataset, {}", datasetId)
          sender() ! None
        }
    }
  }

}
