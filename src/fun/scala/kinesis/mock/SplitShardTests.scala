package kinesis.mock

import scala.concurrent.duration._
import scala.jdk.CollectionConverters._

import cats.effect.IO
import software.amazon.awssdk.services.kinesis.model._

import kinesis.mock.syntax.javaFuture._

class SplitShardTests extends AwsFunctionalTests {

  fixture.test("It should split a shard") { resources =>
    for {
      shard <- resources.kinesisClient
        .listShards(
          ListShardsRequest
            .builder()
            .streamName(resources.streamName.streamName)
            .build()
        )
        .toIO
        .map(_.shards().asScala.head)
      _ <- resources.kinesisClient
        .splitShard(
          SplitShardRequest
            .builder()
            .streamName(resources.streamName.streamName)
            .newStartingHashKey(
              (BigInt(shard.hashKeyRange().endingHashKey()) / BigInt(2))
                .toString()
            )
            .shardToSplit(shard.shardId())
            .build()
        )
        .toIO
      _ <- IO.sleep(resources.cacheConfig.splitShardDuration.plus(400.millis))
      openShards <- resources.kinesisClient
        .listShards(
          ListShardsRequest
            .builder()
            .streamName(resources.streamName.streamName)
            .build()
        )
        .toIO
        .map(
          _.shards().asScala.toVector.filter(
            _.sequenceNumberRange()
              .endingSequenceNumber() == null // scalafix:ok
          )
        )
    } yield assert(
      openShards.length == 4,
      s"$openShards"
    )
  }
}
