#!/bin/sh

systemctl stop trellis-db
systemctl disable trellis-db
systemctl daemon-reload
systemctl reset-failed
