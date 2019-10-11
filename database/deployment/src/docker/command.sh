#!/bin/sh
sleep 10
/opt/trellis/bin/trellis-db db migrate /opt/trellis/etc/config.yml
/opt/trellis/bin/trellis-db server /opt/trellis/etc/config.yml
