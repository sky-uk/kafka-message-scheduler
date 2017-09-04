# Kafka Message Scheduler

This application is a scheduler for low-frequency and long-term scheduling of
delayed messages to [Kafka](https://kafka.apache.org/) topics.

## Background

This component was initially designed for Sky's metadata ingestion pipeline.
We wanted to manage content expiry (for scheduled airings or on-demand assets)
in one single component, instead of implementing the expiry logic on all
consumers.

Given that the pipeline is based on Kafka, it felt natural to
use it as input, output and data store.

## How it works

The Kafka Message Scheduler (KMS, for short) consumes messages from a source topic.  On this topic:
-  message keys are "Schedule IDs" - string values, with an expectation of uniqueness
-  message values are Schedule messages, encoded in Avro binary format according to the [Schema](#schema).

A schedule is composed of:
- The topic you want to send the delayed message to
- The timestamp telling when you want that message to be delivered
- The actual message to be sent, both key and value

The KMS is responsible for sending the actual message to the specified topic at the specified time.

The Schedule ID can be used to delete a scheduled message, via a delete message (with a null message value)
in the source topic.

## Schema

To generate the avro schema from the Schedule case class, run `sbt schema`. The schema will be written to
`avro/target/schemas/schedule.avsc`.

## How to run it

### Start services

Start Kafka:

`docker run -p 2181:2181 -p 9092:9092 -e ADVERTISED_HOST=127.0.0.1 -e ADVERTISED_PORT=9092 spotify/kafka`

Start KMS:

`docker run --net=host --add-host moby:127.0.0.1 -e SCHEDULE_TOPIC=scheduler skyuk/kafka-message-scheduler`

### Send messages

With the services running, you can send a message to the defined scheduler topic (`scheduler` in the example
above). See the [Schema](#schema) section for details of generating the Avro schema to be used.

### Monitoring

JMX metrics are exposed using Kamon. Port 9186 has to be exposed to obtain them.

### Topic configuration

The `schedule-topic` must be configured to use [log compaction](https://kafka.apache.org/documentation/#compaction). 
This is for two reasons:
1.  to allow the scheduler to delete the schedule after it has been written to its destination topic.
2.  because the scheduler uses the `schedule-topic` to reconstruct its state - in case of a restart of the
    KMS, this ensures that schedules are not lost.
    
### Restart logic

Due to the restart logic described above, the KMS Kafka consumer must *never* commit offsets. This is why we use the 
`plainSource` instead of the `committableSource` from [reactive-kafka](https://github.com/akka/reactive-kafka). 

To allow for the restart logic to work as intended you must ensure that the `enable.auto.commit` Kafka consumer property is 
set to false. This combined with the `plainSource` ensures that consumer offsets are never committed, allowing the 
application to consume from the beginning of the schedule topic every time it is restarted. 
