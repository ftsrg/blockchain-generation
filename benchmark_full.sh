#!/usr/bin/env bash

for i in $(seq 1 6); do
  ./refinery/bin/refinery-blockchain -p "raft_size$i" -a analysis_raft.lp -t 300 -w 5 -r 30
done

for i in $(seq 1 3); do
  ./refinery/bin/refinery-blockchain -p "kafka_size$i" -a analysis_kafka.lp -t 300 -w 5 -r 30
done