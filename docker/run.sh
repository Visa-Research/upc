#!/bin/sh
BASEDIR=$(pwd)
docker run --rm -p 8545:8545 --name besu_node -v "$BASEDIR/config/genesis.json:/opt/besu/genesis.json" -v "$BASEDIR/config/config.toml:/opt/besu/config.toml" hyperledger/besu --config-file=config.toml
