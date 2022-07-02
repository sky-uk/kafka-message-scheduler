package base

import cats.scalatest.{EitherMatchers, EitherValues}
import com.danielasfregola.randomdatagenerator.RandomDataGenerator
import io.github.embeddedkafka.EmbeddedKafka.createCustomTopic
import org.scalatest._
import org.scalatest.concurrent.Eventually
import org.scalatest.featurespec.FixtureAnyFeatureSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.concurrent.ScalaFutures
import utils.KafkaUtils

import scala.concurrent.duration._

abstract class IntegrationBase
    extends FixtureAnyFeatureSpec
    with fixture.ConfigMapFixture
    with BeforeAndAfterAll
    with BeforeAndAfterEachTestData
    with Matchers
    with EitherValues
    with EitherMatchers
    with OptionValues
    with LoneElement
    with RandomDataGenerator
    with ScalaFutures
    with Eventually
    with KafkaUtils {
  val timeout: Duration                                = 60.seconds
  override implicit val patienceConfig: PatienceConfig = PatienceConfig(timeout, interval = 200.millis)

  override def beforeAll(): Unit = {
    super.beforeAll()
    createCustomTopic(outputTopic)
  }
}
