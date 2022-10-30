# Kafka Message Scheduler

This application is a scheduler for low-frequency and long-term scheduling of delayed messages to [Kafka](https://kafka.apache.org/) topics.

## Background

This component was initially designed for Sky's metadata ingestion pipeline. We wanted to manage content expiry (for scheduled airings or on-demand assets) in one single component, instead of implementing the expiry logic on all consumers.

Given that the pipeline is based on Kafka, it felt natural to use it as input, output and data store.

## How it works

The Kafka Message Scheduler (KMS for short) consumes messages from configured source (schedule) topics. On this topic:

- message keys are "Schedule IDs" - string values, with an expectation of uniqueness
- message values are Schedule messages, encoded in Avro binary format according to the [Schema](#schema).

A schedule is composed of:

- The topic you want to send the delayed message to
- The timestamp telling when you want that message to be delivered
- The actual message to be sent, both key and value

The KMS is responsible for sending the actual message to the specified topic at the specified time.

The Schedule ID can be used to delete a scheduled message, via a delete message (with a null message value)
in the source topic.

### Startup logic

When the KMS starts up it uses the [kafka-topic-loader](https://github.com/sky-uk/kafka-topic-loader) to consume all messages from the configured `schedule-topics` and populate the scheduling actors state. Once this has completed, all of the schedules loaded are scheduled and the application will start normal processing. This means that schedules that have been fired and tombstoned, but not compacted yet, will not be replayed during startup.

## Schema

To generate the avro schema from the Schedule case class, run `sbt schema`. The schema will be written to
`avro/target/schemas/schedule.avsc`.

## How to run it

### Start services

```bash
docker-compose pull && docker-compose up -d
```

### Send messages

With the services running, you can send a message to the defined scheduler topic (`scheduler` in the example
above). See the [Schema](#schema) section for details of generating the Avro schema to be used.

### Monitoring

Metrics are exposed and reported using Kamon. By default, the [Kamon Prometheus reporter](https://kamon.io/docs/latest/reporters/prometheus/) is used for reporting and the scraping endpoint for Prometheus is exposed on port `9095` (this is configurable by setting the `PROMETHEUS_SCRAPING_ENDPOINT_PORT` environment variable).

Prometheus is included as part of the docker-compose and will expose a monitoring dashboard on port `9090`.

#### Startup metrics

Given the scheduler may take some time to recreate it's internal state, the following metric is available to monitor the startup process:

- `scheduler_startup`

This will be a value of 0-2 depending on the state of the startup:

- 0 - The scheduler is loading
- 1 - The scheduler has finished loading
- 2 - The scheduler failed to recreate it's state

### Topic configuration

The `schedule-topics` must be configured to use [log compaction](https://kafka.apache.org/documentation/#compaction). This is for two reasons:

1.  to allow the scheduler to delete the schedule after it has been written to its destination topic.
2.  because the scheduler uses the `schedule-topics` to reconstruct its state - in case of a restart of the
    KMS, this ensures that schedules are not lost.

#### Recommended configuration

It is advised that the log compaction configuration of the `schedule-topics` is quite aggressive to keep the restart times low, see below for recommended configuration:

```
cleanup.policy: compact
delete.retention.ms: 3600000
min.compaction.lag.ms: 0
min.cleanable.dirty.ratio: "0.1"
segment.ms: 86400000
segment.bytes: 100000000
```

## Limitations

Until [this issue](/../../issues/69) is addressed the KMS does not fully support horizontal scaling. Multiple instances can be run, and Kafka will balance the partitions, however schedules are likely to be duplicated as when a rebalance happens the state for the rebalanced partition will not be removed from the original instance. If there is a desire to run multiple instances before that issue is addressed, it is best to not attempt dynamic scaling, but to start with your desired number of instances.
