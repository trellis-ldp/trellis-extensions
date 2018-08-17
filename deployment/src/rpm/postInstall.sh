#!/bin/sh

/usr/bin/systemctl link /opt/trellis/etc/trellis-db.service
/usr/bin/systemctl daemon-reload

printf "In order to complete the setup of Trellis, please configure the database connection in /opt/trellis/etc/config.yml\n\n"
printf "Once the configuration is correct, you can initialize the database with this command:\n\n"
printf "    /opt/trelils/bin/migrate\n\n"
printf "And you can start up the system with this command:\n\n"
printf "    systemctl start trellis-db\n"
