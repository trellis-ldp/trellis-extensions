#!/bin/sh

systemctl link /opt/trellis/etc/trellis-db.service
systemctl daemon-reload

echo "In order to complete the setup of Trellis, please configure the database connection in /opt/trellis/etc/config.yml\n\n"
echo "Once the configuration is correct, you can initialize the database with these commands:\n\n"
echo "    /opt/trelils/bin/trellis-db db migrate /opt/trellis/etc/config.yml\n"
echo "    systemctl start trellis-db\n"
