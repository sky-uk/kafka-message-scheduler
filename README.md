# Kafka Message Scheduler

Fs2 design ideas:

## JSON Schema

Pros

- Easier to integrate, no need for custom Avro OffsetDateTime codecs etc
- Vanilla Kafka with no Avro
- Still b64 encode payloads

Cons

- No Schema, can break with upgrades. Will need to think about how to version (maybe a separate lib that produces the
  schema?)
- Will need to compare payload sizes, might be bigger?

## Scheduling

Pros

Cons

- No dedicated Cats Effect Scheduling Library