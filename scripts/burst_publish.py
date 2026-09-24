#!/usr/bin/env python3
"""
Burst-publishes readings to an MQTT topic to deliberately overwhelm the
bounded queue in ReadingWorkerPool, for testing backpressure under load.

Usage:
    pip install paho-mqtt
    python3 scripts/burst_publish.py --device-id BOMX1 --count 500
"""

import argparse
import json
import subprocess
import time

import paho.mqtt.client as mqtt


def reading_count(container, db_user, db_name, device_id):
    result = subprocess.run(
        [
            "docker", "exec", container,
            "psql", "-U", db_user, "-d", db_name, "-t", "-c",
            f"select count(*) from reading where device_id='{device_id}';",
        ],
        capture_output=True, text=True, check=True,
    )
    return int(result.stdout.strip())


def main():
    parser = argparse.ArgumentParser(description="Burst-publish MQTT readings and measure drain time")
    parser.add_argument("--broker", default="localhost", help="MQTT broker host")
    parser.add_argument("--port", type=int, default=1883, help="MQTT broker port")
    parser.add_argument("--device-id", default="BOMX1", help="Device ID (must already exist in the DB)")
    parser.add_argument("--count", type=int, default=500, help="Number of messages to publish")
    parser.add_argument("--qos", type=int, default=1, choices=[0, 1, 2], help="MQTT QoS level")
    parser.add_argument("--db-container", default="edge-telemetry-postgres", help="Postgres container name")
    parser.add_argument("--db-user", default="telemetry", help="Postgres user")
    parser.add_argument("--db-name", default="telemetry", help="Postgres database name")
    parser.add_argument("--poll-interval", type=float, default=0.5, help="Seconds between DB polls while draining")
    parser.add_argument("--timeout", type=float, default=120.0, help="Max seconds to wait for the queue to drain")
    args = parser.parse_args()

    topic = f"devices/{args.device_id}/readings"

    baseline = reading_count(args.db_container, args.db_user, args.db_name, args.device_id)
    target = baseline + args.count
    print(f"Baseline reading count for {args.device_id}: {baseline} (target: {target})")

    client = mqtt.Client(mqtt.CallbackAPIVersion.VERSION2)
    client.connect(args.broker, args.port)
    client.loop_start()

    print(f"Publishing {args.count} messages to '{topic}' at QoS {args.qos} as fast as possible...")
    publish_start = time.monotonic()

    for i in range(args.count):
        payload = json.dumps({"metricType": "temperature", "value": 20.0 + (i % 10)})
        client.publish(topic, payload, qos=args.qos)

    publish_elapsed = time.monotonic() - publish_start
    print(f"Queued {args.count} publishes with the local MQTT client in {publish_elapsed:.2f}s "
          f"({args.count / publish_elapsed:.1f} msgs/sec)")

    print("Polling DB until the app has drained the queue...")
    drain_start = time.monotonic()
    current = baseline

    while current < target:
        elapsed = time.monotonic() - drain_start
        if elapsed > args.timeout:
            print(f"Timed out after {args.timeout:.0f}s — only reached {current}/{target} readings.")
            break

        time.sleep(args.poll_interval)
        current = reading_count(args.db_container, args.db_user, args.db_name, args.device_id)
        print(f"  t={elapsed:5.1f}s  readings={current}/{target}")
    else:
        drain_elapsed = time.monotonic() - drain_start
        print(f"\nDrained all {args.count} readings in {drain_elapsed:.2f}s "
              f"({args.count / drain_elapsed:.1f} readings/sec sustained processing rate)")

    client.loop_stop()
    client.disconnect()


if __name__ == "__main__":
    main()
