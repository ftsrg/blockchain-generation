#!/usr/bin/env bash

./refinery/bin/refinery-blockchain -p raft_size1 -a analysis_raft.lp -t 60 -w 0 -r 10
./refinery/bin/refinery-blockchain -p raft_size2 -a analysis_raft.lp -t 60 -w 0 -r 10
./refinery/bin/refinery-blockchain -p kafka_size1 -a analysis_kafka.lp -t 60 -w 0 -r 10