#!/bin/bash
echo "$GH_PAT" | docker login ghcr.io -u "$GH_USERNAME" --password-stdin
