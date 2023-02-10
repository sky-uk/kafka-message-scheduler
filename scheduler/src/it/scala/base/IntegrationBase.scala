package base

import com.danielasfregola.randomdatagenerator.RandomDataGenerator
import com.sky.kms.base.KafkaIntSpecBase
import io.github.embeddedkafka.Codecs.stringDeserializer
import io.github.embeddedkafka.EmbeddedKafkaConfig
import org.scalatest._
import org.scalatest.concurrent.Eventually
import org.scalatest.featurespec.FixtureAnyFeatureSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.concurrent.ScalaFutures
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.TopicPartition

import scala.concurrent.duration._
import scala.jdk.CollectionConverters._

abstract class IntegrationBase
    extends FixtureAnyFeatureSpec
    with fixture.ConfigMapFixture
    with BeforeAndAfterEach
    with Matchers
    with RandomDataGenerator
    with ScalaFutures
    with Eventually
    with KafkaIntSpecBase {

  override implicit val patienceConfig: PatienceConfig = PatienceConfig(kafkaConsumerTimeout, interval = 200.millis)

  override implicit lazy val kafkaConfig: EmbeddedKafkaConfig = EmbeddedKafkaConfig(kafkaPort = 9093)

  def seekToEnd(consumer: KafkaConsumer[String, String], topics: List[String]): Unit = {
    val topicPartitions =
      topics.flatMap(topic => consumer.partitionsFor(topic).asScala.map(i => new TopicPartition(topic, i.partition)))
    consumer.assign(topicPartitions.asJava)
    consumer.seekToEnd(topicPartitions.asJava)
    topicPartitions.foreach(consumer.position)
    consumer.commitSync()
    consumer.unsubscribe()
  }

  override def afterEach(): Unit = {
    super.afterEach()
    withConsumer[String, String, Unit] { consumer =>
      seekToEnd(
        consumer,
        allTopics.map(_.value)
      )
    }
  }
}
