#!/bin/sh

systemctl link /opt/trellis/etc/trellis-db.service
systemctl daemon-reload

echo "In order to complete the setup of Trellis, please configure the database connection in /opt/trellis/etc/config.yml\n\n"
echo "Once the configuration is correct, you can initialize the database with this command:\n\n"
echo "    /opt/trelils/bin/migrate\n\n"
echo "And you can start up the system with this command:\n\n"
echo "    systemctl start trellis-db\n"
