# Kafka Message Scheduler

This application is a scheduler for low-frequency and long-term scheduling of delayed messages
to [Kafka](https://kafka.apache.org/) topics.

## How it works

The Kafka Message Scheduler (KMS for short) consumes messages from configured source (schedule) topics. On this topic:

- message keys are "Schedule IDs" - string values, with an expectation of uniqueness
- message values are Schedule messages, encoded in Avro binary format according to the [Schema](#schema).

A schedule is composed of:

- The topic you want to send the delayed message to
- The timestamp telling when you want that message to be delivered
- The actual message to be sent, both key and value

The KMS is responsible for sending the actual message to the specified topic at the specified time.

> [!NOTE]
>
> If the timestamp of when to deliver the message is in the past, the schedule will be sent immediately.

The Schedule ID can be used to delete a scheduled message, via a delete message (with a null message value)
in the source topic.

### Startup logic

When the KMS starts up it uses the [fs2-kafka-topic-loader](https://github.com/sky-uk/fs2-kafka-topic-loader) to consume
all messages from the configured `schedule-topics` and populate the schedule queue. Once this has completed,
all of the schedules loaded are scheduled and the application will start normal processing. This means that schedules
that have been fired and tombstoned, but not compacted yet, will not be replayed during startup.

## Schema

To generate the avro schema from the Schedule case class, run `sbt schema`. The schema will be written to
`avro/target/schemas/schedule.avsc`.

## How to run it

### Start services

To run KMS with its dependent services, run:

```shell
docker-compose up -d
```

To run a locally built KMS with its dependent services, run:

```bash
./build.sh
```

### Send messages

With the services running, you can send a message to the defined scheduler topic (`avro-schedules` or `json-schedules`
in the example above). See the [Schema](#schema) section for details of generating the Avro schema to be used.

Topics can be viewed from the locally running AKHQ instance at http://localhost:8080/.

### Monitoring

Metrics are exposed and reported using OpenTelemetry. By default, the scraping endpoint for Prometheus is exposed on
port `9401` (this is configurable by setting the `OTEL_EXPORTER_PROMETHEUS_PORT` environment variable).

Prometheus is included as part of the docker-compose and will expose a monitoring dashboard on port `9090`.

### Topic configuration

The `schedule-topics` must be configured to use [log compaction](https://kafka.apache.org/documentation/#compaction).
This is for two reasons:

1. to allow the scheduler to delete the schedule after it has been written to its destination topic.
2. because the scheduler uses the `schedule-topics` to reconstruct its state - in case of a restart of the
   KMS, this ensures that schedules are not lost.

#### Recommended configuration

It is advised that the log compaction configuration of the `schedule-topics` is quite aggressive to keep the restart
times low, see below for recommended configuration:

```
cleanup.policy: compact
delete.retention.ms: 3600000
min.compaction.lag.ms: 0
min.cleanable.dirty.ratio: "0.1"
segment.ms: 86400000
segment.bytes: 100000000
```

## Limitations

The KMS does not fully support horizontal scaling. Multiple instances
can be run, and Kafka will balance the partitions, however schedules are likely to be duplicated as when a rebalance
happens the state for the rebalanced partition will not be removed from the original instance. If there is a desire to
run multiple instances before that issue is addressed, it is best to not attempt dynamic scaling, but to start with your
desired number of instances.