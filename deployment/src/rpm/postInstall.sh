#!/bin/sh

/usr/bin/systemctl link /opt/trellis/etc/trellis-db.service
/usr/bin/systemctl daemon-reload

echo "In order to complete the setup of Trellis, please configure the database connection in /opt/trellis/etc/config.yml\n\n"
echo "Once the configuration is correct, you can start Trellis with this command:\n\n"
echo "    systemctl start trellis-db\n"
