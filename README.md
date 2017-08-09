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

The Kafka Message Scheduler (KMS, for short) consumes messages from a source
topic. The keys are Schedule IDs, and the values Schedule messages in Avro
format.

A schedule is composed of:
- The topic you want to send the delayed message to
- The timestamp telling when you want that message to be delivered
- The actual message to be sent, both key and value

The KMS will be responsible of sending the actual message to the specified
topic when the time comes.

The Schedule ID can be used to delete a scheduled message, via a delete
message (with a null value) in the source topic.

## Schema generation

To generate the avro schema from the Schedule case class, run `sbt schema`. The schema will appear in `avro/target/schemas/schedule.avsc`

## How to run it

### Start services

Start Kafka:

`docker run -p 2181:2181 -p 9092:9092 -e ADVERTISED_HOST=127.0.0.1 -e ADVERTISED_PORT=9092 spotify/kafka`

Start KMS:

`docker run --net=host --add-host moby:127.0.0.1 -e SCHEDULE_TOPIC=scheduler skyuk/scheduler`


### Send messages

With the services running, you can send a message to `scheduler` topic. See [Schema generation](#schema-generation)
for generating the Avro schema.


### Monitoring

JMX metrics are exposed using Kamon. Port 9186 has to be exposed to obtain them.

### Topic configuration

The `schedule-topic` must be configured to use [log compaction](https://kafka.apache.org/documentation/#compaction). 
This is to allow the scheduler to delete the schedule after it has been written to its destination topic. This is very 
important because the scheduler uses the `schedule-topic` to reconstruct its state. This application will also support 
longer-term schedules so log compaction is required to ensure they are not prematurely removed from Kafka allowing the 
application to recover them after a restart.