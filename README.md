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

`docker-compose pull && docker-compose up -d`

### Send messages

With the services running, you can send a message to the defined scheduler topic (`scheduler` in the example
above). See the [Schema](#schema) section for details of generating the Avro schema to be used.

### Monitoring

Metrics are exposed and reported using Kamon. By default the [Kamon Prometheus reporter](http://kamon.io/documentation/1.x/reporters/prometheus/)
is used for reporting, the scraping endpoint for Prometheus is exposed on port 9095 (this is configurable by setting
the PROMETHEUS_SCRAPING_ENDPOINT_PORT environment variable).

### Topic configuration

The `schedule-topic` must be configured to use [log compaction](https://kafka.apache.org/documentation/#compaction). 
This is for two reasons:
1.  to allow the scheduler to delete the schedule after it has been written to its destination topic.
2.  because the scheduler uses the `schedule-topic` to reconstruct its state - in case of a restart of the
    KMS, this ensures that schedules are not lost.
    
### Restart logic

The KMS commits offset when schedules reach the scheduling actor. This is so that when starting up, the KMS will 
reload everything from its input topics up until its last committed offset. Once that has finished, all of those 
messages are scheduled - this prevents us from replaying already processed schedules that have not been compacted yet.
