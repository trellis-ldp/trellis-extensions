#!/bin/sh

/usr/bin/systemctl stop trellis-db
/usr/bin/systemctl disable trellis-db
/usr/bin/systemctl daemon-reload
/usr/bin/systemctl reset-failed
